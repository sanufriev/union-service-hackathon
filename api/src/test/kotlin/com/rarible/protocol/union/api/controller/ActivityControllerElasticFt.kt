package com.rarible.protocol.union.api.controller

import com.ninjasquad.springmockk.MockkBean
import com.rarible.core.common.nowMillis
import com.rarible.core.test.data.randomAddress
import com.rarible.core.test.data.randomBigDecimal
import com.rarible.core.test.data.randomBigInt
import com.rarible.core.test.data.randomInt
import com.rarible.protocol.dto.ActivitiesByIdRequestDto
import com.rarible.protocol.dto.AssetDto
import com.rarible.protocol.dto.AuctionActivitiesDto
import com.rarible.protocol.dto.Erc721AssetTypeDto
import com.rarible.protocol.dto.FlowActivitiesDto
import com.rarible.protocol.dto.NftActivitiesByIdRequestDto
import com.rarible.protocol.dto.NftActivitiesDto
import com.rarible.protocol.dto.OrderActivitiesDto
import com.rarible.protocol.solana.dto.ActivitiesDto
import com.rarible.protocol.union.api.client.ActivityControllerApi
import com.rarible.protocol.union.api.controller.test.AbstractIntegrationTest
import com.rarible.protocol.union.api.controller.test.IntegrationTest
import com.rarible.protocol.union.core.converter.UnionAddressConverter
import com.rarible.protocol.union.core.es.ElasticsearchTestBootstrapper
import com.rarible.protocol.union.core.util.PageSize
import com.rarible.protocol.union.core.util.truncatedToSeconds
import com.rarible.protocol.union.dto.ActivityTypeDto
import com.rarible.protocol.union.dto.AuctionStartActivityDto
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.CollectionIdDto
import com.rarible.protocol.union.dto.ItemIdDto
import com.rarible.protocol.union.dto.MintActivityDto
import com.rarible.protocol.union.dto.OrderBidActivityDto
import com.rarible.protocol.union.dto.UserActivityTypeDto
import com.rarible.protocol.union.enrichment.repository.search.EsActivityRepository
import com.rarible.protocol.union.enrichment.test.data.randomEsActivity
import com.rarible.protocol.union.integration.ethereum.data.randomEthAddress
import com.rarible.protocol.union.integration.ethereum.data.randomEthAuctionStartActivity
import com.rarible.protocol.union.integration.ethereum.data.randomEthItemMintActivity
import com.rarible.protocol.union.integration.ethereum.data.randomEthOrderActivityMatch
import com.rarible.protocol.union.integration.ethereum.data.randomEthOrderBidActivity
import com.rarible.protocol.union.integration.ethereum.data.randomEthOrderListActivity
import com.rarible.protocol.union.integration.flow.data.randomFlowBurnDto
import com.rarible.protocol.union.integration.flow.data.randomFlowCancelBidActivityDto
import com.rarible.protocol.union.integration.solana.data.randomSolanaMintActivity
import com.rarible.protocol.union.integration.tezos.data.randomTezosItemBurnActivity
import com.rarible.protocol.union.integration.tezos.data.randomTzktItemBurnActivity
import com.rarible.tzkt.client.TokenActivityClient
import io.mockk.coEvery
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import reactor.kotlin.core.publisher.toMono
import scalether.domain.AddressFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import com.rarible.protocol.solana.dto.ActivitiesByIdRequestDto as SolanaActivitiesByIdRequestDto

@FlowPreview
@IntegrationTest
@TestPropertySource(
    properties = [
        "common.feature-flags.enableActivityQueriesToElasticSearch=true",
        "common.feature-flags.enableActivityAscQueriesWithApiMerge=false",
        "integration.tezos.dipdup.enabled=true",
    ]
)
class ActivityControllerElasticFt : AbstractIntegrationTest() {

    private val continuation: String? = null
    private val defaultSize = PageSize.ACTIVITY.default
    private val sort: com.rarible.protocol.union.dto.ActivitySortDto? = null

    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var activityControllerApi: ActivityControllerApi

