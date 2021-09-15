package com.rarible.protocol.union.dto.flow.deserializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import com.rarible.protocol.union.dto.FlowBlockchainDto
import com.rarible.protocol.union.dto.flow.FlowContract
import com.rarible.protocol.union.dto.flow.FlowItemIdDto
import java.math.BigInteger

object FlowItemIdDeserializer : StdDeserializer<FlowItemIdDto>(FlowItemIdDto::class.java) {

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FlowItemIdDto? {
        val tree: ObjectNode = p.codec.readTree(p) ?: return null
        val blockchain = tree.get(FlowItemIdDto::blockchain.name)
        val token = tree.get(FlowItemIdDto::token.name)
        val tokenId = tree.get(FlowItemIdDto::tokenId.name)
        return FlowItemIdDto(
            blockchain = FlowBlockchainDto.valueOf(blockchain.textValue()),
            token = token.traverse(p.codec).readValueAs(FlowContract::class.java),
            tokenId = tokenId.traverse(p.codec).readValueAs(BigInteger::class.java)
        )
    }
}