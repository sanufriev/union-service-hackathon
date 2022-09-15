package com.rarible.protocol.union.enrichment.repository.search

import com.fasterxml.jackson.databind.ObjectMapper
import com.rarible.core.apm.CaptureSpan
import com.rarible.core.apm.SpanType
import com.rarible.core.logging.Logger
import com.rarible.protocol.union.core.elasticsearch.EsHelper
import com.rarible.protocol.union.core.elasticsearch.EsRepository
import com.rarible.protocol.union.core.model.elasticsearch.EntityDefinitionExtended
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.Requests
import org.elasticsearch.common.xcontent.XContentType
import org.springframework.data.elasticsearch.BulkFailureException
import org.springframework.data.elasticsearch.client.reactive.ReactiveElasticsearchClient
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.Criteria
import org.springframework.data.elasticsearch.core.query.CriteriaQuery
import org.springframework.data.elasticsearch.core.query.Query
import java.io.IOException
import javax.annotation.PostConstruct

@CaptureSpan(type = SpanType.DB)
abstract class ElasticSearchRepository<T>(
    val objectMapper: ObjectMapper,
    val esOperations: ReactiveElasticsearchOperations,
    val entityDefinition: EntityDefinitionExtended,
    private val elasticsearchConverter: ElasticsearchConverter,
    private val elasticClient: ReactiveElasticsearchClient,
    private val entityType: Class<T>,
    private val idFieldName: String
) : EsRepository {

    protected val logger by Logger()

    private var brokenEsState: Boolean = true

    abstract fun entityId(entity: T): String

    @PostConstruct
    override fun init() = runBlocking {
        brokenEsState = try {
            !EsHelper.existsIndexesForEntity(esOperations, entityDefinition.indexRootName)
        } catch (_: Exception) {
            true
        }
    }

    suspend fun findById(id: String): T? {
        return esOperations.get(id, entityType, entityDefinition.searchIndexCoordinates).awaitFirstOrNull()
    }

    suspend fun save(entity: T): T {
        if (brokenEsState) {
            throw IllegalStateException("No indexes to save")
        }

        return esOperations.save(entity, entityDefinition.writeIndexCoordinates).awaitFirst()
    }

    @Deprecated("Use bulk() instead")
    suspend fun saveAll(
        entities: List<T>,
        indexName: String? = null,
        refreshPolicy: WriteRequest.RefreshPolicy = WriteRequest.RefreshPolicy.IMMEDIATE
    ): List<T> {
        if (entities.isEmpty()) {
            logger.info("No entities to save")
            return emptyList()
        }

        if (brokenEsState) {
            throw IllegalStateException("No indexes to save")
        }

        val bulkRequest = BulkRequest()
            .setRefreshPolicy(refreshPolicy)

        for (entity in entities) {
            val document = elasticsearchConverter.mapObject(entity)
            index(indexName).indexNames.forEach {
                bulkRequest.add(
                    Requests.indexRequest(it)
                        .id(entityId(entity))
                        .source(document, XContentType.JSON)
                        .create(false)
                )
            }
        }

        val result = elasticClient.bulk(bulkRequest).awaitFirst()

        if(result.hasFailures()) {
            logger.error(
                "Failed to saveAll [{}] entities to index [{}] with policy {}: {}",
                entities.size,
                index(indexName),
                refreshPolicy,
                result.buildFailureMessage()
            )
            throw RuntimeException(result.buildFailureMessage())
        }
        return entities
    }

    suspend fun bulk(
        entitiesToSave: List<T>,
        idsToDelete: List<String>,
        indexName: String? = null,
        refreshPolicy: WriteRequest.RefreshPolicy = WriteRequest.RefreshPolicy.IMMEDIATE,
    ) {
        if (entitiesToSave.isEmpty() && idsToDelete.isEmpty()) {
            logger.info("Nothing to save or delete")
            return
        }

        if (brokenEsState) {
            throw IllegalStateException("No indexes to save")
        }

        val bulkRequest = BulkRequest()
            .setRefreshPolicy(refreshPolicy)

        for (entity in entitiesToSave) {
            val document = elasticsearchConverter.mapObject(entity)
            index(indexName).indexNames.forEach { index ->
                bulkRequest.add(
                    Requests.indexRequest(index)
                        .id(entityId(entity))
                        .source(document, XContentType.JSON)
                        .create(false)
                )
            }
        }

        for (id in idsToDelete) {
            index(indexName).indexNames.forEach { index ->
                bulkRequest.add(
                    Requests.deleteRequest(index)
                        .id(id)
                )
            }
        }

        val result = elasticClient.bulk(bulkRequest).awaitFirst()

        if (result.hasFailures()) {
            logger.error(
                "Failed to bulk entities to index [{}] with policy {}: {}",
                index(indexName),
                refreshPolicy,
                result.buildFailureMessage()
            )
            throw RuntimeException(result.buildFailureMessage())
        }
    }

    suspend fun deleteAll(ids: List<String>): Long? {
        val query = CriteriaQuery(Criteria(idFieldName).`in`(ids))
        return esOperations.delete(
            query, entityType, entityDefinition.writeIndexCoordinates
        ).awaitFirstOrNull()?.deleted
    }

    override suspend fun refresh() {
        val refreshRequest = RefreshRequest().indices(entityDefinition.aliasName, entityDefinition.writeAliasName)

        try {
            esOperations.execute { it.indices().refreshIndex(refreshRequest) }.awaitFirstOrNull()
        } catch (e: IOException) {
            throw RuntimeException(entityDefinition.writeAliasName + " refreshModifyIndex failed", e)
        }
    }

    private fun index(indexName: String?) = indexName
        ?.let { IndexCoordinates.of(it) } ?: entityDefinition.writeIndexCoordinates
}