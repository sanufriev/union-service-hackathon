package com.rarible.protocol.union.search.indexer.repository

import com.rarible.core.common.nowMillis
import com.rarible.protocol.union.core.es.ElasticsearchTestBootstrapper
import com.rarible.protocol.union.core.model.elastic.EsItemSort
import com.rarible.protocol.union.enrichment.configuration.SearchConfiguration
import com.rarible.protocol.union.enrichment.repository.search.EsItemRepository
import com.rarible.protocol.union.enrichment.repository.search.internal.EsItemQuerySortService
import com.rarible.protocol.union.enrichment.test.data.randomEsItem
import com.rarible.protocol.union.search.indexer.test.IntegrationTest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder
import org.springframework.test.context.ContextConfiguration

@IntegrationTest
@EnableAutoConfiguration
@ContextConfiguration(classes = [SearchConfiguration::class])
class EsItemQuerySortServiceIntegrationTest {

    @Autowired
    protected lateinit var repository: EsItemRepository

    // Sort by price is tested in EsItemQueryScoreServiceIntegrationTest
    @Autowired
    private lateinit var service: EsItemQuerySortService

    @Autowired
    private lateinit var elasticsearchTestBootstrapper: ElasticsearchTestBootstrapper

    @BeforeEach
    fun setUp() = runBlocking<Unit> {
        elasticsearchTestBootstrapper.bootstrap()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `should sort by lastUpdatedAt and id`(descending: Boolean) = runBlocking<Unit> {
        // given
        elasticsearchTestBootstrapper.bootstrap()
        val builder = NativeSearchQueryBuilder()

        val sort = if (descending) EsItemSort.LATEST_FIRST else EsItemSort.EARLIEST_FIRST
        val now = nowMillis()
        val first = randomEsItem().copy(lastUpdatedAt = now.plusSeconds(100), itemId = "A")
        val second = randomEsItem().copy(lastUpdatedAt = now.plusSeconds(100), itemId = "B")
        val third = randomEsItem().copy(lastUpdatedAt = now.plusSeconds(50), itemId = "C")
        val fourth = randomEsItem().copy(lastUpdatedAt = now.plusSeconds(25), itemId = "D")
        val fifth = randomEsItem().copy(lastUpdatedAt = now.plusSeconds(25), itemId = "E")
        repository.saveAll(listOf(first, second, third, fourth, fifth).shuffled())
        val expected = if (descending) {
            listOf(first, second, third, fourth, fifth).map { it.itemId }
        } else {
            listOf(fourth, fifth, third, first, second).map { it.itemId }
        }

        // when
        service.applySort(builder, sort)
        val actual = repository.search(builder.build())
            .map { it.content.itemId }

        // then
        assertThat(actual).isEqualTo(expected)
    }
}
