package com.rarible.protocol.union.integration.aptos.service

import com.rarible.core.apm.CaptureSpan
import com.rarible.protocol.union.core.exception.UnionNotFoundException
import com.rarible.protocol.union.core.model.TokenId
import com.rarible.protocol.union.core.model.UnionCollection
import com.rarible.protocol.union.core.model.UnionCollectionMeta
import com.rarible.protocol.union.core.service.CollectionService
import com.rarible.protocol.union.core.service.router.AbstractBlockchainService
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.CollectionIdDto
import com.rarible.protocol.union.dto.continuation.page.Page
import org.slf4j.LoggerFactory

@CaptureSpan(type = "blockchain")
open class AptosCollectionService(
) : AbstractBlockchainService(BlockchainDto.APTOS), CollectionService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun getAllCollections(
        continuation: String?,
        size: Int
    ): Page<UnionCollection> {
         return Page.empty()
    }

    override suspend fun getCollectionById(collectionId: String): UnionCollection {
        return UnionCollection(
            id = CollectionIdDto(BlockchainDto.APTOS, ""),
            name = "a",
            type = UnionCollection.Type.ERC1155
        )
    }

    override suspend fun getCollectionMetaById(collectionId: String): UnionCollectionMeta {
        throw UnionNotFoundException("Meta not found for Collection $blockchain:$collectionId")
    }

    override suspend fun refreshCollectionMeta(collectionId: String) {
        // TODO[TEZOS]: implement.
    }

    override suspend fun getCollectionsByIds(ids: List<String>): List<UnionCollection> {
        return emptyList()
    }

    override suspend fun generateNftTokenId(collectionId: String, minter: String?): TokenId {
        return TokenId("")
    }

    override suspend fun getCollectionsByOwner(
        owner: String,
        continuation: String?,
        size: Int
    ): Page<UnionCollection> {
        return Page.empty()
    }
}
