package com.rarible.protocol.union.integration.aptos.service

import com.rarible.core.apm.CaptureSpan
import com.rarible.protocol.union.core.service.SignatureService
import com.rarible.protocol.union.core.service.router.AbstractBlockchainService
import com.rarible.protocol.union.dto.BlockchainDto

@CaptureSpan(type = "blockchain")
open class AptosSignatureService(
) : AbstractBlockchainService(BlockchainDto.APTOS), SignatureService {

    override suspend fun validate(
        signer: String,
        publicKey: String?,
        signature: String,
        message: String
    ): Boolean {
        return false
    }
}
