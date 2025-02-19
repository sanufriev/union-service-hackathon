package com.rarible.protocol.union.listener.service

import com.rarible.core.test.data.randomAddress
import com.rarible.protocol.dto.OrderStatusDto
import com.rarible.protocol.union.core.model.PoolItemAction
import com.rarible.protocol.union.core.model.ReconciliationMarkType
import com.rarible.protocol.union.core.model.UnionItemDeleteEvent
import com.rarible.protocol.union.core.model.UnionItemUpdateEvent
import com.rarible.protocol.union.core.model.stubEventMark
import com.rarible.protocol.union.dto.AuctionStatusDto
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.PlatformDto
import com.rarible.protocol.union.enrichment.converter.ItemDtoConverter
import com.rarible.protocol.union.enrichment.converter.OrderDtoConverter
import com.rarible.protocol.union.enrichment.converter.ShortItemConverter
import com.rarible.protocol.union.enrichment.converter.ShortOrderConverter
import com.rarible.protocol.union.enrichment.model.ShortItem
import com.rarible.protocol.union.enrichment.model.ShortItemId
import com.rarible.protocol.union.enrichment.model.ShortPoolOrder
import com.rarible.protocol.union.enrichment.repository.ReconciliationMarkRepository
import com.rarible.protocol.union.enrichment.service.EnrichmentItemEventService
import com.rarible.protocol.union.enrichment.service.EnrichmentItemService
import com.rarible.protocol.union.enrichment.service.EnrichmentOwnershipService
import com.rarible.protocol.union.enrichment.test.data.randomItemMetaDownloadEntry
import com.rarible.protocol.union.enrichment.test.data.randomShortItem
import com.rarible.protocol.union.enrichment.test.data.randomShortOwnership
import com.rarible.protocol.union.enrichment.test.data.randomUnionBidOrder
import com.rarible.protocol.union.enrichment.test.data.randomUnionItem
import com.rarible.protocol.union.enrichment.test.data.randomUnionSellOrder

import com.rarible.protocol.union.integration.ethereum.converter.EthAuctionConverter
import com.rarible.protocol.union.integration.ethereum.converter.EthItemConverter
import com.rarible.protocol.union.integration.ethereum.converter.EthMetaConverter
import com.rarible.protocol.union.integration.ethereum.converter.EthOrderConverter
import com.rarible.protocol.union.integration.ethereum.data.randomEthAssetErc20
import com.rarible.protocol.union.integration.ethereum.data.randomEthAuctionDto
import com.rarible.protocol.union.integration.ethereum.data.randomEthBidOrderDto
import com.rarible.protocol.union.integration.ethereum.data.randomEthItemId
import com.rarible.protocol.union.integration.ethereum.data.randomEthItemMeta
import com.rarible.protocol.union.integration.ethereum.data.randomEthNftItemDto
import com.rarible.protocol.union.integration.ethereum.data.randomEthSellOrderDto
import com.rarible.protocol.union.listener.test.AbstractIntegrationTest
import com.rarible.protocol.union.listener.test.IntegrationTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import scalether.domain.Address
import java.time.Instant

@IntegrationTest
class EnrichmentItemEventServiceIt : AbstractIntegrationTest() {

    @Autowired
    private lateinit var itemEventService: EnrichmentItemEventService

    @Autowired
    private lateinit var itemService: EnrichmentItemService

    @Autowired
    private lateinit var ownershipService: EnrichmentOwnershipService

    @Autowired
    lateinit var ethOrderConverter: EthOrderConverter

    @Autowired
    lateinit var ethAuctionConverter: EthAuctionConverter

    @Autowired
    private lateinit var itemReconciliationMarkRepository: ReconciliationMarkRepository

