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
import com.rarible.protocol.union.integration.aptos.repository.AptosRepository
import com.rarible.protocol.union.integration.aptos.service.PollerService
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.web.reactive.function.client.WebClient

@AptosConfiguration
@Import(value = [CoreConfiguration::class])
@EnableConfigurationProperties(value = [AptosIntegrationProperties::class])
class AptosApiConfiguration(
    private val template: ReactiveMongoTemplate
) {

    @Bean
    fun aptosRepository(): AptosRepository {
        return AptosRepository(template)
    }

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
        poller: PollerService,
        repository: AptosRepository,
    ): CommandLineRunner {
        val job = AptosPollerJob(
            meterRegistry,
            poller,
            repository
        )
        return CommandLineRunner {
            job.start()
        }
    }





    // -------------------- Services --------------------//

}
