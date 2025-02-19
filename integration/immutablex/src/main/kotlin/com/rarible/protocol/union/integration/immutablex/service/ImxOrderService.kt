package com.rarible.protocol.union.integration.immutablex.service

import com.rarible.protocol.union.core.continuation.UnionOrderContinuation
import com.rarible.protocol.union.core.exception.UnionException
import com.rarible.protocol.union.core.model.UnionAssetType
import com.rarible.protocol.union.core.model.UnionOrder
import com.rarible.protocol.union.core.service.OrderService
import com.rarible.protocol.union.core.service.router.AbstractBlockchainService
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.ItemIdDto
import com.rarible.protocol.union.dto.OrderSortDto
import com.rarible.protocol.union.dto.OrderStatusDto
import com.rarible.protocol.union.dto.PlatformDto
import com.rarible.protocol.union.dto.SyncSortDto
import com.rarible.protocol.union.dto.continuation.page.Paging
import com.rarible.protocol.union.dto.continuation.page.Slice
import com.rarible.protocol.union.dto.parser.IdParser
import com.rarible.protocol.union.integration.immutablex.client.ImxOrderClient
import com.rarible.protocol.union.integration.immutablex.client.TokenIdDecoder
import com.rarible.protocol.union.integration.immutablex.converter.ImxOrderConverter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class ImxOrderService(
    private val orderClient: ImxOrderClient,
    private val imxOrderConverter: ImxOrderConverter
) : AbstractBlockchainService(BlockchainDto.IMMUTABLEX), OrderService {

    // TODO move out to configuration
    private val currencyProbeBatchSize = 64
    private val supportedPlatforms = setOf(PlatformDto.IMMUTABLEX)

    override suspend fun getOrdersAll(
        continuation: String?,
        size: Int,
        sort: OrderSortDto?,
        status: List<OrderStatusDto>?,
    ): Slice<UnionOrder> {
        val orders = orderClient.getAllOrders(
            continuation,
            size,
            sort,
            status
        ).map { imxOrderConverter.convert(it, blockchain) }

        val continuationFactory = when (sort) {
            OrderSortDto.LAST_UPDATE_ASC -> UnionOrderContinuation.ByLastUpdatedAndIdAsc
            OrderSortDto.LAST_UPDATE_DESC, null -> UnionOrderContinuation.ByLastUpdatedAndIdDesc
        }

        return Paging(continuationFactory, orders).getSlice(size)
    }

    override suspend fun getAllSync(
        continuation: String?,
        size: Int,
        sort: SyncSortDto?
    ): Slice<UnionOrder> {
        val safeSort = when (sort) {
            SyncSortDto.DB_UPDATE_ASC -> OrderSortDto.LAST_UPDATE_ASC
            SyncSortDto.DB_UPDATE_DESC -> OrderSortDto.LAST_UPDATE_DESC
            null -> OrderSortDto.LAST_UPDATE_ASC
        }
        return getOrdersAll(continuation, size, safeSort, null)
    }

    override suspend fun getOrderById(id: String): UnionOrder {
        val order = orderClient.getById(id)
        return imxOrderConverter.convert(order, blockchain)
    }

    override suspend fun getOrdersByIds(orderIds: List<String>): List<UnionOrder> {
        return orderClient.getByIds(orderIds).map {
            imxOrderConverter.convert(it, blockchain)
        }
    }

    override suspend fun getBidCurrencies(
        itemId: String,
        status: List<OrderStatusDto>?
    ): List<UnionAssetType> {
        // TODO bids disabled for IMX
        return emptyList()
        /*
        // TODO we can miss some orders here if item have a lot of cancelled/filled orders with different currencies
        // TODO maybe split it into 2 queries to distinguish ETH/ERC20 currencies?
        val (token, tokenId) = IdParser.split(TokenIdDecoder.decodeItemId(itemId), 2)
        // TODO IMX add filter by status
        val orders = orderClient.getBuyOrdersByItem(token, tokenId, null, currencyProbeBatchSize)
        return orders.result.map { ImxOrderConverter.convert(it, blockchain).make.type }
            .toSet()
            .filter { it.isCurrency() }
            .toList()
        */
    }

    // IMX doesn't support floor orders
    override suspend fun getBidCurrenciesByCollection(
        collectionId: String,
        status: List<OrderStatusDto>?
    ): List<UnionAssetType> {
        return emptyList()
    }

    override suspend fun getOrderBidsByItem(
        platform: PlatformDto?,
        itemId: String,
        makers: List<String>?,
        origin: String?,
        status: List<OrderStatusDto>?,
        start: Long?, // Not supported for bids by IMX
        end: Long?, // Not supported for bids by IMX
        currencyAddress: String,
        continuation: String?,
        size: Int,
    ): Slice<UnionOrder> {
        // TODO bids disabled for IMX
        return Slice.empty()
        /*
        if (!isPlatformSupported(platform)) return Slice.empty()
        origin?.let { return Slice.empty() }
        val (token, tokenId) = IdParser.split(TokenIdDecoder.decodeItemId(itemId), 2)
        val orders = orderClient
            .getBuyOrdersByItem(token, tokenId, makers, status, currencyAddress, continuation, size)
            .map { ImxOrderConverter.convert(it, blockchain) }

        return Paging(UnionOrderContinuation.ByBidPriceUsdAndIdDesc, orders).getSlice(size)
        */
    }

    override suspend fun getOrderBidsByMaker(
        platform: PlatformDto?,
        maker: List<String>,
        origin: String?,
        status: List<OrderStatusDto>?,
        currencyAddresses: List<String>?,
        start: Long?, // Not supported for bids by IMX
        end: Long?, // Not supported for bids by IMX
        continuation: String?,
        size: Int,
    ): Slice<UnionOrder> {
        // TODO bids disabled for IMX
        return Slice.empty()
        /*
        if (!isPlatformSupported(platform)) return Slice.empty()
        origin?.let { return Slice.empty() }
        val orders = orderClient.getBuyOrdersByMaker(maker, status, continuation, size)
            .map { ImxOrderConverter.convert(it, blockchain) }

        return Paging(UnionOrderContinuation.ByLastUpdatedAndIdDesc, orders).getSlice(size)
        */
    }

    override suspend fun getSellCurrencies(
        itemId: String,
        status: List<OrderStatusDto>?
    ): List<UnionAssetType> {
        // TODO we can miss some orders here if item have a lot of cancelled/filled orders with different currencies
        // TODO maybe split it into 2 queries to distinguish ETH/ERC20 currencies?
        val (token, tokenId) = IdParser.split(TokenIdDecoder.decodeItemId(itemId), 2)
        // We MUST request active order in order to definitely have it as 'best sell'
        val activeOrder = coroutineScope {
            async {
                orderClient.getSellOrdersByItem(
                    token, tokenId, OrderStatusDto.ACTIVE, null, currencyProbeBatchSize
                )
            }
        }

        // TODO IMX add filter by status
        val ordersWithAllStatuses = orderClient.getSellOrdersByItem(
            token, tokenId, null, null, currencyProbeBatchSize
        ).result + activeOrder.await().result

        return ordersWithAllStatuses.map { imxOrderConverter.convert(it, blockchain).take.type }
            .toSet()
            .filter { it.isCurrency() }
            .toList()
    }

    // IMX doesn't support floor orders
    override suspend fun getSellCurrenciesByCollection(
        collectionId: String,
        status: List<OrderStatusDto>?
    ): List<UnionAssetType> {
        return emptyList()
    }

    override suspend fun getSellOrders(
        platform: PlatformDto?,
        origin: String?,
        continuation: String?,
        size: Int,
    ): Slice<UnionOrder> {
        if (!isPlatformSupported(platform)) return Slice.empty()
        origin?.let { return Slice.empty() }
        val orders = orderClient.getSellOrders(continuation, size).map {
            imxOrderConverter.convert(it, blockchain)
        }
        return Paging(UnionOrderContinuation.ByLastUpdatedAndIdDesc, orders).getSlice(size)
    }

    override suspend fun getSellOrdersByCollection(
        platform: PlatformDto?,
        collection: String,
        origin: String?,
        continuation: String?,
        size: Int,
    ): Slice<UnionOrder> {
        if (!isPlatformSupported(platform)) return Slice.empty()
        origin?.let { return Slice.empty() }
        val orders = orderClient.getSellOrdersByCollection(collection, continuation, size).map {
            imxOrderConverter.convert(it, blockchain)
        }
        return Paging(UnionOrderContinuation.ByLastUpdatedAndIdDesc, orders).getSlice(size)
    }

    // IMX doesn't support floor orders
    override suspend fun getOrderFloorSellsByCollection(
        platform: PlatformDto?,
        collectionId: String,
        origin: String?,
        status: List<OrderStatusDto>?,
        currencyAddress: String,
        continuation: String?,
        size: Int
    ): Slice<UnionOrder> {
        return Slice.empty()
    }

    // IMX doesn't support floor orders
    override suspend fun getOrderFloorBidsByCollection(
        platform: PlatformDto?,
        collectionId: String,
        origin: String?,
        status: List<OrderStatusDto>?,
        start: Long?,
        end: Long?,
        currencyAddress: String,
        continuation: String?,
        size: Int,
    ): Slice<UnionOrder> {
        return Slice.empty()
    }

    override suspend fun getSellOrdersByItem(
        platform: PlatformDto?,
        itemId: String,
        maker: String?,
        origin: String?,
        status: List<OrderStatusDto>?,
        currencyId: String,
        continuation: String?,
        size: Int
    ): Slice<UnionOrder> {
        if (!isPlatformSupported(platform)) return Slice.empty()
        origin?.let { return Slice.empty() }
        val (token, tokenId) = IdParser.split(TokenIdDecoder.decodeItemId(itemId), 2)
        val orders = orderClient.getSellOrdersByItem(
            token,
            tokenId,
            maker,
            status,
            currencyId,
            continuation,
            size
        ).map { imxOrderConverter.convert(it, blockchain) }

        return Paging(UnionOrderContinuation.BySellPriceUsdAndIdAsc, orders).getSlice(size)
    }

    override suspend fun getSellOrdersByMaker(
        platform: PlatformDto?,
        maker: List<String>,
        origin: String?,
        status: List<OrderStatusDto>?,
        continuation: String?,
        size: Int,
    ): Slice<UnionOrder> {
        if (!isPlatformSupported(platform)) return Slice.empty()
        origin?.let { return Slice.empty() }
        val orders = orderClient.getSellOrdersByMaker(maker, status, continuation, size).map {
            imxOrderConverter.convert(it, blockchain)
        }
        return Paging(UnionOrderContinuation.ByLastUpdatedAndIdDesc, orders).getSlice(size)
    }

    override suspend fun getAmmOrdersByItem(
        itemId: String,
        continuation: String?,
        size: Int
    ): Slice<UnionOrder> {
        return Slice.empty()
    }

    override suspend fun getAmmOrderItemIds(
        id: String,
        continuation: String?,
        size: Int
    ): Slice<ItemIdDto> {
        return Slice.empty()
    }

    override suspend fun cancelOrder(id: String): UnionOrder {
        throw UnionException("Operation is not supported for this blockchain")
    }

    override suspend fun getAmmOrdersAll(
        status: List<OrderStatusDto>?,
        continuation: String?,
        size: Int
    ): Slice<UnionOrder> {
        return Slice.empty()
    }

    private fun isPlatformSupported(platform: PlatformDto?): Boolean {
        if (platform == null) {
            return true
        }
        return supportedPlatforms.contains(platform)
    }
}
