package com.rarible.protocol.union.integration.aptos

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["integration.aptos.enabled"], havingValue = "true")
annotation class AptosConfiguration
