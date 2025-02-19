package com.rarible.protocol.union.core.converter.helper

import com.rarible.core.common.nowMillis
import com.rarible.core.test.data.randomAddress
import com.rarible.core.test.data.randomBigDecimal
import com.rarible.core.test.data.randomBigInt
import com.rarible.core.test.data.randomBoolean
import com.rarible.core.test.data.randomDouble
import com.rarible.core.test.data.randomInt
import com.rarible.core.test.data.randomLong
import com.rarible.core.test.data.randomString
import com.rarible.protocol.union.core.EsActivityEnrichmentProperties
import com.rarible.protocol.union.core.converter.EsActivityConverter
import com.rarible.protocol.union.core.model.UnionItem
import com.rarible.protocol.union.core.service.ItemService
import com.rarible.protocol.union.core.service.router.BlockchainRouter
import com.rarible.protocol.union.core.util.truncatedToSeconds
import com.rarible.protocol.union.dto.ActivityBlockchainInfoDto
import com.rarible.protocol.union.dto.ActivityIdDto
import com.rarible.protocol.union.dto.ActivityTypeDto
import com.rarible.protocol.union.dto.AssetDto
import com.rarible.protocol.union.dto.AssetTypeDto
import com.rarible.protocol.union.dto.AuctionBidActivityDto
import com.rarible.protocol.union.dto.AuctionBidDto
import com.rarible.protocol.union.dto.AuctionCancelActivityDto
import com.rarible.protocol.union.dto.AuctionDataDto
import com.rarible.protocol.union.dto.AuctionDto
import com.rarible.protocol.union.dto.AuctionEndActivityDto
import com.rarible.protocol.union.dto.AuctionFinishActivityDto
import com.rarible.protocol.union.dto.AuctionHistoryDto
import com.rarible.protocol.union.dto.AuctionIdDto
import com.rarible.protocol.union.dto.AuctionOpenActivityDto
import com.rarible.protocol.union.dto.AuctionStartActivityDto
import com.rarible.protocol.union.dto.AuctionStatusDto
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.BlockchainGroupDto
import com.rarible.protocol.union.dto.BurnActivityDto
import com.rarible.protocol.union.dto.CollectionIdDto
import com.rarible.protocol.union.dto.ContractAddress
import com.rarible.protocol.union.dto.EthErc1155AssetTypeDto
import com.rarible.protocol.union.dto.EthErc20AssetTypeDto
import com.rarible.protocol.union.dto.ItemIdDto
import com.rarible.protocol.union.dto.MintActivityDto
import com.rarible.protocol.union.dto.OrderActivityMatchSideDto
import com.rarible.protocol.union.dto.OrderActivitySourceDto
import com.rarible.protocol.union.dto.OrderBidActivityDto
import com.rarible.protocol.union.dto.OrderCancelBidActivityDto
import com.rarible.protocol.union.dto.OrderCancelListActivityDto
import com.rarible.protocol.union.dto.OrderListActivityDto
import com.rarible.protocol.union.dto.OrderMatchSellDto
import com.rarible.protocol.union.dto.OrderMatchSwapDto
import com.rarible.protocol.union.dto.RaribleAuctionV1BidDataV1Dto
import com.rarible.protocol.union.dto.RaribleAuctionV1BidV1Dto
import com.rarible.protocol.union.dto.RaribleAuctionV1DataV1Dto
import com.rarible.protocol.union.dto.SolanaNftAssetTypeDto
import com.rarible.protocol.union.dto.TransferActivityDto
import com.rarible.protocol.union.dto.UnionAddress
import com.rarible.protocol.union.dto.ext
import com.rarible.protocol.union.dto.parser.IdParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.Instant

class EsActivityConverterTest {

    private val router = mockk<BlockchainRouter<ItemService>>()
    private val enricher = mockk<SellActivityEnricher>()

    private val converter = EsActivityConverter(
        router,
        enricher,
        EsActivityEnrichmentProperties(),
    )

