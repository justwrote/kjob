package it.justwrote.kjob.repository.inmem

import it.justwrote.kjob.job.JobProgress
import it.justwrote.kjob.job.JobSettings
import it.justwrote.kjob.job.JobStatus
import it.justwrote.kjob.job.ScheduledJob
import it.justwrote.kjob.repository.JobRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import java.time.Clock
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class InMemJobRepository(private val clock: Clock) : JobRepository {
    private val map = ConcurrentHashMap<String, ScheduledJob>()
    private fun newId(): String = UUID.randomUUID().toString()

    override suspend fun exist(jobId: String): Boolean = map.values.find { it.settings.id == jobId } != null

    override suspend fun save(jobSettings: JobSettings, runAt: Instant?): ScheduledJob {
        if (exist(jobSettings.id)) {
            throw IllegalArgumentException("Job[${jobSettings.id}] does already exist")
        }
        val now = Instant.now(clock)
        val sj = ScheduledJob(newId(), JobStatus.CREATED, runAt, null, 0, null, now, now, jobSettings, JobProgress(0))
        map[sj.id] = sj
        return sj
    }

    override suspend fun get(id: String): ScheduledJob? = map[id]

    override suspend fun update(id: String, oldKjobId: UUID?, kjobId: UUID?, status: JobStatus, statusMessage: String?, retries: Int): Boolean {
        val sj = map[id]
        return if (sj == null || sj.kjobId != oldKjobId) {
            false
        } else {
            val newJe = sj.copy(
                    kjobId = kjobId,
                    status = status,
                    statusMessage = statusMessage,
                    retries = retries,
                    updatedAt = Instant.now(clock)
            )
            map[id] = newJe
            true
        }
    }

    override suspend fun reset(id: String, oldKjobId: UUID?): Boolean {
        val sj = map[id]
        return if (sj == null || sj.kjobId != oldKjobId) {
            false
        } else {
            val newJe = sj.copy(
                    status = JobStatus.CREATED,
                    statusMessage = null,
                    kjobId = null,
                    updatedAt = Instant.now(clock),
                    progress = JobProgress(0)
            )
            map[id] = newJe
            true
        }
    }

    override suspend fun startProgress(id: String): Boolean {
        val sj = map[id]
        return if (sj == null) {
            false
        } else {
            map[id] = sj.copy(
                    updatedAt = Instant.now(clock),
                    progress = sj.progress.copy(startedAt = Instant.now(clock))
            )
            true
        }
    }

    override suspend fun completeProgress(id: String): Boolean {
        val sj = map[id]
        return if (sj == null) {
            false
        } else {
            map[id] = sj.copy(
                    updatedAt = Instant.now(clock),
                    progress = sj.progress.copy(completedAt = Instant.now(clock))
            )
            true
        }
    }

    override suspend fun stepProgress(id: String, step: Long): Boolean {
        val sj = map[id]
        return if (sj == null) {
            false
        } else {
            map[id] = sj.copy(
                    updatedAt = Instant.now(clock),
                    progress = sj.progress.copy(step = sj.progress.step + step)
            )
            true
        }
    }

    override suspend fun setProgressMax(id: String, max: Long): Boolean {
        val sj = map[id]
        return if (sj == null) {
            false
        } else {
            map[id] = sj.copy(
                    updatedAt = Instant.now(clock),
                    progress = sj.progress.copy(max = max)
            )
            true
        }
    }

    @ExperimentalCoroutinesApi
    override suspend fun findNext(names: Set<String>, status: Set<JobStatus>, limit: Int): Flow<ScheduledJob> {
        val filter1: (ScheduledJob) -> Boolean = { if (names.isEmpty()) true else names.contains(it.settings.name) }
        val filter2: (ScheduledJob) -> Boolean = { status.contains(it.status) }
        return map.values.sortedBy { it.createdAt.epochSecond }.asFlow().filter { filter1(it) && filter2(it) }.take(limit)
    }

    internal fun deleteAll() {
        map.clear()
    }
}