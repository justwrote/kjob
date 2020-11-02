package it.justwrote.kjob.kron

import com.cronutils.model.time.ExecutionTime
import it.justwrote.kjob.KronJob
import it.justwrote.kjob.internal.JobScheduler
import it.justwrote.kjob.internal.scheduler.SimplePeriodScheduler
import it.justwrote.kjob.job.JobSettings
import java.time.Clock
import java.time.ZonedDateTime
import java.util.concurrent.ScheduledExecutorService

internal open class CronScheduler(executorService: ScheduledExecutorService,
                                  private val jobScheduler: JobScheduler,
                                  private val clock: Clock,
                                  period: Long) : SimplePeriodScheduler(executorService, period) {
    private val jobs: MutableMap<KronJob, ExecutionTime> = mutableMapOf()
    private val jobsWithLastExecutionTime: MutableMap<KronJob, ZonedDateTime?> = mutableMapOf()

    fun add(job: KronJob, executionTime: ExecutionTime): Unit {
        if (jobs.contains(job)) {
            error("Already added to cron execution '$job'")
        }
        jobs[job] = executionTime
    }

    private fun willBeExecutedUntil(nextExecution: ZonedDateTime?, until: ZonedDateTime, lastExecution: ZonedDateTime?): Boolean {
        return nextExecution?.let { next ->
            next.isBefore(until) && lastExecution?.let { !it.isEqual(next) } ?: true
        } ?: false
    }

    private suspend fun findNextSchedulableJobs(): Unit {
        val now = ZonedDateTime.now(clock).plusSeconds(1)
        val inFuture = now.plusSeconds(10)
        jobs
            .mapValues { it.value.nextExecution(now).toNullable()?.withNano(0) }
            .filter { willBeExecutedUntil(it.value, inFuture, jobsWithLastExecutionTime[it.key]) }
            .filterValuesNotNull()
            .forEach { (job, nextExecution) ->
                val instant = nextExecution.toInstant()
                val settings = JobSettings("${job.name}_$instant", job.name, emptyMap())
                try {
                    jobScheduler.schedule(settings, instant)
                    jobsWithLastExecutionTime[job] = nextExecution
                } catch (e: Exception) {
                    // we ignore this error since it is highly likely a duplicate entry for a cron job
                    // which happens if there are multiple instances running - only one can win
                }
            }
    }

    fun start(): Unit = run {
        findNextSchedulableJobs()
    }
}