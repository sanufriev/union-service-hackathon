package com.rarible.protocol.union.integration.aptos

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rarible.protocol.union.integration.aptos.client.AptosWebClientFactory
import com.rarible.protocol.union.integration.aptos.service.PollerService
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigInteger

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

    @Test
    fun convertId() {
        val origin = "0x482b9e1a7a7fa56001b4ddff90f8e77c149e6e15773165ce5e4286f58d115ec8"
        val i = Word.apply(origin).toBigInteger()
        assertThat("0x${i.toString(16)}").isEqualTo(origin)
        val p = BigInteger("12521145027864177621611235868423364946310408629259234741814386685737831289570").toString(16)
        println(p)
    }

}
