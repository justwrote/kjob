package it.justwrote.kjob.internal

import it.justwrote.kjob.BaseJob
import it.justwrote.kjob.dsl.JobContext
import it.justwrote.kjob.job.JobExecutionType

interface RunnableJob {

    val job: BaseJob

    val executionType: JobExecutionType

    val maxRetries: Int

    suspend fun execute(context: JobContext<*>): JobResult
}