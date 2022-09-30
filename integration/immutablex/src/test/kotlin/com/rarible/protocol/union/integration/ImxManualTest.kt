package com.rarible.protocol.union.integration

import com.rarible.protocol.union.integration.immutablex.client.ImxActivityClient
import com.rarible.protocol.union.integration.immutablex.client.ImxAssetClient
import com.rarible.protocol.union.integration.immutablex.client.ImxCollectionClient
import com.rarible.protocol.union.integration.immutablex.client.ImxOrderClient
import com.rarible.protocol.union.integration.immutablex.client.ImxWebClientFactory
import com.rarible.protocol.union.integration.immutablex.converter.ImxActivityConverter
import com.rarible.protocol.union.integration.immutablex.repository.ImxCollectionCreatorRepository
import com.rarible.protocol.union.integration.immutablex.repository.ImxCollectionMetaSchemaRepository
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal

abstract class ImxManualTest {

    protected val webClient = ImxWebClientFactory.createClient(
        "https://api.x.immutable.com/v1",
        null
    )

    private val chunkSize = 16

    protected val assetClient = ImxAssetClient(webClient, chunkSize)
    protected val activityClient = ImxActivityClient(webClient, chunkSize)
    protected val collectionClient = ImxCollectionClient(webClient, chunkSize)
    protected val orderClient = ImxOrderClient(webClient, chunkSize)

    protected val imxActivityConverter = ImxActivityConverter(
        mockk() {
            coEvery {
                toUsd(any(), any(), any(), any())
            } returns BigDecimal.ONE
        }
    )

    protected val collectionCreatorRepository: ImxCollectionCreatorRepository = mockk {
        coEvery { getAll(any()) } returns emptyList()
        coEvery { saveAll(any()) } returns Unit
    }

    protected val collectionMetaSchemaRepository: ImxCollectionMetaSchemaRepository = mockk {
        coEvery { getAll(any()) } returns emptyList()
        coEvery { save(any()) } returns Unit
        coEvery { getById(any()) } returns null
    }

}