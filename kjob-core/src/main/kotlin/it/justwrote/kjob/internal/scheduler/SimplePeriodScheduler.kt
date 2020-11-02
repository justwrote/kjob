package it.justwrote.kjob.internal.scheduler

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

abstract class SimplePeriodScheduler(private val executorService: ScheduledExecutorService, private val periodInMilliSeconds: Long) {
    private val logger = LoggerFactory.getLogger(javaClass)

    protected fun run(block: suspend () -> Unit) = run(block, 0)

    private fun run(block: suspend () -> Unit, initialDelay: Long): Unit {
        if (executorService.isShutdown || executorService.isTerminated) {
            throw IllegalStateException("Scheduler has already been shutdown.")
        }
        executorService.scheduleAtFixedRate({
            runBlocking {
                try {
                    block()
                } catch (e: Exception) {
                    logger.error("Scheduled task failed", e)
                    run(block, periodInMilliSeconds)
                    throw e
                }
            }
        }, initialDelay, periodInMilliSeconds, TimeUnit.MILLISECONDS)
    }

    open fun shutdown(): Unit {
        logger.debug("Shutting down ${javaClass.simpleName}")
        executorService.shutdown()
    }
}