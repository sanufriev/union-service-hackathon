package com.rarible.protocol.union.listener.downloader

import com.rarible.protocol.union.core.model.UnionMeta
import com.rarible.protocol.union.dto.parser.IdParser
import com.rarible.protocol.union.enrichment.download.DownloadTaskEvent
import com.rarible.protocol.union.enrichment.repository.ItemMetaRepository
import com.rarible.protocol.union.enrichment.service.DownloadTaskService
import org.springframework.stereotype.Component

@Component
class ItemMetaTaskScheduler(
    downloadTaskService: DownloadTaskService,
    repository: ItemMetaRepository,
    metrics: DownloadSchedulerMetrics
) : DownloadScheduler<UnionMeta>(downloadTaskService, repository, metrics) {
    // TODO duplicated code with ItemTaskExecutor, refactoring required
    override val type = "Item"
    override fun getBlockchain(task: DownloadTaskEvent) = IdParser.parseItemId(task.id).blockchain
}
