package com.rarible.protocol.union.worker.task

import com.rarible.core.task.RunTask
import com.rarible.core.task.TaskHandler
import com.rarible.protocol.union.core.FeatureFlagsProperties
import com.rarible.protocol.union.enrichment.configuration.EnrichmentCollectionProperties
import com.rarible.protocol.union.worker.job.CustomCollectionJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.stereotype.Component

@Component
class CustomCollectionTaskHandler(
    private val job: CustomCollectionJob,
    private val enrichmentCollectionProperties: EnrichmentCollectionProperties,
    private val ff: FeatureFlagsProperties
) : TaskHandler<String> {

    override val type = "CUSTOM_COLLECTION_MIGRATION"

    override suspend fun isAbleToRun(param: String): Boolean {
        return ff.enableCustomCollections
    }

    override fun getAutorunParams(): List<RunTask> {
        return enrichmentCollectionProperties.mappings
            .filter { it.enabled }
            .map { RunTask(it.name) }
    }

    override fun runLongTask(from: String?, param: String): Flow<String> {
        return flow {
            var next = from
            do {
                next = job.migrate(param, next)
                if (next != null) {
                    emit(next)
                }
            } while (next != null)
        }
    }
}
