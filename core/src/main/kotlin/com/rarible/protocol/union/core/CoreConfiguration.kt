package com.rarible.protocol.union.core

import com.rarible.protocol.flow.nft.api.client.FlowNftCollectionControllerApi
import com.rarible.protocol.flow.nft.api.client.FlowNftItemControllerApi
import com.rarible.protocol.flow.nft.api.client.FlowNftOrderActivityControllerApi
import com.rarible.protocol.flow.nft.api.client.FlowNftOwnershipControllerApi
import com.rarible.protocol.flow.nft.api.client.FlowOrderControllerApi
import com.rarible.protocol.nft.api.client.NftActivityControllerApi
import com.rarible.protocol.nft.api.client.NftCollectionControllerApi
import com.rarible.protocol.nft.api.client.NftItemControllerApi
import com.rarible.protocol.nft.api.client.NftOwnershipControllerApi
import com.rarible.protocol.order.api.client.OrderActivityControllerApi
import com.rarible.protocol.order.api.client.OrderControllerApi
import com.rarible.protocol.order.api.client.OrderSignatureControllerApi
import com.rarible.protocol.union.core.ethereum.service.EthereumActivityService
import com.rarible.protocol.union.core.ethereum.service.EthereumCollectionService
import com.rarible.protocol.union.core.ethereum.service.EthereumItemService
import com.rarible.protocol.union.core.ethereum.service.EthereumOrderService
import com.rarible.protocol.union.core.ethereum.service.EthereumOwnershipService
import com.rarible.protocol.union.core.ethereum.service.EthereumSignatureService
import com.rarible.protocol.union.core.flow.service.FlowActivityService
import com.rarible.protocol.union.core.flow.service.FlowCollectionService
import com.rarible.protocol.union.core.flow.service.FlowItemService
import com.rarible.protocol.union.core.flow.service.FlowOrderService
import com.rarible.protocol.union.core.flow.service.FlowOwnershipService
import com.rarible.protocol.union.core.flow.service.FlowSignatureService
import com.rarible.protocol.union.core.service.ActivityService
import com.rarible.protocol.union.core.service.CollectionService
import com.rarible.protocol.union.core.service.ItemService
import com.rarible.protocol.union.core.service.OrderService
import com.rarible.protocol.union.core.service.OwnershipService
import com.rarible.protocol.union.core.service.SignatureService
import com.rarible.protocol.union.dto.BlockchainDto
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan(basePackageClasses = [CoreConfiguration::class])
class CoreConfiguration {

    //--------------------- ETHEREUM --------------------//
    @Bean
    fun ethereumItemService(@Qualifier("ethereum.item.api") ethereumItemApi: NftItemControllerApi): ItemService {
        return EthereumItemService(BlockchainDto.ETHEREUM, ethereumItemApi)
    }

    @Bean
    fun ethereumOwnershipService(@Qualifier("ethereum.ownership.api") ethereumOwnershipApi: NftOwnershipControllerApi): OwnershipService {
        return EthereumOwnershipService(BlockchainDto.ETHEREUM, ethereumOwnershipApi)
    }

    @Bean
    fun ethereumCollectionService(@Qualifier("ethereum.collection.api") ethereumCollectionApi: NftCollectionControllerApi): CollectionService {
        return EthereumCollectionService(BlockchainDto.ETHEREUM, ethereumCollectionApi)
    }

    @Bean
    fun ethereumOrderService(@Qualifier("ethereum.order.api") ethereumOrderApi: OrderControllerApi): OrderService {
        return EthereumOrderService(BlockchainDto.ETHEREUM, ethereumOrderApi)
    }

    @Bean
    fun ethereumSignatureService(@Qualifier("ethereum.signature.api") ethereumSignatureApi: OrderSignatureControllerApi): SignatureService {
        return EthereumSignatureService(BlockchainDto.ETHEREUM, ethereumSignatureApi)
    }

    @Bean
    fun ethereumActivityService(
        @Qualifier("ethereum.activity.api.item") ethereumActivityItemApi: NftActivityControllerApi,
        @Qualifier("ethereum.activity.api.order") ethereumActivityOrderApi: OrderActivityControllerApi
    ): ActivityService {
        return EthereumActivityService(BlockchainDto.ETHEREUM, ethereumActivityItemApi, ethereumActivityOrderApi)
    }

    //--------------------- POLYGON ---------------------//
    @Bean
    fun polygonItemService(@Qualifier("polygon.item.api") ethereumItemApi: NftItemControllerApi): ItemService {
        return EthereumItemService(BlockchainDto.POLYGON, ethereumItemApi)
    }

    @Bean
    fun polygonOwnershipService(@Qualifier("polygon.ownership.api") ethereumOwnershipApi: NftOwnershipControllerApi): OwnershipService {
        return EthereumOwnershipService(BlockchainDto.POLYGON, ethereumOwnershipApi)
    }

    @Bean
    fun polygonCollectionService(@Qualifier("polygon.collection.api") ethereumCollectionApi: NftCollectionControllerApi): CollectionService {
        return EthereumCollectionService(BlockchainDto.POLYGON, ethereumCollectionApi)
    }

    @Bean
    fun polygonOrderService(@Qualifier("polygon.order.api") ethereumOrderApi: OrderControllerApi): OrderService {
        return EthereumOrderService(BlockchainDto.POLYGON, ethereumOrderApi)
    }

    @Bean
    fun polygonSignatureService(@Qualifier("polygon.signature.api") ethereumSignatureApi: OrderSignatureControllerApi): SignatureService {
        return EthereumSignatureService(BlockchainDto.POLYGON, ethereumSignatureApi)
    }

    @Bean
    fun polygonActivityService(
        @Qualifier("polygon.activity.api.item") polygonActivityItemApi: NftActivityControllerApi,
        @Qualifier("polygon.activity.api.order") polygonActivityOrderApi: OrderActivityControllerApi
    ): ActivityService {
        return EthereumActivityService(BlockchainDto.POLYGON, polygonActivityItemApi, polygonActivityOrderApi)
    }

    //---------------------- FLOW -----------------------//
    @Bean
    fun flowItemService(flowItemApi: FlowNftItemControllerApi): ItemService {
        return FlowItemService(BlockchainDto.FLOW, flowItemApi)
    }

    @Bean
    fun flowOwnershipService(flowOwnershipApi: FlowNftOwnershipControllerApi): OwnershipService {
        return FlowOwnershipService(BlockchainDto.FLOW, flowOwnershipApi)
    }

    @Bean
    fun flowCollectionService(flowCollectionApi: FlowNftCollectionControllerApi): CollectionService {
        return FlowCollectionService(BlockchainDto.FLOW, flowCollectionApi)
    }

    @Bean
    fun flowOrderService(flowOrderApi: FlowOrderControllerApi): OrderService {
        return FlowOrderService(BlockchainDto.FLOW, flowOrderApi)
    }

    @Bean
    fun flowSignatureService(flowOrderApi: FlowOrderControllerApi): SignatureService {
        return FlowSignatureService(BlockchainDto.FLOW) // TODO implement it later
    }

    @Bean
    fun flowActivityService(flowActivityApi: FlowNftOrderActivityControllerApi): ActivityService {
        return FlowActivityService(BlockchainDto.FLOW, flowActivityApi)
    }

}