    @Test
    fun `should convert activities batch`() = runBlocking<Unit> {
        // given
        val ethMintItemId = ItemIdDto(BlockchainDto.ETHEREUM, "contract1", BigInteger.ONE)
        val ethMintId = "123"
        val ethMintColId = CollectionIdDto(BlockchainDto.ETHEREUM, "col1")
        val ethMint = MintActivityDto(
            id = ActivityIdDto(BlockchainDto.ETHEREUM, ethMintId),
            date = randomDate(),
            blockchainInfo = ActivityBlockchainInfoDto(
                transactionHash = randomString(),
                blockHash = randomString(),
                blockNumber = randomLong(),
                logIndex = randomInt(),
            ),
            owner = randomUnionAddress(),
            itemId = ethMintItemId,
            transactionHash = randomString(),
            value = randomBigInt(),
        )
        val ethMintItem = randomUnionItem(ethMintItemId, ethMintColId)

        val ethBurnItemId = ItemIdDto(BlockchainDto.ETHEREUM, "contract2", BigInteger.ONE)
        val ethBurnId = "456"
        val ethBurnColId = CollectionIdDto(BlockchainDto.ETHEREUM, "col2")
        val ethBurn = BurnActivityDto(
            id = ActivityIdDto(BlockchainDto.ETHEREUM, ethBurnId),
            date = randomDate(),
            blockchainInfo = ActivityBlockchainInfoDto(
                transactionHash = randomString(),
                blockHash = randomString(),
                blockNumber = randomLong(),
                logIndex = randomInt(),
            ),
            owner = randomUnionAddress(),
            itemId = ethBurnItemId,
            transactionHash = randomString(),
            value = randomBigInt(),
        )
        val ethBurnItem = randomUnionItem(ethBurnItemId, ethBurnColId)

        val solanaListItemId = ItemIdDto(BlockchainDto.SOLANA, "contract3", BigInteger.ONE)
        val solanaListId = "789"
        val solanaListColId = CollectionIdDto(BlockchainDto.SOLANA, "col3")
        val solanaList = OrderListActivityDto(
            id = ActivityIdDto(BlockchainDto.SOLANA, solanaListId),
            date = randomDate(),
            maker = randomUnionAddress(),
            make = AssetDto(
                type = SolanaNftAssetTypeDto(
                    itemId = solanaListItemId
                ),
                value = randomBigDecimal(),
            ),
            take = randomPaymentAsset(),
            source = OrderActivitySourceDto.RARIBLE,
            price = randomBigDecimal(),
            hash = randomString(),
        )
        val solanaListItem = randomUnionItem(solanaListItemId, solanaListColId)

        val source = listOf(ethMint, ethBurn, solanaList)

        // The only blockchain to query items is SOLANA so far
        coEvery {
            router.getService(BlockchainDto.SOLANA).getItemsByIds(listOf(solanaListItemId.value))
        } returns listOf(solanaListItem)

        // when
        val actual = converter.batchConvert(source)

        // then
        assertThat(actual).hasSize(3)
        assertThat(actual[0].activityId).isEqualTo(ethMint.id.toString())
        assertThat(actual[0].collection).isEqualTo("contract1")
        assertThat(actual[1].activityId).isEqualTo(ethBurn.id.toString())
        assertThat(actual[1].collection).isEqualTo("contract2")
        assertThat(actual[2].activityId).isEqualTo(solanaList.id.toString())
        assertThat(actual[2].collection).isEqualTo(solanaListColId.value)
        coVerify {
            router.getService(BlockchainDto.SOLANA).getItemsByIds(listOf(solanaListItemId.value))
        }
        confirmVerified(router)
    }

