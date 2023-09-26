package com.rarible.protocol.union.integration.aptos

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rarible.protocol.union.integration.aptos.client.AptosWebClientFactory
import com.rarible.protocol.union.integration.aptos.service.PollerService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class PollerTest {

    val mapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @Test
    fun simple() = runBlocking {
        val webClient = AptosWebClientFactory.createClient("https://indexer.mainnet.aptoslabs.com").build()
        val service = PollerService(webClient, mapper)

        val reponse = service.poll()
        println(reponse)
    }

}
