package com.rarible.protocol.union.core.flow.converter

import com.rarible.protocol.dto.FlowActivitiesDto
import com.rarible.protocol.dto.FlowActivityDto
import com.rarible.protocol.dto.FlowBurnDto
import com.rarible.protocol.dto.FlowMintDto
import com.rarible.protocol.dto.FlowNftOrderActivityCancelListDto
import com.rarible.protocol.dto.FlowNftOrderActivityListDto
import com.rarible.protocol.dto.FlowNftOrderActivitySellDto
import com.rarible.protocol.dto.FlowOrderActivityMatchSideDto
import com.rarible.protocol.dto.FlowTransferDto
import com.rarible.protocol.union.core.continuation.Slice
import com.rarible.protocol.union.core.converter.UnionAddressConverter
import com.rarible.protocol.union.dto.ActivityBlockchainInfoDto
import com.rarible.protocol.union.dto.ActivityDto
import com.rarible.protocol.union.dto.ActivityIdDto
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.BurnActivityDto
import com.rarible.protocol.union.dto.MintActivityDto
import com.rarible.protocol.union.dto.OrderActivityMatchSideDto
import com.rarible.protocol.union.dto.OrderCancelListActivityDto
import com.rarible.protocol.union.dto.OrderListActivityDto
import com.rarible.protocol.union.dto.OrderMatchActivityDto
import com.rarible.protocol.union.dto.TransferActivityDto

object FlowActivityConverter {

    fun convert(source: FlowActivityDto, blockchain: BlockchainDto): ActivityDto {
        val activityId = ActivityIdDto(blockchain, source.id)
        return when (source) {
            is FlowNftOrderActivitySellDto -> {
                OrderMatchActivityDto(
                    id = activityId,
                    date = source.date,
                    left = convert(source.left, blockchain),
                    right = convert(source.right, blockchain),
                    price = source.price,
                    priceUsd = source.price, // TODO should be in USD
                    blockchainInfo = ActivityBlockchainInfoDto(
                        transactionHash = source.transactionHash,
                        blockHash = source.blockHash,
                        blockNumber = source.blockNumber,
                        logIndex = source.logIndex
                    )
                )
            }
            is FlowNftOrderActivityListDto -> {
                OrderListActivityDto(
                    id = activityId,
                    date = source.date,
                    price = source.price,
                    priceUsd = source.price, // TODO should be in USD
                    hash = source.hash,
                    maker = UnionAddressConverter.convert(source.maker, blockchain),
                    make = FlowConverter.convert(source.make, blockchain),
                    take = FlowConverter.convert(source.take, blockchain)
                )
            }
            is FlowNftOrderActivityCancelListDto -> {
                OrderCancelListActivityDto(
                    id = activityId,
                    date = source.date,
                    hash = source.hash,
                    maker = UnionAddressConverter.convert(source.maker, blockchain),
                    make = FlowConverter.convertToType(source.make, blockchain),
                    take = FlowConverter.convertToType(source.take, blockchain)
                )
            }
            is FlowMintDto -> {
                MintActivityDto(
                    id = activityId,
                    date = source.date,
                    owners = listOf(UnionAddressConverter.convert(source.owner, blockchain)),
                    contract = FlowContractConverter.convert(source.contract, blockchain),
                    tokenId = source.tokenId,
                    value = source.value,
                    blockchainInfo = ActivityBlockchainInfoDto(
                        transactionHash = source.transactionHash,
                        blockHash = source.blockHash,
                        blockNumber = source.blockNumber,
                        logIndex = source.logIndex
                    )
                )
            }
            is FlowBurnDto -> {
                BurnActivityDto(
                    id = activityId,
                    date = source.date,
                    owners = listOf(UnionAddressConverter.convert(source.owner, blockchain)),
                    contract = FlowContractConverter.convert(source.contract, blockchain),
                    tokenId = source.tokenId,
                    value = source.value,
                    blockchainInfo = ActivityBlockchainInfoDto(
                        transactionHash = source.transactionHash,
                        blockHash = source.blockHash,
                        blockNumber = source.blockNumber,
                        logIndex = source.logIndex
                    )
                )
            }
            is FlowTransferDto -> {
                TransferActivityDto(
                    id = activityId,
                    date = source.date,
                    from = UnionAddressConverter.convert(source.from, blockchain),
                    owners = listOf(UnionAddressConverter.convert(source.owner, blockchain)),
                    contract = FlowContractConverter.convert(source.contract, blockchain),
                    tokenId = source.tokenId,
                    value = source.value,
                    blockchainInfo = ActivityBlockchainInfoDto(
                        transactionHash = source.transactionHash,
                        blockHash = source.blockHash,
                        blockNumber = source.blockNumber,
                        logIndex = source.logIndex
                    )
                )
            }
        }
    }

    fun convert(source: FlowActivitiesDto, blockchain: BlockchainDto): Slice<ActivityDto> {
        return Slice(
            continuation = source.continuation,
            entities = source.items.map { convert(it, blockchain) }
        )
    }

    private fun convert(
        source: FlowOrderActivityMatchSideDto,
        blockchain: BlockchainDto
    ): OrderActivityMatchSideDto {
        // TODO как здесь разделять?
        return OrderActivityMatchSideDto(
            maker = UnionAddressConverter.convert(source.maker, blockchain),
            asset = FlowConverter.convert(source.asset, blockchain)
        )
    }
}