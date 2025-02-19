package com.rarible.protocol.union.enrichment.service

import com.rarible.core.common.optimisticLock
import com.rarible.core.logging.asyncWithTraceId
import com.rarible.protocol.union.core.model.UnionItem
import com.rarible.protocol.union.core.model.UnionMeta
import com.rarible.protocol.union.core.model.UnionOrder
import com.rarible.protocol.union.core.service.ItemService
import com.rarible.protocol.union.core.service.OriginService
import com.rarible.protocol.union.core.service.router.BlockchainRouter
import com.rarible.protocol.union.dto.AuctionDto
import com.rarible.protocol.union.dto.AuctionIdDto
import com.rarible.protocol.union.dto.CollectionIdDto
import com.rarible.protocol.union.dto.ItemDto
import com.rarible.protocol.union.dto.ItemIdDto
import com.rarible.protocol.union.dto.OrderIdDto
import com.rarible.protocol.union.enrichment.converter.ItemDtoConverter
import com.rarible.protocol.union.enrichment.custom.collection.CustomCollectionResolutionRequest
import com.rarible.protocol.union.enrichment.custom.collection.CustomCollectionResolver
import com.rarible.protocol.union.enrichment.download.DownloadEntry
import com.rarible.protocol.union.enrichment.download.DownloadStatus
import com.rarible.protocol.union.enrichment.meta.content.ContentMetaService
import com.rarible.protocol.union.enrichment.meta.item.ItemMetaMetrics
import com.rarible.protocol.union.enrichment.meta.item.ItemMetaPipeline
import com.rarible.protocol.union.enrichment.meta.item.ItemMetaService
import com.rarible.protocol.union.enrichment.meta.item.MetaTrimmer
import com.rarible.protocol.union.enrichment.model.MetaDownloadPriority
import com.rarible.protocol.union.enrichment.model.ShortItem
import com.rarible.protocol.union.enrichment.model.ShortItemId
import com.rarible.protocol.union.enrichment.repository.ItemRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toSet
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException

