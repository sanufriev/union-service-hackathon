package com.rarible.protocol.union.integration.aptos.repository

import com.rarible.protocol.union.integration.aptos.model.NftEvent
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.findById
import org.springframework.data.mongodb.core.query.Query

class AptosRepository(
    private val template: ReactiveMongoTemplate
) {

    suspend fun save(event: NftEvent): AptosItemDocument {
        return template.save(AptosItemDocument(
            id = event.currentTokenData.tokenDataId,
            data = event
        )).awaitSingle()
    }

    suspend fun get(id: String): AptosItemDocument? {
        return template.findById<AptosItemDocument>(id).awaitSingleOrNull()
    }

    suspend fun getAll(size: Int): List<AptosItemDocument> {
        val sort = Sort.by(Sort.Order.desc("createdAt"))
        return template.find<AptosItemDocument>(Query().limit(size).with(sort)).collectList().awaitSingle()
    }

}
