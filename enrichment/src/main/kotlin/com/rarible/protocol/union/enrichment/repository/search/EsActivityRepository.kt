package com.rarible.protocol.union.enrichment.repository.search

import com.fasterxml.jackson.databind.ObjectMapper
import com.rarible.protocol.union.core.elasticsearch.EsNameResolver
import com.rarible.protocol.union.core.model.elastic.ElasticActivityFilter
import com.rarible.protocol.union.core.model.elastic.EsActivity
import com.rarible.protocol.union.core.model.elastic.EsActivityCursor.Companion.fromActivity
import com.rarible.protocol.union.core.model.elastic.EsActivityQueryResult
import com.rarible.protocol.union.core.model.elastic.EsActivitySort
import com.rarible.protocol.union.core.util.PageSize
import com.rarible.protocol.union.enrichment.repository.search.internal.EsActivityQueryBuilderService
import kotlinx.coroutines.reactive.awaitFirst
import org.springframework.data.elasticsearch.client.reactive.ReactiveElasticsearchClient
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery
import org.springframework.stereotype.Component

@Component
class EsActivityRepository(
    private val queryBuilderService: EsActivityQueryBuilderService,
    objectMapper: ObjectMapper,
    elasticsearchConverter: ElasticsearchConverter,
    esOperations: ReactiveElasticsearchOperations,
    elasticClient: ReactiveElasticsearchClient,
    esNameResolver: EsNameResolver
) : ElasticSearchRepository<EsActivity>(
    objectMapper,
    esOperations,
    esNameResolver.createEntityDefinitionExtended(EsActivity.ENTITY_DEFINITION),
    elasticsearchConverter,
    elasticClient,
    EsActivity::class.java,
    EsActivity::activityId.name,
) {

    override fun entityId(entity: EsActivity): String {
        return entity.activityId
    }

    suspend fun search(
        filter: ElasticActivityFilter,
        sort: EsActivitySort,
        limit: Int?
    ): EsActivityQueryResult {
        val query = queryBuilderService.build(filter, sort)
        query.maxResults = PageSize.ACTIVITY.limit(limit)
        query.trackTotalHits = false

        return search(query)
    }

    suspend fun search(query: NativeSearchQuery): EsActivityQueryResult {
        val activities = esOperations.search(query, EsActivity::class.java, entityDefinition.searchIndexCoordinates)
            .collectList()
            .awaitFirst()
            .map { it.content }

        val cursor = if (activities.isEmpty()) {
            null
        } else {
            activities.last().fromActivity().toString()
        }

        return EsActivityQueryResult(
            activities = activities,
            cursor = cursor
        )
    }
}
