package it.justwrote.kjob.dsl

import it.justwrote.kjob.Job
import it.justwrote.kjob.internal.utils.Generated
import it.justwrote.kjob.job.JobProps
import it.justwrote.kjob.job.ScheduledJob
import it.justwrote.kjob.repository.JobRepository
import kotlinx.coroutines.CoroutineScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

@JobDslMarker
class JobContext<J : Job> internal constructor(
        override val coroutineContext: CoroutineContext,
        val props: JobProps<J>,
        scheduledJob: ScheduledJob,
        private val jobRepository: JobRepository
) : CoroutineScope {
    private val internalJobId = scheduledJob.id
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

    @Generated
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JobContext<*>) return false

        if (coroutineContext != other.coroutineContext) return false
        if (props != other.props) return false
        if (jobRepository != other.jobRepository) return false
        if (internalJobId != other.internalJobId) return false
        if (logger != other.logger) return false
        if (stepProgress != other.stepProgress) return false

        return true
    }

    @Generated
    override fun hashCode(): Int {
        var result = coroutineContext.hashCode()
        result = 31 * result + props.hashCode()
        result = 31 * result + jobRepository.hashCode()
        result = 31 * result + internalJobId.hashCode()
        result = 31 * result + logger.hashCode()
        result = 31 * result + stepProgress.hashCode()
        return result
    }
}