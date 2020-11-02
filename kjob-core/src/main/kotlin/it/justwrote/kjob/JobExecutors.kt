package it.justwrote.kjob

import it.justwrote.kjob.internal.DispatcherWrapper
import it.justwrote.kjob.job.JobExecutionType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.*

interface JobExecutors {
    val executorService: ScheduledExecutorService
    val dispatchers: Map<JobExecutionType, DispatcherWrapper>

    fun shutdown(): Unit {
        dispatchers.values.forEach { it.shutdown() }
        executorService.shutdown()
    }
}

internal class DefaultJobExecutors(config: KJob.Configuration) : JobExecutors {
    override val executorService: ScheduledExecutorService by lazy { ScheduledThreadPoolExecutor(3) }
    override val dispatchers: Map<JobExecutionType, DispatcherWrapper> = mapOf(
            JobExecutionType.BLOCKING to object : DispatcherWrapper {
                private val executor = Executors.newFixedThreadPool(config.blockingMaxJobs) { r -> Thread(r, "kjob-blocking-executor") } as ThreadPoolExecutor
                override fun canExecute(): Boolean = executor.activeCount < executor.corePoolSize
                override val coroutineDispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
                override fun shutdown() {
                    executor.shutdown()
                }
            },
            JobExecutionType.NON_BLOCKING to object : DispatcherWrapper {
                private val executor = Executors.newWorkStealingPool(config.nonBlockingMaxJobs) as ForkJoinPool
                override fun canExecute(): Boolean = executor.activeThreadCount < executor.parallelism
                override val coroutineDispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
                override fun shutdown() {
                    executor.shutdown()
                }
            }
    )
}