package it.justwrote.kjob

import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import it.justwrote.kjob.extension.BaseExtension
import it.justwrote.kjob.extension.ExtensionId
import it.justwrote.kjob.extension.ExtensionModule
import it.justwrote.kjob.internal.DefaultJobScheduler
import it.justwrote.kjob.internal.JobScheduler
import it.justwrote.kjob.internal.scheduler.js
import it.justwrote.kjob.internal.scheduler.sj
import it.justwrote.kjob.job.JobStatus.*
import it.justwrote.kjob.job.Lock
import it.justwrote.kjob.repository.JobRepository
import it.justwrote.kjob.repository.LockRepository
import it.justwrote.kjob.utils.MutableClock
import it.justwrote.kjob.utils.waitSomeTime
import kotlinx.coroutines.flow.emptyFlow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@ExperimentalTime
class BaseKJobSpec : ShouldSpec() {

    override fun isolationMode(): IsolationMode? {
        return IsolationMode.InstancePerTest
    }

    object TestJob : Job("test-job") {
        val start = integer("start").nullable()
        val end = integer("end")

        // define all possible types
        val x1 = integer("x1")
        val x2 = double("x2")
        val x3 = long("x3")
        val x4 = bool("x4")
        val x5 = string("x5")

        val y1 = integerList("y1")
        val y2 = doubleList("y2")
        val y3 = longList("y3")
        val y4 = boolList("y4")
        val y5 = stringList("y5")

    }

    val jobRepoMock = mockk<JobRepository>()
    val lockRepoMock = mockk<LockRepository>()
    val jobSchedulerMock = spyk<JobScheduler>(DefaultJobScheduler(jobRepoMock))

    private val config = BaseKJob.Configuration().apply {
    }

    object TestExtension : ExtensionId<TestEx>

    class TestEx(val config: Configuration, private val kjobConfig: BaseKJob.Configuration, private val kjob: BaseKJob<BaseKJob.Configuration>) : BaseExtension(TestExtension) {
        class Configuration : BaseExtension.Configuration() {
            var start = 0
            var stop = 0
        }

        fun test(): String {
            return "Hello Extension"
        }

        override fun start() {
            config.start += 1
        }

        override fun shutdown() {
            config.stop += 1
        }
    }

    object TestModule : ExtensionModule<TestEx, TestEx.Configuration, BaseKJob<BaseKJob.Configuration>, BaseKJob.Configuration> {
        override val id: ExtensionId<TestEx> = TestExtension
        override fun create(configure: TestEx.Configuration.() -> Unit, kjobConfig: BaseKJob.Configuration): (BaseKJob<BaseKJob.Configuration>) -> TestEx {
            return { TestEx(TestEx.Configuration().apply(configure), kjobConfig, it) }
        }
    }


    private fun newTestee(config: BaseKJob.Configuration, clock: Clock = Clock.systemUTC()) = autoClose(object : BaseKJob<BaseKJob.Configuration>(config), AutoCloseable {
        override val jobRepository: JobRepository = jobRepoMock
        override val lockRepository: LockRepository = lockRepoMock
        override val jobScheduler: JobScheduler = jobSchedulerMock
        override val clock: Clock = clock
        override val millis: Long = 10
        override fun close() {
            shutdown()
        }
    })

