package it.justwrote.kjob.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import it.justwrote.kjob.Job
import it.justwrote.kjob.dsl.JobContext
import it.justwrote.kjob.job.JobExecutionType
import it.justwrote.kjob.job.JobProps
import it.justwrote.kjob.job.JobStatus
import it.justwrote.kjob.job.ScheduledJob
import it.justwrote.kjob.repository.JobRepository
import kotlinx.coroutines.Dispatchers
import java.util.*

class DefaultJobExecutorSpec : ShouldSpec() {

    init {
        val dispatcherWrapperMock1 = mockk<DispatcherWrapper>()
        val dispatcherWrapperMock2 = mockk<DispatcherWrapper>()
        every { dispatcherWrapperMock1.shutdown() } just Runs
        every { dispatcherWrapperMock2.shutdown() } just Runs
        every { dispatcherWrapperMock1.coroutineDispatcher } returns Dispatchers.Unconfined
        every { dispatcherWrapperMock2.coroutineDispatcher } returns Dispatchers.Unconfined
        every { dispatcherWrapperMock1.canExecute() } returns true
        every { dispatcherWrapperMock2.canExecute() } returns false

        val kjobId = UUID.randomUUID()
        val dispatchers = mapOf(
                JobExecutionType.BLOCKING to dispatcherWrapperMock1,
                JobExecutionType.NON_BLOCKING to dispatcherWrapperMock2
        )


        should("check if execution for execution type is possible") {
            val testee = DefaultJobExecutor(kjobId, dispatchers, Dispatchers.Unconfined)
            testee.canExecute(JobExecutionType.NON_BLOCKING) shouldBe false
            testee.canExecute(JobExecutionType.BLOCKING) shouldBe true
        }

        should("throw an exception if dispatcher for requested execution type is not defined") {
            val testee = DefaultJobExecutor(kjobId, mapOf(JobExecutionType.BLOCKING to dispatcherWrapperMock1), Dispatchers.Unconfined)
            val runnableJobMock = mockk<RunnableJob>()
            every { runnableJobMock.executionType } returns JobExecutionType.NON_BLOCKING
            shouldThrow<IllegalStateException> {
                testee.execute(runnableJobMock, mockk(), mockk())
            }
        }

        should("throw an exception if dispatcher for requested execution type is not defined #2") {
            val testee = DefaultJobExecutor(kjobId, mapOf(JobExecutionType.BLOCKING to dispatcherWrapperMock1), Dispatchers.Unconfined)
            shouldThrow<IllegalStateException> {
                testee.canExecute(JobExecutionType.NON_BLOCKING)
            }
        }

        should("execute a job successfully") {
            val testee = DefaultJobExecutor(kjobId, dispatchers, Dispatchers.Unconfined)
            val runnableJobMock = mockk<RunnableJob>()
            val sjMock = mockk<ScheduledJob>()
            val jobRepositoryMock = mockk<JobRepository>()
            every { runnableJobMock.executionType } returns JobExecutionType.NON_BLOCKING
            val context = slot<JobContext<Job>>()
            coEvery { runnableJobMock.execute(capture(context)) } answers {
                if (context.isCaptured && context.captured.props == JobProps<Job>(mapOf("test" to 1))) {
                    JobSuccessful
                } else {
                    JobError(RuntimeException("failed"))
                }
            }
            every { sjMock.id } returns "internal-job-id"
            every { sjMock.progress.step } returns 0
            every { sjMock.retries } returns 0
            every { sjMock.settings.id } returns "my-id"
            every { sjMock.settings.name } returns "my-test-1"
            every { sjMock.settings.properties } returns mapOf("test" to 1)
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, null, JobStatus.COMPLETE, null, 1) } returns true

            testee.execute(runnableJobMock, sjMock, jobRepositoryMock)

