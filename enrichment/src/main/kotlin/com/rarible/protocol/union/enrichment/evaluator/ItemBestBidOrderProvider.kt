package com.rarible.protocol.union.enrichment.evaluator

import com.rarible.protocol.union.dto.OrderDto
import com.rarible.protocol.union.enrichment.model.CurrencyId
import com.rarible.protocol.union.enrichment.model.ShortItem
import com.rarible.protocol.union.enrichment.model.ShortItemId
import com.rarible.protocol.union.enrichment.service.EnrichmentOrderService

class ItemBestBidOrderProvider(
    private val itemId: ShortItemId,
    private val currencyId: CurrencyId,
    private val enrichmentOrderService: EnrichmentOrderService
) : BestOrderProvider<ShortItem> {

    override val entityId: String = itemId.toString()
    override val entityType: Class<ShortItem> get() = ShortItem::class.java

    override suspend fun fetch(): OrderDto? {
        return enrichmentOrderService.getBestBid(itemId, currencyId)
    }
}
