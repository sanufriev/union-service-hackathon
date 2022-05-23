package com.rarible.protocol.union.search.indexer.repository

import com.rarible.core.test.data.randomAddress
import com.rarible.core.test.data.randomLong
import com.rarible.core.test.data.randomString
import com.rarible.protocol.union.core.es.ElasticsearchTestBootstrapper
import com.rarible.protocol.union.core.model.ElasticItemFilter
import com.rarible.protocol.union.core.model.EsItem
import com.rarible.protocol.union.core.model.EsItemSort
import com.rarible.protocol.union.core.model.EsTrait
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.enrichment.configuration.SearchConfiguration
import com.rarible.protocol.union.enrichment.repository.search.EsItemRepository
import com.rarible.protocol.union.search.indexer.test.IntegrationTest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.elasticsearch.index.query.BoolQueryBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery
import org.springframework.test.context.ContextConfiguration
import java.time.Instant
import java.time.temporal.ChronoUnit

@IntegrationTest
@EnableAutoConfiguration
@ContextConfiguration(classes = [SearchConfiguration::class])
internal class EsItemRepositoryFt {

    @Autowired
    protected lateinit var repository: EsItemRepository

    @Autowired
    private lateinit var elasticsearchTestBootstrapper: ElasticsearchTestBootstrapper

    @BeforeEach
    fun setUp() = runBlocking<Unit> {
        elasticsearchTestBootstrapper.bootstrap()
    }

    @Test
    fun `should save and read`(): Unit = runBlocking {

        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val esItem = EsItem(
            itemId = "0x03",
            blockchain = BlockchainDto.ETHEREUM,
            collection = "0x02",
            name = "TestItem",
            description = "description",
            traits = listOf(EsTrait("long", "10"), EsTrait("test", "eye")),
            creators = listOf("0x01"),
            mintedAt = now,
            lastUpdatedAt = now
        )

        val id = repository.save(esItem).itemId
        val found = repository.findById(id)
        assertThat(found).isEqualTo(esItem)
    }

    @Test
    fun `should be able to search up to 1000 items`(): Unit = runBlocking {
        // given
        val items = List(1000) { randomEsItem() }
        repository.saveAll(items)

        // when
        val query = NativeSearchQuery(BoolQueryBuilder())
        query.maxResults = 1000
        val actual = repository.search(query)

        // then
        assertThat(actual.content).hasSize(1000)
    }

    @Test
    fun `should search by collection`(): Unit = runBlocking {

        val esItem = randomEsItem()
        repository.save(esItem)
        val result =
            repository.search(ElasticItemFilter(collections = setOf(esItem.collection!!)), EsItemSort.DEFAULT, 10)

        assertThat(result.content.size).isEqualTo(1)

        assertThat(result.content[0].itemId).isEqualTo(esItem.itemId)
    }

    @Test
    fun `should search by owners`(): Unit = runBlocking {

        val esItem = randomEsItem()
        repository.save(esItem)
        val result = repository.search(
            ElasticItemFilter(
//                    owners = setOf(esItem.owner!!)
            ), EsItemSort.DEFAULT, 10
        )

        assertThat(result.content.size).isEqualTo(1)

        assertThat(result.content[0].itemId).isEqualTo(esItem.itemId)
    }

    @Test
    fun `should search by creators`(): Unit = runBlocking {

        val esItem = randomEsItem()
        repository.save(esItem)
        val result =
            repository.search(ElasticItemFilter(creators = setOf(esItem.creators.first())), EsItemSort.DEFAULT, 10)

        assertThat(result.content.size).isEqualTo(1)

        assertThat(result.content[0].itemId).isEqualTo(esItem.itemId)
    }

    @Test
    fun `should search by blockchains`(): Unit = runBlocking {

        val esItem = randomEsItem()
        repository.save(esItem)
        val result = repository.search(
            ElasticItemFilter(blockchains = setOf(esItem.blockchain.toString())), EsItemSort.DEFAULT, 10
        )

        assertThat(result.content.size).isEqualTo(1)

        assertThat(result.content[0].itemId).isEqualTo(esItem.itemId)
    }

