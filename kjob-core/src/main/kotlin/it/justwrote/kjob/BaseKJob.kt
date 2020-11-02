package it.justwrote.kjob

import it.justwrote.kjob.dsl.*
import it.justwrote.kjob.extension.Extension
import it.justwrote.kjob.extension.ExtensionId
import it.justwrote.kjob.extension.ExtensionModule
import it.justwrote.kjob.internal.*
import it.justwrote.kjob.internal.scheduler.JobCleanupScheduler
import it.justwrote.kjob.internal.scheduler.JobService
import it.justwrote.kjob.internal.scheduler.KeepAliveScheduler
import it.justwrote.kjob.job.JobSettings
import it.justwrote.kjob.repository.JobRepository
import it.justwrote.kjob.repository.LockRepository
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

abstract class BaseKJob<Config : BaseKJob.Configuration>(val config: Config) : KJob {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var isRunning = false

    private val extensions: Map<ExtensionId<*>, Extension> = config.extensions.mapValues { it.value(this) }

    abstract val jobRepository: JobRepository
    abstract val lockRepository: LockRepository

    internal open val millis: Long = 1000 // allow override for testing

    open val clock: Clock = Clock.systemUTC() // meant only for testing
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

        internal val extensions: MutableMap<ExtensionId<*>, (KJob) -> Extension> = mutableMapOf()

        @Suppress("UNCHECKED_CAST")
        fun <Ex : Extension, ExConfig : Extension.Configuration, Kj : KJob, KjConfig : Configuration> KjConfig.extension(
                module: ExtensionModule<Ex, ExConfig, Kj, KjConfig>,
                configure: ExConfig.() -> Unit = {}
        ): Unit {
            val fn = module.create(configure, this)
            extensions[module.id] = fn as (KJob) -> Extension
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <Ex : Extension, ExId : ExtensionId<Ex>> invoke(extensionId: ExId): Ex =
            extensions[extensionId] as? Ex ?: throw IllegalStateException("Extension '${extensionId.name()}' not found")

    private val handler = CoroutineExceptionHandler { _, throwable -> config.exceptionHandler(throwable) }

    fun jobScheduler(): JobScheduler = jobScheduler
    fun jobExecutors(): JobExecutors = jobExecutors
    fun jobRegister(): JobRegister = jobRegister
    fun jobExecutor(): JobExecutor = jobExecutor

    internal open val jobExecutors: JobExecutors by lazy { DefaultJobExecutors(config) }
    internal open val jobScheduler: JobScheduler by lazy { DefaultJobScheduler(jobRepository) }
    internal open val jobRegister: JobRegister by lazy { DefaultJobRegister() }
    internal open val jobExecutor: JobExecutor by lazy { DefaultJobExecutor(id, jobExecutors.dispatchers, clock, kjobScope.coroutineContext) }

    private val kjobScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + jobExecutors.executorService.asCoroutineDispatcher() + CoroutineName("kjob[$id]") + handler)
    }

    private val keepAliveScheduler: KeepAliveScheduler by lazy {
        KeepAliveScheduler(
                jobExecutors.executorService,
                config.keepAliveExecutionPeriodInSeconds * millis,
                lockRepository
        )
    }
    private val cleanupScheduler: JobCleanupScheduler by lazy {
        JobCleanupScheduler(
                jobExecutors.executorService,
                config.cleanupPeriodInSeconds * millis,
                jobRepository,
                lockRepository,
                config.cleanupSize
        )
    }
    private val jobService: JobService by lazy {
        JobService(
                jobExecutors.executorService,
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
        extensions.forEach { (_, extension) -> extension.start() }
        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun <J : Job> register(job: J, block: JobRegisterContext<J, JobContextWithProps<J>>.(J) -> KJobFunctions<J, JobContextWithProps<J>>): KJob {
        val runnableJob = DefaultRunnableJob(job, config, block as JobRegisterContext<J, JobContext<J>>.(J) -> KJobFunctions<J, JobContext<J>>)
        jobRegister.register(runnableJob)
        return this
    }

    override suspend fun <J : Job> schedule(job: J, block: ScheduleContext<J>.(J) -> Unit): KJob {
        val ctx = ScheduleContext<J>()
        block(ctx, job)
        val settings = JobSettings(ctx.jobId, job.name, ctx.props.props)
        jobScheduler.schedule(settings)
        return this
    }

    override suspend fun <J : Job> schedule(job: J, delay: java.time.Duration, block: ScheduleContext<J>.(J) -> Unit): KJob {
        val ctx = ScheduleContext<J>()
        block(ctx, job)
        val settings = JobSettings(ctx.jobId, job.name, ctx.props.props)
        jobScheduler.schedule(settings, Instant.now(clock).plus(delay))
        return this
    }

    @ExperimentalTime
    override suspend fun <J : Job> schedule(job: J, delay: Duration, block: ScheduleContext<J>.(J) -> Unit): KJob {
        val ctx = ScheduleContext<J>()
        block(ctx, job)
        val settings = JobSettings(ctx.jobId, job.name, ctx.props.props)
        jobScheduler.schedule(settings, Instant.now(clock).plus(delay.toJavaDuration()))
        return this
    }

    override fun shutdown() {
        cleanupScheduler.shutdown()
        keepAliveScheduler.shutdown()
        jobService.shutdown()
        extensions.forEach { (_, extension) -> extension.shutdown() }

        jobExecutors.shutdown()
    }
}