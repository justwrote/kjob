package it.justwrote.kjob.internal.scheduler

import io.kotest.core.spec.style.ShouldSpec
import io.mockk.*
import it.justwrote.kjob.Job
import it.justwrote.kjob.internal.JobExecutor
import it.justwrote.kjob.internal.JobRegister
import it.justwrote.kjob.internal.RunnableJob
import it.justwrote.kjob.job.JobExecutionType
import it.justwrote.kjob.job.JobSettings
import it.justwrote.kjob.job.JobStatus.CREATED
import it.justwrote.kjob.job.JobStatus.SCHEDULED
import it.justwrote.kjob.job.ScheduledJob
import it.justwrote.kjob.repository.JobRepository
import kotlinx.coroutines.Dispatchers
import java.util.*

class JobServiceSpec : ShouldSpec() {

    init {
        val job1 = object : Job("test-job-1") {}


        should("do nothing if called without job names") {
            val jobRepoMock = mockk<JobRepository>()
            val jobRegisterMock = mockk<JobRegister>()
            val jobExecutorMock = mockk<JobExecutor>()
            val kjobId = UUID.randomUUID()

            every { jobRegisterMock.jobs(JobExecutionType.BLOCKING) } returns emptySet()
            every { jobExecutorMock.canExecute(JobExecutionType.BLOCKING) } returns true
            every { jobExecutorMock.canExecute(JobExecutionType.NON_BLOCKING) } returns false

            val testee = JobService(
                    newScheduler(),
                    10000,
                    kjobId,
                    Dispatchers.Unconfined,
                    jobRegisterMock,
                    jobExecutorMock,
                    jobRepoMock
            )

            testee.start()

            verify(timeout = 50) { jobExecutorMock.canExecute(JobExecutionType.BLOCKING) }
            verify(timeout = 50) { jobExecutorMock.canExecute(JobExecutionType.NON_BLOCKING) }
            verify(timeout = 50) { jobRegisterMock.jobs(JobExecutionType.BLOCKING) }
            coVerify { jobRepoMock wasNot Called }
        }

        should("do nothing if no jobs for given names could be found") {
            val jobRepoMock = mockk<JobRepository>()
            val jobRegisterMock = mockk<JobRegister>()
            val jobExecutorMock = mockk<JobExecutor>()
            val kjobId = UUID.randomUUID()

            every { jobRegisterMock.jobs(JobExecutionType.BLOCKING) } returns setOf(job1.name)
            every { jobExecutorMock.canExecute(JobExecutionType.BLOCKING) } returns true
            every { jobExecutorMock.canExecute(JobExecutionType.NON_BLOCKING) } returns false
            coEvery { jobRepoMock.findNextOne(setOf("test-job-1"), setOf(CREATED)) } returns null

            val testee = JobService(
                    newScheduler(),
                    10000,
                    kjobId,
                    Dispatchers.Unconfined,
                    jobRegisterMock,
                    jobExecutorMock,
                    jobRepoMock
            )

            testee.start()

            verify(timeout = 50) { jobExecutorMock.canExecute(JobExecutionType.BLOCKING) }
            verify(timeout = 50) { jobExecutorMock.canExecute(JobExecutionType.NON_BLOCKING) }
            verify(timeout = 50) { jobRegisterMock.jobs(JobExecutionType.BLOCKING) }
            coVerify(timeout = 50) { jobRepoMock.findNextOne(setOf("test-job-1"), setOf(CREATED)) }
        }

        should("find next job and start execution") {
            val jobRepoMock = mockk<JobRepository>()
            val jobRegisterMock = mockk<JobRegister>()
            val jobExecutorMock = mockk<JobExecutor>()
            val scheduledJobMock = mockk<ScheduledJob>()
            val runnableJobMock = mockk<RunnableJob>()
            val kjobId = UUID.randomUUID()

            val testee = JobService(
                    newScheduler(),
                    10000,
                    kjobId,
                    Dispatchers.Unconfined,
                    jobRegisterMock,
                    jobExecutorMock,
                    jobRepoMock
            )
            every { scheduledJobMock.id } returns "random-id"
            every { scheduledJobMock.settings } returns js(id = "my-id", name = job1.name)
            every { scheduledJobMock.retries } returns 0
            coEvery { jobRepoMock.findNextOne(setOf(job1.name), setOf(CREATED)) } returns scheduledJobMock
            coEvery { jobRepoMock.update("random-id", null, kjobId, SCHEDULED, null, 0) } returns true
            every { jobExecutorMock.canExecute(JobExecutionType.BLOCKING) } returns true
            every { jobExecutorMock.canExecute(JobExecutionType.NON_BLOCKING) } returns false
            every { jobExecutorMock.execute(runnableJobMock, scheduledJobMock, jobRepoMock) } just Runs
            every { jobRegisterMock.jobs(JobExecutionType.BLOCKING) } returns setOf(job1.name)
            every { jobRegisterMock.get(job1.name) } returns runnableJobMock

            testee.start()

            coVerify(timeout = 50) { jobRepoMock.findNextOne(setOf(job1.name), setOf(CREATED)) }
            verify(timeout = 50, inverse = true) { jobRegisterMock.jobs(JobExecutionType.NON_BLOCKING) }


            verify(timeout = 50) { scheduledJobMock.id }
            verify(timeout = 50) { scheduledJobMock.retries }
            verify(timeout = 50) { scheduledJobMock.settings }
            verify(timeout = 50) { jobExecutorMock.canExecute(JobExecutionType.BLOCKING) }
            verify(timeout = 50) { jobExecutorMock.canExecute(JobExecutionType.NON_BLOCKING) }
            verify(timeout = 50) { jobExecutorMock.execute(runnableJobMock, scheduledJobMock, jobRepoMock) }
            verify(timeout = 50) { jobRegisterMock.jobs(JobExecutionType.BLOCKING) }
            verify(timeout = 50) { jobRegisterMock.get(job1.name) }
        }

        should("find next job and do not start execution since another service has done it already")
        {
            val jobRepoMock = mockk<JobRepository>()
            val jobRegisterMock = mockk<JobRegister>()
            val jobExecutorMock = mockk<JobExecutor>()
            val scheduledJobMock = mockk<ScheduledJob>()
            val runnableJobMock = mockk<RunnableJob>()
            val kjobId = UUID.randomUUID()

            val testee = JobService(
                    newScheduler(),
                    10000,
                    kjobId,
                    Dispatchers.Unconfined,
                    jobRegisterMock,
                    jobExecutorMock,
                    jobRepoMock
            )
            every { scheduledJobMock.id } returns "random-id"
            every { scheduledJobMock.settings } returns JobSettings("my-id", job1.name, emptyMap())
            every { scheduledJobMock.retries } returns 0
            coEvery { jobRepoMock.findNextOne(setOf(job1.name), setOf(CREATED)) } returns scheduledJobMock
            coEvery { jobRepoMock.update("random-id", null, kjobId, SCHEDULED, null, 0) } returns false
            every { jobExecutorMock.canExecute(JobExecutionType.BLOCKING) } returns false
            every { jobExecutorMock.canExecute(JobExecutionType.NON_BLOCKING) } returns true
            every { jobRegisterMock.jobs(JobExecutionType.NON_BLOCKING) } returns setOf(job1.name)
            every { jobRegisterMock.get(job1.name) } returns runnableJobMock

            testee.start()

            coVerify(timeout = 50) { jobRepoMock.findNextOne(setOf(job1.name), setOf(CREATED)) }

            verify(timeout = 25, inverse = true) { jobExecutorMock.execute(runnableJobMock, scheduledJobMock, jobRepoMock) }
            verify(timeout = 25, inverse = true) { jobRegisterMock.jobs(JobExecutionType.BLOCKING) }


            verify(timeout = 50) { scheduledJobMock.id }
            verify(timeout = 50) { scheduledJobMock.retries }
            verify(timeout = 50) { scheduledJobMock.settings }
            verify(timeout = 50) { jobExecutorMock.canExecute(JobExecutionType.BLOCKING) }
            verify(timeout = 50) { jobExecutorMock.canExecute(JobExecutionType.NON_BLOCKING) }
            verify(timeout = 50) { jobRegisterMock.jobs(JobExecutionType.NON_BLOCKING) }
            verify(timeout = 50) { jobRegisterMock.get(job1.name) }
        }
    }
}