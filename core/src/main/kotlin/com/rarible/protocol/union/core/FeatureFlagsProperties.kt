package com.rarible.protocol.union.core

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("common.feature-flags")
@ConstructorBinding
data class FeatureFlagsProperties(
    val enableNotificationOnCollectionOrders: Boolean = true,
    val enableRevertedActivityEventSending: Boolean = false,
    val enableOwnershipSourceEnrichment: Boolean = false,
    val enableItemLastSaleEnrichment: Boolean = true,
    val enableContentMetaCache: Boolean = true,
    val enableEmbeddedContentMigrationJob: Boolean = true,
    val enablePoolOrders: Boolean = false,
    val enableWebClientConnectionPool: Boolean = false,
    // activities
    var enableActivityQueriesToElasticSearch: Boolean = false,
    var enableActivityAscQueriesWithApiMerge: Boolean = true,
    var enableActivitySaveImmediateToElasticSearch: Boolean = false,
    // orders
    var enableOrderQueriesToElasticSearch: Boolean = false,
    var enableOrderSaveImmediateToElasticSearch: Boolean = false,
    // collections
    val enableCollectionQueriesToElastic: Boolean = false,
    var enableCollectionSaveImmediateToElasticSearch: Boolean = false,
    var enableSearchCollections: Boolean = true,
    // ownerships
    var enableOwnershipQueriesToElasticSearch: Boolean = false,
    var enableOwnershipSaveImmediateToElasticSearch: Boolean = false,
    // items
    var enableItemQueriesToElasticSearch: Boolean = false,
    var enableSearchItems: Boolean = false,
    var enableItemSaveImmediateToElasticSearch: Boolean = false,
    val enableItemBestBidsByCurrency: Boolean = true,
    val enableIncrementalItemStats: Boolean = true,

    val enableCustomCollections: Boolean = false,

    val enableElasticsearchCompatibilityMode: Boolean = false,
    val enableMongoActivityWrite: Boolean = false,
    val enableMongoActivityRead: Boolean = false,

    val enableInternalEventChunkAsyncHandling: Boolean = true,

    val enableCollectionAutoReveal: Boolean = true,
    val enableCollectionSetBaseUriEvent: Boolean = true,
    val enableCollectionAutoRefreshOnCreation: Boolean = false,
    val enableCollectionItemMetaRefreshApi: Boolean = true,

    val enableOptimizedSearchForItems: Boolean = false,
    val enableOptimizedSearchForActivities: Boolean = true,
    val enableOptimizedSearchForOwnerships: Boolean = true,

    val enableStrictMetaComparison: Boolean = false,
    val enableMetaDownloadLimit: Boolean = true,
)