    @Test
    fun `update event - item doesn't exist`() = runBlocking {
        val itemId = randomEthItemId()
        val unionItem = randomUnionItem(itemId)

        itemEventService.onItemUpdated(UnionItemUpdateEvent(unionItem, stubEventMark()))

        val created = itemService.get(ShortItemId(itemId))!!

        // Item should be created since it wasn't in DB before update
        assertThat(created).isEqualTo(
            ShortItem.empty(created.id).copy(
                lastUpdatedAt = created.lastUpdatedAt,
                version = created.version,
                metaEntry = created.metaEntry
            )
        )
        assertThat(created.lastUpdatedAt).isAfter(Instant.now().minusSeconds(5))
        // But there should be single Item event "as is"
        waitAssert {
            val messages = findItemUpdates(itemId.value)
            assertThat(messages).hasSize(1)
            assertThat(messages[0].itemId).isEqualTo(itemId)
            assertThat(messages[0].item).isEqualTo(ItemDtoConverter.convert(unionItem, meta = unionItem.meta))
        }
    }

    @Test
    fun `update event - existing item updated`() = runBlocking {
        val itemId = randomEthItemId()
        val ethItem = randomEthNftItemDto(itemId)
        val bestSellOrder = randomEthSellOrderDto(itemId)
        val bestBidOrder = randomEthBidOrderDto(itemId)
        val unionBestSell = ethOrderConverter.convert(bestSellOrder, itemId.blockchain)
        val unionBestBid = ethOrderConverter.convert(bestBidOrder, itemId.blockchain)

        val whitelistedBidOrder = randomEthBidOrderDto(itemId).copy(
            make = randomEthAssetErc20(Address.apply("0xc778417e063141139fce010982780140aa0cd5ab"))
        )
        val notWhitelistedBidOrder = randomEthBidOrderDto(itemId)
        val unionWhitelistedBidOrder = ethOrderConverter.convert(whitelistedBidOrder, itemId.blockchain)
        val unionNotWhitelistedBidOrder = ethOrderConverter.convert(notWhitelistedBidOrder, itemId.blockchain)

        val unionItem = EthItemConverter.convert(ethItem, itemId.blockchain)
        val shortItem = ShortItemConverter.convert(unionItem).copy(
            bestSellOrder = ShortOrderConverter.convert(unionBestSell),
            bestBidOrder = ShortOrderConverter.convert(unionBestBid),
            bestBidOrders = listOf(
                unionBestBid,
                unionWhitelistedBidOrder,
                unionNotWhitelistedBidOrder
            ).associate { it.bidCurrencyId() to ShortOrderConverter.convert(it) }
        )

        itemService.save(shortItem)

        ethereumOrderControllerApiMock.mockGetByIds(bestSellOrder, bestBidOrder, whitelistedBidOrder)
        ethereumItemControllerApiMock.mockGetNftItemById(itemId, ethItem)

        itemEventService.onItemUpdated(UnionItemUpdateEvent(unionItem, stubEventMark()))

        val expected = ItemDtoConverter.convert(unionItem)
            .copy(
                bestSellOrder = OrderDtoConverter.convert(unionBestSell),
                bestBidOrder = OrderDtoConverter.convert(unionBestBid),
                bestBidOrdersByCurrency = listOf(OrderDtoConverter.convert(unionWhitelistedBidOrder))
            )

        val saved = itemService.get(shortItem.id)!!
        assertThat(saved.bestSellOrder).isEqualTo(shortItem.bestSellOrder)
        assertThat(saved.bestBidOrder).isEqualTo(shortItem.bestBidOrder)

        waitAssert {
            val messages = findItemUpdates(itemId.value)
            assertThat(messages).hasSize(1)
            assertThat(messages[0].itemId).isEqualTo(itemId)
            assertThat(messages[0].item.id).isEqualTo(expected.id)
            assertThat(messages[0].item.bestSellOrder!!.id).isEqualTo(expected.bestSellOrder!!.id)
            assertThat(messages[0].item.bestBidOrder!!.id).isEqualTo(expected.bestBidOrder!!.id)
            assertThat(messages[0].item.bestBidOrdersByCurrency!!.map { it.id })
                .isEqualTo(expected.bestBidOrdersByCurrency!!.map { it.id })
        }
    }

