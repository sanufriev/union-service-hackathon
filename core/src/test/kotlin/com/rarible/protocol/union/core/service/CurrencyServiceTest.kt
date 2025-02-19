package com.rarible.protocol.union.core.service

import com.rarible.core.common.nowMillis
import com.rarible.core.test.data.randomBigDecimal
import com.rarible.core.test.data.randomBigInt
import com.rarible.core.test.data.randomString
import com.rarible.protocol.currency.api.client.CurrencyControllerApi
import com.rarible.protocol.currency.dto.CurrenciesDto
import com.rarible.protocol.currency.dto.CurrencyRateDto
import com.rarible.protocol.union.core.client.CurrencyClient
import com.rarible.protocol.union.core.converter.ContractAddressConverter
import com.rarible.protocol.union.core.converter.CurrencyConverter
import com.rarible.protocol.union.core.exception.UnionCurrencyException
import com.rarible.protocol.union.core.model.UnionEthErc721AssetType
import com.rarible.protocol.union.core.test.nativeTestCurrencies
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.EthErc20AssetTypeDto
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.math.BigDecimal

class CurrencyServiceTest {

    private val currencyControllerApi: CurrencyControllerApi = mockk()
    private val currencyService = CurrencyService(CurrencyClient(currencyControllerApi))

    @BeforeEach
    fun beforeEach() {
        clearMocks(currencyControllerApi)
        currencyService.invalidateCache()
    }

    @Test
    fun `get rate`() = runBlocking<Unit> {
        val rate = randomBigDecimal()
        val blockchain = BlockchainDto.ETHEREUM
        val address = randomString()
        mockCurrency(blockchain, address, rate)

        val usdRate1 = currencyService.getRate(blockchain, address, nowMillis())
        val usdRate2 = currencyService.getRate(blockchain, address, nowMillis())

        assertThat(usdRate1.rate).isEqualTo(rate)
        assertThat(usdRate2.rate).isEqualTo(rate)

        // This request is not cached, so should be executed twice
        verifyCurrency(blockchain, address, 2)
    }

    @Test
    fun `get rate - unsupported`() = runBlocking<Unit> {
        val blockchain = BlockchainDto.ETHEREUM
        val address = randomString()
        mockCurrency(blockchain, address, null)

        assertThrows<UnionCurrencyException> {
            runBlocking { currencyService.getRate(blockchain, address, nowMillis()) }
        }
    }

    @Test
    fun `to usd - actual`() = runBlocking<Unit> {
        val rate = randomBigDecimal()
        val blockchain = BlockchainDto.ETHEREUM
        val address = randomString()
        mockCurrency(blockchain, address, rate, BigDecimal.ONE)

        val at = nowMillis().minusSeconds(60 * 29L)

        val assetType = EthErc20AssetTypeDto(ContractAddressConverter.convert(blockchain, address))
        val usdRate1 = currencyService.toUsd(blockchain, assetType, BigDecimal.ONE, at)
        val usdRate2 = currencyService.toUsd(blockchain, assetType, BigDecimal.ONE, at)

        // Both times should be rate * BigDecimal.ONE
        assertThat(usdRate1).isEqualTo(rate)
        assertThat(usdRate2).isEqualTo(rate)

        // Both times we requested rate in "actual" time rage - so only 1 call should be here
        verifyCurrency(blockchain, address, 1)
    }

    @Test
    fun `to usd - historical`() = runBlocking<Unit> {
        val rate = randomBigDecimal()
        val blockchain = BlockchainDto.ETHEREUM
        val address = randomString()
        mockCurrency(blockchain, address, rate, BigDecimal.ONE)

        val at = nowMillis().minusSeconds(60 * 29L)

        val assetType = EthErc20AssetTypeDto(ContractAddressConverter.convert(blockchain, address))
        val usdRate1 = currencyService.toUsd(blockchain, assetType, BigDecimal.ONE, at)
        val usdRate2 = currencyService.toUsd(blockchain, assetType, BigDecimal.ONE, at.minusSeconds(2 * 60L))

        // Second request should return rate == 1
        assertThat(usdRate1).isEqualTo(rate)
        assertThat(usdRate2).isEqualTo(BigDecimal.ONE)

        // Both times we requested rate in "actual" time rage - so only 1 call should be here
        verifyCurrency(blockchain, address, 2)
    }

