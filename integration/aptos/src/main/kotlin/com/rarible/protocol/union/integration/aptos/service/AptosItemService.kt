package com.rarible.protocol.union.integration.aptos.service

import com.rarible.core.apm.CaptureSpan
import com.rarible.protocol.union.core.exception.UnionNotFoundException
import com.rarible.protocol.union.core.model.UnionItem
import com.rarible.protocol.union.core.model.UnionMeta
import com.rarible.protocol.union.core.service.ItemService
import com.rarible.protocol.union.core.service.router.AbstractBlockchainService
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.RoyaltyDto
import com.rarible.protocol.union.dto.continuation.page.Page
import com.rarible.protocol.union.integration.aptos.converter.AptosItemConverter
import com.rarible.protocol.union.integration.aptos.repository.AptosRepository

@CaptureSpan(type = "blockchain")
open class AptosItemService(
    private val repository: AptosRepository
) : AbstractBlockchainService(BlockchainDto.APTOS), ItemService {

    override suspend fun getAllItems(
        continuation: String?,
        size: Int,
        showDeleted: Boolean?,
        lastUpdatedFrom: Long?,
        lastUpdatedTo: Long?
    ): Page<UnionItem> {
        val items = repository.getAll().map { AptosItemConverter.convert(it) }
        return Page(
            total = items.size.toLong(),
            continuation = null,
            entities = items
        )
    }

    override suspend fun getItemById(
        itemId: String
    ): UnionItem {
        val tokenId = itemId.split(":").last()
        val item = repository.get(tokenId) ?: throw UnionNotFoundException("Not found $tokenId")
        return AptosItemConverter.convert(item)
    }

    override suspend fun getItemRoyaltiesById(itemId: String): List<RoyaltyDto> {
        return emptyList()
    }

    override suspend fun getItemMetaById(itemId: String): UnionMeta {
        val item = getItemById(itemId)
        return item.meta!!
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
