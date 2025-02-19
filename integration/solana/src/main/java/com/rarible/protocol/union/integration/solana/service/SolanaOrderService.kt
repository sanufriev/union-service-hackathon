package com.rarible.protocol.union.integration.solana.service

import com.rarible.core.apm.CaptureSpan
import com.rarible.protocol.solana.api.client.OrderControllerApi
import com.rarible.protocol.solana.dto.OrderIdsDto
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
import com.rarible.protocol.union.dto.continuation.page.Slice
import com.rarible.protocol.union.integration.solana.converter.SolanaConverter
import com.rarible.protocol.union.integration.solana.converter.SolanaOrderConverter
import kotlinx.coroutines.reactive.awaitFirst

@CaptureSpan(type = "blockchain")
open class SolanaOrderService(
    private val orderApi: OrderControllerApi,
    private val solanaOrderConverter: SolanaOrderConverter
) : AbstractBlockchainService(BlockchainDto.SOLANA), OrderService {

    override suspend fun getOrderById(id: String): UnionOrder {
        val order = orderApi.getOrderById(id).awaitFirst()
        return solanaOrderConverter.convert(order, blockchain)
    }

    override suspend fun getOrdersAll(
        continuation: String?,
        size: Int,
        sort: OrderSortDto?,
        status: List<OrderStatusDto>?
    ): Slice<UnionOrder> {
        val orders = orderApi.getOrdersAll(
            continuation,
            size,
            solanaOrderConverter.convert(sort),
            solanaOrderConverter.convert(status)
        ).awaitFirst()
        return solanaOrderConverter.convert(orders, blockchain)
    }

    override suspend fun getAllSync(
        continuation: String?,
        size: Int,
        sort: SyncSortDto?
    ): Slice<UnionOrder> {
        val orders = orderApi.getOrdersSync(
            continuation,
            size,
            solanaOrderConverter.convert(sort),
        ).awaitFirst()

        return solanaOrderConverter.convert(orders, blockchain)
    }

    override suspend fun getOrdersByIds(orderIds: List<String>): List<UnionOrder> {
        val result = orderApi.getOrdersByIds(OrderIdsDto(orderIds)).awaitFirst()
        return result.orders.map { solanaOrderConverter.convert(it, blockchain) }
    }

    override suspend fun getBidCurrencies(
        itemId: String,
        status: List<OrderStatusDto>?
    ): List<UnionAssetType> {
        // TODO SOLANA add filter by status
        val result = orderApi.getBidCurrencies(itemId).awaitFirst()
        return result.currencies.map { SolanaConverter.convert(it, blockchain) }
    }

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
        start: Long?,
        end: Long?,
        currencyAddress: String,
        continuation: String?,
        size: Int
    ): Slice<UnionOrder> {
        val orders = orderApi.getOrderBidsByItem(
            itemId,
            currencyAddress,
            solanaOrderConverter.convert(status),
            makers,
            origin,
            start,
            end,
            continuation,
            size
        ).awaitFirst()
        return solanaOrderConverter.convert(orders, blockchain)
    }

    override suspend fun getOrderBidsByMaker(
        platform: PlatformDto?,
        maker: List<String>,
        origin: String?,
        status: List<OrderStatusDto>?,
        currencyAddresses: List<String>?,
        start: Long?,
        end: Long?,
        continuation: String?,
        size: Int
    ): Slice<UnionOrder> {
        val orders = orderApi.getOrderBidsByMaker(
            maker,
            origin,
            solanaOrderConverter.convert(status),
            start,
            end,
            continuation,
            size
        ).awaitFirst()
        return solanaOrderConverter.convert(orders, blockchain)
    }

    override suspend fun getSellCurrencies(
        itemId: String,
        status: List<OrderStatusDto>?
    ): List<UnionAssetType> {
        // TODO SOLANA add filter by status
        val result = orderApi.getSellCurrencies(itemId).awaitFirst()
        return result.currencies.map { SolanaConverter.convert(it, blockchain) }
    }

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
        size: Int
    ): Slice<UnionOrder> {
        val orders = orderApi.getSellOrders(
            origin,
            continuation,
            size
        ).awaitFirst()
        return solanaOrderConverter.convert(orders, blockchain)
    }

    // TODO not used, should be removed
    override suspend fun getSellOrdersByCollection(
        platform: PlatformDto?,
        collection: String,
        origin: String?,
        continuation: String?,
        size: Int
    ): Slice<UnionOrder> {
        return Slice.empty()
    }

    override suspend fun getOrderFloorSellsByCollection(
        platform: PlatformDto?,
        collectionId: String,
        origin: String?,
        status: List<OrderStatusDto>?,
        currencyAddress: String,
        continuation: String?,
        size: Int
    ): Slice<UnionOrder> {
        // Not implemented
        return Slice.empty()
    }

    override suspend fun getOrderFloorBidsByCollection(
        platform: PlatformDto?,
        collectionId: String,
        origin: String?,
        status: List<OrderStatusDto>?,
        start: Long?,
        end: Long?,
        currencyAddress: String,
        continuation: String?,
        size: Int
    ): Slice<UnionOrder> {
        // Not implemented
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
        val orders = orderApi.getSellOrdersByItem(
            itemId,
            currencyId,
            listOf(maker),
            origin,
            solanaOrderConverter.convert(status),
            continuation,
            size
        ).awaitFirst()
        return solanaOrderConverter.convert(orders, blockchain)
    }

    override suspend fun getSellOrdersByMaker(
        platform: PlatformDto?,
        maker: List<String>,
        origin: String?,
        status: List<OrderStatusDto>?,
        continuation: String?,
        size: Int
    ): Slice<UnionOrder> {
        val orders = orderApi.getSellOrdersByMaker(
            maker,
            origin,
            solanaOrderConverter.convert(status),
            continuation,
            size
        ).awaitFirst()
        return solanaOrderConverter.convert(orders, blockchain)
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
}
