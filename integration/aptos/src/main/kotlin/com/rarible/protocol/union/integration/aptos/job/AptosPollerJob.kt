package com.rarible.protocol.union.integration.aptos.job

import com.rarible.core.daemon.DaemonWorkerProperties
import com.rarible.core.daemon.sequential.SequentialDaemonWorker
import com.rarible.protocol.union.integration.aptos.service.PollerService
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.time.delay
import java.time.Duration

class AptosPollerJob(
    meterRegistry: MeterRegistry,
    private val poller: PollerService
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
        logger.info("Get events: ${data.data.token_activities_v2.size}")
        delay(pollingPeriod)
    }
}
