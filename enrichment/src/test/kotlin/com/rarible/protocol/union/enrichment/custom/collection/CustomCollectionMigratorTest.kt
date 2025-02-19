package com.rarible.protocol.union.enrichment.custom.collection

import com.rarible.protocol.union.core.producer.UnionInternalItemEventProducer
import com.rarible.protocol.union.enrichment.custom.collection.updater.CustomCollectionUpdater
import com.rarible.protocol.union.enrichment.test.data.randomUnionItem
import com.rarible.protocol.union.integration.ethereum.data.randomEthItemId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class CustomCollectionMigratorTest {

    private val collectionUpdater: CustomCollectionUpdater = mockk {
        coEvery { update(any()) } returns Unit
    }

    private val eventProducer: UnionInternalItemEventProducer = mockk() {
        coEvery { sendChangeEvent(any()) } returns Unit
    }

    private val migrator = CustomCollectionMigrator(
        eventProducer,
        listOf(collectionUpdater)
    )

    @Test
    fun `migrate - ok`() = runBlocking<Unit> {
        val item1 = randomUnionItem(randomEthItemId())
        val item2 = randomUnionItem(randomEthItemId())
        val item3 = randomUnionItem(randomEthItemId())

        migrator.migrate(listOf(item1, item2, item3))

        coVerify { collectionUpdater.update(item1) }
        coVerify { collectionUpdater.update(item2) }
        coVerify { collectionUpdater.update(item3) }

        coVerify(exactly = 1) { eventProducer.sendChangeEvent(item1.id) }
        coVerify(exactly = 1) { eventProducer.sendChangeEvent(item2.id) }
        coVerify(exactly = 1) { eventProducer.sendChangeEvent(item3.id) }
    }
}
