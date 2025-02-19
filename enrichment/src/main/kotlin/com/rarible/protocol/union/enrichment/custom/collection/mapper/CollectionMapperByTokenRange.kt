package com.rarible.protocol.union.enrichment.custom.collection.mapper

import com.rarible.protocol.union.core.util.CompositeItemIdParser
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.ItemIdDto
import com.rarible.protocol.union.enrichment.custom.collection.provider.CustomCollectionProvider
import com.rarible.protocol.union.enrichment.model.ShortItem
import java.math.BigInteger

class CollectionMapperByTokenRange(
    private val provider: CustomCollectionProvider,
    private val range: ClosedRange<BigInteger>
) : CollectionMapper {

    override suspend fun getCustomCollectionProviders(
        itemIds: Collection<ItemIdDto>,
        hint: Map<ItemIdDto, ShortItem>
    ): Map<ItemIdDto, CustomCollectionProvider> {
        // Doesn't work for solana
        return itemIds.filter { it.blockchain != BlockchainDto.SOLANA }
            .filter { CompositeItemIdParser.split(it.value).second in range }
            .associateWith { provider }
    }
}
