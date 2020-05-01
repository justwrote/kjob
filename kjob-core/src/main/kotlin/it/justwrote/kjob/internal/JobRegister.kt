package it.justwrote.kjob.internal

import it.justwrote.kjob.Job
import it.justwrote.kjob.KJob
import it.justwrote.kjob.dsl.KJobFunctions
import it.justwrote.kjob.dsl.RegisterContext
import it.justwrote.kjob.job.JobExecutionType
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal interface JobRegister {
    fun <J : Job> register(name: J, block: RegisterContext<J>.(J) -> KJobFunctions<J>): Unit
    fun jobs(executionType: JobExecutionType): Set<String>
    fun get(name: String): RunnableJob
}

internal class DefaultJobRegister(private val configuration: KJob.Configuration) : JobRegister {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val jobsMap: ConcurrentMap<String, RunnableJob> = ConcurrentHashMap()
    private var jobsByExecutionType: Map<JobExecutionType, Set<String>> = emptyMap()

    override fun jobs(executionType: JobExecutionType): Set<String> = jobsByExecutionType.getOrDefault(executionType, emptySet())
    override fun get(name: String): RunnableJob = jobsMap.getValue(name)

    private fun register(runnableJob: RunnableJob): Unit {
        jobsMap.putIfAbsent(runnableJob.name.name, runnableJob)
        jobsByExecutionType = jobsMap.values.groupBy { it.executionType }.mapValues { it.value.map { it.name.name }.toSet() }
    }

    override fun <J : Job> register(name: J, block: RegisterContext<J>.(J) -> KJobFunctions<J>): Unit {
        val runnableJob = DefaultRunnableJob(name, configuration, block)
        logger.debug("kjob registered a new job named '${name.name}'")
        register(runnableJob)
    }
}