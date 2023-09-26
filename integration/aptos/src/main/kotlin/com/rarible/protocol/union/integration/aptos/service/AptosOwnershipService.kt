package com.rarible.protocol.union.integration.aptos.service

import com.rarible.core.apm.CaptureSpan
import com.rarible.core.common.nowMillis
import com.rarible.protocol.union.core.model.UnionOwnership
import com.rarible.protocol.union.core.service.OwnershipService
import com.rarible.protocol.union.core.service.router.AbstractBlockchainService
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.OwnershipIdDto
import com.rarible.protocol.union.dto.continuation.page.Page
import com.rarible.protocol.union.dto.continuation.page.Slice
import java.math.BigInteger

@CaptureSpan(type = "blockchain")
open class AptosOwnershipService(
) : AbstractBlockchainService(BlockchainDto.APTOS), OwnershipService {

    override suspend fun getOwnershipById(ownershipId: String): UnionOwnership {
        return UnionOwnership(
            id = OwnershipIdDto(blockchain, "", BigInteger.ONE, ""),
            collection = null,
            value = BigInteger.ONE,
            createdAt = nowMillis(),
            lastUpdatedAt = null,
            lazyValue = BigInteger.ONE
        )
    }

    override suspend fun getOwnershipsByIds(ownershipIds: List<String>): List<UnionOwnership> {
        return emptyList()
    }

    override suspend fun getOwnershipsAll(continuation: String?, size: Int): Slice<UnionOwnership> {
        return Slice.empty()
    }

    override suspend fun getOwnershipsByItem(
        itemId: String,
        continuation: String?,
        size: Int
    ): Page<UnionOwnership> {
        return Page.empty()
    }

    override suspend fun getOwnershipsByOwner(address: String, continuation: String?, size: Int): Page<UnionOwnership> {
        // Will be implemented via es
        return Page.empty()
    }
}
