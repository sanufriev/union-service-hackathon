package com.rarible.protocol.union.integration.aptos.repository

import com.rarible.core.common.nowMillis
import com.rarible.protocol.union.integration.aptos.model.NftEvent
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document
class AptosItemDocument(
    @Id
    val id: String,
    val data: NftEvent,

    val createdAt: Instant = nowMillis(),
)
