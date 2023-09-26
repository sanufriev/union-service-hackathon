package com.rarible.protocol.union.integration.aptos.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.rarible.protocol.union.integration.aptos.model.AptosTokenResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Service
class PollerService(
    private val client: WebClient,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun poll(): AptosTokenResponse {
        val result = client.post()
            .uri("v1/graphql")
            .bodyValue(request())
            .retrieve().awaitBody<String>()
        logger.info("Received: $result")
        return objectMapper.readValue(result)
    }

    fun request() = """
{
    "query": "query MyQuery {
        token_activities_v2(order_by: {transaction_version: desc, event_index: desc})
        {
            type
            transaction_timestamp
            transaction_version
            token_standard
            token_amount
            current_token_data {
                token_properties
                token_name
                token_data_id
                token_standard
                token_uri
                supply
                description
                current_collection {
                    collection_id
                    collection_name
                    creator_address
                    current_supply
                    description      }
                current_token_ownership {
                    owner_address
                }
            }
            from_address
            to_address  }}",
    "variables": null,
    "operationName": "MyQuery"
}
    """.trimIndent().replace(Regex("\\R+"), "")


}
