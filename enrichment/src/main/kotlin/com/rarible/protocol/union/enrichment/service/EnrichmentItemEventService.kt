package com.rarible.protocol.union.enrichment.service

import com.rarible.core.common.optimisticLock
import com.rarible.protocol.union.core.FeatureFlagsProperties
import com.rarible.protocol.union.core.event.OutgoingEventListener
import com.rarible.protocol.union.core.model.PoolItemAction
import com.rarible.protocol.union.core.model.UnionActivity
import com.rarible.protocol.union.core.model.UnionEventTimeMarks
import com.rarible.protocol.union.core.model.UnionItem
import com.rarible.protocol.union.core.model.UnionItemChangeEvent
import com.rarible.protocol.union.core.model.UnionItemDeleteEvent
import com.rarible.protocol.union.core.model.UnionItemUpdateEvent
import com.rarible.protocol.union.core.model.UnionOrder
import com.rarible.protocol.union.core.model.getItemId
import com.rarible.protocol.union.dto.ActivityIdDto
import com.rarible.protocol.union.dto.AuctionDto
import com.rarible.protocol.union.dto.AuctionStatusDto
import com.rarible.protocol.union.dto.ItemDeleteEventDto
import com.rarible.protocol.union.dto.ItemEventDto
import com.rarible.protocol.union.dto.ItemIdDto
import com.rarible.protocol.union.dto.ItemUpdateEventDto
import com.rarible.protocol.union.enrichment.converter.ItemLastSaleConverter
import com.rarible.protocol.union.enrichment.evaluator.OrderPoolEvaluator
import com.rarible.protocol.union.enrichment.meta.item.ItemMetaPipeline
import com.rarible.protocol.union.enrichment.model.ItemLastSale
import com.rarible.protocol.union.enrichment.model.ItemSellStats
import com.rarible.protocol.union.enrichment.model.ShortItem
import com.rarible.protocol.union.enrichment.model.ShortItemId
import com.rarible.protocol.union.enrichment.model.ShortOwnership
import com.rarible.protocol.union.enrichment.validator.EntityValidator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class EnrichmentItemEventService(
    private val enrichmentItemService: EnrichmentItemService,
    private val enrichmentOwnershipService: EnrichmentOwnershipService,
    private val enrichmentActivityService: EnrichmentActivityService,
    private val itemEventListeners: List<OutgoingEventListener<ItemEventDto>>,
    private val bestOrderService: BestOrderService,
    private val reconciliationEventService: ReconciliationEventService,
    private val enrichmentItemSellStatsService: EnrichmentItemSellStatsService,
    private val featureFlagsProperties: FeatureFlagsProperties,
) {

    private val logger = LoggerFactory.getLogger(EnrichmentItemEventService::class.java)

    suspend fun onItemChanged(event: UnionItemChangeEvent) {
        val itemId = event.itemId
        val existing = enrichmentItemService.getOrEmpty(ShortItemId(itemId))
        val updateEvent = buildUpdateEvent(
            short = existing,
            eventTimeMarks = event.eventTimeMarks
        )
        sendUpdate(updateEvent)
    }

    suspend fun onItemUpdated(event: UnionItemUpdateEvent) {
        val item = event.item
        val existing = enrichmentItemService.getOrCreateWithLastUpdatedAtUpdate(ShortItemId(item.id))
        val updateEvent = buildUpdateEvent(
            short = existing,
            item = item,
            eventTimeMarks = event.eventTimeMarks
        )
        sendUpdate(updateEvent)
    }

    suspend fun onItemDeleted(itemDeleteEvent: UnionItemDeleteEvent) = optimisticLock {
        val itemId = itemDeleteEvent.itemId
        val existing = enrichmentItemService.getOrEmpty(ShortItemId(itemId))
        enrichmentItemService.save(existing)
        val event = ItemDeleteEventDto(
            itemId = itemId,
            eventId = UUID.randomUUID().toString(),
            eventTimeMarks = itemDeleteEvent.eventTimeMarks?.addOut()?.toDto()
        )
        sendDelete(event)
    }

    // If ownership was updated, we need to recalculate totalStock/sellers for related item,
    // also, we can specify here Order which triggered this update - ItemService
    // can use this full Order to avoid unnecessary getOrderById calls
    suspend fun onOwnershipUpdated(
        oldOwnership: ShortOwnership?,
        newOwnership: ShortOwnership?,
        order: UnionOrder?,
        eventTimeMarks: UnionEventTimeMarks?,
        notificationEnabled: Boolean = true
    ) {
        if (!enrichmentItemSellStatsService.isSellStatsChanged(oldOwnership, newOwnership)) {
            return
        }
        val ownershipId = oldOwnership?.id ?: newOwnership!!.id
        val itemId = ShortItemId(ownershipId.blockchain, ownershipId.itemId)
        optimisticLock {
            val item = enrichmentItemService.get(itemId)
            if (item == null) {
                logger.debug(
                    "Item [{}] not found in DB, skipping sell stats update on Ownership event: [{}]",
                    itemId, ownershipId
                )
            } else {
                val refreshedSellStats = if (featureFlagsProperties.enableIncrementalItemStats) {
                    enrichmentItemSellStatsService.incrementSellStats(
                        item = item,
                        oldOwnership = oldOwnership,
                        newOwnership = newOwnership
                    )
                } else {
                    enrichmentOwnershipService.getItemSellStats(itemId)
                }
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
                    saveAndNotify(
                        updated = updatedItem,
                        notificationEnabled = notificationEnabled,
                        order = order,
                        eventTimeMarks = eventTimeMarks
                    )
                } else {
                    logger.debug(
                        "Sell stats of Item [{}] are the same as before Ownership event [{}], skipping update",
                        itemId, ownershipId
                    )
                }
            }
        }
    }

    suspend fun onActivity(
        activity: UnionActivity,
        item: UnionItem? = null,
        eventTimeMarks: UnionEventTimeMarks?,
        notificationEnabled: Boolean = true
    ) {
        val lastSale = ItemLastSaleConverter.convert(activity) ?: return
        val itemId = activity.itemId() ?: return

        onActivity(
            id = activity.id,
            reverted = activity.reverted,
            itemId = itemId,
            lastSale = lastSale,
            eventTimeMarks = eventTimeMarks,
            notificationEnabled = notificationEnabled
        )
    }

    suspend fun onActivity(
        id: ActivityIdDto,
        itemId: ItemIdDto,
        lastSale: ItemLastSale,
        reverted: Boolean?,
        item: UnionItem? = null,
        eventTimeMarks: UnionEventTimeMarks?,
        notificationEnabled: Boolean = true
    ) {

        optimisticLock {
            val existing = enrichmentItemService.getOrEmpty(ShortItemId(itemId))
            val currentLastSale = existing.lastSale

            val newLastSale = if (reverted == true) {
                // We should re-evaluate last sale only if received activity has the same sale data
                if (lastSale == currentLastSale) {
                    logger.info("Reverting Activity LastSale {} for Item [{}], reverting it", lastSale, itemId)
                    enrichmentActivityService.getItemLastSale(itemId)
                } else {
                    currentLastSale
                }
            } else {
                if (currentLastSale == null || currentLastSale.date.isBefore(lastSale.date)) {
                    lastSale
                } else {
                    currentLastSale
                }
            }

            if (newLastSale == currentLastSale) {
                logger.info("Item [{}] not changed after Activity event [{}]", itemId, id)
            } else {
                logger.info(
                    "Item [{}] LastSale changed on Activity event [{}]: {} -> {}",
                    itemId, id, currentLastSale, newLastSale
                )
                saveAndNotify(
                    updated = existing.copy(lastSale = newLastSale),
                    notificationEnabled = notificationEnabled,
                    item = item,
                    eventTimeMarks = eventTimeMarks // TODO ideally to have marks for activity too
                )
            }
        }
    }

    suspend fun recalculateBestOrders(
        item: ShortItem,
        eventTimeMarks: UnionEventTimeMarks
    ): Boolean {
        val updated = bestOrderService.updateBestOrders(item)
        if (updated != item) {
            logger.info(
                "Item BestSellOrder updated ([{}] -> [{}]), BestBidOrder updated ([{}] -> [{}]) due to currency rate changed",
                item.bestSellOrder?.dtoId, updated.bestSellOrder?.dtoId,
                item.bestBidOrder?.dtoId, updated.bestBidOrder?.dtoId
            )
            saveAndNotify(
                updated = updated,
                notificationEnabled = true,
                eventTimeMarks = eventTimeMarks
            )
            return true
        }
        return false
    }

    suspend fun onItemBestSellOrderUpdated(
        itemId: ShortItemId,
        order: UnionOrder,
        eventTimeMarks: UnionEventTimeMarks?,
        notificationEnabled: Boolean = true
    ) {
        updateOrder(itemId, order, notificationEnabled, eventTimeMarks) { item ->
            val origins = enrichmentItemService.getItemOrigins(itemId)
            bestOrderService.updateBestSellOrder(item, order, origins)
        }
    }

    suspend fun onItemBestBidOrderUpdated(
        itemId: ShortItemId,
        order: UnionOrder,
        eventTimeMarks: UnionEventTimeMarks?,
        notificationEnabled: Boolean = true
    ) {
        updateOrder(itemId, order, notificationEnabled, eventTimeMarks) { item ->
            val origins = enrichmentItemService.getItemOrigins(itemId)
            bestOrderService.updateBestBidOrder(item, order, origins)
        }
    }

    suspend fun onPoolOrderUpdated(
        itemId: ShortItemId,
        order: UnionOrder,
        action: PoolItemAction,
        eventTimeMarks: UnionEventTimeMarks?,
        notificationEnabled: Boolean = true
    ) {
        val hackedOrder = order.applyStatusByAction(action)

        updateOrder(itemId, hackedOrder, notificationEnabled, eventTimeMarks) { item ->
            val updated = OrderPoolEvaluator.updatePoolOrderSet(item, hackedOrder, action)
            if (OrderPoolEvaluator.needUpdateOrder(updated, hackedOrder, action)) {
                // Origins might be ignored for such orders
                bestOrderService.updateBestSellOrder(updated, hackedOrder, emptyList())
            } else {
                item
            }
        }
    }

    @Deprecated("Not used")
    suspend fun onAuctionUpdated(auction: AuctionDto, notificationEnabled: Boolean = true) {
        updateAuction(auction, notificationEnabled) {
            if (auction.status == AuctionStatusDto.ACTIVE) {
                it.copy(auctions = it.auctions + auction.id)
            } else {
                it.copy(auctions = it.auctions - auction.id)
            }
        }
    }

    @Deprecated("Not used")
    suspend fun onAuctionDeleted(auction: AuctionDto, notificationEnabled: Boolean = true) {
        updateAuction(auction, notificationEnabled) { it.copy(auctions = it.auctions - auction.id) }
    }

    private suspend fun updateOrder(
        itemId: ShortItemId,
        order: UnionOrder,
        notificationEnabled: Boolean,
        eventTimeMarks: UnionEventTimeMarks?,
        orderUpdateAction: suspend (item: ShortItem) -> ShortItem
    ) = optimisticLock {
        val (short, updated) = update(itemId, orderUpdateAction)
        if (short != updated) {
            saveAndNotify(
                updated = updated,
                notificationEnabled = notificationEnabled,
                order = order,
                eventTimeMarks = eventTimeMarks
            )
            logger.info("Saved Item [{}] after Order event [{}]", itemId, order.id)
        } else {
            logger.info("Item [{}] not changed after Order event [{}], event won't be published", itemId, order.id)
        }
    }

    @Deprecated("Not used")
    private suspend fun updateAuction(
        auction: AuctionDto,
        notificationEnabled: Boolean,
        updateAction: suspend (item: ShortItem) -> ShortItem
    ) = optimisticLock {
        val itemId = ShortItemId(auction.getItemId())
        val (current, updated) = update(itemId, updateAction)
        if (current != updated) {
            saveAndNotify(
                updated = updated,
                notificationEnabled = notificationEnabled,
                auction = auction,
                eventTimeMarks = null
            )
            logger.info("Saved Item [{}] after Auction event [{}]", itemId, auction.auctionId)
        } else {
            logger.info("Item [{}] not changed after Auction event [{}], event won't be published", itemId, auction.id)
        }
    }

    private suspend fun update(
        itemId: ShortItemId,
        action: suspend (item: ShortItem) -> ShortItem
    ): Pair<ShortItem, ShortItem> {
        val current = enrichmentItemService.getOrEmpty(itemId)
        return current to action(current)
    }

    // Potentially we could have updated Order here (no matter - bid/sell) and when we need to fetch
    // full version of the order, we can use this already fetched Order if it has same ID (hash)
    private suspend fun saveAndNotify(
        updated: ShortItem,
        notificationEnabled: Boolean,
        item: UnionItem? = null,
        order: UnionOrder? = null,
        auction: AuctionDto? = null,
        eventTimeMarks: UnionEventTimeMarks?
    ) {
        if (!notificationEnabled) {
            enrichmentItemService.save(updated)
            return
        }

        val event = buildUpdateEvent(updated, item, order, auction, eventTimeMarks)
        enrichmentItemService.save(updated)
        sendUpdate(event)
    }

    private suspend fun buildUpdateEvent(
        short: ShortItem,
        item: UnionItem? = null,
        order: UnionOrder? = null,
        auction: AuctionDto? = null,
        eventTimeMarks: UnionEventTimeMarks? = null
    ): ItemUpdateEventDto {
        val dto = enrichmentItemService.enrichItem(
            shortItem = short,
            item = item,
            orders = listOfNotNull(order).associateBy { it.id },
            auctions = listOfNotNull(auction).associateBy { it.id },
            metaPipeline = ItemMetaPipeline.EVENT
        )

        return ItemUpdateEventDto(
            itemId = dto.id,
            item = dto,
            eventId = UUID.randomUUID().toString(),
            eventTimeMarks = eventTimeMarks?.addOut()?.toDto()
        )
    }

    private suspend fun sendUpdate(event: ItemUpdateEventDto) {
        // If item in corrupted state, we will try to reconcile it instead of sending corrupted
        // data to the customers
        if (!EntityValidator.isValid(event.item)) {
            reconciliationEventService.onCorruptedItem(event.item.id)
        } else {
            itemEventListeners.forEach { it.onEvent(event) }
        }
    }

    private suspend fun sendDelete(event: ItemDeleteEventDto) {
        itemEventListeners.forEach { it.onEvent(event) }
    }
}
