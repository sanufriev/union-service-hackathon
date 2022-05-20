package com.rarible.protocol.union.worker.task.search

import com.rarible.core.common.mapAsync
import com.rarible.core.logging.Logger
import com.rarible.core.task.Task
import com.rarible.core.task.TaskRepository
import com.rarible.core.task.TaskStatus
import com.rarible.protocol.union.core.elasticsearch.ReindexSchedulingService
import com.rarible.protocol.union.core.model.EsActivity
import com.rarible.protocol.union.core.model.elasticsearch.EntityDefinitionExtended
import com.rarible.protocol.union.core.model.elasticsearch.EsEntity
import com.rarible.protocol.union.dto.ActivityTypeDto
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.worker.task.search.activity.ActivityTaskParam
import com.rarible.protocol.union.worker.task.search.activity.ChangeEsActivityAliasTask
import com.rarible.protocol.union.worker.task.search.activity.ChangeEsActivityAliasTaskParam
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.stereotype.Component

@Component
class ReindexService(
    private val taskRepository: TaskRepository,
    private val paramFactory: ParamFactory
) : ReindexSchedulingService {

    override suspend fun scheduleReindex(
        newIndexName: String,
        entityDefinition: EntityDefinitionExtended
    ) {
        when (entityDefinition.entity) {
            EsEntity.ACTIVITY -> {
                scheduleActivityReindex(newIndexName)
            }
            EsEntity.ORDER,
            EsEntity.COLLECTION,
            EsEntity.OWNERSHIP -> {
                throw UnsupportedOperationException("Unsupported entity ${entityDefinition.entity} reindex")
            }
        }
    }

    suspend fun scheduleActivityReindex(indexName: String) {
        val blockchains = BlockchainDto.values()
        val types = ActivityTypeDto.values()
        val taskParams = blockchains.flatMap { blockchain ->
            types.map { type ->
                ActivityTaskParam(blockchain, type, indexName)
            }
        }

        val tasks = tasks(taskParams)
        val indexSwitch = indexSwitchTask(taskParams, indexName)
        taskRepository.saveAll(tasks + indexSwitch).collectList().awaitFirst()
    }

    private suspend fun tasks(params: List<ActivityTaskParam>): List<Task> {
        return params.mapAsync {
            task(it)
        }.filterNotNull()
    }

    private suspend fun task(
        param: ActivityTaskParam
    ): Task? {
        val taskParamJson = paramFactory.toString(param)
        val taskType = EsActivity.ENTITY_DEFINITION.reindexTask

        val existing = taskRepository
            .findByTypeAndParam(taskType, taskParamJson)
            .awaitSingleOrNull()
        return if (existing == null) {
            logger.info(
                "Scheduling activity reindexing with param: {}",
                param
            )
            Task(
                type = taskType,
                param = taskParamJson,
                state = null,
                running = false,
                lastStatus = TaskStatus.NONE
            )
        } else {
            logger.info(
                "Activity reindexing with param {} already exists with id={}",
                param, existing.id.toHexString()
            )
            null
        }
    }

    private suspend fun indexSwitchTask(
        activityParams: List<ActivityTaskParam>,
        indexName: String
    ): List<Task> {
        val changeEsActivityAliasTaskParam = ChangeEsActivityAliasTaskParam(
            indexName, activityParams
        )
        val taskParamJson = paramFactory.toString(changeEsActivityAliasTaskParam)
        val taskType = ChangeEsActivityAliasTask.TYPE

        val existing = taskRepository
            .findByTypeAndParam(taskType, taskParamJson)
            .awaitSingleOrNull()
        return if (existing == null) {
            logger.info(
                "Scheduling activity index alias switch with param: {}",
                changeEsActivityAliasTaskParam
            )
            listOf(Task(
                type = taskType,
                param = taskParamJson,
                state = null,
                running = false,
                lastStatus = TaskStatus.NONE
            ))
        } else {
            logger.info(
                "Activity index alias switch with param {} already exists with id={}",
                changeEsActivityAliasTaskParam, existing.id.toHexString()
            )
            emptyList()
        }
    }

    companion object {
        val logger by Logger()
    }
}