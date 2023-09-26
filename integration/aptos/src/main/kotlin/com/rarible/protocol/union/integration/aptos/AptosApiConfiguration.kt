package com.rarible.protocol.union.integration.aptos

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rarible.protocol.union.core.CoreConfiguration
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.integration.aptos.client.AptosWebClientFactory
import com.rarible.protocol.union.integration.aptos.job.AptosPollerJob
import com.rarible.protocol.union.integration.aptos.service.PollerService
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClient

@AptosConfiguration
@Import(value = [CoreConfiguration::class])
@EnableConfigurationProperties(value = [AptosIntegrationProperties::class])
class AptosApiConfiguration {

    @Bean
    fun aptosBlockchain(): BlockchainDto {
        return BlockchainDto.APTOS
    }

    @Bean
    fun pollerService(): PollerService {
        return PollerService(
            client = AptosWebClientFactory.createClient("https://indexer.mainnet.aptoslabs.com").build(),
            objectMapper = ObjectMapper().apply {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
            }
        )
    }

    @Bean
    fun aptosPollerStarter(
        meterRegistry: MeterRegistry,
        poller: PollerService
    ): CommandLineRunner {
        val job = AptosPollerJob(
            meterRegistry,
            poller
        )
        return CommandLineRunner {
            job.start()
        }
    }





    // -------------------- Services --------------------//

}