    @Test
    fun `update event - existing item updated, order corrupted`() = runBlocking {
        val itemId = randomEthItemId()
        val ethItem = randomEthNftItemDto(itemId)

        // Corrupted order with taker
        val bestBidOrder = randomEthSellOrderDto(itemId).copy(taker = randomAddress())
        val unionBestBid = ethOrderConverter.convert(bestBidOrder, itemId.blockchain)

        val unionItem = EthItemConverter.convert(ethItem, itemId.blockchain)
        val shortItem = ShortItemConverter.convert(unionItem).copy(
            bestBidOrder = ShortOrderConverter.convert(unionBestBid)
        )

        itemService.save(shortItem)

        ethereumOrderControllerApiMock.mockGetByIds(bestBidOrder)
        ethereumItemControllerApiMock.mockGetNftItemById(itemId, ethItem)

        itemEventService.onItemUpdated(UnionItemUpdateEvent(unionItem, stubEventMark()))

        waitAssert {
            // Event should not be sent in case of corrupted enrichment data
            val messages = findItemUpdates(itemId.value)
            assertThat(messages).hasSize(0)

            // Reconciliation mark should be created for such item
            val reconcileMarks = itemReconciliationMarkRepository.findByType(ReconciliationMarkType.ITEM, 100)
            val expectedMark = reconcileMarks.find { it.id == itemId.fullId() }
            assertThat(expectedMark).isNotNull()
        }
    }

    @Test
    fun `on ownership updated`() = runBlocking {
        val itemId = randomEthItemId()
        val ethItem = randomEthNftItemDto(itemId)
        val ethMeta = randomEthItemMeta()
        val unionMeta = EthMetaConverter.convert(ethMeta, itemId.value)
        val unionItem = EthItemConverter.convert(ethItem, itemId.blockchain)
        val shortItem = randomShortItem(itemId).copy(
            sellers = 3,
            totalStock = 20.toBigInteger(),
            metaEntry = randomItemMetaDownloadEntry(data = unionMeta)
        )
        itemService.save(shortItem)

        val bestSellOrder1 = randomUnionSellOrder(itemId).copy(makeStock = 20.toBigDecimal())
        val oldOwnership1 = randomShortOwnership(itemId)
        val ownership1 = oldOwnership1.copy(bestSellOrder = ShortOrderConverter.convert(bestSellOrder1))
        ownershipService.save(ownership1)

        val bestSellOrder2 = randomUnionSellOrder(itemId).copy(makeStock = 10.toBigDecimal())
        val ownership2 = randomShortOwnership(itemId).copy(bestSellOrder = ShortOrderConverter.convert(bestSellOrder2))
        ownershipService.save(ownership2)

        ethereumItemControllerApiMock.mockGetNftItemById(itemId, ethItem)
        ethereumItemControllerApiMock.mockGetNftItemMetaById(itemId, ethMeta)

        itemEventService.onOwnershipUpdated(
            oldOwnership = oldOwnership1,
            newOwnership = ownership1,
            order = bestSellOrder1,
            eventTimeMarks = stubEventMark()
        )

        val saved = itemService.get(shortItem.id)!!
        assertThat(saved.sellers).isEqualTo(4)
        assertThat(saved.totalStock).isEqualTo(40.toBigInteger())

        // In result event for item we expect updated totalStock/sellers
        val expected = ItemDtoConverter.convert(unionItem, saved, meta = unionMeta).copy(
            sellers = 4,
            totalStock = 40.toBigInteger()
        )

        waitAssert {
            val messages = findItemUpdates(itemId.value)
            assertThat(messages).hasSize(1)
            assertThat(messages[0].itemId).isEqualTo(itemId)
            assertThat(messages[0].item).isEqualTo(expected)
        }
    }

