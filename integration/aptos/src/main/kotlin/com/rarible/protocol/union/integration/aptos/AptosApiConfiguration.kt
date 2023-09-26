package com.rarible.protocol.union.integration.aptos

import com.rarible.protocol.union.core.CoreConfiguration
import com.rarible.protocol.union.dto.BlockchainDto
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

@AptosConfiguration
@Import(value = [CoreConfiguration::class])
@EnableConfigurationProperties(value = [AptosIntegrationProperties::class])
class AptosApiConfiguration {

    @Bean
    fun aptosBlockchain(): BlockchainDto {
        return BlockchainDto.APTOS
    }

    // -------------------- Services --------------------//

}
