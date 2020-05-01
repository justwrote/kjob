package it.justwrote.kjob.job

import java.time.Instant

data class JobProgress(val step: Long,
                       val max: Long? = null,
                       val startedAt: Instant? = null,
                       val completedAt: Instant? = null)