package com.rarible.protocol.union.test.mock

import com.rarible.core.common.nowMillis
import com.rarible.protocol.currency.api.client.CurrencyControllerApi
import com.rarible.protocol.currency.dto.CurrencyRateDto
import com.rarible.protocol.union.core.client.CurrencyClient
import com.rarible.protocol.union.core.service.CurrencyService
import com.rarible.protocol.union.core.test.nativeTestCurrencies
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import reactor.kotlin.core.publisher.toMono
import java.math.BigDecimal
import kotlin.math.pow

object CurrencyMock {

    val currencyControllerApiMock: CurrencyControllerApi = mockk()
    val currencyClientMock = CurrencyClient(currencyControllerApiMock)
    val currencyServiceMock = CurrencyService(currencyClientMock)

    init {
        clearCurrencyMock()
    }

    fun mockCurrencies(): Map<String, Double> {
        val ratesPerCurrency = mutableMapOf<String, Double>()

        nativeTestCurrencies().forEachIndexed { index, currency ->
            val rate = 1.0 + 2.0.pow(index.toDouble())
            ratesPerCurrency["${currency.blockchain}:${currency.address}"] = rate
            every { currencyControllerApiMock.getCurrencyRate(currency.blockchain, currency.address, any()) } returns
                CurrencyRateDto(
                    fromCurrencyId = currency.currencyId,
                    toCurrencyId = "",
                    rate = rate.toBigDecimal(),
                    date = nowMillis(),
                ).toMono()
        }

        return ratesPerCurrency
    }

    fun clearCurrencyMock() {
        clearMocks(currencyControllerApiMock)
        every {
            currencyControllerApiMock.getCurrencyRate(any(), any(), any())
        } answers {
            CurrencyRateDto(
                fromCurrencyId = it.invocation.args[1] as String,
                toCurrencyId = "usd",
                rate = BigDecimal.ONE,
                date = nowMillis()
            ).toMono()
        }
    }
}
