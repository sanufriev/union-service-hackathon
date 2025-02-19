package com.rarible.protocol.union.worker.task.search.item

import com.rarible.core.task.TaskHandler
import com.rarible.core.task.TaskRepository
import com.rarible.protocol.union.core.converter.EsItemConverter.toEsItem
import com.rarible.protocol.union.core.model.elastic.EsItem
import com.rarible.protocol.union.core.service.ItemService
import com.rarible.protocol.union.core.service.router.BlockchainRouter
import com.rarible.protocol.union.core.task.ItemTaskParam
import com.rarible.protocol.union.core.util.PageSize
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.enrichment.meta.item.ItemMetaPipeline
import com.rarible.protocol.union.enrichment.repository.search.EsItemRepository
import com.rarible.protocol.union.enrichment.service.EnrichmentItemService
import com.rarible.protocol.union.worker.config.ItemReindexProperties
import com.rarible.protocol.union.worker.metrics.SearchTaskMetricFactory
import com.rarible.protocol.union.worker.task.search.EsRateLimiter
import com.rarible.protocol.union.worker.task.search.ParamFactory
import com.rarible.protocol.union.worker.task.search.activity.TimePeriodContinuationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.elasticsearch.action.support.WriteRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ItemTask(
    private val properties: ItemReindexProperties,
    private val itemServiceRouter: BlockchainRouter<ItemService>,
    private val enrichmentItemService: EnrichmentItemService,
    private val paramFactory: ParamFactory,
    private val repository: EsItemRepository,
    private val searchTaskMetricFactory: SearchTaskMetricFactory,
    private val taskRepository: TaskRepository,
    private val rateLimiter: EsRateLimiter,
) : TaskHandler<String> {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val type: String
        get() = EsItem.ENTITY_DEFINITION.reindexTask

    override suspend fun isAbleToRun(param: String): Boolean {
        val blockchain = paramFactory.parse<ItemTaskParam>(param).blockchain
        return properties.isBlockchainActive(blockchain)
    }

    override fun runLongTask(from: String?, param: String): Flow<String> {
        val taskParam = paramFactory.parse<ItemTaskParam>(param)
        val blockchain = taskParam.blockchain
        val timeFrom = taskParam.from
        val timeTo = taskParam.to
        val counter = searchTaskMetricFactory.createReindexItemCounter(blockchain)
        val itemService = itemServiceRouter.getService(blockchain)
        // TODO read values from config
        val size = when (blockchain) {
            BlockchainDto.IMMUTABLEX -> 200 // Max size allowed by IMX
            else -> PageSize.ITEM.max
        }
        return if (from == "") {
            emptyFlow()
        } else {
            var continuation = from
            flow {
                do {
                    rateLimiter.waitIfNecessary(size)
                    val res = itemService.getAllItems(
                        continuation,
                        size,
                        showDeleted = true,
                        lastUpdatedFrom = null,
                        lastUpdatedTo = null
                    )
                    logger.info("Got ${res.entities} items, continuation ${res.continuation}")

                    val enrichedItems = enrichmentItemService.enrichItems(
                        res.entities,
                        ItemMetaPipeline.SYNC
                    )

                    logger.info("Enriched items: ${enrichedItems.size}")

                    if (enrichedItems.isNotEmpty()) {
                        repository.saveAll(
                            enrichedItems.map { it.toEsItem() },
                            refreshPolicy = WriteRequest.RefreshPolicy.NONE
                        )
                        counter.increment(enrichedItems.size)
                    }

                    continuation = TimePeriodContinuationHelper.adjustContinuation(
                        res.continuation,
                        timeFrom,
                        timeTo,
                        hasPrefix = false
                    )
                    emit(continuation.orEmpty())
                } while (!continuation.isNullOrBlank())
            }
                .takeWhile { taskRepository.findByTypeAndParam(type, param).awaitSingleOrNull()?.running ?: false }
        }
    }
}