    @Test
    fun `on ownership updated - sell stats not changed`() = runBlocking<Unit> {
        val itemId = randomEthItemId()
        val shortItem = randomShortItem(itemId).copy(sellers = 1, totalStock = 20.toBigInteger())
        // Item should not be changed - we'll check version
        val expectedItem = itemService.save(shortItem)

        val bestSellOrder = randomUnionSellOrder(itemId).copy(makeStock = 20.toBigDecimal())
        val ownership = randomShortOwnership(itemId).copy(bestSellOrder = ShortOrderConverter.convert(bestSellOrder))
        ownershipService.save(ownership)

        itemEventService.onOwnershipUpdated(
            oldOwnership = ownership,
            newOwnership = ownership,
            order = bestSellOrder,
            eventTimeMarks = stubEventMark()
        )

        val saved = itemService.get(expectedItem.id)!!

        assertThat(saved.version).isEqualTo(expectedItem.version)
    }

    @Test
    fun `on best sell order updated - item exists`() = runBlocking {
        val itemId = randomEthItemId()
        val shortItem = randomShortItem(itemId)
        val ethItem = randomEthNftItemDto(itemId)
        val ethMeta = randomEthItemMeta()
        val unionItem = EthItemConverter.convert(ethItem, itemId.blockchain)
        itemService.save(shortItem)

        val bestSellOrder = randomEthSellOrderDto(itemId)
        val unionBestSell = ethOrderConverter.convert(bestSellOrder, itemId.blockchain)

        ethereumItemControllerApiMock.mockGetNftItemById(itemId, ethItem)
        ethereumItemControllerApiMock.mockGetNftItemMetaById(itemId, ethMeta)

        itemEventService.onItemBestSellOrderUpdated(shortItem.id, unionBestSell, stubEventMark())

        // In result event for Item we expect updated bestSellOrder
        val expected = ItemDtoConverter.convert(unionItem)
            .copy(bestSellOrder = OrderDtoConverter.convert(unionBestSell))

        val saved = itemService.get(shortItem.id)!!
        assertThat(saved.bestSellOrder).isEqualTo(ShortOrderConverter.convert(unionBestSell))

        waitAssert {
            val messages = findItemUpdates(itemId.value)
            assertThat(messages).hasSize(1)
            assertThat(messages[0].itemId).isEqualTo(itemId)
            assertThat(messages[0].item.id).isEqualTo(expected.id)
            assertThat(messages[0].item.bestSellOrder!!.id).isEqualTo(expected.bestSellOrder!!.id)
            assertThat(messages[0].item.bestBidOrder).isNull()
        }
    }

    @Test
    fun `on best sell order updated - item added to the pool`() = runBlocking<Unit> {
        val itemId = randomEthItemId()
        val shortItem = randomShortItem(itemId)
        itemService.save(shortItem)

        val bestSellOrder = randomUnionSellOrder().copy(platform = PlatformDto.SUDOSWAP)
        val shortOrder = ShortOrderConverter.convert(bestSellOrder)
        val poolShortOrder = ShortPoolOrder(bestSellOrder.sellCurrencyId(), shortOrder)

        itemEventService.onPoolOrderUpdated(
            shortItem.id,
            bestSellOrder,
            PoolItemAction.INCLUDED,
            stubEventMark(),
            false
        )

        val saved = itemService.get(shortItem.id)!!

        // Since there is no best order, pool order should become the best
        assertThat(saved.bestSellOrder).isEqualTo(shortOrder)
        assertThat(saved.poolSellOrders).hasSize(1)
        assertThat(saved.poolSellOrders[0]).isEqualTo(poolShortOrder)
    }

