package it.justwrote.kjob.internal

import it.justwrote.kjob.Job
import it.justwrote.kjob.dsl.ScheduleContext
import it.justwrote.kjob.job.JobSettings
import it.justwrote.kjob.job.ScheduledJob
import it.justwrote.kjob.repository.JobRepository
import org.slf4j.LoggerFactory

interface JobScheduler {
    suspend fun <J : Job> schedule(job: J, block: ScheduleContext<J>.(J) -> Unit = {}): ScheduledJob
}

internal class DefaultJobScheduler(private val jobRepository: JobRepository) : JobScheduler {
    private val logger = LoggerFactory.getLogger(javaClass)

    private suspend fun schedule(jobSettings: JobSettings): ScheduledJob = jobSettings.run {
        return if (jobRepository.exist(id)) {
            error("Job '$name' with id '$id' has already been scheduled.")
        } else {
            jobRepository.save(this).also { logger.debug("Scheduled new job '${it.settings.name}'") }
        }
    }

    override suspend fun <J : Job> schedule(job: J, block: ScheduleContext<J>.(J) -> Unit): ScheduledJob {
        val ctx = ScheduleContext<J>()
        block(ctx, job)
        return schedule(JobSettings(ctx.jobId, job.name, ctx.props.props))
    }
}