    @Test
    fun `should convert MintActivityDto`() = runBlocking<Unit> {
        // given
        val customCollection = CollectionIdDto(BlockchainDto.ETHEREUM, "custom")
        val source = MintActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            blockchainInfo = ActivityBlockchainInfoDto(
                transactionHash = randomString(),
                blockHash = randomString(),
                blockNumber = randomLong(),
                logIndex = randomInt(),
            ),
            owner = randomUnionAddress(),
            itemId = randomItemId(),
            transactionHash = randomString(),
            value = randomBigInt(),
            collection = customCollection
        )

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isEqualTo(source.blockchainInfo!!.blockNumber)
        assertThat(actual.logIndex).isEqualTo(source.blockchainInfo!!.logIndex)
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.MINT)
        assertThat(actual.userFrom).isNull()
        assertThat(actual.userTo).isEqualTo(source.owner.value)
        assertThat(actual.collection).isEqualTo(customCollection.value)
        assertThat(actual.item).isEqualTo(source.itemId!!.value)
    }

    @Test
    fun `should convert BurnActivityDto`() = runBlocking<Unit> {
        // given
        val source = BurnActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            blockchainInfo = ActivityBlockchainInfoDto(
                transactionHash = randomString(),
                blockHash = randomString(),
                blockNumber = randomLong(),
                logIndex = randomInt(),
            ),
            owner = randomUnionAddress(),
            itemId = randomItemId(),
            transactionHash = randomString(),
            value = randomBigInt(),
        )

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isEqualTo(source.blockchainInfo!!.blockNumber)
        assertThat(actual.logIndex).isEqualTo(source.blockchainInfo!!.logIndex)
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.BURN)
        assertThat(actual.userFrom).isEqualTo(source.owner.value)
        assertThat(actual.userTo).isNull()
        assertThat(actual.collection).isEqualTo(source.itemId!!.extractCollection())
        assertThat(actual.item).isEqualTo(source.itemId!!.value)
    }

    @Test
    fun `should convert TransferActivityDto`() = runBlocking<Unit> {
        // given
        val source = TransferActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            blockchainInfo = ActivityBlockchainInfoDto(
                transactionHash = randomString(),
                blockHash = randomString(),
                blockNumber = randomLong(),
                logIndex = randomInt(),
            ),
            from = randomUnionAddress(),
            owner = randomUnionAddress(),
            itemId = randomItemId(),
            transactionHash = randomString(),
            value = randomBigInt(),
        )

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isEqualTo(source.blockchainInfo!!.blockNumber)
        assertThat(actual.logIndex).isEqualTo(source.blockchainInfo!!.logIndex)
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.TRANSFER)
        assertThat(actual.userFrom).isEqualTo(source.from.value)
        assertThat(actual.userTo).isEqualTo(source.owner.value)
        assertThat(actual.collection).isEqualTo(source.itemId!!.extractCollection())
        assertThat(actual.item).isEqualTo(source.itemId!!.value)
    }

    @Test
    fun `should convert OrderMatchSwapDto`() = runBlocking<Unit> {
        // given
        val source = OrderMatchSwapDto(
            id = randomActivityId(),
            date = randomDate(),
            blockchainInfo = ActivityBlockchainInfoDto(
                transactionHash = randomString(),
                blockHash = randomString(),
                blockNumber = randomLong(),
                logIndex = randomInt(),
            ),
            transactionHash = randomString(),
            left = randomOrderMatchSide(),
            right = randomOrderMatchSide(),
            source = OrderActivitySourceDto.RARIBLE,
        )

        // when
        val actual = converter.convert(source, null)

        assertThat(actual).isNull()
    }

    @Test
    fun `should convert OrderMatchSellDto`() = runBlocking<Unit> {
        // given
        val source = OrderMatchSellDto(
            id = randomActivityId(),
            date = randomDate(),
            blockchainInfo = ActivityBlockchainInfoDto(
                transactionHash = randomString(),
                blockHash = randomString(),
                blockNumber = randomLong(),
                logIndex = randomInt(),
            ),
            transactionHash = randomString(),
            buyer = randomUnionAddress(),
            seller = randomUnionAddress(),
            nft = randomNftAsset(),
            payment = randomPaymentAsset(),
            source = OrderActivitySourceDto.RARIBLE,
            price = randomBigDecimal(),
            type = OrderMatchSellDto.Type.SELL,
        )
        val volumeInfo = SellActivityEnricher.SellVolumeInfo(
            sellCurrency = randomString(),
            volumeUsd = randomDouble(),
            volumeSell = randomDouble(),
            volumeNative = randomDouble(),
        )
        coEvery { enricher.provideVolumeInfo(source) } returns volumeInfo

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isEqualTo(source.blockchainInfo!!.blockNumber)
        assertThat(actual.logIndex).isEqualTo(source.blockchainInfo!!.logIndex)
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.SELL)
        assertThat(actual.userFrom).isEqualTo(source.seller.value)
        assertThat(actual.userTo).isEqualTo(source.buyer.value)
        assertThat(actual.collection).isEqualTo(source.nft.type.ext.itemId!!.extractCollection())
        assertThat(actual.item).isEqualTo(source.nft.type.ext.itemId!!.value)
        assertThat(actual.currency).isEqualTo(volumeInfo.sellCurrency)
        assertThat(actual.volumeUsd).isEqualTo(volumeInfo.volumeUsd)
        assertThat(actual.volumeSell).isEqualTo(volumeInfo.volumeSell)
        assertThat(actual.volumeNative).isEqualTo(volumeInfo.volumeNative)
    }

    @Test
    fun `should convert OrderBidActivityDto`() = runBlocking<Unit> {
        // given
        val source = OrderBidActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            maker = randomUnionAddress(),
            make = randomPaymentAsset(),
            take = randomNftAsset(),
            source = OrderActivitySourceDto.RARIBLE,
            price = randomBigDecimal(),
            hash = randomString(),
        )

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isNull()
        assertThat(actual.logIndex).isNull()
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.BID)
        assertThat(actual.userFrom).isEqualTo(source.maker.value)
        assertThat(actual.userTo).isNull()
        assertThat(actual.collection).isEqualTo(source.take.type.ext.itemId!!.extractCollection())
        assertThat(actual.item).isEqualTo(source.take.type.ext.itemId!!.value)
        assertThat(actual.currency).isEqualTo(source.make.type.ext.currencyAddress())
    }

    @Test
    fun `should convert OrderListActivityDto`() = runBlocking<Unit> {
        // given
        val source = OrderListActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            maker = randomUnionAddress(),
            make = randomNftAsset(),
            take = randomPaymentAsset(),
            source = OrderActivitySourceDto.RARIBLE,
            price = randomBigDecimal(),
            hash = randomString(),
        )

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isNull()
        assertThat(actual.logIndex).isNull()
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.LIST)
        assertThat(actual.userFrom).isEqualTo(source.maker.value)
        assertThat(actual.userTo).isNull()
        assertThat(actual.collection).isEqualTo(source.make.type.ext.collectionId!!.value)
        assertThat(actual.item).isEqualTo(source.make.type.ext.itemId!!.value)
        assertThat(actual.currency).isEqualTo(source.take.type.ext.currencyAddress())
    }

    @Test
    fun `should convert OrderCancelBidActivityDto`() = runBlocking<Unit> {
        // given
        val source = OrderCancelBidActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            maker = randomUnionAddress(),
            make = randomPaymentAssetType(),
            take = randomNftAssetType(),
            source = OrderActivitySourceDto.RARIBLE,
            hash = randomString(),
            transactionHash = randomString(),
            blockchainInfo = ActivityBlockchainInfoDto(
                transactionHash = randomString(),
                blockHash = randomString(),
                blockNumber = randomLong(),
                logIndex = randomInt(),
            ),
        )

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isEqualTo(source.blockchainInfo!!.blockNumber)
        assertThat(actual.logIndex).isEqualTo(source.blockchainInfo!!.logIndex)
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.CANCEL_BID)
        assertThat(actual.userFrom).isEqualTo(source.maker.value)
        assertThat(actual.userTo).isNull()
        assertThat(actual.collection).isEqualTo(source.take.ext.itemId!!.extractCollection())
        assertThat(actual.item).isEqualTo(source.take.ext.itemId!!.value)
        assertThat(actual.currency).isEqualTo(source.make.ext.currencyAddress())
    }

    @Test
    fun `should convert OrderCancelListActivityDto`() = runBlocking<Unit> {
        // given
        val source = OrderCancelListActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            maker = randomUnionAddress(),
            make = randomNftAssetType(),
            take = randomPaymentAssetType(),
            source = OrderActivitySourceDto.RARIBLE,
            hash = randomString(),
            transactionHash = randomString(),
            blockchainInfo = ActivityBlockchainInfoDto(
                transactionHash = randomString(),
                blockHash = randomString(),
                blockNumber = randomLong(),
                logIndex = randomInt(),
            ),
        )

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isEqualTo(source.blockchainInfo!!.blockNumber)
        assertThat(actual.logIndex).isEqualTo(source.blockchainInfo!!.logIndex)
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.CANCEL_LIST)
        assertThat(actual.userFrom).isEqualTo(source.maker.value)
        assertThat(actual.userTo).isNull()
        assertThat(actual.collection).isEqualTo(source.make.ext.itemId!!.extractCollection())
        assertThat(actual.item).isEqualTo(source.make.ext.itemId!!.value)
        assertThat(actual.currency).isEqualTo(source.take.ext.currencyAddress())
    }

    @Test
    fun `should convert AuctionOpenActivityDto`() = runBlocking<Unit> {
        // given
        val source = AuctionOpenActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            auction = randomAuction(),
            transactionHash = randomString(),
        )

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isNull()
        assertThat(actual.logIndex).isNull()
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.AUCTION_CREATED)
        assertThat(actual.userFrom).isEqualTo(source.auction.seller.value)
        assertThat(actual.userTo).isNull()
        assertThat(actual.collection).isEqualTo(source.auction.sell.type.ext.itemId!!.extractCollection())
        assertThat(actual.item).isEqualTo(source.auction.sell.type.ext.itemId!!.value)
        assertThat(actual.currency).isEqualTo(source.auction.buy.ext.currencyAddress())
    }

    @Test
    fun `should convert AuctionBidActivityDto`() = runBlocking<Unit> {
        // given
        val source = AuctionBidActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            auction = randomAuction(),
            transactionHash = randomString(),
            bid = randomAuctionBidData(),
        )

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isNull()
        assertThat(actual.logIndex).isNull()
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.AUCTION_BID)
        assertThat(actual.userFrom).isEqualTo(source.auction.seller.value)
        assertThat(actual.userTo).isEqualTo(source.bid.buyer.value)
        assertThat(actual.collection).isEqualTo(source.auction.sell.type.ext.itemId!!.extractCollection())
        assertThat(actual.item).isEqualTo(source.auction.sell.type.ext.itemId!!.value)
        assertThat(actual.currency).isEqualTo(source.auction.buy.ext.currencyAddress())
    }

    @Test
    fun `should convert AuctionFinishActivityDto`() = runBlocking<Unit> {
        // given
        val source = AuctionFinishActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            auction = randomAuction(),
            transactionHash = randomString(),
        )

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isNull()
        assertThat(actual.logIndex).isNull()
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.AUCTION_FINISHED)
        assertThat(actual.userFrom).isEqualTo(source.auction.seller.value)
        assertThat(actual.userTo).isNull()
        assertThat(actual.collection).isEqualTo(source.auction.sell.type.ext.itemId!!.extractCollection())
        assertThat(actual.item).isEqualTo(source.auction.sell.type.ext.itemId!!.value)
        assertThat(actual.currency).isEqualTo(source.auction.buy.ext.currencyAddress())
    }

    @Test
    fun `should convert AuctionCancelActivityDto`() = runBlocking<Unit> {
        // given
        val source = AuctionCancelActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            auction = randomAuction(),
            transactionHash = randomString(),
        )

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isNull()
        assertThat(actual.logIndex).isNull()
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.AUCTION_CANCEL)
        assertThat(actual.userFrom).isEqualTo(source.auction.seller.value)
        assertThat(actual.userTo).isNull()
        assertThat(actual.collection).isEqualTo(source.auction.sell.type.ext.itemId!!.extractCollection())
        assertThat(actual.item).isEqualTo(source.auction.sell.type.ext.itemId!!.value)
        assertThat(actual.currency).isEqualTo(source.auction.buy.ext.currencyAddress())
    }

    @Test
    fun `should convert AuctionStartActivityDto`() = runBlocking<Unit> {
        // given
        val source = AuctionStartActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            auction = randomAuction(),
        )

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isNull()
        assertThat(actual.logIndex).isNull()
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.AUCTION_STARTED)
        assertThat(actual.userFrom).isEqualTo(source.auction.seller.value)
        assertThat(actual.userTo).isNull()
        assertThat(actual.collection).isEqualTo(source.auction.sell.type.ext.itemId!!.extractCollection())
        assertThat(actual.item).isEqualTo(source.auction.sell.type.ext.itemId!!.value)
        assertThat(actual.currency).isEqualTo(source.auction.buy.ext.currencyAddress())
    }

    @Test
    fun `should convert AuctionEndActivityDto`() = runBlocking<Unit> {
        // given
        val source = AuctionEndActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            auction = randomAuction(),
        )

        // when
        val actual = converter.convert(source, null)!!

        // then
        assertThat(actual.activityId).isEqualTo(source.id.toString())
        assertThat(actual.date).isEqualTo(source.date)
        assertThat(actual.blockNumber).isNull()
        assertThat(actual.logIndex).isNull()
        assertThat(actual.blockchain).isEqualTo(source.id.blockchain)
        assertThat(actual.type).isEqualTo(ActivityTypeDto.AUCTION_ENDED)
        assertThat(actual.userFrom).isEqualTo(source.auction.seller.value)
        assertThat(actual.userTo).isNull()
        assertThat(actual.collection).isEqualTo(source.auction.sell.type.ext.itemId!!.extractCollection())
        assertThat(actual.item).isEqualTo(source.auction.sell.type.ext.itemId!!.value)
        assertThat(actual.currency).isEqualTo(source.auction.buy.ext.currencyAddress())
    }

    @Test
    fun `should not fail when itemId is null`() = runBlocking<Unit> {
        // given
        val source = TransferActivityDto(
            id = randomActivityId(),
            date = randomDate(),
            blockchainInfo = ActivityBlockchainInfoDto(
                transactionHash = randomString(),
                blockHash = randomString(),
                blockNumber = randomLong(),
                logIndex = randomInt(),
            ),
            from = randomUnionAddress(),
            owner = randomUnionAddress(),
            itemId = null,
            transactionHash = randomString(),
            value = randomBigInt(),
        )

        // when & then - nothing thrown
        converter.convert(source, null)!!
    }

    private fun randomActivityId(): ActivityIdDto {
        return ActivityIdDto(
            blockchain = randomBlockchain(),
            value = randomAddress().toString()
        )
    }

    private fun randomDate(): Instant {
        return Instant.ofEpochMilli(randomLong()).truncatedToSeconds()
    }

    private fun randomBlockchain(): BlockchainDto {
        return BlockchainDto.values().random()
    }

    private fun randomUnionAddress(): UnionAddress {
        return UnionAddress(
            blockchainGroup = randomBlockchainGroup(),
            value = randomAddress().toString()
        )
    }

    private fun randomBlockchainGroup(): BlockchainGroupDto {
        return BlockchainGroupDto.values().random()
    }

    private fun randomOrderMatchSide(): OrderActivityMatchSideDto {
        return OrderActivityMatchSideDto(
            maker = randomUnionAddress(),
            hash = randomString(),
            asset = randomPaymentAsset()
        )
    }

    private fun randomNftAsset(): AssetDto {
        return AssetDto(
            type = randomNftAssetType(),
            value = randomBigDecimal(),
        )
    }

    private fun randomPaymentAsset(): AssetDto {
        return AssetDto(
            type = randomPaymentAssetType(),
            value = randomBigDecimal(),
        )
    }

    private fun randomNftAssetType(): AssetTypeDto {
        val blockchain = BlockchainDto.ETHEREUM
        val address = randomString()
        return EthErc1155AssetTypeDto(
            contract = ContractAddress(blockchain, address),
            collection = CollectionIdDto(blockchain, address),
            tokenId = randomBigInt(),
        )
    }

    private fun randomPaymentAssetType(): AssetTypeDto {
        return EthErc20AssetTypeDto(
            contract = ContractAddress(
                randomBlockchain(),
                randomString()
            )
        )
    }

    private fun randomAuction(): AuctionDto {
        return AuctionDto(
            id = AuctionIdDto(
                randomBlockchain(),
                randomString(),
            ),
            contract = ContractAddress(
                randomBlockchain(),
                randomString(),
            ),
            type = AuctionDto.Type.values().random(),
            seller = randomUnionAddress(),
            sell = randomNftAsset(),
            buy = randomPaymentAssetType(),
            endTime = randomDate(),
            minimalStep = randomBigDecimal(),
            minimalPrice = randomBigDecimal(),
            createdAt = randomDate(),
            lastUpdateAt = randomDate(),
            buyPrice = randomBigDecimal(),
            buyPriceUsd = randomBigDecimal(),
            pending = listOf(AuctionHistoryDto(randomString())),
            status = AuctionStatusDto.values().random(),
            ongoing = randomBoolean(),
            hash = randomString(),
            auctionId = randomBigInt(),
            lastBid = null,
            data = randomAuctionData()
        )
    }

    private fun randomAuctionBidData(): AuctionBidDto {
        return RaribleAuctionV1BidV1Dto(
            buyer = randomUnionAddress(),
            amount = randomBigDecimal(),
            date = randomDate(),
            status = AuctionBidDto.Status.values().random(),
            data = RaribleAuctionV1BidDataV1Dto(
                originFees = emptyList(),
                payouts = emptyList(),
            )
        )
    }

    private fun randomAuctionData(): AuctionDataDto {
        return RaribleAuctionV1DataV1Dto(
            originFees = emptyList(),
            payouts = emptyList(),
            startTime = randomDate(),
            duration = randomBigInt(),
            buyOutPrice = randomBigDecimal(),
        )
    }

    private fun ItemIdDto.extractCollection(): String? {
        return IdParser.extractContract(this)
    }

    private fun randomItemId(): ItemIdDto {
        return if (randomBoolean()) ItemIdDto(randomBlockchain(), randomString(), randomBigInt())
        else ItemIdDto(randomBlockchain(), randomString())
    }

    private fun randomUnionItem(id: ItemIdDto, collectionIdDto: CollectionIdDto): UnionItem {
        return UnionItem(
            id = id,
            collection = collectionIdDto,
            lazySupply = BigInteger.ONE,
            mintedAt = nowMillis(),
            lastUpdatedAt = nowMillis(),
            supply = BigInteger.ONE,
            deleted = false,
        )
    }
}
