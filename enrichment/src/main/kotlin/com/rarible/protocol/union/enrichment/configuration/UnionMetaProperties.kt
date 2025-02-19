package com.rarible.protocol.union.enrichment.configuration

import com.rarible.protocol.union.dto.BlockchainDto
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration

@ConstructorBinding
@ConfigurationProperties("meta")
data class UnionMetaProperties(
    val ipfsGateway: String = "https://ipfs.io",
    val ipfsPublicGateway: String = "https://ipfs.io",
    val ipfsLegacyGateway: String? = "https://ipfs.io",
    val mediaFetchMaxSize: Long = 1024 * 1024 * 10,
    val openSeaProxyUrl: String = "https://api.opensea.io/api/v1",
    val embedded: EmbeddedContentProperties = EmbeddedContentProperties(publicUrl = "http://localhost:8085/v0.1"),
    val trimming: MetaTrimmingProperties = MetaTrimmingProperties(),
    val httpClient: HttpClient = HttpClient(),
    val simpleHash: SimpleHash = SimpleHash(),
    private val retries: String = "" //  TODO not sure it should be here
) {

    val retryIntervals = retries.split(",")
        .filter { it.isNotBlank() }
        .map { Duration.parse(it) }
        .ifEmpty {
            listOf(
                Duration.ofHours(1),
                Duration.ofHours(24)
            )
        }

    class HttpClient(
        val type: HttpClientType = HttpClientType.KTOR_CIO,
        val threadCount: Int = 8,
        val timeOut: Int = 5000,
        val totalConnection: Int = 500,
        val connectionsPerRoute: Int = 20,
        val keepAlive: Boolean = true
    ) {

        enum class HttpClientType {
            KTOR_APACHE,
            KTOR_CIO,
            ASYNC_APACHE
        }
    }
}

data class EmbeddedContentProperties(
    val publicUrl: String,
    @Deprecated("Should be removed after migration")
    val legacyUrls: String = ""
)

data class MetaTrimmingProperties(
    val suffix: String = "...",
    val nameLength: Int = 10000,
    val descriptionLength: Int = 50000,
    val attributesSize: Int = 200,
    val attributeNameLength: Int = 500,
    val attributeValueLength: Int = 2000
)

data class SimpleHash(
    val enabled: Boolean = false,
    val endpoint: String = "https://api.simplehash.com/api/v0",
    val apiKey: String = "",
    val supported: Set<BlockchainDto> = emptySet(),

    // this is needed to mapping for test networks
    val mapping: Map<String, String> = emptyMap(),
    val cacheExpiration: Duration = Duration.ofMinutes(10),

    val kafka: SimpleHashKafka = SimpleHashKafka()
)

data class SimpleHashKafka(
    val enabled: Boolean = false,
    val broker: String = "pkc-3w22w.us-central1.gcp.confluent.cloud:9092",
    val concurrency: Int = 1,
    val batchSize: Int = 100,

    // topic depends on environment
    val topics: List<String> = emptyList(),

    val username: String? = null,
    val password: String? = null,
)
