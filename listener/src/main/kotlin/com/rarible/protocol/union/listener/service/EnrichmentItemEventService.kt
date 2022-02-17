package com.rarible.protocol.union.listener.service

import com.rarible.core.common.optimisticLock
import com.rarible.protocol.union.core.event.OutgoingItemEventListener
import com.rarible.protocol.union.core.model.UnionItem
import com.rarible.protocol.union.core.model.getItemId
import com.rarible.protocol.union.core.service.ReconciliationEventService
import com.rarible.protocol.union.dto.AuctionDto
import com.rarible.protocol.union.dto.AuctionStatusDto
import com.rarible.protocol.union.dto.ItemDeleteEventDto
import com.rarible.protocol.union.dto.ItemIdDto
import com.rarible.protocol.union.dto.ItemUpdateEventDto
import com.rarible.protocol.union.dto.OrderDto
import com.rarible.protocol.union.enrichment.model.ItemSellStats
import com.rarible.protocol.union.enrichment.model.ShortItem
import com.rarible.protocol.union.enrichment.model.ShortItemId
import com.rarible.protocol.union.enrichment.model.ShortOwnershipId
import com.rarible.protocol.union.enrichment.service.BestOrderService
import com.rarible.protocol.union.enrichment.service.EnrichmentItemService
import com.rarible.protocol.union.enrichment.service.EnrichmentOwnershipService
import com.rarible.protocol.union.enrichment.validator.ItemValidator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