    @Test
    fun `on best sell order updated - item removed from the pool`() = runBlocking<Unit> {
        val bestSellOrder = randomUnionSellOrder().copy(platform = PlatformDto.SUDOSWAP)
        val shortOrder = ShortOrderConverter.convert(bestSellOrder)
        val poolShortOrder = ShortPoolOrder(bestSellOrder.sellCurrencyId(), shortOrder)

        val itemId = randomEthItemId()
        val shortItem = randomShortItem(itemId).copy(
            bestSellOrder = shortOrder,
            bestBidOrder = ShortOrderConverter.convert(randomUnionBidOrder()),
            poolSellOrders = listOf(poolShortOrder)
        )
        itemService.save(shortItem)

        ethereumOrderControllerApiMock.mockGetSellOrdersByItemAndByStatus(itemId, bestSellOrder.sellCurrencyId())

        itemEventService.onPoolOrderUpdated(
            shortItem.id,
            bestSellOrder,
            PoolItemAction.EXCLUDED,
            stubEventMark(),
            false
        )

        val saved = itemService.get(shortItem.id)!!

        assertThat(saved.bestSellOrder).isNull()
        assertThat(saved.poolSellOrders).hasSize(0)
    }

    @Test
    fun `on best sell order updated - item has no pool order`() = runBlocking<Unit> {
        val bestSellOrder = randomUnionSellOrder().copy(platform = PlatformDto.SUDOSWAP)
        val shortOrder = ShortOrderConverter.convert(bestSellOrder)

        val itemId = randomEthItemId()
        val shortItem = randomShortItem(itemId).copy(
            bestSellOrder = null,
            poolSellOrders = emptyList()
        )
        itemService.save(shortItem)

        ethereumOrderControllerApiMock.mockGetSellOrdersByItemAndByStatus(itemId, bestSellOrder.sellCurrencyId())

        itemEventService.onPoolOrderUpdated(
            shortItem.id,
            bestSellOrder,
            PoolItemAction.UPDATED,
            stubEventMark(),
            false
        )

        val saved = itemService.get(shortItem.id)!!

        assertThat(saved.bestSellOrder).isNull()
        assertThat(saved.poolSellOrders).hasSize(0)
    }

    @Test
    fun `on best bid order updated - item exists with same order, order cancelled`() = runBlocking {
        val itemId = randomEthItemId()

        val bestBidOrder = randomEthBidOrderDto(itemId).copy(status = OrderStatusDto.CANCELLED)
        val unionBestBid = ethOrderConverter.convert(bestBidOrder, itemId.blockchain)

        val shortItem = randomShortItem(itemId).copy(bestBidOrder = ShortOrderConverter.convert(unionBestBid))
        val ethItem = randomEthNftItemDto(itemId)
        val ethMeta = randomEthItemMeta()
        itemService.save(shortItem)

        ethereumItemControllerApiMock.mockGetNftItemById(itemId, ethItem)
        ethereumItemControllerApiMock.mockGetNftItemMetaById(itemId, ethMeta)
        ethereumOrderControllerApiMock.mockGetOrderBidsByItemAndByStatus(itemId, unionBestBid.bidCurrencyId())

        itemEventService.onItemBestBidOrderUpdated(shortItem.id, unionBestBid, stubEventMark())

        val saved = itemService.get(shortItem.id)!!
        assertThat(saved.bestSellOrder).isNull()
        assertThat(saved.bestBidOrder).isNull()

        waitAssert {
            val messages = findItemUpdates(itemId.value)
            assertThat(messages).hasSize(1)
            assertThat(messages[0].itemId).isEqualTo(itemId)
            assertThat(messages[0].item.bestBidOrder).isNull()
        }
    }

    @Test
    fun `on best bid order updated - item doesn't exists, order cancelled`() = runBlocking {
        val itemId = randomEthItemId()
        val shortItem = randomShortItem(itemId)
        val ethItem = randomEthNftItemDto(itemId)
        // In this case we don't have saved ShortItem in Enrichment DB

        val bestBidOrder = randomEthBidOrderDto(itemId).copy(status = OrderStatusDto.INACTIVE)
        val unionBestBid = ethOrderConverter.convert(bestBidOrder, itemId.blockchain)

        ethereumItemControllerApiMock.mockGetNftItemById(itemId, ethItem)

        itemEventService.onItemBestBidOrderUpdated(shortItem.id, unionBestBid, stubEventMark())

        val saved = itemService.get(shortItem.id)
        assertThat(saved).isNull()

        waitAssert {
            val messages = findItemUpdates(itemId.value)
            assertThat(messages).hasSize(0)
        }
    }

