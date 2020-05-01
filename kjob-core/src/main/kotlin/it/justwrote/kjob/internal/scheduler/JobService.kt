package it.justwrote.kjob.internal.scheduler

import it.justwrote.kjob.internal.JobExecutor
import it.justwrote.kjob.internal.JobRegister
import it.justwrote.kjob.job.JobExecutionType
import it.justwrote.kjob.job.JobStatus
import it.justwrote.kjob.job.ScheduledJob
import it.justwrote.kjob.repository.JobRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import kotlin.coroutines.CoroutineContext

internal class JobService(
        executorService: ScheduledExecutorService,
        period: Long,
        private val id: UUID,
        override val coroutineContext: CoroutineContext,
        private val jobRegister: JobRegister,
        private val jobExecutor: JobExecutor,
        private val jobRepository: JobRepository
) : SimpleScheduler(executorService, period), CoroutineScope {
    private val logger = LoggerFactory.getLogger(javaClass)

    private suspend fun executeJob(scheduledJob: ScheduledJob): Unit {
        val runnableJob = jobRegister.get(scheduledJob.settings.name)
        val isMyJob = jobRepository.update(scheduledJob.id, null, id, JobStatus.RUNNING, null, scheduledJob.retries)
        if (isMyJob) {
            jobExecutor.execute(runnableJob, scheduledJob, jobRepository)
        }
    }

    private suspend fun findAndExecuteJob(names: Set<String>) {
        if (names.isNotEmpty()) {
            jobRepository.findNextOne(names, setOf(JobStatus.CREATED))?.let { executeJob(it) }
        }
    }

    private fun tryExecuteJob() {
        launch {
            if (jobExecutor.canExecute(JobExecutionType.BLOCKING))
                findAndExecuteJob(jobRegister.jobs(JobExecutionType.BLOCKING))

            if (jobExecutor.canExecute(JobExecutionType.NON_BLOCKING))
                findAndExecuteJob(jobRegister.jobs(JobExecutionType.NON_BLOCKING))
        }
    }

    fun start(): Unit = run {
        logger.debug("Job service scheduled.")
        tryExecuteJob()
    }
}