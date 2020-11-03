package it.justwrote.kjob.internal.scheduler

import io.kotest.core.spec.style.ShouldSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import it.justwrote.kjob.job.JobStatus.*
import it.justwrote.kjob.repository.JobRepository
import it.justwrote.kjob.repository.LockRepository
import kotlinx.coroutines.flow.asFlow
import java.util.*

class JobCleanupSchedulerSpec : ShouldSpec() {

    init {
        val otherKjobId = UUID.randomUUID()
        val otherKjobId2 = UUID.randomUUID()
        val kjobId = UUID.randomUUID()
        val je1 = sj(id = "1", status = RUNNING, kjobId = kjobId)
        val je2 = sj(id = "2", status = RUNNING, kjobId = otherKjobId)
        val je3 = sj(id = "3", status = ERROR, kjobId = kjobId)
        val je4 = sj(id = "4", status = ERROR, kjobId = otherKjobId2)
        val je5 = sj(id = "5", status = ERROR)
        val all = listOf(
                je1,
                je2,
                je3,
                je4,
                je5
        )

        should("find next jobs for clean up") {
            val jobRepoMock = mockk<JobRepository>()
            val lockRepoMock = mockk<LockRepository>()
            val testee = JobCleanupScheduler(
                    newScheduler(),
                    10000,
                    jobRepoMock,
                    lockRepoMock,
                    10
            )
            coEvery { lockRepoMock.exists(kjobId) } returns true
            coEvery { lockRepoMock.exists(otherKjobId) } returns false
            coEvery { lockRepoMock.exists(otherKjobId2) } returns true
            coEvery { jobRepoMock.findNext(emptySet(), setOf(SCHEDULED, RUNNING, ERROR), 10) } returns all.asFlow()
            coEvery { jobRepoMock.reset(je2.id, je2.kjobId) } returns true
            coEvery { jobRepoMock.reset(je5.id, je5.kjobId) } returns true

            testee.start()
            coVerify(timeout = 50) { jobRepoMock.findNext(emptySet(), setOf(SCHEDULED, RUNNING, ERROR), 10) }
            coVerify(timeout = 50) { lockRepoMock.exists(kjobId) }
            coVerify(timeout = 50) { lockRepoMock.exists(otherKjobId) }
            coVerify(timeout = 50) { jobRepoMock.reset(je2.id, je2.kjobId) }
            coVerify(timeout = 50) { jobRepoMock.reset(je5.id, je5.kjobId) }

            testee.shutdown()
        }
    }
}