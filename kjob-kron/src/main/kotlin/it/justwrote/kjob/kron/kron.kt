package it.justwrote.kjob.kron

import com.cronutils.descriptor.CronDescriptor
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import it.justwrote.kjob.*
import it.justwrote.kjob.dsl.JobContext
import it.justwrote.kjob.dsl.JobRegisterContext
import it.justwrote.kjob.dsl.KJobFunctions
import it.justwrote.kjob.extension.BaseExtension
import it.justwrote.kjob.extension.ExtensionId
import it.justwrote.kjob.extension.ExtensionModule
import it.justwrote.kjob.internal.DefaultRunnableJob
import org.slf4j.LoggerFactory
import java.util.*

object Kron : ExtensionId<KronEx>

object KronModule : ExtensionModule<KronEx, KronEx.Configuration, BaseKJob<BaseKJob.Configuration>, BaseKJob.Configuration> {
    override val id: ExtensionId<KronEx> = Kron
    override fun create(configure: KronEx.Configuration.() -> Unit, kjobConfig: BaseKJob.Configuration): (BaseKJob<BaseKJob.Configuration>) -> KronEx {
        return { KronEx(KronEx.Configuration().apply(configure), kjobConfig, it) }
    }
}

class KronEx(private val config: Configuration, private val kjobConfig: BaseKJob.Configuration, private val kjob: BaseKJob<BaseKJob.Configuration>) : BaseExtension(Kron) {
    private val logger = LoggerFactory.getLogger(javaClass)

    class Configuration : BaseExtension.Configuration()

    private val cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
    private val cronParser = CronParser(cronDefinition)
    private val descriptor = CronDescriptor.instance(Locale.UK)

    private val cronScheduler: CronScheduler by lazy { CronScheduler(kjob.jobExecutors().executorService, kjob.jobScheduler(), kjob.clock, 1000) }

    override fun start() {
        cronScheduler.start()
        logger.debug("Started ${id.name()} extension.")
    }

    override fun shutdown() {
        logger.debug("Shutting down ${id.name()} extension.")
        cronScheduler.shutdown()
    }

    fun <J : KronJob> kron(kronJob: J, block: JobRegisterContext<J, JobContext<J>>.(J) -> KJobFunctions<J, JobContext<J>>): Unit {
        val runnableJob = DefaultRunnableJob(kronJob, kjobConfig, block)
        kjob.jobRegister().register(runnableJob)
        val cron = cronParser.parse(kronJob.cronExpression)
        cron.validate()
        logger.debug("Add '${kronJob.name}' with cron expression '${descriptor.describe(cron)}'...")
        val executionTime = ExecutionTime.forCron(cron)
        cronScheduler.add(kronJob, executionTime)
    }
}