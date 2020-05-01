package it.justwrote.kjob

import it.justwrote.kjob.dsl.KJobFunctions
import it.justwrote.kjob.dsl.RegisterContext
import it.justwrote.kjob.dsl.ScheduleContext
import it.justwrote.kjob.internal.*
import it.justwrote.kjob.internal.scheduler.JobCleanupScheduler
import it.justwrote.kjob.internal.scheduler.JobService
import it.justwrote.kjob.internal.scheduler.KeepAliveScheduler
import it.justwrote.kjob.repository.JobRepository
import it.justwrote.kjob.repository.LockRepository
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.*

abstract class BaseKJob<Config : BaseKJob.Configuration>(protected val config: Config) : KJob {

    private val logger = LoggerFactory.getLogger(javaClass)
    private var isRunning = false

    protected abstract val jobRepository: JobRepository
    protected abstract val lockRepository: LockRepository
    protected open val executors: KJobExecutors = DefaultKJobExecutors(config)
    internal open val millis: Long = 1000 // allow override for testing

    val id: UUID = UUID.randomUUID()

    open class Configuration : KJob.Configuration() {
        private val logger = LoggerFactory.getLogger(BaseKJob::class.java) // Don't like this

        /**
         * Error handler for coroutines. Per default all errors will be logged
         */
        var exceptionHandler = { t: Throwable -> logger.error("Unhandled exception", t) }

        /**
         * The interval of for 'I am alive' notifications
         */
        var keepAliveExecutionPeriodInSeconds: Long = 60 // 1 minute

        /**
         * The interval for new job executions
         */
        var jobExecutionPeriodInSeconds: Long = 1 // every second

        /**
         * The interval for job clean ups (resetting jobs that had been scheduled with a different kjob instance)
         */
        var cleanupPeriodInSeconds: Long = 300 // 5 minutes

        /**
         * How many jobs to 'clean up' per schedule
         */
        var cleanupSize: Int = 50

    }

    private val handler = CoroutineExceptionHandler { _, throwable -> config.exceptionHandler(throwable) }

    private val kjobScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + executors.executorService.asCoroutineDispatcher() + CoroutineName("kjob[$id]") + handler)
    }

    private val jobScheduler: JobScheduler by lazy { DefaultJobScheduler(jobRepository) }
    private val jobExecutor: JobExecutor by lazy { DefaultJobExecutor(id, executors.dispatchers, kjobScope.coroutineContext) }
    private val jobRegister: JobRegister by lazy { DefaultJobRegister(config) }

    private val keepAliveScheduler: KeepAliveScheduler by lazy {
        KeepAliveScheduler(
                executors.executorService,
                config.keepAliveExecutionPeriodInSeconds * millis,
                lockRepository
        )
    }
    private val cleanupScheduler: JobCleanupScheduler by lazy {
        JobCleanupScheduler(
                executors.executorService,
                config.cleanupPeriodInSeconds * millis,
                jobRepository,
                lockRepository,
                config.cleanupSize
        )
    }
    private val jobService: JobService by lazy {
        JobService(
                executors.executorService,
                config.jobExecutionPeriodInSeconds * millis,
                id,
                kjobScope.coroutineContext,
                jobRegister,
                jobExecutor,
                jobRepository
        )
    }

    override fun start(): KJob = synchronized(this) {
        if (isRunning)
            error("kjob has already been started")

        isRunning = true
        jobService.start()
        cleanupScheduler.start()
        keepAliveScheduler.start(id)
        return this
    }

    override fun <J : Job> register(job: J, block: RegisterContext<J>.(J) -> KJobFunctions<J>): KJob {
        jobRegister.register(job, block)
        return this
    }

    override suspend fun <J : Job> schedule(job: J, block: ScheduleContext<J>.(J) -> Unit): KJob {
        jobScheduler.schedule(job, block)
        return this
    }

    override fun shutdown() {
        cleanupScheduler.shutdown()
        keepAliveScheduler.shutdown()
        jobService.shutdown()

        executors.shutdown()
    }
}