@Component
class EnrichmentItemService(
    private val itemServiceRouter: BlockchainRouter<ItemService>,
    private val itemRepository: ItemRepository,
    private val enrichmentOrderService: EnrichmentOrderService,
    private val enrichmentAuctionService: EnrichmentAuctionService,
    private val itemMetaService: ItemMetaService,
    private val itemMetaTrimmer: MetaTrimmer,
    private val contentMetaService: ContentMetaService,
    private val originService: OriginService,
    private val customCollectionResolver: CustomCollectionResolver,
    private val metrics: ItemMetaMetrics,
    private val enrichmentHelperService: EnrichmentHelperService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun get(itemId: ShortItemId): ShortItem? {
        return itemRepository.get(itemId)
    }

    suspend fun getOrCreateWithLastUpdatedAtUpdate(itemId: ShortItemId): ShortItem = optimisticLock {
        val item = itemRepository.get(itemId) ?: ShortItem.empty(itemId)
        itemRepository.save(item.withCalculatedFields())
    }

    suspend fun getItemCollection(itemId: ShortItemId): CollectionIdDto? {
        val collectionId = itemServiceRouter.getService(itemId.blockchain)
            .getItemCollectionId(itemId.itemId) ?: return null
        return CollectionIdDto(itemId.blockchain, collectionId)
    }

    suspend fun getItemOrigins(itemId: ShortItemId): List<String> {
        val collectionId = getItemCollection(itemId)
        return originService.getOrigins(collectionId)
    }

    suspend fun getOrEmpty(itemId: ShortItemId): ShortItem {
        return itemRepository.get(itemId) ?: ShortItem.empty(itemId)
    }

    suspend fun save(item: ShortItem): ShortItem {
        return itemRepository.save(item.withCalculatedFields())
    }

    suspend fun findByPoolOrder(orderId: OrderIdDto): Set<ShortItemId> {
        return itemRepository.findByPoolOrder(orderId.blockchain, orderId.value).toSet()
    }

    suspend fun findAll(ids: List<ShortItemId>): List<ShortItem> {
        return itemRepository.getAll(ids)
    }

    suspend fun fetch(itemId: ShortItemId): UnionItem {
        return itemServiceRouter.getService(itemId.blockchain).getItemById(itemId.itemId)
    }

    suspend fun fetchOrNull(itemId: ShortItemId): UnionItem? {
        return try {
            fetch(itemId)
        } catch (e: WebClientResponseException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                null
            } else {
                throw e
            }
        }
    }

    // [orders] is a set of already fetched orders that can be used as cache to avoid unnecessary 'getById' calls
    suspend fun enrichItem(
        shortItem: ShortItem?,
        item: UnionItem? = null,
        orders: Map<OrderIdDto, UnionOrder> = emptyMap(),
        auctions: Map<AuctionIdDto, AuctionDto> = emptyMap(),
        metaPipeline: ItemMetaPipeline
    ) = coroutineScope {

        require(shortItem != null || item != null)
        val itemId = shortItem?.id?.toDto() ?: item!!.id

        val fetchedItem = asyncWithTraceId(context = NonCancellable) { item ?: fetch(ShortItemId(itemId)) }

        val metaEntry = shortItem?.metaEntry
        val meta = metaEntry?.data
        onMetaEntry(itemId, metaPipeline, metaEntry)

        val itemOrders = enrichmentHelperService.getExistingOrders(shortItem)
        val bestOrders = enrichmentOrderService.fetchMissingOrders(
            existing = itemOrders,
            orders = orders
        )
        val itemBestOrders = itemOrders.mapNotNull { bestOrders[it.dtoId] }.associateBy { it.id }

        val auctionIds = shortItem?.auctions ?: emptySet()
        val auctionsData = asyncWithTraceId(context = NonCancellable) {
            enrichmentAuctionService.fetchAuctionsIfAbsent(
                auctionIds,
                auctions
            )
        }

        val trimmedMeta = itemMetaTrimmer.trim(meta)
        if (meta != trimmedMeta) {
            logger.info("Received Item with large meta: $itemId")
        }

        val resolvedItem = fetchedItem.await()
        val itemHint = shortItem?.let { mapOf(itemId to it) } ?: emptyMap()
        val customCollection = customCollectionResolver.resolve(
            listOf(CustomCollectionResolutionRequest(itemId, itemId, null)),
            itemHint
        )[itemId]

        ItemDtoConverter.convert(
            item = resolvedItem,
            shortItem = shortItem,
            // replacing inner IPFS urls with public urls
            meta = contentMetaService.exposePublicUrls(trimmedMeta),
            orders = enrichmentOrderService.enrich(itemBestOrders),
            auctions = auctionsData.await(),
            customCollection = customCollection
        )
    }

    suspend fun enrichItems(
        unionItems: List<UnionItem>,
        metaPipeline: ItemMetaPipeline
    ): List<ItemDto> {
        if (unionItems.isEmpty()) {
            return emptyList()
        }

        val enrichedItems = coroutineScope {

            val shortItems: Map<ItemIdDto, ShortItem> = findAll(
                unionItems
                    .map { ShortItemId(it.id) }
            )
                .associateBy { it.id.toDto() }

            // Looking for full orders for existing items in order-indexer
            val shortOrderIds = enrichmentHelperService.getExistingOrders(shortItems.values)
                .map { it.dtoId }

            val orders = enrichmentOrderService.getByIds(shortOrderIds)
                .associateBy { it.id }

            val result = unionItems.map {
                val shortItem = shortItems[it.id]
                enrichItem(
                    shortItem = shortItem,
                    item = it,
                    orders = orders,
                    metaPipeline = metaPipeline
                )
            }
            result
        }

        return enrichedItems
    }

    private suspend fun onMetaEntry(
        itemId: ItemIdDto,
        metaPipeline: ItemMetaPipeline,
        entry: DownloadEntry<UnionMeta>?
    ) {
        when {
            // No entry - it means we see this item/meta first time, not cached at all
            entry == null -> {
                itemMetaService.schedule(
                    itemId = itemId,
                    pipeline = metaPipeline,
                    force = false,
                    // TODO potentially we can customize priority for specific collections here
                    priority = MetaDownloadPriority.RIGHT_NOW
                )
                metrics.onMetaCacheMiss(itemId.blockchain)
            }
            // Downloaded - cool, we hit cache!
            entry.status == DownloadStatus.SUCCESS -> {
                metrics.onMetaCacheHit(itemId.blockchain)
            }
            // Otherwise, downloading in progress or completely failed - mark as "empty" cache
            else -> {
                metrics.onMetaCacheEmpty(itemId.blockchain)
            }
        }
    }
}
