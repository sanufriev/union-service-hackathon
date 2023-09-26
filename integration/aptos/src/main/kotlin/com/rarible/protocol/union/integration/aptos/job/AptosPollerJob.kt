package com.rarible.protocol.union.integration.aptos.job

import com.rarible.core.daemon.DaemonWorkerProperties
import com.rarible.core.daemon.sequential.SequentialDaemonWorker
import com.rarible.protocol.union.integration.aptos.repository.AptosRepository
import com.rarible.protocol.union.integration.aptos.service.PollerService
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.time.delay
import java.time.Duration

class AptosPollerJob(
    meterRegistry: MeterRegistry,
    private val poller: PollerService,
    private val aptosRepository: AptosRepository,
) : SequentialDaemonWorker(
    meterRegistry = meterRegistry,
    properties = DaemonWorkerProperties().copy(
        pollingPeriod = Duration.ofSeconds(1),
        errorDelay = Duration.ofSeconds(1)
    ),
    workerName = "aptos_poller"
) {

    override suspend fun handle() {
        val data = poller.poll()
        coroutineScope {
            data.data.token_activities_v2.map { event ->
                async {
                    aptosRepository.save(event)
                }
            }.awaitAll()
        }
        logger.info("Get events: ${data.data.token_activities_v2.size}, lastVersion=${data.data.token_activities_v2.last().transactionVersion}")
        delay(pollingPeriod)
    }
}
