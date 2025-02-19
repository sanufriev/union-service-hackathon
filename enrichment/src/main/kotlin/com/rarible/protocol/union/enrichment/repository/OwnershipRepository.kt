package com.rarible.protocol.union.enrichment.repository

import com.rarible.core.apm.CaptureSpan
import com.rarible.core.apm.SpanType
import com.rarible.protocol.union.dto.PlatformDto
import com.rarible.protocol.union.enrichment.model.ItemSellStats
import com.rarible.protocol.union.enrichment.model.ShortDateIdOwnership
import com.rarible.protocol.union.enrichment.model.ShortItemId
import com.rarible.protocol.union.enrichment.model.ShortOrder
import com.rarible.protocol.union.enrichment.model.ShortOwnership
import com.rarible.protocol.union.enrichment.model.ShortOwnershipId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrElse
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.findById
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.PartialIndexFilter
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.exists
import org.springframework.data.mongodb.core.query.gt
import org.springframework.data.mongodb.core.query.inValues
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.lte
import org.springframework.stereotype.Component
import java.math.BigInteger
import java.time.Instant

@Component
@CaptureSpan(type = SpanType.DB)
class OwnershipRepository(
    private val template: ReactiveMongoTemplate
) {
    private val logger = LoggerFactory.getLogger(OwnershipRepository::class.java)

    val collection: String = template.getCollectionName(ShortOwnership::class.java)

    suspend fun createIndices() = runBlocking {
        Indices.ALL.forEach { index ->
            logger.info("Ensure index '{}' for collection '{}'", index, collection)
            template.indexOps(collection).ensureIndex(index).awaitFirst()
        }
    }

    suspend fun save(ownership: ShortOwnership): ShortOwnership {
        return template.save(ownership).awaitFirst()
    }

    suspend fun get(id: ShortOwnershipId): ShortOwnership? {
        return template.findById<ShortOwnership>(id).awaitFirstOrNull()
    }

    suspend fun getAll(ids: List<ShortOwnershipId>): List<ShortOwnership> {
        return template.find(
            Query(ShortOwnership::id inValues ids),
            ShortOwnership::class.java
        ).collectList().awaitFirst()
    }

    fun findByPlatformWithSell(
        platform: PlatformDto,
        fromOwnershipId: ShortOwnershipId?,
        fromLastUpdatedAt: Instant = Instant.now(),
        limit: Int? = null
    ): Flow<ShortOwnership> {
        val criteria = Criteria().andOperator(
            listOfNotNull(
                Criteria(Indices.BEST_SELL_PLATFORM_FIELD).exists(true),
                Criteria(Indices.BEST_SELL_PLATFORM_FIELD).isEqualTo(platform.name),
                Criteria(ShortOwnership::lastUpdatedAt.name).lte(fromLastUpdatedAt),
                fromOwnershipId?.let { Criteria.where("_id").gt(it) }
            )
        )
        val query = Query(criteria).with(
            Sort.by(
                Sort.Order.asc(Indices.BEST_SELL_PLATFORM_FIELD),
                Sort.Order.desc(ShortOwnership::lastUpdatedAt.name),
                Sort.Order.asc("_id"),
            )
        )

        limit?.let { query.limit(it) }

        return template.find(query, ShortOwnership::class.java).asFlow()
    }

    fun findWithMultiCurrency(lastUpdateAt: Instant): Flow<ShortOwnership> {
        val query = Query(
            Criteria().andOperator(
                ShortOwnership::multiCurrency isEqualTo true,
                ShortOwnership::lastUpdatedAt lte lastUpdateAt
            )
        )

        return template.find(query, ShortOwnership::class.java).asFlow()
    }

    suspend fun delete(ownershipId: ShortOwnershipId): ShortOwnership? {
        val criteria = Criteria("_id").isEqualTo(ownershipId)
        return template.findAndRemove(Query(criteria), ShortOwnership::class.java).awaitFirstOrNull()
    }

    suspend fun getItemSellStats(itemId: ShortItemId): ItemSellStats {
        val bestSellOrderField = ShortOwnership::bestSellOrder.name
        val makeStockField = ShortOrder::makeStock.name
        val query = Query(
            Criteria().andOperator(
                ShortOwnership::blockchain isEqualTo itemId.blockchain,
                ShortOwnership::itemId isEqualTo itemId.itemId,
                ShortOwnership::bestSellOrder exists true
            )
        )

        query.fields().include("$bestSellOrderField.$makeStockField")

        // BigInteger stored as String, so we have to retrieve it and cast to Number manually
        val mapping = template.find(query, Document::class.java, collection)
            // each record means 1 unique ownership
            .map { 1 to BigInteger(it.get(bestSellOrderField, Document::class.java).getString(makeStockField)) }
            .reduce { n1, n2 -> Pair(n1.first + n2.first, n1.second.plus(n2.second)) }
            .awaitFirstOrElse { Pair(0, BigInteger.ZERO) }

        return ItemSellStats(mapping.first, mapping.second)
    }

    suspend fun findIdsByLastUpdatedAt(
        lastUpdatedFrom: Instant,
        lastUpdatedTo: Instant,
        fromId: ShortOwnershipId?,
        size: Int = 20
    ): List<ShortDateIdOwnership> {
        val criteria = if (fromId != null) {
            Criteria().orOperator(
                (ShortOwnership::lastUpdatedAt gt lastUpdatedFrom).lte(lastUpdatedTo),
                (ShortOwnership::lastUpdatedAt isEqualTo lastUpdatedFrom).and("_id").gt(fromId)
            )
        } else {
            (ShortOwnership::lastUpdatedAt gt lastUpdatedFrom).lte(lastUpdatedTo)
        }
        val query = Query
            .query(criteria)
            .with(Sort.by(ShortOwnership::lastUpdatedAt.name, ShortOwnership::id.name))
            .limit(size)

        query.fields()
            .include(ShortOwnership::id.name)
            .include(ShortOwnership::lastUpdatedAt.name)

        return template.find(query, ShortDateIdOwnership::class.java, ShortOwnership.COLLECTION)
            .collectList()
            .awaitFirst()
    }

    object Indices {

        const val BEST_SELL_PLATFORM_FIELD = "bestSellOrder.platform"

        private val BLOCKCHAIN_ITEM_ID: Index = Index()
            .on(ShortOwnership::blockchain.name, Sort.Direction.ASC)
            .on(ShortOwnership::itemId.name, Sort.Direction.ASC)
            .background()

        private val MULTI_CURRENCY_OWNERSHIP = Index()
            .partial(PartialIndexFilter.of(ShortOwnership::multiCurrency isEqualTo true))
            .on(ShortOwnership::lastUpdatedAt.name, Sort.Direction.DESC)
            // Originally we don't need it here, but without it there can be collisions in future
            // with other partial indices based on partial filter and lastUpdateAt only
            .on(ShortOwnership::multiCurrency.name, Sort.Direction.DESC)
            .background()

        private val BY_BEST_SELL_PLATFORM_DEFINITION = Index()
            .partial(PartialIndexFilter.of(Criteria.where(BEST_SELL_PLATFORM_FIELD).exists(true)))
            .on(BEST_SELL_PLATFORM_FIELD, Sort.Direction.ASC)
            .on(ShortOwnership::lastUpdatedAt.name, Sort.Direction.DESC)
            .on("_id", Sort.Direction.ASC)
            .background()

        private val LAST_UPDATED_AT_ID: Index = Index()
            .on(ShortOwnership::lastUpdatedAt.name, Sort.Direction.ASC)
            .on("_id", Sort.Direction.ASC)
            .background()

        val ALL = listOf(
            BY_BEST_SELL_PLATFORM_DEFINITION,
            BLOCKCHAIN_ITEM_ID,
            MULTI_CURRENCY_OWNERSHIP,
            LAST_UPDATED_AT_ID
        )
    }
}
