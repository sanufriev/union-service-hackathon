package com.rarible.protocol.union.enrichment.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration

@ConstructorBinding
@ConfigurationProperties("meta")
data class UnionMetaProperties(
    val ipfsGateway: String,
    val ipfsPublicGateway: String,
    val ipfsLegacyGateway: String?,
    val mediaFetchMaxSize: Long,
    val openSeaProxyUrl: String,
    val embedded: EmbeddedContentProperties,
    val trimming: ItemMetaTrimmingProperties = ItemMetaTrimmingProperties(),
    val httpClient: HttpClient = HttpClient(),
    private val retries: String = "" //  TODO not sure it should be here
) {

    val retryIntervals = retries.split(",")
        .filter { it.isNotBlank() }
        .map { Duration.parse(it) }
        .ifEmpty {
            listOf(
                Duration.ofMinutes(5),
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

data class ItemMetaTrimmingProperties(
    val suffix: String = "...",
    val nameLength: Int = 10000,
    val descriptionLength: Int = 50000,
    val attributesSize: Int = 200,
    val attributeNameLength: Int = 500,
    val attributeValueLength: Int = 2000
)