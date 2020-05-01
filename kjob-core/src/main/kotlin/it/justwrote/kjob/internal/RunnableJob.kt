package it.justwrote.kjob.internal

import it.justwrote.kjob.Job
import it.justwrote.kjob.dsl.JobContext
import it.justwrote.kjob.job.JobExecutionType

internal interface RunnableJob {

    val name: Job

    val executionType: JobExecutionType

    val maxRetries: Int

    suspend fun execute(context: JobContext<*>): JobResult
}