    @Test
    fun `delete event - existing item deleted`() = runBlocking {
        val item = itemService.save(randomShortItem())
        delay(10) // To ensure updatedAt is changed
        val itemId = item.id.toDto()
        assertThat(itemService.get(item.id)).isNotNull()

        itemEventService.onItemDeleted(UnionItemDeleteEvent(itemId, stubEventMark()))

        // Item should still exist, we don't want to delete items - but updatedAt should be changed
        val updated = itemService.get(item.id)!!
        assertThat(updated.lastUpdatedAt).isAfter(item.lastUpdatedAt)
        waitAssert {
            val messages = findItemDeletions(itemId.value)
            assertThat(messages).hasSize(1)
            assertThat(messages[0].itemId).isEqualTo(itemId)
        }
    }

    @Test
    fun `delete event - item doesn't exist`() = runBlocking {
        val shortItemId = randomShortItem().id
        val itemId = shortItemId.toDto()

        itemEventService.onItemDeleted(UnionItemDeleteEvent(itemId, stubEventMark()))

        // We want to have updatedAt in items
        assertThat(itemService.get(shortItemId)).isNotNull()
        waitAssert {
            val messages = findItemDeletions(itemId.value)
            assertThat(messages).hasSize(1)
            assertThat(messages[0].itemId).isEqualTo(itemId)
        }
    }

    @Test
    fun `damn dot`() = runBlocking<Unit> {
        val item = ShortItemConverter.convert(randomUnionItem(randomEthItemId()))

        val itemWithDotMapKey = item.copy(
            bestSellOrders = mapOf("A.something.Flow" to ShortOrderConverter.convert(randomUnionSellOrder()))
        )

        val saved = itemService.save(itemWithDotMapKey)
        val fromMongo = itemService.get(item.id)!!

        assertThat(itemWithDotMapKey).isEqualTo(saved.copy(version = null, lastUpdatedAt = item.lastUpdatedAt))
        assertThat(itemWithDotMapKey).isEqualTo(fromMongo.copy(version = null, lastUpdatedAt = item.lastUpdatedAt))
    }

    @Test
    fun `on auction update`() = runBlocking {
        val itemId = randomEthItemId()
        val ethItem = randomEthNftItemDto(itemId)
        val ethMeta = randomEthItemMeta()

        val unionItem = EthItemConverter.convert(ethItem, itemId.blockchain)
        val shortItem = ShortItemConverter.convert(unionItem)
        val auction = ethAuctionConverter.convert(randomEthAuctionDto(itemId), BlockchainDto.ETHEREUM)
        itemService.save(shortItem)

        ethereumItemControllerApiMock.mockGetNftItemById(itemId, ethItem)
        ethereumItemControllerApiMock.mockGetNftItemMetaById(itemId, ethMeta)

        itemEventService.onAuctionUpdated(auction)

        val saved = itemService.get(shortItem.id)!!
        assertThat(saved.auctions).isEqualTo(setOf(auction.id))

        waitAssert {
            val messages = findItemUpdates(itemId.value)
            assertThat(messages).hasSize(1)
            assertThat(messages[0].itemId).isEqualTo(itemId)
            assertThat(messages[0].item.auctions.size).isEqualTo(1)
        }
    }

