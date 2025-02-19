package com.rarible.protocol.union.integration.ethereum.service

import com.rarible.protocol.dto.NftItemIdsDto
import com.rarible.protocol.dto.parser.AddressParser
import com.rarible.protocol.nft.api.client.NftItemControllerApi
import com.rarible.protocol.union.core.exception.UnionMetaException
import com.rarible.protocol.union.core.exception.UnionNotFoundException
import com.rarible.protocol.union.core.model.UnionItem
import com.rarible.protocol.union.core.model.UnionMeta
import com.rarible.protocol.union.core.service.ItemService
import com.rarible.protocol.union.core.service.router.AbstractBlockchainService
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.RoyaltyDto
import com.rarible.protocol.union.dto.continuation.page.Page
import com.rarible.protocol.union.integration.ethereum.converter.EthConverter
import com.rarible.protocol.union.integration.ethereum.converter.EthItemConverter
import com.rarible.protocol.union.integration.ethereum.converter.EthMetaConverter
import com.rarible.protocol.union.integration.ethereum.converter.MetaStatusChecker
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.http.HttpStatus

open class EthItemService(
    blockchain: BlockchainDto,
    private val itemControllerApi: NftItemControllerApi
) : AbstractBlockchainService(blockchain), ItemService {

    override suspend fun getAllItems(
        continuation: String?,
        size: Int,
        showDeleted: Boolean?,
        lastUpdatedFrom: Long?,
        lastUpdatedTo: Long?
    ): Page<UnionItem> {
        val items = itemControllerApi.getNftAllItems(
            continuation,
            size,
            showDeleted,
            lastUpdatedFrom,
            lastUpdatedTo
        ).awaitFirst()
        return EthItemConverter.convert(items, blockchain)
    }

    override suspend fun getItemById(itemId: String): UnionItem {
        val item = itemControllerApi.getNftItemById(itemId).awaitFirst()
        return EthItemConverter.convert(item, blockchain)
    }

    override suspend fun getItemRoyaltiesById(itemId: String): List<RoyaltyDto> {
        val royaltyList = itemControllerApi.getNftItemRoyaltyById(itemId).awaitFirst()
        val royalties = royaltyList.royalty.orEmpty()
        return royalties.map { EthConverter.convert(it, blockchain) }
    }

    override suspend fun getItemMetaById(itemId: String): UnionMeta {
        val entityId = "Item: $blockchain:$itemId"
        try {
            val meta = itemControllerApi.getNftItemMetaById(itemId).awaitFirst()
            MetaStatusChecker.checkStatus(meta.status, entityId)
            return EthMetaConverter.convert(meta, itemId)
        } catch (e: NftItemControllerApi.ErrorGetNftItemMetaById) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                throw UnionNotFoundException("Meta not found for $entityId")
            }
            throw UnionMetaException(UnionMetaException.ErrorCode.ERROR, e.message)
        }
    }

    override suspend fun resetItemMeta(itemId: String) {
        itemControllerApi.resetNftItemMetaById(itemId).awaitFirstOrNull()
    }

    override suspend fun getItemsByCollection(
        collection: String,
        owner: String?,
        continuation: String?,
        size: Int
    ): Page<UnionItem> {
        val items = itemControllerApi.getNftItemsByCollection(
            collection,
            owner,
            continuation,
            size
        ).awaitFirst()
        return EthItemConverter.convert(items, blockchain)
    }

    override suspend fun getItemsByCreator(
        creator: String,
        continuation: String?,
        size: Int
    ): Page<UnionItem> {
        val items = itemControllerApi.getNftItemsByCreator(
            creator,
            continuation,
            size
        ).awaitFirst()
        return EthItemConverter.convert(items, blockchain)
    }

    override suspend fun getItemsByOwner(
        owner: String,
        continuation: String?,
        size: Int
    ): Page<UnionItem> {
        val items = itemControllerApi.getNftItemsByOwner(
            owner,
            continuation,
            size
        ).awaitFirst()
        return EthItemConverter.convert(items, blockchain)
    }

    override suspend fun getItemsByIds(itemIds: List<String>): List<UnionItem> {
        val items = itemControllerApi.getNftItemsByIds(NftItemIdsDto(itemIds)).collectList().awaitSingle()
        return items.map { EthItemConverter.convert(it, blockchain) }
    }

    override suspend fun getItemCollectionId(itemId: String): String {
        // Conversion used as validation here
        val ethToken = AddressParser.parse(itemId.substringBefore(":"))
        return EthConverter.convert(ethToken)
    }
}

open class EthereumItemService(
    itemControllerApi: NftItemControllerApi
) : EthItemService(
    BlockchainDto.ETHEREUM,
    itemControllerApi
)

open class PolygonItemService(
    itemControllerApi: NftItemControllerApi
) : EthItemService(
    BlockchainDto.POLYGON,
    itemControllerApi
)

open class MantleItemService(
    itemControllerApi: NftItemControllerApi
) : EthItemService(
    BlockchainDto.MANTLE,
    itemControllerApi
)