    @Test
    fun `to usd - incorrect input`() = runBlocking<Unit> {
        val blockchain = BlockchainDto.ETHEREUM
        val address = ContractAddressConverter.convert(blockchain, randomString())
        val assetType = EthErc20AssetTypeDto(address)
        val nftAssetType = UnionEthErc721AssetType(address, randomBigInt())

        val nullValue = currencyService.toUsd(blockchain, assetType, null)
        assertThat(nullValue).isNull()

        val nftAddress = currencyService.toUsd(blockchain, nftAssetType, BigDecimal.ONE)
        assertThat(nftAddress).isNull()

        val zeroValue = currencyService.toUsd(blockchain, assetType, BigDecimal.ZERO)
        assertThat(zeroValue).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `to usd - unsupported currency`() = runBlocking<Unit> {
        val blockchain = BlockchainDto.ETHEREUM
        val address = randomString()
        mockCurrency(blockchain, address, null)

        val assetType = EthErc20AssetTypeDto(ContractAddressConverter.convert(blockchain, address))

        val rate = currencyService.toUsd(blockchain, assetType, BigDecimal.ONE, nowMillis())

        assertThat(rate).isNull()
    }

    @Test
    fun `get current rate`() = runBlocking<Unit> {
        val rate = randomBigDecimal()
        val blockchain = BlockchainDto.FLOW
        val address = randomString()
        mockCurrency(blockchain, address, rate)

        val usdRate1 = currencyService.getCurrentRate(blockchain, address)!!
        val usdRate2 = currencyService.getCurrentRate(blockchain, address)!!

        assertThat(usdRate1.rate).isEqualTo(rate)
        assertThat(usdRate2.rate).isEqualTo(rate)

        // This request is cached, so should be executed only once
        verifyCurrency(blockchain, address, 1)
    }

    @Test
    fun `get current rate - failed`() = runBlocking<Unit> {
        coEvery {
            currencyControllerApi.getCurrencyRate(any(), any(), any())
        } throws RuntimeException()

        val currency = currencyService.getCurrentRate(BlockchainDto.POLYGON, randomString())
        assertThat(currency).isNull()
    }

    @Test
    fun `refresh cache`() = runBlocking<Unit> {
        val allCurrencies = mockk<CurrenciesDto>()
        val ethRate1 = randomBigDecimal()
        val ethRate2 = randomBigDecimal()
        val ethBlockchain = BlockchainDto.ETHEREUM
        val ethAddress = randomString()

        val flowRate1 = randomBigDecimal()
        val flowBlockchain = BlockchainDto.FLOW
        val flowAddress = randomString()

        mockCurrency(ethBlockchain, ethAddress, ethRate1, ethRate2)
        mockCurrency(flowBlockchain, flowAddress, flowRate1, null)
        coEvery {
            currencyControllerApi.allCurrencies
        } returns allCurrencies.toMono()
        every { allCurrencies.currencies } returns nativeTestCurrencies()

        // Filling cache with initial values
        val currentEthRate = currencyService.getCurrentRate(ethBlockchain, ethAddress)!!
        val currentFlowRate = currencyService.getCurrentRate(flowBlockchain, flowAddress)!!

        assertThat(currentEthRate.rate).isEqualTo(ethRate1)
        assertThat(currentFlowRate.rate).isEqualTo(flowRate1)

        // Refreshing cache - there should be new values
        currencyService.refreshCache()

        val refreshedEthRate = currencyService.getCurrentRate(ethBlockchain, ethAddress)!!
        val refreshedFlowRate = currencyService.getCurrentRate(flowBlockchain, flowAddress)!!

        // Eth rate should be updated, flow rate should stay the same since refresh failed for it
        assertThat(refreshedEthRate.rate).isEqualTo(ethRate2)
        assertThat(refreshedFlowRate.rate).isEqualTo(flowRate1)

        verifyCurrency(ethBlockchain, ethAddress, 2)
        verifyCurrency(flowBlockchain, flowAddress, 2)
    }

    @Test
    fun `should get all currency rates`() = runBlocking<Unit> {
        // given
        coEvery {
            currencyControllerApi.allCurrencies
        } returns CurrenciesDto(
            currencies = nativeTestCurrencies()
        ).toMono()
        mockCurrency(BlockchainDto.ETHEREUM, "0x0000000000000000000000000000000000000000", BigDecimal(4))
        mockCurrency(BlockchainDto.FLOW, "A.1654653399040a61.FlowToken", BigDecimal(6))

        // when
        val currencyRates = currencyService.getAllCurrencyRates()

        // then
        assertThat(currencyRates).hasSize(2)
        assertThat(currencyRates.first().blockchain).isEqualTo(BlockchainDto.ETHEREUM)
        assertThat(currencyRates.first().currencyId).isEqualTo("ETHEREUM:0x0000000000000000000000000000000000000000")
        assertThat(currencyRates.first().rate).isEqualTo(BigDecimal(4))
        assertThat(currencyRates.last().blockchain).isEqualTo(BlockchainDto.FLOW)
        assertThat(currencyRates.last().currencyId).isEqualTo("FLOW:A.1654653399040a61.FlowToken")
        assertThat(currencyRates.last().rate).isEqualTo(BigDecimal(6))
    }

    @Test
    fun `should cache consequent calls of getAllCurrencyRates`() = runBlocking<Unit> {
        // given
        val allCurrencies = mockk<CurrenciesDto>()
        coEvery {
            currencyControllerApi.allCurrencies
        } returns allCurrencies.toMono()
        every { allCurrencies.currencies } returns nativeTestCurrencies()
        mockCurrency(BlockchainDto.ETHEREUM, "0x0000000000000000000000000000000000000000", BigDecimal(2))
        mockCurrency(BlockchainDto.FLOW, "A.1654653399040a61.FlowToken", BigDecimal(3))
        mockCurrency(BlockchainDto.POLYGON, "0x0000000000000000000000000000000000000000", BigDecimal(4))
        mockCurrency(BlockchainDto.TEZOS, "tz1Ke2h7sDdakHJQh8WX4Z372du1KChsksyU", BigDecimal(5))
        mockCurrency(BlockchainDto.SOLANA, "So11111111111111111111111111111111111111112", BigDecimal(6))
        mockCurrency(BlockchainDto.IMMUTABLEX, "0x0000000000000000000000000000000000000000", BigDecimal(7))
        mockCurrency(BlockchainDto.MANTLE, "0x0000000000000000000000000000000000000000", BigDecimal(8))

        // when
        val actual1 = currencyService.getAllCurrencyRates()
        val actual2 = currencyService.getAllCurrencyRates()

        // then
        assertThat(actual1).isEqualTo(actual2)
        coVerify(exactly = 1) {
            currencyControllerApi.allCurrencies
            currencyControllerApi.getCurrencyRate(com.rarible.protocol.currency.dto.BlockchainDto.ETHEREUM, "0x0000000000000000000000000000000000000000", any())
            currencyControllerApi.getCurrencyRate(com.rarible.protocol.currency.dto.BlockchainDto.FLOW, "A.1654653399040a61.FlowToken", any())
            currencyControllerApi.getCurrencyRate(com.rarible.protocol.currency.dto.BlockchainDto.POLYGON, "0x0000000000000000000000000000000000000000", any())
            currencyControllerApi.getCurrencyRate(com.rarible.protocol.currency.dto.BlockchainDto.TEZOS, "tz1Ke2h7sDdakHJQh8WX4Z372du1KChsksyU", any())
            currencyControllerApi.getCurrencyRate(com.rarible.protocol.currency.dto.BlockchainDto.SOLANA, "So11111111111111111111111111111111111111112", any())
            currencyControllerApi.getCurrencyRate(com.rarible.protocol.currency.dto.BlockchainDto.IMMUTABLEX, "0x0000000000000000000000000000000000000000", any())
            currencyControllerApi.getCurrencyRate(com.rarible.protocol.currency.dto.BlockchainDto.MANTLE, "0x0000000000000000000000000000000000000000", any())
        }
        confirmVerified(currencyControllerApi)
    }

    @Test
    fun `should be able to retrieve native currencies for all blockchains`() = runBlocking<Unit> {
        // given
        val allCurrencies = mockk<CurrenciesDto>()
        coEvery {
            currencyControllerApi.allCurrencies
        } returns allCurrencies.toMono()
        every { allCurrencies.currencies } returns nativeTestCurrencies()

        // when
        val results = BlockchainDto.values().map { currencyService.getNativeCurrency(it) }

        // then
        assertThat(results).isNotEmpty
        assertThat(results).doesNotHaveDuplicates()
    }

    private fun mockCurrency(blockchain: BlockchainDto, address: String, vararg rates: BigDecimal?) {
        val mock = coEvery {
            currencyControllerApi.getCurrencyRate(
                eq(CurrencyConverter.convert(blockchain)),
                eq(address),
                any()
            )
        }
        rates.forEach {
            if (it != null) {
                mock.returns(
                    CurrencyRateDto(
                        fromCurrencyId = address,
                        toCurrencyId = "usd",
                        rate = it,
                        date = nowMillis()
                    ).toMono()
                )
            } else {
                mock.returns(Mono.empty())
            }
        }
    }

    private fun verifyCurrency(blockchain: BlockchainDto, address: String, count: Int) {
        coVerify(exactly = count) {
            currencyControllerApi.getCurrencyRate(
                eq(CurrencyConverter.convert(blockchain)),
                eq(address),
                any()
            )
        }
    }
}