            coVerify(timeout = 50) { runnableJobMock.execute(any()) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, null, JobStatus.COMPLETE, null, 1) }
            verify { runnableJobMock.executionType }
            verify { sjMock.id }
            verify { sjMock.progress.step }
            verify { sjMock.retries }
            verify { sjMock.settings.id }
            verify { sjMock.settings.name }
            verify { sjMock.settings.properties }

        }

        should("handle an exception while executing a job gracefully") {
            val testee = DefaultJobExecutor(kjobId, dispatchers, Dispatchers.Unconfined)
            val runnableJobMock = mockk<RunnableJob>()
            val sjMock = mockk<ScheduledJob>()
            val jobRepositoryMock = mockk<JobRepository>()
            every { runnableJobMock.executionType } returns JobExecutionType.NON_BLOCKING
            every { runnableJobMock.maxRetries } returns 5
            coEvery { runnableJobMock.execute(any()) } answers { throw RuntimeException("#test") }
            every { sjMock.id } returns "internal-job-id"
            every { sjMock.progress.step } returns 0
            every { sjMock.retries } returns 0
            every { sjMock.settings.id } returns "my-id"
            every { sjMock.settings.name } returns "my-test-1"
            every { sjMock.settings.properties } returns emptyMap()
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, null, JobStatus.ERROR, "#test", 1) } returns true

            testee.execute(runnableJobMock, sjMock, jobRepositoryMock)

            coVerify(timeout = 50) { runnableJobMock.execute(any()) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, null, JobStatus.ERROR, "#test", 1) }
            verify { runnableJobMock.executionType }
            verify { runnableJobMock.maxRetries }
            verify { sjMock.id }
            verify { sjMock.progress.step }
            verify { sjMock.retries }
            verify { sjMock.settings.id }
            verify { sjMock.settings.name }
            verify { sjMock.settings.properties }
        }

        should("handle a JobError after execution") {
            val testee = DefaultJobExecutor(kjobId, dispatchers, Dispatchers.Unconfined)
            val runnableJobMock = mockk<RunnableJob>()
            val sjMock = mockk<ScheduledJob>()
            val jobRepositoryMock = mockk<JobRepository>()
            every { runnableJobMock.executionType } returns JobExecutionType.NON_BLOCKING
            every { runnableJobMock.maxRetries } returns 5
            coEvery { runnableJobMock.execute(any()) } returns JobError(RuntimeException("#test2"))
            every { sjMock.id } returns "internal-job-id"
            every { sjMock.progress.step } returns 0
            every { sjMock.retries } returns 0
            every { sjMock.settings.id } returns "my-id"
            every { sjMock.settings.name } returns "my-test-1"
            every { sjMock.settings.properties } returns emptyMap()
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, null, JobStatus.ERROR, "#test2", 1) } returns true

            testee.execute(runnableJobMock, sjMock, jobRepositoryMock)

            coVerify(timeout = 50) { runnableJobMock.execute(any()) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, null, JobStatus.ERROR, "#test2", 1) }
            verify { runnableJobMock.executionType }
            verify { runnableJobMock.maxRetries }
            verify { sjMock.id }
            verify { sjMock.progress.step }
            verify { sjMock.retries }
            verify { sjMock.settings.id }
            verify { sjMock.settings.name }
            verify { sjMock.settings.properties }
        }

        should("mark a job as failed if too many errors occurred") {
            val testee = DefaultJobExecutor(kjobId, dispatchers, Dispatchers.Unconfined)
            val runnableJobMock = mockk<RunnableJob>()
            val sjMock = mockk<ScheduledJob>()
            val jobRepositoryMock = mockk<JobRepository>()
            every { runnableJobMock.executionType } returns JobExecutionType.NON_BLOCKING
            every { runnableJobMock.maxRetries } returns 2
            coEvery { runnableJobMock.execute(any()) } returns JobError(RuntimeException("#test3"))
            every { sjMock.id } returns "internal-job-id"
            every { sjMock.progress.step } returns 0
            every { sjMock.retries } returns 2
            every { sjMock.settings.id } returns "my-id"
            every { sjMock.settings.name } returns "my-test-1"
            every { sjMock.settings.properties } returns emptyMap()
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, null, JobStatus.FAILED, "#test3", 3) } returns true

            testee.execute(runnableJobMock, sjMock, jobRepositoryMock)

            coVerify(timeout = 50) { runnableJobMock.execute(any()) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, null, JobStatus.FAILED, "#test3", 3) }
            verify { runnableJobMock.executionType }
            verify { runnableJobMock.maxRetries }
            verify { sjMock.id }
            verify { sjMock.progress.step }
            verify { sjMock.retries }
            verify { sjMock.settings.id }
            verify { sjMock.settings.name }
            verify { sjMock.settings.properties }
        }
    }

}