    @Test
    fun `on auction update - inactive removed`() = runBlocking {
        val itemId = randomEthItemId()
        val ethItem = randomEthNftItemDto(itemId)
        val ethMeta = randomEthItemMeta()

        val auction = ethAuctionConverter.convert(randomEthAuctionDto(itemId), BlockchainDto.ETHEREUM)
            .copy(status = AuctionStatusDto.CANCELLED)

        val unionItem = EthItemConverter.convert(ethItem, itemId.blockchain)
        val shortItem = ShortItemConverter.convert(unionItem)
            .copy(auctions = setOf(auction.id))

        itemService.save(shortItem)

        ethereumItemControllerApiMock.mockGetNftItemById(itemId, ethItem)
        ethereumItemControllerApiMock.mockGetNftItemMetaById(itemId, ethMeta)

        itemEventService.onAuctionUpdated(auction)

        val saved = itemService.get(shortItem.id)!!
        assertThat(saved.auctions).isEmpty()

        waitAssert {
            val messages = findItemUpdates(itemId.value)
            assertThat(messages).hasSize(1)
            assertThat(messages[0].itemId).isEqualTo(itemId)
            assertThat(messages[0].item.auctions.size).isEqualTo(0)
        }
    }

    @Test
    fun `on auction update - another auction fetched`() = runBlocking {
        val itemId = randomEthItemId()
        val ethItem = randomEthNftItemDto(itemId)
        val ethMeta = randomEthItemMeta()

        val unionItem = EthItemConverter.convert(ethItem, itemId.blockchain)
        val shortItem = ShortItemConverter.convert(unionItem)
        val ethAuction = randomEthAuctionDto(itemId)
        val auction = ethAuctionConverter.convert(ethAuction, BlockchainDto.ETHEREUM)
        itemService.save(shortItem)

        ethereumItemControllerApiMock.mockGetNftItemById(itemId, ethItem)
        ethereumItemControllerApiMock.mockGetNftItemMetaById(itemId, ethMeta)
        ethereumAuctionControllerApiMock.mockGetAuctionsByIds(ethAuction)

        itemEventService.onAuctionUpdated(auction)

        val newAuction = ethAuctionConverter.convert(randomEthAuctionDto(itemId), BlockchainDto.ETHEREUM)
        itemEventService.onAuctionUpdated(newAuction)

        val saved = itemService.get(shortItem.id)!!
        assertThat(saved.auctions.size).isEqualTo(2)

        waitAssert {
            val messages = findItemUpdates(itemId.value)
            assertThat(messages).hasSize(2)

            messages.filter { it.item.auctions.size == 2 }.forEach {
                assertThat(messages[0].itemId).isEqualTo(itemId)
                assertThat(it.item.auctions.size).isEqualTo(2)
            }
        }
    }

    @Test
    fun `on auction delete`() = runBlocking {
        val itemId = randomEthItemId()
        val ethItem = randomEthNftItemDto(itemId)
        val ethMeta = randomEthItemMeta()

        val bestSell = randomEthSellOrderDto()
        val unionBestSell = ethOrderConverter.convert(bestSell, BlockchainDto.ETHEREUM)

        val ethAuction = randomEthAuctionDto(itemId)
        val auction = ethAuctionConverter.convert(ethAuction, BlockchainDto.ETHEREUM)

        val unionItem = EthItemConverter.convert(ethItem, itemId.blockchain)
        val shortItem = ShortItemConverter.convert(unionItem).copy(
            auctions = setOf(auction.id),
            bestSellOrder = ShortOrderConverter.convert(unionBestSell)
        )
        itemService.save(shortItem)

        ethereumItemControllerApiMock.mockGetNftItemById(itemId, ethItem)
        ethereumItemControllerApiMock.mockGetNftItemMetaById(itemId, ethMeta)
        ethereumOrderControllerApiMock.mockGetByIds(bestSell)

        itemEventService.onAuctionDeleted(auction)

        val saved = itemService.get(shortItem.id)!!
        // Should be not deleted since there is some enrich data
        assertThat(saved.auctions).isNullOrEmpty()

        waitAssert {
            val messages = findItemUpdates(itemId.value)
            assertThat(messages).hasSize(1)
            assertThat(messages[0].itemId).isEqualTo(itemId)
            assertThat(messages[0].item.auctions).isEmpty()
        }
    }
}
