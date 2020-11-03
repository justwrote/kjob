package it.justwrote.kjob.internal.scheduler

import it.justwrote.kjob.repository.LockRepository
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ScheduledExecutorService

internal class KeepAliveScheduler(
        executorService: ScheduledExecutorService,
        period: Long,
        private val lockRepository: LockRepository
) : SimplePeriodScheduler(executorService, period) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private suspend fun iAmAlive(id: UUID) {
        lockRepository.ping(id)
    }

    fun start(id: UUID): Unit = run {
        logger.debug("Keep alive scheduled.")
        iAmAlive(id)
    }

}