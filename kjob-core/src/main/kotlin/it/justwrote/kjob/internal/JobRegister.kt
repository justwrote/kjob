package it.justwrote.kjob.internal

import it.justwrote.kjob.job.JobExecutionType
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

interface JobRegister {
    fun register(runnableJob: RunnableJob): Unit
    fun jobs(executionType: JobExecutionType): Set<String>
    fun get(name: String): RunnableJob
}

internal class DefaultJobRegister : JobRegister {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val jobsMap: ConcurrentMap<String, RunnableJob> = ConcurrentHashMap()
    private var jobsByExecutionType: Map<JobExecutionType, Set<String>> = emptyMap()

    override fun jobs(executionType: JobExecutionType): Set<String> = jobsByExecutionType.getOrDefault(executionType, emptySet())
    override fun get(name: String): RunnableJob = jobsMap.getValue(name)

    override fun register(runnableJob: RunnableJob): Unit {
        logger.debug("kjob registered a new job named '${runnableJob.job.name}'")
        jobsMap.putIfAbsent(runnableJob.job.name, runnableJob)
        jobsByExecutionType = jobsMap.values.groupBy { it.executionType }.mapValues { it.value.map { it.job.name }.toSet() }
    }
}