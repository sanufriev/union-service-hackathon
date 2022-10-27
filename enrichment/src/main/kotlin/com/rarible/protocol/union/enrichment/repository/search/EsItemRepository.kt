package com.rarible.protocol.union.enrichment.repository.search

import com.fasterxml.jackson.databind.ObjectMapper
import com.rarible.protocol.union.core.elasticsearch.EsNameResolver
import com.rarible.protocol.union.core.model.EsItem
import com.rarible.protocol.union.core.model.EsItemFilter
import com.rarible.protocol.union.core.model.EsItemSort
import com.rarible.protocol.union.core.util.PageSize
import com.rarible.protocol.union.dto.continuation.page.Slice
import com.rarible.protocol.union.enrichment.repository.search.internal.EsEntitySearchAfterCursorService
import com.rarible.protocol.union.enrichment.repository.search.internal.EsItemQueryBuilderService
import kotlinx.coroutines.reactive.awaitFirst
import org.elasticsearch.index.query.QueryBuilders.termQuery
import org.springframework.data.elasticsearch.client.reactive.ReactiveElasticsearchClient
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations
import org.springframework.data.elasticsearch.core.SearchHit
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery
import org.springframework.stereotype.Component

@Component
class EsItemRepository(
    private val queryBuilderService: EsItemQueryBuilderService,
    private val queryCursorService: EsEntitySearchAfterCursorService,
    objectMapper: ObjectMapper,
    elasticsearchConverter: ElasticsearchConverter,
    esOperations: ReactiveElasticsearchOperations,
    elasticClient: ReactiveElasticsearchClient,
    esNameResolver: EsNameResolver
) : ElasticSearchRepository<EsItem>(
    objectMapper,
    esOperations,
    esNameResolver.createEntityDefinitionExtended(EsItem.ENTITY_DEFINITION),
    elasticsearchConverter,
    elasticClient,
    EsItem::class.java,
    EsItem::itemId.name,
) {

    override fun entityId(entity: EsItem): String {
        return entity.id
    }

    suspend fun search(filter: EsItemFilter, sort: EsItemSort, limit: Int?): Slice<EsItem> {
        val query = queryBuilderService.build(filter, sort)

        query.maxResults = PageSize.ITEM.limit(limit)
        query.trackTotalHits = false

        val searchHits = search(query)
        val cursor = queryCursorService.buildCursor(searchHits.lastOrNull())

        return Slice(
            continuation = cursor,
            entities = searchHits.map { it.content },
        )
    }

    // TODO: return lightweight EsItem type (similarly to EsActivityLite)
    suspend fun search(query: NativeSearchQuery): List<SearchHit<EsItem>> {
        return esOperations.search(query, EsItem::class.java, entityDefinition.searchIndexCoordinates)
            .collectList()
            .awaitFirst()
            //.apply { logger.debug(this.map { it.score }.joinToString()) }
    }

    suspend fun countItemsInCollection(collectionId: String): Long {
        val query = NativeSearchQuery(termQuery(EsItem::collection.name, collectionId))

        return esOperations
            .count(
                query,
                EsItem::class.java,
                entityDefinition.searchIndexCoordinates
            )
            .awaitFirst()
    }
}
