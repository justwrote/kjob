package it.justwrote.kjob.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import it.justwrote.kjob.Job
import it.justwrote.kjob.dsl.JobContextWithProps
import it.justwrote.kjob.job.JobExecutionType
import it.justwrote.kjob.job.JobProps
import it.justwrote.kjob.job.JobStatus.*
import it.justwrote.kjob.job.ScheduledJob
import it.justwrote.kjob.repository.JobRepository
import it.justwrote.kjob.utils.MutableClock
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.*

class DefaultJobExecutorSpec : ShouldSpec() {

    override fun isolationMode(): IsolationMode? {
        return IsolationMode.InstancePerTest
    }

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
            val testee = DefaultJobExecutor(kjobId, dispatchers, Clock.systemUTC(), Dispatchers.Unconfined)
            testee.canExecute(JobExecutionType.NON_BLOCKING) shouldBe false
            testee.canExecute(JobExecutionType.BLOCKING) shouldBe true
        }

        should("throw an exception if dispatcher for requested execution type is not defined") {
            val testee = DefaultJobExecutor(kjobId, mapOf(JobExecutionType.BLOCKING to dispatcherWrapperMock1), Clock.systemUTC(), Dispatchers.Unconfined)
            val runnableJobMock = mockk<RunnableJob>()
            every { runnableJobMock.executionType } returns JobExecutionType.NON_BLOCKING
            shouldThrow<IllegalStateException> {
                testee.execute(runnableJobMock, mockk(), mockk())
            }
        }

        should("throw an exception if dispatcher for requested execution type is not defined #2") {
            val testee = DefaultJobExecutor(kjobId, mapOf(JobExecutionType.BLOCKING to dispatcherWrapperMock1), Clock.systemUTC(), Dispatchers.Unconfined)
            shouldThrow<IllegalStateException> {
                testee.canExecute(JobExecutionType.NON_BLOCKING)
            }
        }

        should("execute a job successfully") {
            val testee = DefaultJobExecutor(kjobId, dispatchers, Clock.systemUTC(), Dispatchers.Unconfined)
            val runnableJobMock = mockk<RunnableJob>()
            val sjMock = mockk<ScheduledJob>()
            val jobRepositoryMock = mockk<JobRepository>()
            every { runnableJobMock.executionType } returns JobExecutionType.NON_BLOCKING
            val context = slot<JobContextWithProps<Job>>()
            coEvery { runnableJobMock.execute(capture(context)) } answers {
                if (context.isCaptured && context.captured.props == JobProps<Job>(mapOf("test" to 1))) {
                    JobSuccessful
                } else {
                    JobError(RuntimeException("failed"))
                }
            }
            every { sjMock.id } returns "internal-job-id"
            every { sjMock.runAt } returns null
            every { sjMock.progress.step } returns 0
            every { sjMock.retries } returns 0
            every { sjMock.settings.id } returns "my-id"
            every { sjMock.settings.name } returns "my-test-1"
            every { sjMock.settings.properties } returns mapOf("test" to 1)
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 0) } returns true
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, null, COMPLETE, null, 1) } returns true

            testee.execute(runnableJobMock, sjMock, jobRepositoryMock)

            coVerify(timeout = 50) { runnableJobMock.execute(any()) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 0) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, null, COMPLETE, null, 1) }
            verify { runnableJobMock.executionType }
            verify { sjMock.id }
            verify { sjMock.runAt }
            verify { sjMock.progress.step }
            verify { sjMock.retries }
            verify { sjMock.settings.id }
            verify { sjMock.settings.name }
            verify { sjMock.settings.properties }
        }

        should("handle an exception while executing a job gracefully") {
            val testee = DefaultJobExecutor(kjobId, dispatchers, Clock.systemUTC(), Dispatchers.Unconfined)
            val runnableJobMock = mockk<RunnableJob>()
            val sjMock = mockk<ScheduledJob>()
            val jobRepositoryMock = mockk<JobRepository>()
            every { runnableJobMock.executionType } returns JobExecutionType.NON_BLOCKING
            every { runnableJobMock.maxRetries } returns 5
            coEvery { runnableJobMock.execute(any()) } answers { throw RuntimeException("#test") }
            every { sjMock.id } returns "internal-job-id"
            every { sjMock.runAt } returns null
            every { sjMock.progress.step } returns 0
            every { sjMock.retries } returns 0
            every { sjMock.settings.id } returns "my-id"
            every { sjMock.settings.name } returns "my-test-1"
            every { sjMock.settings.properties } returns emptyMap()
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 0) } returns true
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, null, ERROR, "#test", 1) } returns true

            testee.execute(runnableJobMock, sjMock, jobRepositoryMock)

            coVerify(timeout = 50) { runnableJobMock.execute(any()) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 0) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, null, ERROR, "#test", 1) }
            verify { runnableJobMock.executionType }
            verify { runnableJobMock.maxRetries }
            verify { sjMock.id }
            verify { sjMock.runAt }
            verify { sjMock.progress.step }
            verify { sjMock.retries }
            verify { sjMock.settings.id }
            verify { sjMock.settings.name }
            verify { sjMock.settings.properties }
        }

        should("handle a JobError after execution") {
            val testee = DefaultJobExecutor(kjobId, dispatchers, Clock.systemUTC(), Dispatchers.Unconfined)
            val runnableJobMock = mockk<RunnableJob>()
            val sjMock = mockk<ScheduledJob>()
            val jobRepositoryMock = mockk<JobRepository>()
            every { runnableJobMock.executionType } returns JobExecutionType.NON_BLOCKING
            every { runnableJobMock.maxRetries } returns 5
            coEvery { runnableJobMock.execute(any()) } returns JobError(RuntimeException("#test2"))
            every { sjMock.id } returns "internal-job-id"
            every { sjMock.runAt } returns null
            every { sjMock.progress.step } returns 0
            every { sjMock.retries } returns 0
            every { sjMock.settings.id } returns "my-id"
            every { sjMock.settings.name } returns "my-test-1"
            every { sjMock.settings.properties } returns emptyMap()
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 0) } returns true
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, null, ERROR, "#test2", 1) } returns true

            testee.execute(runnableJobMock, sjMock, jobRepositoryMock)

            coVerify(timeout = 50) { runnableJobMock.execute(any()) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 0) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, null, ERROR, "#test2", 1) }
            verify { runnableJobMock.executionType }
            verify { runnableJobMock.maxRetries }
            verify { sjMock.id }
            verify { sjMock.runAt }
            verify { sjMock.progress.step }
            verify { sjMock.retries }
            verify { sjMock.settings.id }
            verify { sjMock.settings.name }
            verify { sjMock.settings.properties }
        }

        should("mark a job as failed if too many errors occurred") {
            val testee = DefaultJobExecutor(kjobId, dispatchers, Clock.systemUTC(), Dispatchers.Unconfined)
            val runnableJobMock = mockk<RunnableJob>()
            val sjMock = mockk<ScheduledJob>()
            val jobRepositoryMock = mockk<JobRepository>()
            every { runnableJobMock.executionType } returns JobExecutionType.NON_BLOCKING
            every { runnableJobMock.maxRetries } returns 2
            coEvery { runnableJobMock.execute(any()) } returns JobError(RuntimeException("#test3"))
            every { sjMock.id } returns "internal-job-id"
            every { sjMock.runAt } returns null
            every { sjMock.progress.step } returns 0
            every { sjMock.retries } returns 2
            every { sjMock.settings.id } returns "my-id"
            every { sjMock.settings.name } returns "my-test-1"
            every { sjMock.settings.properties } returns emptyMap()
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 2) } returns true
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, null, FAILED, "#test3", 3) } returns true

            testee.execute(runnableJobMock, sjMock, jobRepositoryMock)

            coVerify(timeout = 50) { runnableJobMock.execute(any()) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 2) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, null, FAILED, "#test3", 3) }
            verify { runnableJobMock.executionType }
            verify { runnableJobMock.maxRetries }
            verify { sjMock.id }
            verify { sjMock.runAt }
            verify { sjMock.progress.step }
            verify { sjMock.retries }
            verify { sjMock.settings.id }
            verify { sjMock.settings.name }
            verify { sjMock.settings.properties }
        }

        should("delay a job if runAt is specified") {
            val now = Instant.parse("2020-11-01T10:00:00.222222Z")
            val clock = MutableClock(Clock.fixed(now, ZoneId.systemDefault()))
            val testee = DefaultJobExecutor(kjobId, dispatchers, clock, Dispatchers.Unconfined)
            val runnableJobMock = mockk<RunnableJob>()
            val sjMock = mockk<ScheduledJob>()
            val jobRepositoryMock = mockk<JobRepository>()
            every { runnableJobMock.executionType } returns JobExecutionType.NON_BLOCKING
            coEvery { runnableJobMock.execute(any()) } returns JobSuccessful
            every { runnableJobMock.maxRetries } returns 2
            every { sjMock.id } returns "internal-job-id"
            every { sjMock.runAt } returns now.plusSeconds(10)
            every { sjMock.progress.step } returns 0
            every { sjMock.retries } returns 0
            every { sjMock.settings.id } returns "my-id"
            every { sjMock.settings.name } returns "my-test-1"
            every { sjMock.settings.properties } returns emptyMap()
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 0) } returns true
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, null, COMPLETE, null, 1) } returns true

            clock.update(now.plusSeconds(10).minusMillis(40))

            testee.execute(runnableJobMock, sjMock, jobRepositoryMock)

            clock.update(now.plusSeconds(10))

            coVerify(timeout = 50) { runnableJobMock.execute(any()) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 0) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, null, COMPLETE, null, 1) }
        }

        should("run a job immediately if runAt is in the past") {
            val now = Instant.parse("2020-11-01T10:00:00.222222Z")
            val clock = MutableClock(Clock.fixed(now, ZoneId.systemDefault()))
            val testee = DefaultJobExecutor(kjobId, dispatchers, clock, Dispatchers.Unconfined)
            val runnableJobMock = mockk<RunnableJob>()
            val sjMock = mockk<ScheduledJob>()
            val jobRepositoryMock = mockk<JobRepository>()
            every { runnableJobMock.executionType } returns JobExecutionType.NON_BLOCKING
            coEvery { runnableJobMock.execute(any()) } returns JobSuccessful
            every { runnableJobMock.maxRetries } returns 2
            every { sjMock.id } returns "internal-job-id"
            every { sjMock.runAt } returns now.minusSeconds(10)
            every { sjMock.progress.step } returns 0
            every { sjMock.retries } returns 0
            every { sjMock.settings.id } returns "my-id"
            every { sjMock.settings.name } returns "my-test-1"
            every { sjMock.settings.properties } returns emptyMap()
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 0) } returns true
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, null, COMPLETE, null, 1) } returns true

            testee.execute(runnableJobMock, sjMock, jobRepositoryMock)

            coVerify(timeout = 50) { runnableJobMock.execute(any()) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 0) }
            coVerify(timeout = 50) { jobRepositoryMock.update("internal-job-id", kjobId, null, COMPLETE, null, 1) }
        }

        should("not run a delayed task if this kjob instance does not own the job anymore") {
            val now = Instant.parse("2020-11-01T10:00:00.222222Z")
            val clock = MutableClock(Clock.fixed(now, ZoneId.systemDefault()))
            val testee = DefaultJobExecutor(kjobId, dispatchers, clock, Dispatchers.Unconfined)
            val runnableJobMock = mockk<RunnableJob>()
            val sjMock = mockk<ScheduledJob>()
            val jobRepositoryMock = mockk<JobRepository>()
            every { runnableJobMock.executionType } returns JobExecutionType.NON_BLOCKING
            coEvery { runnableJobMock.execute(any()) } returns JobSuccessful
            every { runnableJobMock.maxRetries } returns 2
            every { sjMock.id } returns "internal-job-id"
            every { sjMock.runAt } returns now.minusSeconds(10)
            every { sjMock.progress.step } returns 0
            every { sjMock.retries } returns 0
            every { sjMock.settings.id } returns "my-id"
            every { sjMock.settings.name } returns "my-test-1"
            every { sjMock.settings.properties } returns emptyMap()
            coEvery { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 0) } returns false

            testee.execute(runnableJobMock, sjMock, jobRepositoryMock)

            coVerify(timeout = 50, exactly = 0) { runnableJobMock.execute(any()) }
            coVerify(timeout = 50, exactly = 1) { jobRepositoryMock.update("internal-job-id", kjobId, kjobId, RUNNING, null, 0) }
        }
    }

}