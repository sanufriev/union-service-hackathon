package com.rarible.protocol.union.core.flow.service

import com.rarible.protocol.flow.nft.api.client.FlowNftItemControllerApi
import com.rarible.protocol.union.core.flow.converter.FlowUnionItemConverter
import com.rarible.protocol.union.core.service.ItemService
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.UnionItemDto
import com.rarible.protocol.union.dto.UnionItemsDto
import kotlinx.coroutines.reactive.awaitFirst

class FlowItemService(
    blockchain: BlockchainDto,
    private val flowNftItemControllerApi: FlowNftItemControllerApi
) : AbstractFlowService(blockchain), ItemService {

    override suspend fun getAllItems(
        continuation: String?,
        size: Int,
        showDeleted: Boolean?,
        lastUpdatedFrom: Long?, //TODO not supported by Flow
        lastUpdatedTo: Long? //TODO not supported by Flow
    ): UnionItemsDto {
        val items = flowNftItemControllerApi.getNftAllItems(continuation, size, showDeleted).awaitFirst()
        return FlowUnionItemConverter.convert(items, blockchain)
    }

    override suspend fun getItemById(
        itemId: String
    ): UnionItemDto {
        val item = flowNftItemControllerApi.getNftItemById(itemId).awaitFirst()
        return FlowUnionItemConverter.convert(item, blockchain)
    }

    override suspend fun getItemsByCollection(
        collection: String,
        continuation: String?,
        size: Int
    ): UnionItemsDto {
        val items = flowNftItemControllerApi.getNftItemsByCollection(collection, continuation, size).awaitFirst()
        return FlowUnionItemConverter.convert(items, blockchain)
    }

    override suspend fun getItemsByCreator(
        creator: String,
        continuation: String?,
        size: Int
    ): UnionItemsDto {
        val items = flowNftItemControllerApi.getNftItemsByCreator(creator, continuation, size).awaitFirst()
        return FlowUnionItemConverter.convert(items, blockchain)
    }

    override suspend fun getItemsByOwner(
        owner: String,
        continuation: String?,
        size: Int
    ): UnionItemsDto {
        val items = flowNftItemControllerApi.getNftItemsByOwner(owner, continuation, size).awaitFirst()
        return FlowUnionItemConverter.convert(items, blockchain)
    }

}