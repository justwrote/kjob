package it.justwrote.kjob.internal

import it.justwrote.kjob.Job
import it.justwrote.kjob.KJob
import it.justwrote.kjob.dsl.*
import it.justwrote.kjob.job.JobExecutionType

internal class DefaultRunnableJob<J : Job>(
        override val name: J,
        configuration: KJob.Configuration,
        block: RegisterContext<J>.(J) -> KJobFunctions<J>
) : RunnableJob {
    private val rjc = RegisterContext<J>(configuration)
    private val sjc = block(rjc, name)

    override val executionType: JobExecutionType = rjc.executionType
    override val maxRetries: Int = rjc.maxRetries

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(context: JobContext<*>): JobResult {
        return try {
            if (!context.start()) error("Failed to start execution.")
            sjc.executeFn(context as JobContext<J>)
            if (!context.complete()) error("Failed to complete execution.")
            sjc.completeFn(CompletionJobContext(context.scheduledJob(), context.logger))
            JobSuccessful
        } catch (t: Throwable) {
            context.logger.error("Execution failed", t)
            try {
                sjc.errorFn(ErrorJobContext(context.scheduledJob(), t, context.logger))
            } catch (t: Throwable) {
                context.logger.error("Error handler also failed", t)
            }
            JobError(t)
        }

    }
}