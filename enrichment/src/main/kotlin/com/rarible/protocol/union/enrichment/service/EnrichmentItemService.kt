package com.rarible.protocol.union.enrichment.service

import com.mongodb.client.result.DeleteResult
import com.rarible.core.apm.CaptureSpan
import com.rarible.core.apm.SpanType
import com.rarible.core.apm.withSpan
import com.rarible.core.common.nowMillis
import com.rarible.protocol.union.core.model.UnionItem
import com.rarible.protocol.union.core.service.ItemService
import com.rarible.protocol.union.core.service.router.BlockchainRouter
import com.rarible.protocol.union.dto.AuctionDto
import com.rarible.protocol.union.dto.AuctionIdDto
import com.rarible.protocol.union.dto.CollectionIdDto
import com.rarible.protocol.union.dto.ItemIdDto
import com.rarible.protocol.union.dto.OrderDto
import com.rarible.protocol.union.dto.OrderIdDto
import com.rarible.protocol.union.dto.UnionAddress
import com.rarible.protocol.union.enrichment.converter.EnrichedItemConverter
import com.rarible.protocol.union.enrichment.meta.UnionMetaService
import com.rarible.protocol.union.enrichment.model.ShortItem
import com.rarible.protocol.union.enrichment.model.ShortItemId
import com.rarible.protocol.union.enrichment.repository.ItemRepository
import com.rarible.protocol.union.enrichment.util.spent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
@CaptureSpan(type = SpanType.APP)
class EnrichmentItemService(
    private val itemServiceRouter: BlockchainRouter<ItemService>,
    private val itemRepository: ItemRepository,
    private val enrichmentOrderService: EnrichmentOrderService,
    private val enrichmentAuctionService: EnrichmentAuctionService,
    private val unionMetaService: UnionMetaService
) {

    private val logger = LoggerFactory.getLogger(EnrichmentItemService::class.java)
    private val FETCH_SIZE = 1_000

    suspend fun get(itemId: ShortItemId): ShortItem? {
        return itemRepository.get(itemId)
    }

    suspend fun getOrEmpty(itemId: ShortItemId): ShortItem {
        return itemRepository.get(itemId) ?: ShortItem.empty(itemId)
    }

    suspend fun save(item: ShortItem): ShortItem {
        return itemRepository.save(item.withCalculatedFields())
    }

    suspend fun delete(itemId: ShortItemId): DeleteResult? {
        val now = nowMillis()
        val result = itemRepository.delete(itemId)
        logger.info("Deleting Item [{}], deleted: {} ({}ms)", itemId, result?.deletedCount, spent(now))
        return result
    }

    suspend fun findAll(ids: List<ShortItemId>): List<ShortItem> {
        return itemRepository.getAll(ids)
    }

    fun findByCollection(address: CollectionIdDto, owner: UnionAddress? = null): Flow<ShortItemId> = flow {
        var continuation: String? = null
        logger.info("Fetching all items for collection {} and owner {}", address, owner)
        var count = 0
        do {
            val page = itemServiceRouter.getService(address.blockchain)
                .getItemsByCollection(address.value, owner?.value, continuation, FETCH_SIZE)
            page.entities.map { ShortItemId(it.id) }.forEach { emit(it) }
            count += page.entities.count()
            continuation = page.continuation
        } while (continuation != null)
        logger.info("Fetched {} items for collection {} and owner {}", count, address, owner)
    }

    fun findByAuctionId(auctionIdDto: AuctionIdDto) = itemRepository.findByAuction(auctionIdDto)

    suspend fun fetch(itemId: ItemIdDto): UnionItem {
        val now = nowMillis()
        val itemDto = itemServiceRouter.getService(itemId.blockchain).getItemById(itemId.value)
        logger.info("Fetched Item by Id [{}] ({} ms)", itemId, spent(now))
        return itemDto
    }

    // [orders] is a set of already fetched orders that can be used as cache to avoid unnecessary 'getById' calls
    suspend fun enrichItem(
        shortItem: ShortItem?,
        item: UnionItem? = null,
        orders: Map<OrderIdDto, OrderDto> = emptyMap(),
        auctions: Map<AuctionIdDto, AuctionDto> = emptyMap()
    ) = coroutineScope {
        logger.info("Enriching item shortItem={}, item={}", shortItem, item)
        require(shortItem != null || item != null)
        val itemId = shortItem?.id?.toDto() ?: item!!.id
        val fetchedItem = withSpanAsync("fetchItem", spanType = SpanType.EXT) {
            item ?: fetch(itemId)
        }
        val bestSellOrder = withSpanAsync("fetchBestSellOrder", spanType = SpanType.EXT) {
            enrichmentOrderService.fetchOrderIfDiffers(shortItem?.bestSellOrder, orders)
        }
        val bestBidOrder = withSpanAsync("fetchBestBidOrder", spanType = SpanType.EXT) {
            enrichmentOrderService.fetchOrderIfDiffers(shortItem?.bestBidOrder, orders)
        }
        val meta = withSpanAsync("fetchMeta", spanType = SpanType.CACHE) {
            unionMetaService.getAvailableMetaOrScheduleLoading(itemId)
        }

        val bestOrders = listOf(bestSellOrder, bestBidOrder)
            .awaitAll().filterNotNull()
            .associateBy { it.id }

        val auctionIds = shortItem?.auctions ?: emptySet()

        val auctionsData = withSpanAsync("fetchAuction", spanType = SpanType.EXT) {
            enrichmentAuctionService.fetchAuctionsIfAbsent(auctionIds, auctions)
        }

        EnrichedItemConverter.convert(
            item = fetchedItem.await(),
            shortItem = shortItem,
            meta = meta.await(),
            orders = bestOrders,
            auctions = auctionsData.await()
        )
    }

    private fun <T> CoroutineScope.withSpanAsync(
        spanName: String,
        spanType: String = SpanType.APP,
        block: suspend () -> T
    ): Deferred<T> = async { withSpan(name = spanName, type = spanType, body = block) }
}
