package com.rarible.protocol.union.enrichment.custom.collection.provider

import com.rarible.protocol.union.core.model.UnionCollection
import com.rarible.protocol.union.core.producer.UnionInternalCollectionEventProducer
import com.rarible.protocol.union.enrichment.meta.collection.CollectionMetaPipeline
import com.rarible.protocol.union.enrichment.meta.collection.CollectionMetaService
import com.rarible.protocol.union.enrichment.model.MetaDownloadPriority
import com.rarible.protocol.union.enrichment.repository.CollectionRepository
import com.rarible.protocol.union.enrichment.test.data.randomEnrichmentCollection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ArtificialCollectionServiceTest {

    @MockK
    lateinit var collectionRepository: CollectionRepository

    @MockK
    lateinit var producer: UnionInternalCollectionEventProducer

    @MockK
    lateinit var collectionMetaService: CollectionMetaService

    @InjectMockKs
    lateinit var artificialCollectionService: ArtificialCollectionService

    @Test
    fun `exists - ok`() = runBlocking<Unit> {
        val collection = randomEnrichmentCollection()
        coEvery { collectionRepository.get(collection.id) } returns collection

        val result = artificialCollectionService.exists(collection.id.toDto())
        assertThat(result).isTrue()

        assertThat(artificialCollectionService.exists(collection.id.toDto())).isTrue()

        coVerify(atMost = 1) { collectionRepository.get(collection.id) }
    }

    @Test
    fun `exists - false`() = runBlocking<Unit> {
        val collection = randomEnrichmentCollection()
        coEvery { collectionRepository.get(collection.id) } returns null

        val result = artificialCollectionService.exists(collection.id.toDto())
        assertThat(result).isFalse()
    }

    @Test
    fun `create - ok`() = runBlocking<Unit> {
        val collection = randomEnrichmentCollection().copy(version = 1)
        val subCollection = randomEnrichmentCollection()
        val subId = subCollection.id.toDto()

        val expected = collection.copy(
            collectionId = subCollection.collectionId,
            name = subCollection.name,
            structure = UnionCollection.Structure.PART,
            parent = collection.id,
            version = null
        )

        coEvery { collectionRepository.get(collection.id) } returns collection
        coEvery { collectionRepository.save(expected) } returns expected
        coEvery { producer.sendChangeEvent(subId) } returns Unit
        coEvery {
            collectionMetaService.schedule(
                subId,
                CollectionMetaPipeline.REFRESH,
                true,
                MetaDownloadPriority.HIGH
            )
        } returns Unit

        artificialCollectionService.createArtificialCollection(
            collection.id.toDto(),
            subCollection.id.toDto(),
            subCollection.name,
            UnionCollection.Structure.PART
        )

        coVerify(exactly = 1) { collectionRepository.save(expected) }
        coVerify(exactly = 1) { producer.sendChangeEvent(subCollection.id.toDto()) }
        coVerify(exactly = 1) {
            collectionMetaService.schedule(
                subId,
                CollectionMetaPipeline.REFRESH,
                true,
                MetaDownloadPriority.HIGH
            )
        }
        assertThat(artificialCollectionService.exists(subCollection.id.toDto())).isTrue()
    }

    @Test
    fun `create - failed, original collection not found`() = runBlocking<Unit> {
        val collection = randomEnrichmentCollection()
        val subCollection = randomEnrichmentCollection()
        coEvery { collectionRepository.get(collection.id) } returns null

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                artificialCollectionService.createArtificialCollection(
                    collection.id.toDto(),
                    subCollection.id.toDto(),
                    subCollection.name,
                    UnionCollection.Structure.PART
                )
            }
        }
    }
}
