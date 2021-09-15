package com.rarible.protocol.union.dto.ethereum

import com.rarible.protocol.union.dto.EthBlockchainDto

data class EthAddress(
    override val blockchain: EthBlockchainDto,
    override val value: String
) : EthBlockchainId()