    init {
        should("execute a new job as expected") {
            val map = mapOf(
                    "end" to 3,
                    "x1" to 1,
                    "x2" to 1.2,
                    "x3" to 123L,
                    "x4" to true,
                    "x5" to "test",
                    "y1" to listOf(1, 2, 3),
                    "y2" to listOf(1.2, 3.4, 5.6),
                    "y3" to listOf(1L, 2L, 3L),
                    "y4" to listOf(true, false, true),
                    "y5" to listOf("a", "b", "c")
            )
            val settings = js("my-test-id", props = map)
            val sj = sj(settings = settings)
            val testee = newTestee(config)
            coEvery { lockRepoMock.ping(testee.id) } returns Lock(testee.id, Instant.now())
            coEvery { jobRepoMock.exist("my-test-id") } returns false
            coEvery { jobRepoMock.findNext(emptySet(), setOf(SCHEDULED, RUNNING, ERROR), 50) } returns emptyFlow()
            coEvery { jobRepoMock.save(settings, null) } returns sj
            coEvery { jobRepoMock.update(sj.id, null, testee.id, SCHEDULED, null, 0) } returns true
            coEvery { jobRepoMock.update(sj.id, testee.id, testee.id, RUNNING, null, 0) } returns true
            coEvery { jobRepoMock.findNextOne(setOf("test-job"), setOf(CREATED)) } returns sj andThen null
            coEvery { jobRepoMock.startProgress(sj.id) } returns true
            coEvery { jobRepoMock.setProgressMax(sj.id, 4) } returns true
            coEvery { jobRepoMock.stepProgress(sj.id) } returns true
            coEvery { jobRepoMock.completeProgress(sj.id) } returns true
            coEvery { jobRepoMock.get(sj.id) } returns sj
            coEvery { jobRepoMock.update(sj.id, testee.id, null, COMPLETE, null, 1) } returns true
            testee.start()
            testee.register(TestJob) {
                execute {
                    props[it.x1] shouldBe 1
                    props[it.x2] shouldBe 1.2
                    props[it.x3] shouldBe 123
                    props[it.x4] shouldBe true
                    props[it.x5] shouldBe "test"

                    props[it.y1] shouldBe listOf(1, 2, 3)
                    props[it.y2] shouldBe listOf(1.2, 3.4, 5.6)
                    props[it.y3] shouldBe listOf(1L, 2L, 3L)
                    props[it.y4] shouldBe listOf(true, false, true)
                    props[it.y5] shouldBe listOf("a", "b", "c")

                    val start = props[it.start] ?: 0
                    val end = props[it.end]
                    setInitialMax(end + 1)
                    for (i in start..end) {
                        step()
                    }
                }
            }
            testee.schedule(TestJob) {
                jobId = "my-test-id"
                props[it.end] = 3

                props[it.x1] = 1
                props[it.x2] = 1.2
                props[it.x3] = 123L
                props[it.x4] = true
                props[it.x5] = "test"

                props[it.y1] = listOf(1, 2, 3)
                props[it.y2] = listOf(1.2, 3.4, 5.6)
                props[it.y3] = listOf(1L, 2L, 3L)
                props[it.y4] = listOf(true, false, true)
                props[it.y5] = listOf("a", "b", "c")
            }
            coVerify(timeout = 200) { jobSchedulerMock.schedule(settings) }
            coVerify(timeout = 200, exactly = 4) { jobRepoMock.stepProgress(sj.id) }
            coVerify(timeout = 200) { jobRepoMock.update(sj.id, testee.id, null, COMPLETE, null, 1) }
        }

        should("execute a delayed job as expected") {
            val now = Instant.parse("2020-11-01T10:00:00.222222Z")
            val clock = MutableClock(Clock.fixed(now, ZoneId.systemDefault()))
            val settings1 = js("my-test-id-1")
            val settings2 = js("my-test-id-2")
            val sj1 = sj(settings = settings1)
            val sj2 = sj(settings = settings2)
            val testee = newTestee(config, clock)
            coEvery { lockRepoMock.ping(testee.id) } returns Lock(testee.id, Instant.now())
            coEvery { jobRepoMock.exist("my-test-id-1") } returns false
            coEvery { jobRepoMock.exist("my-test-id-2") } returns false
            coEvery { jobRepoMock.findNext(emptySet(), setOf(SCHEDULED, RUNNING, ERROR), 50) } returns emptyFlow()
            coEvery { jobRepoMock.save(settings1, now.plusSeconds(60)) } returns sj1
            coEvery { jobRepoMock.save(settings2, now.plusSeconds(25)) } returns sj2
            coEvery { jobRepoMock.update(sj1.id, null, testee.id, SCHEDULED, null, 0) } returns true
            coEvery { jobRepoMock.update(sj2.id, null, testee.id, SCHEDULED, null, 0) } returns true
            coEvery { jobRepoMock.update(sj1.id, testee.id, testee.id, RUNNING, null, 0) } returns true
            coEvery { jobRepoMock.update(sj2.id, testee.id, testee.id, RUNNING, null, 0) } returns true
            coEvery { jobRepoMock.findNextOne(setOf("test-job"), setOf(CREATED)) } returns sj1 andThen sj2 andThen null
            coEvery { jobRepoMock.startProgress(sj1.id) } returns true
            coEvery { jobRepoMock.startProgress(sj2.id) } returns true
            coEvery { jobRepoMock.setProgressMax(sj1.id, 4) } returns true
            coEvery { jobRepoMock.setProgressMax(sj2.id, 4) } returns true
            coEvery { jobRepoMock.stepProgress(sj1.id) } returns true
            coEvery { jobRepoMock.stepProgress(sj2.id) } returns true
            coEvery { jobRepoMock.completeProgress(sj1.id) } returns true
            coEvery { jobRepoMock.completeProgress(sj2.id) } returns true
            coEvery { jobRepoMock.get(sj1.id) } returns sj1
            coEvery { jobRepoMock.get(sj2.id) } returns sj2
            coEvery { jobRepoMock.update(sj1.id, testee.id, null, COMPLETE, null, 1) } returns true
            coEvery { jobRepoMock.update(sj2.id, testee.id, null, COMPLETE, null, 1) } returns true
            testee.start()
            testee.register(TestJob) {
                execute {
                    setInitialMax(4)
                    for (i in 0..3) {
                        step()
                    }
                }
            }
            testee.schedule(TestJob, 1.minutes) {
                jobId = "my-test-id-1"
            }

            testee.schedule(TestJob, Duration.ofSeconds(25)) {
                jobId = "my-test-id-2"
            }

            clock.update(now.plusSeconds(120))

            coVerify(timeout = 200) { jobSchedulerMock.schedule(settings1, now.plusSeconds(60)) }
            coVerify(timeout = 200) { jobSchedulerMock.schedule(settings2, now.plusSeconds(25)) }
            coVerify(timeout = 200, exactly = 4) { jobRepoMock.stepProgress(sj1.id) }
            coVerify(timeout = 200, exactly = 4) { jobRepoMock.stepProgress(sj2.id) }
            coVerify(timeout = 200) { jobRepoMock.update(sj1.id, testee.id, null, COMPLETE, null, 1) }
            coVerify(timeout = 200) { jobRepoMock.update(sj2.id, testee.id, null, COMPLETE, null, 1) }
        }

        should("fail to schedule job if the same id has already been used") {
            val settings = js("my-test-id")
            val sj = sj(settings = settings)
            val testee = newTestee(config)
            coEvery { lockRepoMock.ping(testee.id) } returns Lock(testee.id, Instant.now())
            coEvery { jobRepoMock.exist("my-test-id") } returns true
            coEvery { jobRepoMock.findNext(emptySet(), setOf(RUNNING, ERROR), 50) } returns emptyFlow()
            coEvery { jobRepoMock.findNextOne(setOf("test-job"), setOf(CREATED)) } returns sj andThen null
            testee.start()
            val latch = CountDownLatch(1)
            testee.register(TestJob) {
                execute {
                    latch.countDown()
                }
            }
            shouldThrowMessage("Job 'test-job' with id 'my-test-id' has already been scheduled.") {
                testee.schedule(TestJob) {
                    jobId = "my-test-id"
                }
            }

            latch.waitSomeTime(50) shouldBe false
        }

        should("not allow to start multiple times") {
            val testee = newTestee(config)
            testee.start()
            shouldThrowMessage("kjob has already been started") {
                testee.start()
            }
        }

        should("register a new extension") {
            val testee = newTestee(BaseKJob.Configuration().apply {
                extension(TestModule)
            })

            val result = testee(TestExtension).test()

            result shouldBe "Hello Extension"
        }

        should("start and shutdown an extension") {
            val testee = newTestee(BaseKJob.Configuration().apply {
                extension(TestModule)
            })

            testee(TestExtension).config.start shouldBe 0
            testee(TestExtension).config.stop shouldBe 0

            testee.start()

            testee(TestExtension).config.start shouldBe 1
            testee(TestExtension).config.stop shouldBe 0

            testee.shutdown()

            testee(TestExtension).config.start shouldBe 1
            testee(TestExtension).config.stop shouldBe 1
        }
    }
}