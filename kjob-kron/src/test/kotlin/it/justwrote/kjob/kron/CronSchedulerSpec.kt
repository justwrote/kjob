package it.justwrote.kjob.kron

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import it.justwrote.kjob.KronJob
import it.justwrote.kjob.internal.JobScheduler
import it.justwrote.kjob.internal.scheduler.newScheduler
import it.justwrote.kjob.job.JobSettings
import it.justwrote.kjob.utils.MutableClock
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ScheduledExecutorService

class CronSchedulerSpec : ShouldSpec() {
    companion object {
        private val cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
        private val cronParser = CronParser(cronDefinition)
    }

    override fun isolationMode(): IsolationMode? {
        return IsolationMode.InstancePerTest
    }

    private fun newTestee(executorService: ScheduledExecutorService,
                          jobScheduler: JobScheduler,
                          period: Long, clock: Clock = Clock.systemUTC()) = CronScheduler(executorService, jobScheduler, clock, period)

    object EverySecond : KronJob("every-second", "* * * ? * * *") {
        private val cron = cronParser.parse(cronExpression)
        val executionTime: ExecutionTime = ExecutionTime.forCron(cron)
    }

    object EveryMinute : KronJob("every-minute", "0 * * ? * * *") {
        private val cron = cronParser.parse(cronExpression)
        val executionTime: ExecutionTime = ExecutionTime.forCron(cron)
    }

    object Invalid : KronJob("invalid", "2 2 2 1 JAN ? 2020") {
        private val cron = cronParser.parse(cronExpression)
        val executionTime: ExecutionTime = ExecutionTime.forCron(cron)
    }

    init {

        val now = Instant.parse("2020-11-01T10:00:00.222222Z")
        fun newClock() = MutableClock(Clock.fixed(now, ZoneId.systemDefault()))

        should("schedule the 'EverySecond' job every second once") {
            val clock = newClock()
            val jobSchedulerMock = mockk<JobScheduler>()
            val testee = newTestee(newScheduler(), jobSchedulerMock, 10, clock)
            testee.add(EverySecond, EverySecond.executionTime)

            val nextSchedule1 = Instant.parse("2020-11-01T10:00:02Z")
            val nextSchedule2 = Instant.parse("2020-11-01T10:00:03Z")
            val nextSchedule3 = Instant.parse("2020-11-01T10:00:04Z")
            val jobSettings1 = JobSettings("every-second_2020-11-01T10:00:02Z", EverySecond.name, emptyMap())
            val jobSettings2 = JobSettings("every-second_2020-11-01T10:00:03Z", EverySecond.name, emptyMap())
            val jobSettings3 = JobSettings("every-second_2020-11-01T10:00:04Z", EverySecond.name, emptyMap())
            coEvery { jobSchedulerMock.schedule(jobSettings1, nextSchedule1) } returns mockk()
            coEvery { jobSchedulerMock.schedule(jobSettings2, nextSchedule2) } returns mockk()
            coEvery { jobSchedulerMock.schedule(jobSettings3, nextSchedule3) } returns mockk()

            testee.start()

            coVerify(timeout = 50, exactly = 1) { jobSchedulerMock.schedule(jobSettings1, nextSchedule1) }

            delay(20)
            clock.update(now.plusSeconds(1))

            coVerify(timeout = 50, exactly = 1) { jobSchedulerMock.schedule(jobSettings2, nextSchedule2) }

            delay(20)
            clock.update(now.plusSeconds(2))

            coVerify(timeout = 50, exactly = 1) { jobSchedulerMock.schedule(jobSettings3, nextSchedule3) }
        }

        should("schedule the 'EveryMinute' 10 seconds before execution") {
            val clock = newClock()
            val jobSchedulerMock = mockk<JobScheduler>()
            val testee = newTestee(newScheduler(), jobSchedulerMock, 10, clock)

            testee.add(EveryMinute, EveryMinute.executionTime)

            val nextSchedule1 = Instant.parse("2020-11-01T10:01:00Z")
            val nextSchedule2 = Instant.parse("2020-11-01T10:02:00Z")
            val jobSettings1 = JobSettings("every-minute_2020-11-01T10:01:00Z", EveryMinute.name, emptyMap())
            val jobSettings2 = JobSettings("every-minute_2020-11-01T10:02:00Z", EveryMinute.name, emptyMap())


            clock.update(now.plusSeconds(40))
            testee.start()

            coVerify(timeout = 50, exactly = 0) { jobSchedulerMock.schedule(any(), any()) }

            coEvery { jobSchedulerMock.schedule(jobSettings1, nextSchedule1) } returns mockk()
            coEvery { jobSchedulerMock.schedule(jobSettings2, nextSchedule2) } returns mockk()

            delay(20)
            clock.update(now.plusSeconds(50))

            coVerify(timeout = 50, exactly = 1) { jobSchedulerMock.schedule(jobSettings1, nextSchedule1) }

            delay(20)
            clock.update(now.plusSeconds(115))

            coVerify(timeout = 50, exactly = 1) { jobSchedulerMock.schedule(jobSettings2, nextSchedule2) }
        }

        should("throw an exception if the same job will be added") {
            val jobSchedulerMock = mockk<JobScheduler>()
            val testee = newTestee(newScheduler(), jobSchedulerMock, 10)
            testee.add(EverySecond, EverySecond.executionTime)
            shouldThrow<IllegalStateException> {
                testee.add(EverySecond, EverySecond.executionTime)
            }
        }

        should("not schedule invalid cron expression") {
            val jobSchedulerMock = mockk<JobScheduler>()
            val testee = newTestee(newScheduler(), jobSchedulerMock, 10)

            testee.add(Invalid, Invalid.executionTime)

            testee.start()

            coVerify(timeout = 50, exactly = 0) { jobSchedulerMock.schedule(any(), any()) }
        }
    }
}