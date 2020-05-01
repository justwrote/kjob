package it.justwrote.kjob

import it.justwrote.kjob.dsl.KJobFunctions
import it.justwrote.kjob.dsl.RegisterContext
import it.justwrote.kjob.dsl.ScheduleContext
import it.justwrote.kjob.job.JobExecutionType

interface KJob {
    open class Configuration {
        /**
         * How many non-blocking jobs will be executed at max in parallel per instance
         */
        var nonBlockingMaxJobs: Int = 10

        /**
         * How many blocking jobs will be executed at max in parallel per instance
         */
        var blockingMaxJobs: Int = 3

        /**
         * How often will a job be retried until it fails
         */
        var maxRetries: Int = 5

        /**
         * The default job execution type. Can be overridden per job
         */
        var defaultJobExecutor = JobExecutionType.BLOCKING
    }

    /**
     * Starts the kjob scheduler.
     */
    fun start(): KJob

    /**
     * Shutting down the kjob scheduler
     */
    fun shutdown(): Unit

    /**
     * Registers a new job. This is required to make kjob aware of a new job type that might
     * be scheduled later.
     *
     * @param job the job to be registered
     */
    fun <J : Job> register(job: J, block: RegisterContext<J>.(J) -> KJobFunctions<J>): KJob

    /**
     * Schedules a new job that will be processed in the background at some point.
     * @param job the job that has been registered before
     */
    suspend fun <J : Job> schedule(job: J, block: ScheduleContext<J>.(J) -> Unit = {}): KJob
}