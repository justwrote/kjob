package it.justwrote.kjob.dsl

import it.justwrote.kjob.job.ScheduledJob
import org.slf4j.Logger

@JobDslMarker
class ErrorJobContext internal constructor(scheduledJob: ScheduledJob, val error: Throwable, val logger: Logger) {
    val jobName = scheduledJob.settings.name
    val jobId = scheduledJob.settings.id
}