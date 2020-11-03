package it.justwrote.kjob.dsl

import it.justwrote.kjob.BaseJob
import it.justwrote.kjob.Job
import it.justwrote.kjob.job.JobProps
import it.justwrote.kjob.job.ScheduledJob
import it.justwrote.kjob.repository.JobRepository
import kotlinx.coroutines.CoroutineScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

open class JobContext<J : BaseJob> internal constructor(
        override val coroutineContext: CoroutineContext,
        scheduledJob: ScheduledJob,
        private val jobRepository: JobRepository
) : CoroutineScope {
    private val internalJobId = scheduledJob.id
    val jobId = scheduledJob.settings.id
    val logger: Logger = LoggerFactory.getLogger("it.justwrote.kjob.${scheduledJob.settings.name}#${scheduledJob.settings.id}")

    private var stepProgress = scheduledJob.progress.step

    suspend fun setInitialMax(max: Int): Boolean = setInitialMax(max.toLong())

    suspend fun setInitialMax(max: Long): Boolean =
            jobRepository.setProgressMax(internalJobId, max)

    suspend fun step(step: Int): Boolean = step(step.toLong())

    suspend fun step(step: Long = 1L): Boolean {
        stepProgress += step
        return jobRepository.stepProgress(internalJobId, step)
    }

    internal suspend fun start(): Boolean {
        logger.debug("Start execution")
        return jobRepository.startProgress(internalJobId)
    }

    internal suspend fun complete(): Boolean {
        logger.debug("Complete execution")
        val b1 = jobRepository.setProgressMax(internalJobId, stepProgress)
        val b2 = jobRepository.completeProgress(internalJobId)
        return b1 && b2
    }

    internal suspend fun scheduledJob(): ScheduledJob = jobRepository.get(internalJobId)!!
}

@JobDslMarker
class JobContextWithProps<J : Job> internal constructor(
        coroutineContext: CoroutineContext,
        val props: JobProps<J>,
        scheduledJob: ScheduledJob,
        jobRepository: JobRepository
) : JobContext<J>(coroutineContext, scheduledJob, jobRepository)