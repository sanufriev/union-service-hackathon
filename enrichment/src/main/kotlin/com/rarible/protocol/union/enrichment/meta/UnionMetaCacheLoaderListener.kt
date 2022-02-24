package com.rarible.protocol.union.enrichment.meta

import com.rarible.core.kafka.RaribleKafkaProducer
import com.rarible.loader.cache.CacheEntry
import com.rarible.loader.cache.CacheLoaderEvent
import com.rarible.loader.cache.CacheLoaderEventListener
import com.rarible.protocol.union.core.event.KafkaEventFactory
import com.rarible.protocol.union.core.model.UnionItemUpdateEvent
import com.rarible.protocol.union.core.model.UnionMeta
import com.rarible.protocol.union.core.model.UnionWrappedEvent
import com.rarible.protocol.union.core.service.ItemService
import com.rarible.protocol.union.core.service.router.BlockchainRouter
import com.rarible.protocol.union.dto.ItemIdDto
import com.rarible.protocol.union.dto.parser.IdParser
import com.rarible.protocol.union.enrichment.configuration.MetaProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@Component
class UnionMetaCacheLoaderListener(
    private val itemServiceRouter: BlockchainRouter<ItemService>,
    private val wrappedEventProducer: RaribleKafkaProducer<UnionWrappedEvent>,
    private val unionMetaLoadingAwaitService: UnionMetaLoadingAwaitService,
    private val metaProperties: MetaProperties
) : CacheLoaderEventListener<UnionMeta> {

    private val logger = LoggerFactory.getLogger(UnionMetaCacheLoaderListener::class.java)

    override val type
        get() = UnionMetaCacheLoader.TYPE

    override suspend fun onEvent(cacheLoaderEvent: CacheLoaderEvent<UnionMeta>) {
        val itemId = IdParser.parseItemId(cacheLoaderEvent.key)
        unionMetaLoadingAwaitService.onMetaEvent(itemId, cacheLoaderEvent.cacheEntry)
        sendItemUpdateEvent(itemId, cacheLoaderEvent)
    }

    private suspend fun sendItemUpdateEvent(
        itemId: ItemIdDto,
        cacheLoaderEvent: CacheLoaderEvent<UnionMeta>
    ) {
        val meta = when (val cacheEntry = cacheLoaderEvent.cacheEntry) {
            is CacheEntry.Loaded -> {
                logger.info("Loaded meta for $itemId")
                cacheEntry.data
            }
            is CacheEntry.LoadedAndUpdateFailed -> {
                logger.info("Failed to update meta for $itemId: ${cacheEntry.failedUpdateStatus.errorMessage}")
                return
            }
            is CacheEntry.InitialFailed -> {
                logger.info("Failed to load meta for $itemId: ${cacheEntry.failedStatus.errorMessage}")
                return
            }
            is CacheEntry.LoadedAndUpdateScheduled -> return
            is CacheEntry.InitialLoadScheduled -> return
            is CacheEntry.NotAvailable -> return
        }
        if (metaProperties.skipAttachingMetaInEvents) {
            logger.info("Skip meta item update event for $itemId")
            return
        }
        logger.info("Sending meta item update event for $itemId")
        val item = itemServiceRouter.getService(itemId.blockchain).getItemById(itemId.value)
        val itemWithMeta = item.copy(meta = meta)
        wrappedEventProducer.send(KafkaEventFactory.wrappedItemEvent(UnionItemUpdateEvent(itemWithMeta)))
    }
}
