package com.rarible.protocol.union.worker

import com.rarible.core.application.ApplicationEnvironmentInfo
import com.rarible.protocol.currency.api.client.CurrencyControllerApi
import com.rarible.protocol.nft.api.client.NftItemControllerApi
import com.rarible.protocol.union.api.client.FixedUnionApiServiceUriProvider
import com.rarible.protocol.union.api.client.UnionApiClientFactory
import com.rarible.protocol.union.core.elasticsearch.IndexService
import com.rarible.protocol.union.core.elasticsearch.NoopReindexSchedulingService
import com.rarible.protocol.union.core.elasticsearch.bootstrap.ElasticsearchBootstrapper
import com.rarible.protocol.union.test.mock.CurrencyMock
import io.mockk.mockk
import io.mockk.spyk
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.net.URI

@Configuration
class TestWorkerConfiguration {
    @Bean
    fun applicationEnvironmentInfo(): ApplicationEnvironmentInfo {
        return ApplicationEnvironmentInfo("test", "test.com")
    }

    @Bean
    @Qualifier("testLocalhostUri")
    fun testLocalhostUri(@LocalServerPort port: Int): URI {
        return URI("http://localhost:$port")
    }

    @Bean
    fun bootstrapper() = mockk<ElasticsearchBootstrapper>()

    @Bean
    @Primary
    fun testUnionApiClientFactory(@Qualifier("testLocalhostUri") uri: URI): UnionApiClientFactory {
        return UnionApiClientFactory(FixedUnionApiServiceUriProvider(uri))
    }

    @Bean
    fun reindexSchedulingService(indexService: IndexService) =
        spyk(NoopReindexSchedulingService(indexService))

    // --------------------- CURRENCY ---------------------//

    @Bean
    @Primary
    fun testCurrencyApi(): CurrencyControllerApi = CurrencyMock.currencyControllerApiMock

    // --------------------- ETHEREUM ---------------------//

    @Bean
    @Primary
    @Qualifier("ethereum.item.api")
    fun testEthereumItemApi(): NftItemControllerApi = mockk()

    @Bean
    @Primary
    @Qualifier("ethereum.order.api")
    fun testEthereumOrderApi(): com.rarible.protocol.order.api.client.OrderControllerApi = mockk()
}
