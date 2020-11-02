package it.justwrote.kjob.job

import java.time.Instant
import java.util.*

data class ScheduledJob(
        val id: String,
        val status: JobStatus,
        val runAt: Instant?,
        val statusMessage: String?,
        val retries: Int,
        val kjobId: UUID?,
        val createdAt: Instant,
        val updatedAt: Instant,
        val settings: JobSettings,
        val progress: JobProgress)