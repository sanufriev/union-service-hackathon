package com.rarible.protocol.union.integration.aptos.converter

import com.rarible.protocol.union.core.model.MetaSource
import com.rarible.protocol.union.core.model.UnionItem
import com.rarible.protocol.union.core.model.UnionMeta
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.CollectionIdDto
import com.rarible.protocol.union.dto.ItemIdDto
import com.rarible.protocol.union.integration.aptos.repository.AptosItemDocument
import java.math.BigInteger
import java.time.Instant

object AptosItemConverter {

    fun convert(document: AptosItemDocument): UnionItem {
        return UnionItem(
            id = ItemIdDto(
                blockchain = BlockchainDto.APTOS,
                value = document.id
            ),
            collection = CollectionIdDto(
                blockchain = BlockchainDto.APTOS,
                value = document.data.currentTokenData.currentCollection.collectionId
            ),
            creators = emptyList(),
            lazySupply = BigInteger.ZERO,
            mintedAt = Instant.now(), // TODO: change to transaction_timestamp
            deleted = false,
            lastUpdatedAt = Instant.now(), // TODO: change to transaction_timestamp
            supply = BigInteger.ONE,
            meta = UnionMeta(
                name = document.data.currentTokenData.tokenName,
                source = MetaSource.ORIGINAL
            )
        )
    }

}
