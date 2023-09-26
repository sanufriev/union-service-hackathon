package com.rarible.protocol.union.integration.aptos.service

import com.rarible.core.apm.CaptureSpan
import com.rarible.core.common.nowMillis
import com.rarible.protocol.union.core.model.MetaSource
import com.rarible.protocol.union.core.model.UnionItem
import com.rarible.protocol.union.core.model.UnionMeta
import com.rarible.protocol.union.core.service.ItemService
import com.rarible.protocol.union.core.service.router.AbstractBlockchainService
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.ItemIdDto
import com.rarible.protocol.union.dto.RoyaltyDto
import com.rarible.protocol.union.dto.continuation.page.Page
import java.math.BigInteger

@CaptureSpan(type = "blockchain")
open class AptosItemService(
) : AbstractBlockchainService(BlockchainDto.APTOS), ItemService {

    override suspend fun getAllItems(
        continuation: String?,
        size: Int,
        showDeleted: Boolean?,
        lastUpdatedFrom: Long?,
        lastUpdatedTo: Long?
    ): Page<UnionItem> {
        return Page.empty()
    }

    override suspend fun getItemById(
        itemId: String
    ): UnionItem {
        return UnionItem(
            id = ItemIdDto(blockchain, ""),
            collection = null,
            lazySupply = BigInteger.ZERO,
            mintedAt = nowMillis(),
            lastUpdatedAt = nowMillis(),
            supply = BigInteger.ONE,
            deleted = false
        )
    }

    override suspend fun getItemRoyaltiesById(itemId: String): List<RoyaltyDto> {
        return emptyList()
    }

    override suspend fun getItemMetaById(itemId: String): UnionMeta {
        return UnionMeta(
            name = "",
            source = MetaSource.ORIGINAL
        )
    }

    override suspend fun resetItemMeta(itemId: String) {
    }

    override suspend fun getItemsByCollection(
        collection: String,
        owner: String?,
        continuation: String?,
        size: Int
    ): Page<UnionItem> {
        return Page.empty()
    }

    override suspend fun getItemsByCreator(
        creator: String,
        continuation: String?,
        size: Int
    ): Page<UnionItem> {
        return Page.empty()
    }

    override suspend fun getItemsByOwner(
        owner: String,
        continuation: String?,
        size: Int
    ): Page<UnionItem> {
        return Page.empty()
    }

    override suspend fun getItemsByIds(itemIds: List<String>): List<UnionItem> {
        return emptyList()
    }

    override suspend fun getItemCollectionId(itemId: String): String? {
        // TODO is validation possible here?
        return itemId.substringBefore(":")
    }
}
