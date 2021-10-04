package com.rarible.protocol.union.core.ethereum.service

import com.rarible.protocol.nft.api.client.NftOwnershipControllerApi
import com.rarible.protocol.union.core.continuation.Page
import com.rarible.protocol.union.core.ethereum.converter.EthOwnershipConverter
import com.rarible.protocol.union.core.service.OwnershipService
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.UnionOwnershipDto
import kotlinx.coroutines.reactive.awaitFirst

class EthereumOwnershipService(
    blockchain: BlockchainDto,
    private val ownershipControllerApi: NftOwnershipControllerApi
) : AbstractEthereumService(blockchain), OwnershipService {

    override suspend fun getAllOwnerships(continuation: String?, size: Int): Page<UnionOwnershipDto> {
        val ownerships = ownershipControllerApi.getNftAllOwnerships(continuation, size).awaitFirst()
        return EthOwnershipConverter.convert(ownerships, blockchain)
    }

    override suspend fun getOwnershipById(ownershipId: String): UnionOwnershipDto {
        val ownership = ownershipControllerApi.getNftOwnershipById(ownershipId).awaitFirst()
        return EthOwnershipConverter.convert(ownership, blockchain)
    }

    override suspend fun getOwnershipsByItem(
        contract: String,
        tokenId: String,
        continuation: String?,
        size: Int
    ): Page<UnionOwnershipDto> {
        val ownerships =
            ownershipControllerApi.getNftOwnershipsByItem(contract, tokenId, continuation, size).awaitFirst()
        return EthOwnershipConverter.convert(ownerships, blockchain)
    }
}