    @Autowired
    private lateinit var repository: EsActivityRepository

    @Autowired
    private lateinit var elasticsearchTestBootstrapper: ElasticsearchTestBootstrapper

    @MockkBean
    private lateinit var tokenActivityClient: TokenActivityClient

    @BeforeEach
    fun setUp() = runBlocking<Unit> {
        elasticsearchTestBootstrapper.bootstrap()
    }

    @Test
    fun `get all activities`() = runBlocking<Unit> {
        val types = ActivityTypeDto.values().toList()
        val blockchains = listOf(
            BlockchainDto.ETHEREUM, BlockchainDto.POLYGON, BlockchainDto.FLOW, BlockchainDto.SOLANA, BlockchainDto.TEZOS
        )
        val size = 5
        val now = nowMillis().truncatedToSeconds()

        // From this list of activities we expect only the oldest 5 in response ordered as:
        // flowActivity1, polygonItemActivity1, ethOrderActivity3, tezosActivity1 and solanaActivity1
        val ethOrderActivity1 = randomEthOrderBidActivity().copy(date = now)
        val ethOrderActivity2 = randomEthOrderBidActivity().copy(date = now.minusSeconds(5))
        val ethOrderActivity3 = randomEthOrderBidActivity().copy(date = now.minusSeconds(10))
        val ethItemActivity1 = randomEthItemMintActivity().copy(date = now.minusSeconds(4))
        val ethItemActivity2 = randomEthItemMintActivity().copy(date = now.minusSeconds(7))
        val ethItemActivity3 = randomEthItemMintActivity().copy(date = now.minusSeconds(7))
        val polygonOrderActivity1 = randomEthOrderBidActivity().copy(date = now.minusSeconds(1))
        val polygonOrderActivity2 = randomEthOrderBidActivity().copy(date = now.minusSeconds(3))
        val polygonItemActivity1 = randomEthItemMintActivity().copy(date = now.minusSeconds(12))
        val polygonItemActivity2 = randomEthItemMintActivity().copy(date = now.minusSeconds(2))
        val flowActivity1 = randomFlowBurnDto().copy(date = nowMillis().minusSeconds(13))
        val flowActivity2 = randomFlowCancelBidActivityDto().copy(date = now.minusSeconds(3))
        val solanaActivity1 = randomSolanaMintActivity().copy(date = now.minusSeconds(8))
        val tezosActivity1 = randomTezosItemBurnActivity().copy(
            id = randomInt().toString(),
            date = now.minusSeconds(9).atOffset(ZoneOffset.UTC)
        )

        val elasticEthOrderActivity1 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${ethOrderActivity1.id}",
            type = ActivityTypeDto.BID,
            date = ethOrderActivity1.date,
            currency = AddressFactory.create().toString(),
            blockchain = BlockchainDto.ETHEREUM
        )
        val elasticEthOrderActivity2 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${ethOrderActivity2.id}",
            type = ActivityTypeDto.BID,
            date = ethOrderActivity2.date,
            currency = AddressFactory.create().toString(),
            blockchain = BlockchainDto.ETHEREUM
        )
        val elasticEthOrderActivity3 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${ethOrderActivity3.id}",
            type = ActivityTypeDto.BID,
            date = ethOrderActivity3.date,
            currency = AddressFactory.create().toString(),
            blockchain = BlockchainDto.ETHEREUM
        )
        val elasticEthItemActivity1 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${ethItemActivity1.id}",
            type = ActivityTypeDto.MINT,
            date = ethItemActivity1.date,
            blockchain = BlockchainDto.ETHEREUM
        )
        val elasticEthItemActivity2 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${ethItemActivity2.id}",
            type = ActivityTypeDto.MINT,
            date = ethItemActivity2.date,
            blockchain = BlockchainDto.ETHEREUM
        )
        val elasticEthItemActivity3 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${ethItemActivity3.id}",
            type = ActivityTypeDto.MINT,
            date = ethItemActivity3.date,
            blockchain = BlockchainDto.ETHEREUM
        )
        val elasticPolygonOrderActivity1 = randomEsActivity().copy(
            activityId = "${BlockchainDto.POLYGON}:${polygonOrderActivity1.id}",
            type = ActivityTypeDto.BID,
            date = polygonOrderActivity1.date,
            currency = AddressFactory.create().toString(),
            blockchain = BlockchainDto.POLYGON
        )
        val elasticPolygonOrderActivity2 = randomEsActivity().copy(
            activityId = "${BlockchainDto.POLYGON}:${polygonOrderActivity2.id}",
            type = ActivityTypeDto.BID,
            date = polygonOrderActivity2.date,
            currency = AddressFactory.create().toString(),
            blockchain = BlockchainDto.POLYGON
        )
        val elasticPolygonItemActivity1 = randomEsActivity().copy(
            activityId = "${BlockchainDto.POLYGON}:${polygonItemActivity1.id}",
            type = ActivityTypeDto.MINT,
            date = polygonItemActivity1.date,
            blockchain = BlockchainDto.POLYGON
        )
        val elasticPolygonItemActivity2 = randomEsActivity().copy(
            activityId = "${BlockchainDto.POLYGON}:${polygonItemActivity2.id}",
            type = ActivityTypeDto.MINT,
            date = polygonItemActivity2.date,
            blockchain = BlockchainDto.POLYGON
        )
        val elasticFlowActivity1 = randomEsActivity().copy(
            activityId = "${BlockchainDto.FLOW}:${flowActivity1.id}",
            type = ActivityTypeDto.BURN,
            date = flowActivity1.date,
            blockchain = BlockchainDto.FLOW,
        )
        val elasticFlowActivity2 = randomEsActivity().copy(
            activityId = "${BlockchainDto.FLOW}:${flowActivity2.id}",
            type = ActivityTypeDto.CANCEL_BID,
            date = flowActivity2.date,
            currency = AddressFactory.create().toString(),
            blockchain = BlockchainDto.FLOW,
        )
        val elasticSolanaActivity1 = randomEsActivity().copy(
            activityId = "${BlockchainDto.SOLANA}:${solanaActivity1.id}",
            type = ActivityTypeDto.MINT,
            date = solanaActivity1.date,
            blockchain = BlockchainDto.SOLANA,
        )
        val elasticTezosActivity1 = randomEsActivity().copy(
            activityId = "${BlockchainDto.TEZOS}:${tezosActivity1.id}",
            type = ActivityTypeDto.BURN,
            date = tezosActivity1.date.toInstant(),
            blockchain = BlockchainDto.TEZOS,
        )

        repository.bulk(
            listOf(
                elasticEthOrderActivity1, elasticEthOrderActivity2, elasticEthOrderActivity3,
                elasticEthItemActivity1, elasticEthItemActivity2, elasticEthItemActivity3,
                elasticPolygonOrderActivity1, elasticPolygonOrderActivity2,
                elasticPolygonItemActivity1, elasticPolygonItemActivity2,
                elasticFlowActivity1, elasticFlowActivity2,
                elasticSolanaActivity1,
                elasticTezosActivity1,
            )
        )

        // Since all activity types specified in request, all of existing clients should be requested
        coEvery {
            testEthereumActivityOrderApi.getOrderActivitiesById(
                ActivitiesByIdRequestDto(
                    listOf(
                        ethOrderActivity3.id,
                    )
                )
            )
        } returns OrderActivitiesDto(null, listOf(ethOrderActivity3)).toMono()

        coEvery {
            testEthereumActivityItemApi.getNftActivitiesById(
                ActivitiesByIdRequestDto(
                    listOf(
                        ethItemActivity3.id,
                    )
                )
            )
        } returns NftActivitiesDto(null, listOf(ethItemActivity3)).toMono()

        coEvery {
            testPolygonActivityItemApi.getNftActivitiesById(
                ActivitiesByIdRequestDto(
                    listOf(
                        polygonItemActivity1.id,
                    )
                )
            )
        } returns NftActivitiesDto(null, listOf(polygonItemActivity1)).toMono()

        coEvery {
            testFlowActivityApi.getNftOrderActivitiesById(
                NftActivitiesByIdRequestDto(
                    listOf(
                        flowActivity1.id
                    )
                )
            )
        } returns FlowActivitiesDto(null, listOf(flowActivity1)).toMono()

        coEvery {
            testSolanaActivityApi.searchActivitiesByIds(
                SolanaActivitiesByIdRequestDto(listOf(solanaActivity1.id))
            )
        } returns ActivitiesDto(null, listOf(solanaActivity1)).toMono()

        coEvery {
            tokenActivityClient.getActivitiesByIds(any())
        } returns listOf(randomTzktItemBurnActivity(tezosActivity1.id))

        val activities = activityControllerApi.getAllActivities(
            types,
            blockchains,
            null,
            null,
            null,
            size,
            com.rarible.protocol.union.dto.ActivitySortDto.EARLIEST_FIRST,
            null,
        ).awaitFirst()

        assertThat(activities.activities).hasSize(5)

        val firstActivity = activities.activities[0]
        val secondActivity = activities.activities[1]
        val thirdActivity = activities.activities[2]
        val fourthActivity = activities.activities[3]
        val fifthActivity = activities.activities[4]

        val message = "Received activities: ${activities.activities}, but expected: " +
            "${listOf(flowActivity1, polygonItemActivity1, ethOrderActivity3, tezosActivity1, solanaActivity1)}"

        assertThat(firstActivity.id.value).withFailMessage(message).isEqualTo(flowActivity1.id)
        assertThat(secondActivity.id.value).withFailMessage(message).isEqualTo(polygonItemActivity1.id)
        assertThat(thirdActivity.id.value).withFailMessage(message).isEqualTo(ethOrderActivity3.id)
        assertThat(fourthActivity.id.value).withFailMessage(message).isEqualTo(tezosActivity1.id)
        assertThat(fifthActivity.id.value).withFailMessage(message).isEqualTo(solanaActivity1.id)

        assertThat(activities.cursor).isNotNull

        val activities2 = activityControllerApi.getAllActivities(
            listOf(ActivityTypeDto.BID),
            blockchains,
            listOf("${BlockchainDto.ETHEREUM}:${elasticEthOrderActivity3.currency}"),
            null,
            null,
            size,
            com.rarible.protocol.union.dto.ActivitySortDto.EARLIEST_FIRST,
            null,
        ).awaitFirst()
        assertThat(activities2.activities.map { it.id.value }).containsExactlyInAnyOrder(ethOrderActivity3.id)
    }

    @Test
    fun `get activities by collection - ethereum`() = runBlocking<Unit> {
        // Here we expect only one query - to Order activities, since there is only order-related activity type
        val types = listOf(ActivityTypeDto.SELL, ActivityTypeDto.BID)
        val ethCollectionId = CollectionIdDto(BlockchainDto.ETHEREUM, randomEthAddress())
        val now = nowMillis().truncatedToSeconds()
        val orderActivity = randomEthOrderListActivity().copy(date = now)
        val bidActivity1 = randomEthOrderBidActivity().copy(date = now.minusSeconds(5))
        val bidActivity2 = randomEthOrderBidActivity().copy(date = now.minusSeconds(15))
        val elasticActivity = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${orderActivity.id}",
            type = ActivityTypeDto.SELL,
            blockchain = ethCollectionId.blockchain,
            collection = ethCollectionId.value,
            date = orderActivity.date,
        )
        val elasticBidActivity1 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${bidActivity1.id}",
            type = ActivityTypeDto.BID,
            blockchain = ethCollectionId.blockchain,
            collection = ethCollectionId.value,
            currency = AddressFactory.create().toString(),
            date = bidActivity1.date,
        )
        val elasticBidActivity2 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${bidActivity2.id}",
            type = ActivityTypeDto.BID,
            blockchain = ethCollectionId.blockchain,
            collection = ethCollectionId.value,
            currency = AddressFactory.create().toString(),
            date = bidActivity2.date,
        )
        repository.saveAll(listOf(elasticActivity, elasticBidActivity1, elasticBidActivity2))

        coEvery {
            testEthereumActivityOrderApi.getOrderActivitiesById(
                ActivitiesByIdRequestDto(ids = listOf(orderActivity.id, bidActivity1.id))
            )
        } returns OrderActivitiesDto(null, listOf(orderActivity, bidActivity1)).toMono()

        val activities = activityControllerApi.getActivitiesByCollection(
            types,
            listOf(ethCollectionId.fullId()),
            listOf("${BlockchainDto.ETHEREUM}:${elasticBidActivity1.currency}"),
            continuation,
            null,
            defaultSize,
            sort,
            null,
        ).awaitFirst()

        assertThat(activities.activities.map { it.id.value }).containsExactly(orderActivity.id, bidActivity1.id)
    }

    @Test
    fun `get activities by item - ethereum`() = runBlocking<Unit> {
        val types = ActivityTypeDto.values().toList()
        val now = nowMillis()
        val address = randomAddress()
        val tokenId = randomBigInt()
        val assetTypeDto = Erc721AssetTypeDto(address, tokenId)
        val ethItemId = ItemIdDto(BlockchainDto.ETHEREUM, address.toString(), tokenId)
        val orderActivity = randomEthOrderBidActivity().copy(
            date = now,
            make = AssetDto(assetTypeDto, randomBigInt(), randomBigDecimal())
        )
        val itemActivity = randomEthItemMintActivity().copy(date = now.minusSeconds(5))
        val auctionActivity = randomEthAuctionStartActivity().copy(date = now.minusSeconds(15))
        val bidActivity = randomEthOrderBidActivity().copy(
            date = now.minusSeconds(25),
            take = AssetDto(assetTypeDto, randomBigInt(), randomBigDecimal())
        )
        val bidActivity2 = randomEthOrderBidActivity().copy(
            date = now.minusSeconds(35),
            take = AssetDto(assetTypeDto, randomBigInt(), randomBigDecimal())
        )

        val elasticOrderActivity = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${orderActivity.id}",
            type = ActivityTypeDto.SELL,
            blockchain = BlockchainDto.ETHEREUM,
            date = orderActivity.date,
            item = "${assetTypeDto.contract}:${assetTypeDto.tokenId}"
        )
        val elasticItemActivity = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${itemActivity.id}",
            type = ActivityTypeDto.MINT,
            blockchain = BlockchainDto.ETHEREUM,
            date = itemActivity.date,
            item = "${assetTypeDto.contract}:${assetTypeDto.tokenId}"
        )
        val elasticAuctionActivity = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${auctionActivity.id}",
            type = ActivityTypeDto.AUCTION_STARTED,
            blockchain = BlockchainDto.ETHEREUM,
            date = auctionActivity.date,
            item = "${assetTypeDto.contract}:${assetTypeDto.tokenId}"
        )
        val elasticBidActivity1 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${bidActivity.id}",
            type = ActivityTypeDto.BID,
            blockchain = BlockchainDto.ETHEREUM,
            currency = AddressFactory.create().toString(),
            date = bidActivity.date,
            item = "${assetTypeDto.contract}:${assetTypeDto.tokenId}"
        )
        val elasticBidActivity2 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${bidActivity2.id}",
            type = ActivityTypeDto.BID,
            blockchain = BlockchainDto.ETHEREUM,
            date = bidActivity2.date,
            currency = AddressFactory.create().toString(),
            item = "${assetTypeDto.contract}:${assetTypeDto.tokenId}"
        )

        repository.saveAll(
            listOf(
                elasticOrderActivity,
                elasticItemActivity,
                elasticAuctionActivity,
                elasticBidActivity1,
                elasticBidActivity2
            )
        )

        coEvery {
            testEthereumActivityOrderApi.getOrderActivitiesById(
                ActivitiesByIdRequestDto(ids = listOf(orderActivity.id, bidActivity.id))
            )
        } returns OrderActivitiesDto(null, listOf(orderActivity, bidActivity)).toMono()

        coEvery {
            testEthereumActivityAuctionApi.getAuctionActivitiesById(
                ActivitiesByIdRequestDto(ids = listOf(auctionActivity.id))
            )
        } returns AuctionActivitiesDto(null, listOf(auctionActivity)).toMono()

        coEvery {
            testEthereumActivityItemApi.getNftActivitiesById(
                ActivitiesByIdRequestDto(ids = listOf(itemActivity.id))
            )
        } returns NftActivitiesDto(null, listOf(itemActivity)).toMono()

        val activities = activityControllerApi.getActivitiesByItem(
            types,
            ethItemId.fullId(),
            listOf("${BlockchainDto.ETHEREUM}:${elasticBidActivity1.currency}"),
            continuation,
            null,
            100,
            sort,
            null,
        ).awaitFirst()

        assertThat(activities.activities).hasSize(4)
        assertThat(activities.activities[0]).isInstanceOf(OrderBidActivityDto::class.java)
        assertThat(activities.activities[1]).isInstanceOf(MintActivityDto::class.java)
        assertThat(activities.activities[2]).isInstanceOf(AuctionStartActivityDto::class.java)
        assertThat(activities.activities[3].id.value).isEqualTo(bidActivity.id)
    }

    @Test
    fun `get activities by user`() = runBlocking<Unit> {
        // Only Item-specific activity types specified here, so only NFT-Indexer of Ethereum should be requested
        val types = listOf(
            UserActivityTypeDto.TRANSFER_FROM,
            UserActivityTypeDto.TRANSFER_TO,
            UserActivityTypeDto.MINT,
            UserActivityTypeDto.SELL,
            UserActivityTypeDto.MAKE_BID,
            UserActivityTypeDto.GET_BID,
        )
        // Flow and Ethereum user specified - request should be routed only for them, Polygon omitted
        val userEth = UnionAddressConverter.convert(BlockchainDto.ETHEREUM, randomEthAddress())
        val userEth2 = UnionAddressConverter.convert(BlockchainDto.ETHEREUM, randomEthAddress())
        val size = 3

        val ethItemActivity = randomEthItemMintActivity()
            .copy(date = nowMillis().minusSeconds(5), id = "ethitemactivity")
        val ethItemActivity2 = randomEthOrderActivityMatch()
            .copy(date = nowMillis().minusSeconds(6), id = "ethitemactivity2")
        val polygonItemActivity = randomEthItemMintActivity()
            .copy(date = nowMillis().minusSeconds(7), id = "polygonitemactivity")
        val sameTypeDifferentRole = randomEthOrderActivityMatch()
            .copy(date = Instant.now().minusSeconds(8), id = "sametypedifferentrole")
        val ethBidActivity1 = randomEthOrderBidActivity()
            .copy(date = nowMillis().minusSeconds(9), id = "ethbidactivity1")
        val ethBidActivity2 = randomEthOrderBidActivity()
            .copy(date = nowMillis().minusSeconds(10), id = "ethbidactivity2")

        val elasticEthItemActivity = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${ethItemActivity.id}",
            type = ActivityTypeDto.TRANSFER,
            blockchain = BlockchainDto.ETHEREUM,
            date = ethItemActivity.date,
            userFrom = userEth.value,
            userTo = userEth2.value
        )
        val elasticEthItemActivity2 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${ethItemActivity2.id}",
            type = ActivityTypeDto.SELL,
            blockchain = BlockchainDto.ETHEREUM,
            date = ethItemActivity2.date,
            userFrom = userEth.value,
        )
        val elasticPolygonItemActivity = randomEsActivity().copy(
            activityId = "${BlockchainDto.POLYGON}:${polygonItemActivity.id}",
            type = ActivityTypeDto.MINT,
            blockchain = BlockchainDto.POLYGON,
            date = polygonItemActivity.date,
            userFrom = userEth.value,
        )

        val elasticEthItemActivity3 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${sameTypeDifferentRole.id}",
            type = ActivityTypeDto.SELL,
            blockchain = BlockchainDto.ETHEREUM,
            date = sameTypeDifferentRole.date,
            userTo = userEth.value,
        )
        val elasticEthBidActivity1 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${ethBidActivity1.id}",
            type = ActivityTypeDto.BID,
            blockchain = BlockchainDto.ETHEREUM,
            date = ethBidActivity1.date,
            userFrom = userEth.value,
            currency = AddressFactory.create().toString(),
        )
        val elasticEthBidActivity2 = randomEsActivity().copy(
            activityId = "${BlockchainDto.ETHEREUM}:${ethBidActivity2.id}",
            type = ActivityTypeDto.BID,
            blockchain = BlockchainDto.ETHEREUM,
            date = ethBidActivity2.date,
            userFrom = userEth.value,
            currency = AddressFactory.create().toString(),
        )

        repository.saveAll(
            listOf(
                elasticEthItemActivity,
                elasticEthItemActivity2,
                elasticPolygonItemActivity,
                elasticEthItemActivity3,
                elasticEthBidActivity1,
                elasticEthBidActivity2
            )
        )

        coEvery {
            testEthereumActivityItemApi.getNftActivitiesById(
                ActivitiesByIdRequestDto(ids = listOf(ethItemActivity.id))
            )
        } returns NftActivitiesDto(null, listOf(ethItemActivity)).toMono()

        coEvery {
            testEthereumActivityOrderApi.getOrderActivitiesById(any())
        } answers {
            val argument = firstArg<ActivitiesByIdRequestDto>()
            val ids = argument.ids
            val response =
                listOf(ethItemActivity2, sameTypeDifferentRole, ethBidActivity1, ethBidActivity2).filter { act ->
                    ids.contains(act.id)
                }
            OrderActivitiesDto(null, response).toMono()
        }

        coEvery {
            testPolygonActivityItemApi.getNftActivitiesById(
                ActivitiesByIdRequestDto(ids = listOf(polygonItemActivity.id))
            )
        } returns NftActivitiesDto(null, listOf(polygonItemActivity)).toMono()

        val now = nowMillis()
        val oneWeekAgo = now.minus(7, ChronoUnit.DAYS)
        val activities = activityControllerApi.getActivitiesByUser(
            types,
            listOf(userEth.fullId()),
            null,
            listOf("${BlockchainDto.ETHEREUM}:${elasticEthBidActivity1.currency}"),
            oneWeekAgo,
            now,
            null,
            null,
            size,
            sort,
            null,
        ).awaitFirst()

        assertThat(activities.activities.map { it.id.value }).containsExactly(
            ethItemActivity.id,
            ethItemActivity2.id,
            polygonItemActivity.id
        )
        assertThat(activities.cursor).isNotNull

        val activities2 = activityControllerApi.getActivitiesByUser(
            types,
            listOf(userEth.fullId()),
            null,
            listOf("${BlockchainDto.ETHEREUM}:${elasticEthBidActivity1.currency}"),
            oneWeekAgo,
            now,
            null,
            null,
            100,
            sort,
            null,
        ).awaitFirst()

        assertThat(activities2.activities.map { it.id.value }).containsExactly(
            ethItemActivity.id,
            ethItemActivity2.id,
            polygonItemActivity.id,
            sameTypeDifferentRole.id,
            ethBidActivity1.id,
        )

        val fromActivities = activityControllerApi.getActivitiesByUser(
            listOf(UserActivityTypeDto.SELL),
            listOf(userEth.fullId()),
            null,
            null,
            oneWeekAgo,
            now,
            null,
            null,
            size,
            sort,
            null,
        ).awaitFirst()
        assertThat(fromActivities.activities).hasSize(1)

        val toActivities = activityControllerApi.getActivitiesByUser(
            listOf(UserActivityTypeDto.TRANSFER_TO),
            listOf(userEth2.fullId()),
            null,
            null,
            oneWeekAgo,
            now,
            null,
            null,
            size,
            sort,
            null,
        ).awaitFirst()
        assertThat(toActivities.activities).hasSize(1)
    }
}
