package com.rarible.protocol.union.enrichment.service

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.rarible.protocol.union.api.ApiClient
import com.rarible.protocol.union.core.model.MetaSource
import com.rarible.protocol.union.core.model.UnionMeta
import com.rarible.protocol.union.core.service.ItemService
import com.rarible.protocol.union.core.service.router.BlockchainRouter
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.CollectionIdDto
import com.rarible.protocol.union.dto.ItemIdDto
import com.rarible.protocol.union.enrichment.configuration.UnionMetaProperties
import com.rarible.protocol.union.enrichment.meta.item.ItemMetaMetrics
import com.rarible.protocol.union.enrichment.meta.simplehash.SimpleHashConverter
import com.rarible.protocol.union.enrichment.meta.simplehash.SimpleHashConverterService
import com.rarible.protocol.union.enrichment.meta.simplehash.SimpleHashItem
import com.rarible.protocol.union.enrichment.repository.RawMetaCacheRepository
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigInteger
import javax.annotation.PostConstruct

@Service
class SimpleHashService(
    private val props: UnionMetaProperties,
    private val simpleHashClient: WebClient,
    private val metaCacheRepository: RawMetaCacheRepository,
    private val metrics: ItemMetaMetrics,
    private val itemServiceRouter: BlockchainRouter<ItemService>,
    private val simpleHashConverterService: SimpleHashConverterService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val objectMapper = ApiClient.createDefaultObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    private val enabled = props.simpleHash.enabled

    @PostConstruct
    fun postInit() {
        val conf = props.simpleHash.copy(apiKey = "-")
        logger.info("SimpleHashService is loaded with params: $conf")
    }

    fun isSupported(blockchain: BlockchainDto): Boolean {
        return blockchain in props.simpleHash.supported && enabled
    }

    suspend fun fetch(key: ItemIdDto): UnionMeta? {
        return fetchRaw(key)?.let { simpleHashConverterService.convert(it) }
    }

    suspend fun fetchRaw(key: ItemIdDto): SimpleHashItem? {
        if (!enabled) {
            return null
        }
        val cacheMeta = metaCacheRepository.get(SimpleHashConverter.cacheId(key))
        if (cacheMeta != null && !cacheMeta.hasExpired(props.simpleHash.cacheExpiration)) {
            return simpleHashConverterService.convertRawToSimpleHashItem(cacheMeta.data)
        }
        try {
            if (!isSupported(key.blockchain) || isLazyOrNotFound(key)) {
                return null
            }

            val json = simpleHashClient.get()
                .uri("/nfts/${network(key.blockchain)}/${key.value.replace(":", "/")}")
                .retrieve().bodyToMono(String::class.java).awaitSingle()

            return parse(key, json)
        } catch (e: Exception) {
            metrics.onMetaError(key.blockchain, MetaSource.SIMPLE_HASH)
            logger.error("Failed to fetch from simplehash $key", e)
        }
        return null
    }

    private fun parse(key: ItemIdDto, json: String): SimpleHashItem? {
        if (json == "{}") {
            metrics.onMetaFetchNotFound(key.blockchain, MetaSource.SIMPLE_HASH)
            return null
        }
        return try {
            val result = objectMapper.readValue(json, SimpleHashItem::class.java)
            metrics.onMetaFetched(key.blockchain, MetaSource.SIMPLE_HASH)
            result
        } catch (e: Exception) {
            logger.error("Failed to parse meta from simplehash {}: {}", key, json)
            metrics.onMetaCorruptedDataError(key.blockchain, MetaSource.SIMPLE_HASH)
            null
        }
    }

    suspend fun fetch(key: CollectionIdDto): UnionMeta? {
        // TODO: request from simplehash
        return null
    }

    private fun network(blockchain: BlockchainDto): String {
        return props.simpleHash.mapping[blockchain.name.lowercase()] ?: blockchain.name.lowercase()
    }

    private suspend fun isLazyOrNotFound(key: ItemIdDto): Boolean {
        val item = try {
            itemServiceRouter.getService(key.blockchain).getItemById(key.value)
        } catch (e: Exception) {
            logger.warn("Item $key wasn't found: ", e)
            null
        }
        return item?.lazySupply?.let { it > BigInteger.ZERO } ?: true
    }

    suspend fun refreshContract(collectionId: CollectionIdDto) {
        val url = "/nfts/refresh/${network(collectionId.blockchain)}/${collectionId.value}"
        logger.info("Sending request to simplehash for refresh: $url")
        val response = simpleHashClient.post()
            .uri(url)
            .retrieve().toBodilessEntity().awaitSingle()
        logger.info("Collection=$collectionId was refreshed with status=${response.statusCode}")
    }
}