    @Test
    fun `should search by itemIds`(): Unit = runBlocking {

        val esItem = randomEsItem()
        repository.save(esItem)
        val result = repository.search(
            ElasticItemFilter(itemIds = setOf(esItem.itemId)), EsItemSort.DEFAULT, 10
        )

        assertThat(result.content.size).isEqualTo(1)

        assertThat(result.content[0].itemId).isEqualTo(esItem.itemId)
    }

    @Test
    fun `should search by mintedAt range`(): Unit = runBlocking {

        val now = Instant.now()

        (1..100).forEach {
            val esItem = randomEsItem().copy(mintedAt = now.plusSeconds(it.toLong()))
            repository.save(esItem)
        }

        val result = repository.search(
            ElasticItemFilter(mintedFrom = now), EsItemSort.DEFAULT, 200
        )

        assertThat(result.content.size).isEqualTo(100)

        val result1 = repository.search(
            ElasticItemFilter(mintedFrom = now, mintedTo = now.plusSeconds(50)), EsItemSort.DEFAULT, 200
        )

        assertThat(result1.content.size).isEqualTo(50)

        val result2 = repository.search(
            ElasticItemFilter(mintedFrom = now.plusSeconds(21), mintedTo = now.plusSeconds(50)), EsItemSort.DEFAULT, 200
        )

        assertThat(result2.content.size).isEqualTo(30)
    }

    @Test
    fun `should search by name`(): Unit = runBlocking {

        val esItems = (1..50).map { randomEsItem() }
        repository.saveAll(esItems)

        val result = repository.search(
            ElasticItemFilter(text = esItems[13].name), EsItemSort.DEFAULT, 10
        )

        assertThat(result.content.size).isEqualTo(1)
        assertThat(result.content[0].itemId).isEqualTo(esItems[13].itemId)
    }

    @TestFactory
    fun `should search by prefix name key`(): List<DynamicTest> = runBlocking {
        val esItems = (1..50).map { randomEsItem() }
        repository.saveAll(esItems)

        val esItem = randomEsItem().copy(name = "Axie #120738 key3")
        repository.save(esItem)

        listOf(
            "key3", "KEY3", "key", "axie"
        ).map {

            dynamicTest("should search by prefix name key $it") {
                runBlocking {

                    val result = repository.search(
                        ElasticItemFilter(text = it), EsItemSort.DEFAULT, 10
                    )

                    assertThat(result.content.size).isEqualTo(1)
                    assertThat(result.content[0].itemId).isEqualTo(esItem.itemId)
                }
            }
        }
    }

    @Test
    fun `should search by trait string`(): Unit = runBlocking {

        val esItems = (1..50).map { randomEsItem() }
        repository.saveAll(esItems)

        val result = repository.search(
            ElasticItemFilter(text = esItems[13].traits[1].value), EsItemSort.DEFAULT, 10
        )

        assertThat(result.content.size).isEqualTo(1)
        assertThat(result.content[0].itemId).isEqualTo(esItems[13].itemId)
    }

    @Test
    fun `should search by trait long`(): Unit = runBlocking {

        val esItems = (1..50).map { randomEsItem() }
        repository.saveAll(esItems)

        val result = repository.search(
            ElasticItemFilter(text = esItems[13].traits[0].value), EsItemSort.DEFAULT, 10
        )

        assertThat(result.content.size).isEqualTo(1)
        assertThat(result.content[0].itemId).isEqualTo(esItems[13].itemId)
    }

    @Test
    fun `should search by trait date`(): Unit = runBlocking {

        val esItem = randomEsItem()
        repository.save(esItem)
        val result = repository.search(
            ElasticItemFilter(text = esItem.traits[2].value), EsItemSort.DEFAULT, 10
        )

        assertThat(result.content.size).isEqualTo(1)
        assertThat(result.content[0].itemId).isEqualTo(esItem.itemId)
    }

    fun randomEsItem() = EsItem(
        itemId = randomAddress().toString(),
        blockchain = BlockchainDto.values().random(),
        collection = randomAddress().toString(),
        name = randomString(),
        description = randomString(),
        traits = listOf(
            EsTrait("long", randomLong().toString()),
            EsTrait("testString", randomString()),
            EsTrait("testDate", "2022-05-" + (1..30).random())
        ),
        creators = listOf(randomAddress().toString()),
        mintedAt = Instant.now(),
        lastUpdatedAt = Instant.now()
    )
}
