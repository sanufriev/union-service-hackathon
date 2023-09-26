package com.rarible.protocol.union.integration.aptos.model

import com.fasterxml.jackson.annotation.JsonProperty

data class AptosTokenResponse(
    val data: TokenActivityData
) {
    data class TokenActivityData(
        val token_activities_v2: List<NftEvent>
    )
}

data class NftEvent(
    @JsonProperty("type") val type: String,
    @JsonProperty("transaction_timestamp") val transactionTimestamp: String,
    @JsonProperty("transaction_version") val transactionVersion: Long,
    @JsonProperty("token_standard") val tokenStandard: String,
    @JsonProperty("token_amount") val tokenAmount: Int,
    @JsonProperty("current_token_data") val currentTokenData: CurrentTokenData,
    @JsonProperty("from_address") val fromAddress: String?,
    @JsonProperty("to_address") val toAddress: String?
)
data class CurrentTokenData(
    @JsonProperty("token_properties") val tokenProperties: TokenProperties?,
    @JsonProperty("token_name") val tokenName: String,
    @JsonProperty("token_data_id") val tokenDataId: String,
    @JsonProperty("token_standard") val tokenStandard: String,
    @JsonProperty("token_uri") val tokenUri: String,
    @JsonProperty("supply") val supply: Int,
    @JsonProperty("description") val description: String,
    @JsonProperty("current_collection") val currentCollection: CurrentCollection,
    @JsonProperty("current_token_ownership") val currentTokenOwnership: CurrentTokenOwnership
)
data class TokenProperties(
    @JsonProperty("Stem") val stem: String?,
    @JsonProperty("Flower") val flower: String?,
    @JsonProperty("Background") val background: String?
)
data class CurrentCollection(
    @JsonProperty("collection_id") val collectionId: String,
    @JsonProperty("collection_name") val collectionName: String,
    @JsonProperty("creator_address") val creatorAddress: String,
    @JsonProperty("current_supply") val currentSupply: Int,
    @JsonProperty("description") val description: String
)
data class CurrentTokenOwnership(
    @JsonProperty("owner_address") val ownerAddress: String
)
