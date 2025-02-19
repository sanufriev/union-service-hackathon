package com.rarible.protocol.union.integration.ethereum.service

import com.rarible.core.test.data.randomAddress
import com.rarible.core.test.data.randomBinary
import com.rarible.core.test.data.randomString
import com.rarible.protocol.dto.EthereumSignatureValidationFormDto
import com.rarible.protocol.order.api.client.OrderSignatureControllerApi
import com.rarible.protocol.union.integration.ethereum.converter.EthConverter
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.kotlin.core.publisher.toMono

class EthSignatureServiceTest {

    private val signatureControllerApi: OrderSignatureControllerApi = mockk()
    private val service = EthereumSignatureService(signatureControllerApi)

    @Test
    fun `ethereum validate`() = runBlocking {
        val expected = EthereumSignatureValidationFormDto(
            signer = randomAddress(),
            signature = randomBinary(),
            message = randomString()
        )

        coEvery { signatureControllerApi.validate(expected) } returns true.toMono()

        val result = service.validate(
            signer = EthConverter.convert(expected.signer),
            publicKey = null,
            signature = EthConverter.convert(expected.signature),
            message = expected.message
        )

        assertThat(result).isEqualTo(true)
    }
}
