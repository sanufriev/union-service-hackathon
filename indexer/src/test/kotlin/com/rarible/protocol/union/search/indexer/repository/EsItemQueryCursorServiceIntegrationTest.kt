package com.rarible.protocol.union.search.indexer.repository

import com.ninjasquad.springmockk.MockkBean
import com.rarible.core.common.nowMillis
import com.rarible.protocol.union.core.es.ElasticsearchTestBootstrapper
import com.rarible.protocol.union.core.model.CurrencyRate
import com.rarible.protocol.union.core.model.elastic.EsItemGenericFilter
import com.rarible.protocol.union.core.model.elastic.EsItemSort
import com.rarible.protocol.union.core.service.CurrencyService
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.enrichment.configuration.SearchConfiguration
import com.rarible.protocol.union.enrichment.repository.search.EsItemRepository
import com.rarible.protocol.union.enrichment.test.data.randomEsItem
import com.rarible.protocol.union.search.indexer.test.IntegrationTest
import io.mockk.coEvery
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.test.context.ContextConfiguration
import java.math.BigDecimal

@IntegrationTest
@EnableAutoConfiguration
@ContextConfiguration(classes = [SearchConfiguration::class])
class EsItemQueryCursorServiceIntegrationTest {

    @MockkBean
    private lateinit var currencyService: CurrencyService

    @Autowired
    protected lateinit var repository: EsItemRepository

    @Autowired
    private lateinit var elasticsearchTestBootstrapper: ElasticsearchTestBootstrapper

    @BeforeEach
    fun setUp() = runBlocking<Unit> {
        elasticsearchTestBootstrapper.bootstrap()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `should search with cursor by date`(descending: Boolean) = runBlocking<Unit> {
        // given
        coEvery {
            currencyService.getAllCurrencyRates()
        } returns listOf(
            CurrencyRate(BlockchainDto.ETHEREUM, "ETHEREUM:0x0000000000000000000000000000000000000000", BigDecimal(1500)),
            CurrencyRate(BlockchainDto.POLYGON, "POLYGON:0x0000000000000000000000000000000000000000", BigDecimal(150)),
            CurrencyRate(BlockchainDto.POLYGON, "ETHEREUM:0xfca59cd816ab1ead66534d82bc21e7515ce441cf", BigDecimal(15)),
            CurrencyRate(BlockchainDto.SOLANA, "SOLANA:So11111111111111111111111111111111111111112", BigDecimal(50)),
        )
        val sort = if (descending) EsItemSort.LATEST_FIRST else EsItemSort.EARLIEST_FIRST
        val now = nowMillis()
        val first = randomEsItem().copy(
            lastUpdatedAt = now.plusSeconds(50),
            itemId = "A",
        )
        val second = randomEsItem().copy(
            lastUpdatedAt = now.plusSeconds(50),
            itemId = "B",
        )
        val third = randomEsItem().copy(
            lastUpdatedAt = now.plusSeconds(25),
            itemId = "C",
        )
        val fourth = randomEsItem().copy(
            lastUpdatedAt = now.plusSeconds(10),
            itemId = "D",
        )
        val fifth = randomEsItem().copy(
            lastUpdatedAt = now.plusSeconds(10),
            itemId = "E",
        )
        repository.saveAll(listOf(first, second, third, fourth, fifth).shuffled())

        // when
        val filter1 = EsItemGenericFilter(cursor = null)
        val result1 = repository.search(filter1, sort, 3)
        val cursor1 = result1.continuation
        val filter2 = EsItemGenericFilter(cursor = cursor1)
        val result2 = repository.search(filter2, sort, 3)
        val cursor2 = result2.continuation
        val filter3 = EsItemGenericFilter(cursor = cursor2)
        val result3 = repository.search(filter3, sort, 3)
        val cursor3 = result3.continuation

        // then
        if (descending) {
            assertThat(result1.entities.map { it.itemId }).containsExactly(first.itemId, second.itemId, third.itemId)
            assertThat(result2.entities.map { it.itemId }).containsExactly(fourth.itemId, fifth.itemId)
            assertThat(result3.entities).isEmpty()
        } else {
            assertThat(result1.entities.map { it.itemId }).containsExactly(fourth.itemId, fifth.itemId, third.itemId)
            assertThat(result2.entities.map { it.itemId }).containsExactly(first.itemId, second.itemId)
            assertThat(result3.entities.map { it.itemId }).isEmpty()
        }
        assertThat(cursor3).isNullOrEmpty()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `should search with cursor by sell price`(descending: Boolean) = runBlocking<Unit> {
        // given
        val sort = if (descending) EsItemSort.HIGHEST_SELL_PRICE_FIRST else EsItemSort.LOWEST_SELL_PRICE_FIRST
        val first = randomEsItem().copy(
            blockchain = BlockchainDto.ETHEREUM,
            bestSellAmount = 50.0,
            bestSellCurrency = "ETHEREUM:0x0000000000000000000000000000000000000000",
            itemId = "A",
        )
        val second = randomEsItem().copy(
            blockchain = BlockchainDto.ETHEREUM,
            bestSellAmount = 50.0,
            bestSellCurrency = "ETHEREUM:0x0000000000000000000000000000000000000000",
            itemId = "B",
        )
        val third = randomEsItem().copy(
            blockchain = BlockchainDto.ETHEREUM,
            bestSellAmount = 25.0,
            bestSellCurrency = "ETHEREUM:0x0000000000000000000000000000000000000000",
            itemId = "C",
        )
        val fourth = randomEsItem().copy(
            blockchain = BlockchainDto.ETHEREUM,
            bestSellAmount = 10.0,
            bestSellCurrency = "ETHEREUM:0x0000000000000000000000000000000000000000",
            itemId = "D",
        )
        val fifth = randomEsItem().copy(
            blockchain = BlockchainDto.ETHEREUM,
            bestSellAmount = 10.0,
            bestSellCurrency = "ETHEREUM:0x0000000000000000000000000000000000000000",
            itemId = "E",
        )
        repository.saveAll(listOf(first, second, third, fourth, fifth).shuffled())
        coEvery {
            currencyService.getAllCurrencyRates()
        } returns listOf(
            CurrencyRate(BlockchainDto.ETHEREUM, "ETHEREUM:0x0000000000000000000000000000000000000000", BigDecimal(10)),
        )

        // when
        val filter1 = EsItemGenericFilter(cursor = null)
        val result1 = repository.search(filter1, sort, 3)
        val cursor1 = result1.continuation
        val filter2 = EsItemGenericFilter(cursor = cursor1)
        val result2 = repository.search(filter2, sort, 3)
        val cursor2 = result2.continuation
        val filter3 = EsItemGenericFilter(cursor = cursor2)
        val result3 = repository.search(filter3, sort, 3)
        val cursor3 = result3.continuation

        // then
        if (descending) {
            assertThat(result1.entities.map { it.itemId }).containsExactly(first.itemId, second.itemId, third.itemId)
            assertThat(result2.entities.map { it.itemId }).containsExactly(fourth.itemId, fifth.itemId)
            assertThat(result3.entities.map { it.itemId }).isEmpty()
        } else {
            assertThat(result1.entities.map { it.itemId }).containsExactly(fourth.itemId, fifth.itemId, third.itemId)
            assertThat(result2.entities.map { it.itemId }).containsExactly(first.itemId, second.itemId)
            assertThat(result3.entities.map { it.itemId }).isEmpty()
        }
        assertThat(cursor3).isNullOrEmpty()
    }
}