@Component
class EnrichmentItemEventService(
    private val itemService: EnrichmentItemService,
    private val ownershipService: EnrichmentOwnershipService,
    private val itemEventListeners: List<OutgoingItemEventListener>,
    private val bestOrderService: BestOrderService,
    private val reconciliationEventService: ReconciliationEventService
) {

    private val logger = LoggerFactory.getLogger(EnrichmentItemEventService::class.java)

    // If ownership was updated, we need to recalculate totalStock/sellers for related item,
    // also, we can specify here Order which triggered this update - ItemService
    // can use this full Order to avoid unnecessary getOrderById calls
    suspend fun onOwnershipUpdated(
        ownershipId: ShortOwnershipId,
        order: OrderDto?,
        notificationEnabled: Boolean = true
    ) {
        val itemId = ShortItemId(ownershipId.blockchain, ownershipId.token, ownershipId.tokenId)
        optimisticLock {
            val item = itemService.get(itemId)
            if (item == null) {
                logger.debug(
                    "Item [{}] not found in DB, skipping sell stats update on Ownership event: [{}]",
                    itemId, ownershipId
                )
            } else {
                val refreshedSellStats = ownershipService.getItemSellStats(itemId)
                val currentSellStats = ItemSellStats(item.sellers, item.totalStock)
                if (refreshedSellStats != currentSellStats) {
                    val updatedItem = item.copy(
                        sellers = refreshedSellStats.sellers,
                        totalStock = refreshedSellStats.totalStock
                    )
                    logger.info(
                        "Updating Item [{}] with new sell stats, was [{}] , now: [{}]",
                        itemId, currentSellStats, refreshedSellStats
                    )
                    saveAndNotify(updated = updatedItem, notificationEnabled = notificationEnabled, order = order)
                } else {
                    logger.debug(
                        "Sell stats of Item [{}] are the same as before Ownership event [{}], skipping update",
                        itemId, ownershipId
                    )
                }
            }
        }
    }

    suspend fun onItemUpdated(item: UnionItem) {
        val existing = itemService.getOrEmpty(ShortItemId(item.id))
        val updateEvent = buildUpdateEvent(short = existing, item = item)
        sendUpdate(updateEvent)
    }

    suspend fun recalculateBestOrders(item: ShortItem): Boolean {
        val updated = bestOrderService.updateBestOrders(item)
        if (updated != item) {
            logger.info(
                "Item BestSellOrder updated ([{}] -> [{}]), BestBidOrder updated ([{}] -> [{}]) due to currency rate changed",
                item.bestSellOrder?.dtoId, updated.bestSellOrder?.dtoId,
                item.bestBidOrder?.dtoId, updated.bestBidOrder?.dtoId
            )
            saveAndNotify(updated, true)
            return true
        }
        return false
    }

    suspend fun onItemBestSellOrderUpdated(itemId: ShortItemId, order: OrderDto, notificationEnabled: Boolean = true) {
        updateOrder(itemId, order, notificationEnabled) { item -> bestOrderService.updateBestSellOrder(item, order) }
    }

    suspend fun onItemBestBidOrderUpdated(itemId: ShortItemId, order: OrderDto, notificationEnabled: Boolean = true) {
        updateOrder(itemId, order, notificationEnabled) { item -> bestOrderService.updateBestBidOrder(item, order) }
    }

    suspend fun onAuctionUpdated(auction: AuctionDto, notificationEnabled: Boolean = true) {
        updateAuction(auction, notificationEnabled) {
            if (auction.status == AuctionStatusDto.ACTIVE) {
                it.copy(auctions = it.auctions + auction.id)
            } else {
                it.copy(auctions = it.auctions - auction.id)
            }
        }
    }

    suspend fun onAuctionDeleted(auction: AuctionDto, notificationEnabled: Boolean = true) {
        updateAuction(auction, notificationEnabled) { it.copy(auctions = it.auctions - auction.id) }
    }

    private suspend fun updateOrder(
        itemId: ShortItemId,
        order: OrderDto,
        notificationEnabled: Boolean,
        orderUpdateAction: suspend (item: ShortItem) -> ShortItem
    ) = optimisticLock {
        val (short, updated, exist) = update(itemId, orderUpdateAction)
        if (short != updated) {
            if (updated.isNotEmpty()) {
                saveAndNotify(updated = updated, notificationEnabled = notificationEnabled, order = order)
                logger.info("Saved Item [{}] after Order event [{}]", itemId, order.id)
            } else if (exist) {
                cleanupAndNotify(updated = updated, notificationEnabled = notificationEnabled, order = order)
                logger.info("Deleted Item [{}] without enrichment data", itemId)
            }
        } else {
            logger.info("Item [{}] not changed after Order event [{}], event won't be published", itemId, order.id)
        }
    }

    private suspend fun updateAuction(
        auction: AuctionDto,
        notificationEnabled: Boolean,
        updateAction: suspend (item: ShortItem) -> ShortItem
    ) = optimisticLock {
        val itemId = ShortItemId(auction.getItemId())
        val (short, updated, exist) = update(itemId, updateAction)
        if (short != updated) {
            if (updated.isNotEmpty()) {
                saveAndNotify(updated = updated, notificationEnabled = notificationEnabled, auction = auction)
                logger.info("Saved Item [{}] after Auction event [{}]", itemId, auction.auctionId)
            } else if (exist) {
                cleanupAndNotify(updated = updated, notificationEnabled = notificationEnabled, auction = auction)
                logger.info("Deleted Item [{}] without enrichment data", itemId)
            }
        } else {
            logger.info("Item [{}] not changed after Auction event [{}], event won't be published", itemId, auction.id)
        }
    }

    suspend fun onItemDeleted(itemId: ItemIdDto) {
        val shortItemId = ShortItemId(itemId)
        val deleted = deleteItem(shortItemId)
        sendDelete(shortItemId)
        if (deleted) {
            logger.info("Item [{}] deleted (removed from NFT-Indexer)", shortItemId)
        }
    }

    private suspend fun deleteItem(itemId: ShortItemId): Boolean {
        val result = itemService.delete(itemId)
        return result != null && result.deletedCount > 0
    }

    private suspend fun update(
        itemId: ShortItemId,
        action: suspend (item: ShortItem) -> ShortItem
    ): Triple<ShortItem?, ShortItem, Boolean> {
        val current = itemService.get(itemId)
        val exist = current != null
        val short = current ?: ShortItem.empty(itemId)
        return Triple(current, action(short), exist)
    }

    private suspend fun notifyDelete(itemId: ShortItemId) {
        val event = ItemDeleteEventDto(
            itemId = itemId.toDto(),
            eventId = UUID.randomUUID().toString()
        )
        itemEventListeners.forEach { it.onEvent(event) }
    }

    // Potentially we could have updated Order here (no matter - bid/sell) and when we need to fetch
    // full version of the order, we can use this already fetched Order if it has same ID (hash)
    private suspend fun saveAndNotify(
        updated: ShortItem,
        notificationEnabled: Boolean,
        item: UnionItem? = null,
        order: OrderDto? = null,
        auction: AuctionDto? = null
    ) {
        if (!notificationEnabled) {
            itemService.save(updated)
            return
        }

        val event = buildUpdateEvent(updated, item, order, auction)
        itemService.save(updated)
        sendUpdate(event)
    }

    private suspend fun cleanupAndNotify(
        updated: ShortItem,
        notificationEnabled: Boolean,
        item: UnionItem? = null,
        order: OrderDto? = null,
        auction: AuctionDto? = null
    ) {
        if (!notificationEnabled) {
            itemService.delete(updated.id)
            return
        }

        val event = buildUpdateEvent(updated, item, order, auction)
        itemService.delete(updated.id)
        sendUpdate(event)
    }

    private suspend fun buildUpdateEvent(
        short: ShortItem,
        item: UnionItem? = null,
        order: OrderDto? = null,
        auction: AuctionDto? = null
    ): ItemUpdateEventDto {
        val dto = itemService.enrichItem(
            short,
            item,
            listOfNotNull(order).associateBy { it.id },
            listOfNotNull(auction).associateBy { it.id })

        return ItemUpdateEventDto(
            itemId = dto.id,
            item = dto,
            eventId = UUID.randomUUID().toString()
        )
    }

    private suspend fun sendUpdate(event: ItemUpdateEventDto) {
        // If item in corrupted state, we will try to reconcile it instead of sending corrupted
        // data to the customers
        if (!ItemValidator.isValid(event.item)) {
            reconciliationEventService.onCorruptedItem(event.item.id)
        } else {
            itemEventListeners.forEach { it.onEvent(event) }
        }
    }

    private suspend fun sendDelete(itemId: ShortItemId) {
        val event = ItemDeleteEventDto(
            itemId = itemId.toDto(),
            eventId = UUID.randomUUID().toString()
        )
        itemEventListeners.forEach { it.onEvent(event) }
    }
}
