package com.rarible.protocol.union.integration.aptos.converter

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rarible.protocol.union.core.model.MetaSource
import com.rarible.protocol.union.core.model.UnionItem
import com.rarible.protocol.union.core.model.UnionMeta
import com.rarible.protocol.union.core.model.UnionMetaContent
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.dto.CollectionIdDto
import com.rarible.protocol.union.dto.ItemIdDto
import com.rarible.protocol.union.dto.MetaContentDto
import com.rarible.protocol.union.integration.aptos.repository.AptosItemDocument
import io.daonomic.rpc.domain.Word
import org.springframework.web.client.RestTemplate
import java.math.BigInteger
import java.time.Instant

object AptosItemConverter {
    private val rest = RestTemplate()

    private val objectMapper = ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }

    fun convert(document: AptosItemDocument): UnionItem {
        return UnionItem(
            id = ItemIdDto(
                blockchain = BlockchainDto.APTOS,
                contract = document.data.currentTokenData.currentCollection.collectionId,
                tokenId = Word.apply(document.data.currentTokenData.tokenDataId).toBigInteger()
            ),
            collection = CollectionIdDto(
                blockchain = BlockchainDto.APTOS,
                value = document.data.currentTokenData.currentCollection.collectionId
            ),
            creators = emptyList(),
            lazySupply = BigInteger.ZERO,
            mintedAt = Instant.now(), // TODO: change to transaction_timestamp
            deleted = false,
            lastUpdatedAt = Instant.now(), // TODO: change to transaction_timestamp
            supply = BigInteger.ONE,
            meta = UnionMeta(
                name = document.data.currentTokenData.tokenName,
                source = MetaSource.ORIGINAL,
                content = listOf(getImage(document.data.currentTokenData.tokenUri))
            )
        )
    }

    private fun getImage(url: String): UnionMetaContent {
        return try {
            val body = rest.getForEntity(url, String::class.java).body
            val meta = objectMapper.readTree(body)
            val image = meta.get("image")?.asText() ?: meta.get("image_url")?.asText()
            UnionMetaContent(
                url = image ?: url,
                representation = MetaContentDto.Representation.ORIGINAL
            )
        } catch (ex: Throwable) {
            UnionMetaContent(
                url = url,
                representation = MetaContentDto.Representation.ORIGINAL
            )
        }
    }
}
