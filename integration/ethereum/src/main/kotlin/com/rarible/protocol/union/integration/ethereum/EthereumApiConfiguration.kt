package com.rarible.protocol.union.integration.ethereum

import com.rarible.protocol.nft.api.client.NftActivityControllerApi
import com.rarible.protocol.nft.api.client.NftCollectionControllerApi
import com.rarible.protocol.nft.api.client.NftIndexerApiClientFactory
import com.rarible.protocol.nft.api.client.NftItemControllerApi
import com.rarible.protocol.nft.api.client.NftOwnershipControllerApi
import com.rarible.protocol.order.api.client.OrderActivityControllerApi
import com.rarible.protocol.order.api.client.OrderControllerApi
import com.rarible.protocol.order.api.client.OrderIndexerApiClientFactory
import com.rarible.protocol.order.api.client.OrderSignatureControllerApi
import com.rarible.protocol.union.core.CoreConfiguration
import com.rarible.protocol.union.dto.BlockchainDto
import com.rarible.protocol.union.integration.ethereum.converter.EthActivityConverter
import com.rarible.protocol.union.integration.ethereum.converter.EthOrderConverter
import com.rarible.protocol.union.integration.ethereum.service.EthActivityService
import com.rarible.protocol.union.integration.ethereum.service.EthCollectionService
import com.rarible.protocol.union.integration.ethereum.service.EthItemService
import com.rarible.protocol.union.integration.ethereum.service.EthOrderService
import com.rarible.protocol.union.integration.ethereum.service.EthOwnershipService
import com.rarible.protocol.union.integration.ethereum.service.EthSignatureService
import com.rarible.protocol.union.integration.ethereum.service.EthereumActivityService
import com.rarible.protocol.union.integration.ethereum.service.EthereumCollectionService
import com.rarible.protocol.union.integration.ethereum.service.EthereumItemService
import com.rarible.protocol.union.integration.ethereum.service.EthereumOrderService
import com.rarible.protocol.union.integration.ethereum.service.EthereumOwnershipService
import com.rarible.protocol.union.integration.ethereum.service.EthereumSignatureService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import

@EthereumConfiguration
@Import(CoreConfiguration::class)
@ComponentScan(basePackageClasses = [EthOrderConverter::class])
@EnableConfigurationProperties(value = [EthereumIntegrationProperties::class])
class EthereumApiConfiguration {

    private val ethereum = BlockchainDto.ETHEREUM.name.toLowerCase()

    @Bean
    fun ethereumBlockchain(): BlockchainDto {
        return BlockchainDto.ETHEREUM
    }

    //-------------------- API --------------------//

    @Bean
    @Qualifier("ethereum.item.api")
    fun ethereumItemApi(factory: NftIndexerApiClientFactory): NftItemControllerApi =
        factory.createNftItemApiClient(ethereum)

    @Bean
    @Qualifier("ethereum.ownership.api")
    fun ethereumOwnershipApi(factory: NftIndexerApiClientFactory): NftOwnershipControllerApi =
        factory.createNftOwnershipApiClient(ethereum)

    @Bean
    @Qualifier("ethereum.collection.api")
    fun ethereumCollectionApi(factory: NftIndexerApiClientFactory): NftCollectionControllerApi =
        factory.createNftCollectionApiClient(ethereum)

    @Bean
    @Qualifier("ethereum.order.api")
    fun ethereumOrderApi(factory: OrderIndexerApiClientFactory): OrderControllerApi =
        factory.createOrderApiClient(ethereum)

    @Bean
    @Qualifier("ethereum.signature.api")
    fun ethereumSignatureApi(factory: OrderIndexerApiClientFactory): OrderSignatureControllerApi =
        factory.createOrderSignatureApiClient(ethereum)

    @Bean
    @Qualifier("ethereum.activity.api.item")
    fun ethereumActivityItemApi(factory: NftIndexerApiClientFactory): NftActivityControllerApi =
        factory.createNftActivityApiClient(ethereum)

    @Bean
    @Qualifier("ethereum.activity.api.order")
    fun ethereumActivityOrderApi(factory: OrderIndexerApiClientFactory): OrderActivityControllerApi =
        factory.createOrderActivityApiClient(ethereum)

    //-------------------- Services --------------------//

    @Bean
    fun ethereumItemService(
        @Qualifier("ethereum.item.api") controllerApi: NftItemControllerApi
    ): EthItemService {
        return EthereumItemService(controllerApi)
    }

    @Bean
    fun ethereumOwnershipService(
        @Qualifier("ethereum.ownership.api") controllerApi: NftOwnershipControllerApi
    ): EthOwnershipService {
        return EthereumOwnershipService(controllerApi)
    }

    @Bean
    fun ethereumCollectionService(
        @Qualifier("ethereum.collection.api") controllerApi: NftCollectionControllerApi
    ): EthCollectionService {
        return EthereumCollectionService(controllerApi)
    }

    @Bean
    fun ethereumOrderService(
        @Qualifier("ethereum.order.api") controllerApi: OrderControllerApi,
        converter: EthOrderConverter
    ): EthOrderService {
        return EthereumOrderService(controllerApi, converter)
    }

    @Bean
    fun ethereumSignatureService(
        @Qualifier("ethereum.signature.api") controllerApi: OrderSignatureControllerApi
    ): EthSignatureService {
        return EthereumSignatureService(controllerApi)
    }

    @Bean
    fun ethereumActivityService(
        @Qualifier("ethereum.activity.api.item") itemActivityApi: NftActivityControllerApi,
        @Qualifier("ethereum.activity.api.order") orderActivityApi: OrderActivityControllerApi,
        converter: EthActivityConverter
    ): EthActivityService {
        return EthereumActivityService(itemActivityApi, orderActivityApi, converter)
    }
}