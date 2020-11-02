package it.justwrote.kjob.internal

import it.justwrote.kjob.dsl.JobContextWithProps
import it.justwrote.kjob.job.JobExecutionType
import it.justwrote.kjob.job.JobProps
import it.justwrote.kjob.job.JobStatus.*
import it.justwrote.kjob.job.ScheduledJob
import it.justwrote.kjob.repository.JobRepository
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.coroutines.CoroutineContext

interface JobExecutor {
    fun execute(runnableJob: RunnableJob, scheduledJob: ScheduledJob, jobRepository: JobRepository)
    fun canExecute(executionType: JobExecutionType): Boolean
}

internal class DefaultJobExecutor(
        private val kjobId: UUID,
        private val executors: Map<JobExecutionType, DispatcherWrapper>,
        private val clock: Clock,
        override val coroutineContext: CoroutineContext
) : JobExecutor, CoroutineScope {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(runnableJob: RunnableJob, scheduledJob: ScheduledJob, jobRepository: JobRepository) {
        val dispatcher = executors[runnableJob.executionType]?.coroutineDispatcher
                ?: error("Dispatcher not defined for ${runnableJob.executionType}")

        launch(dispatcher + CoroutineName("Job[${scheduledJob.settings.id}]")) {
            scheduledJob.runAt?.let { runAt ->
                while (Instant.now(clock).isBefore(runAt)) {
                    val duration = Duration.between(Instant.now(clock), runAt)
                    if (!duration.isNegative) {
                        delay(duration.toMillis())
                    }
                }
            }
            val isStillMyJob = jobRepository.update(scheduledJob.id, kjobId, kjobId, RUNNING, null, scheduledJob.retries)
            if (!isStillMyJob) {
                return@launch
            }
            val result = try {
                logger.debug("kjob[$kjobId] is executing ${scheduledJob.settings.name}[${scheduledJob.settings.id}]")
                val jobProps = scheduledJob.settings.properties
                runnableJob.execute(JobContextWithProps(coroutineContext, JobProps(jobProps), scheduledJob, jobRepository))
            } catch (e: Exception) {
                logger.error("${scheduledJob.settings.name}[${scheduledJob.settings.id}] failed", e)
                JobError(e)
            }
            withContext(NonCancellable) {
                val (status, message) = when (result) {
                    is JobSuccessful -> COMPLETE to null
                    is JobError ->
                        if (runnableJob.maxRetries <= scheduledJob.retries)
                            FAILED to result.throwable.message
                        else
                            ERROR to result.throwable.message
                }
                jobRepository.update(scheduledJob.id, kjobId, null, status, message, scheduledJob.retries + 1)
            }
        }
    }

    override fun canExecute(executionType: JobExecutionType): Boolean =
            executors[executionType]?.canExecute() ?: error("Executor not defined for $executionType")
}