package it.justwrote.kjob.repository

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import it.justwrote.kjob.internal.scheduler.jp
import it.justwrote.kjob.internal.scheduler.js
import it.justwrote.kjob.internal.scheduler.sj
import it.justwrote.kjob.job.JobStatus
import it.justwrote.kjob.utils.MutableClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.ZoneId
import java.util.*

@ExperimentalCoroutinesApi
abstract class JobRepositoryContract : ShouldSpec() {
    protected fun id(): UUID = UUID.randomUUID()
    protected abstract fun randomJobId(): String

    protected var now = now()
    protected val clock = MutableClock(Clock.fixed(now, ZoneId.systemDefault()))

    protected abstract val testee: JobRepository

    protected abstract suspend fun deleteAll()

    init {
        beforeTest {
            now = clock.update(now())
        }

        afterTest {
            deleteAll()
        }

        should("save a job") {
            val settings = js(props = mapOf("test-double" to 13.2, "test-string" to "test"))
            val r = testee.save(settings)
            r shouldBe sj(id = r.id, createdAt = now, updatedAt = now, settings = settings)
        }

        should("return true if job exists") {
            val settings = js()
            val r = testee.save(settings)
            r.settings shouldBe settings
            testee.exist(settings.id) shouldBe true
        }

        should("return existing job") {
            val settings = js()
            val r = testee.save(settings)
            r.settings shouldBe settings
            testee.get(r.id) shouldBe r
        }

        should("update job") {
            val settings = js()
            val scheduledJob = testee.save(settings)
            val kjobId = id()
            val kjobId2 = id()

            testee.update(scheduledJob.id, null, kjobId, JobStatus.RUNNING, null, 1) shouldBe true
            val r = testee.get(scheduledJob.id)
            r.shouldNotBeNull()
            r.kjobId shouldBe kjobId
            r.status shouldBe JobStatus.RUNNING
            r.retries shouldBe 1
            r.statusMessage.shouldBeNull()

            testee.update(scheduledJob.id, null, id(), JobStatus.ERROR, null, 10) shouldBe false
            val r2 = testee.get(scheduledJob.id)
            r2.shouldNotBeNull()
            r.kjobId shouldBe kjobId
            r2.status shouldBe JobStatus.RUNNING
            r2.retries shouldBe 1
            r2.statusMessage.shouldBeNull()

            testee.update(scheduledJob.id, kjobId, null, JobStatus.ERROR, "message", 10) shouldBe true
            val r3 = testee.get(scheduledJob.id)
            r3.shouldNotBeNull()
            r3.kjobId.shouldBeNull()
            r3.status shouldBe JobStatus.ERROR
            r3.retries shouldBe 10
            r3.statusMessage shouldBe "message"


            val now = clock.update(now())
            testee.update(scheduledJob.id, kjobId, kjobId2, JobStatus.FAILED, "test", 100) shouldBe false
            testee.update(scheduledJob.id, null, kjobId2, JobStatus.CREATED, null, 0) shouldBe true
            val r4 = testee.get(scheduledJob.id)
            r4.shouldNotBeNull()
            r4.kjobId shouldBe kjobId2
            r4.status shouldBe JobStatus.CREATED
            r4.retries shouldBe 0
            r4.statusMessage.shouldBeNull()
            r4.updatedAt shouldBe now
        }

        should("reset a job") {
            val settings = js()
            val scheduledJob = testee.save(settings)
            val kjobId = id()

            testee.startProgress(scheduledJob.id) shouldBe true
            testee.setProgressMax(scheduledJob.id, 10) shouldBe true
            testee.stepProgress(scheduledJob.id, 1) shouldBe true
            testee.update(scheduledJob.id, null, kjobId, JobStatus.ERROR, "message", 10) shouldBe true

            val now = clock.update(now())
            testee.reset(scheduledJob.id, null) shouldBe false
            testee.reset(scheduledJob.id, kjobId) shouldBe true

            val r = testee.get(scheduledJob.id)
            r.shouldNotBeNull()
            r.kjobId.shouldBeNull()
            r.status shouldBe JobStatus.CREATED
            r.retries shouldBe 10
            r.statusMessage.shouldBeNull()
            r.updatedAt shouldBe now
            r.progress shouldBe jp()
        }

        should("update start time when starting progress") {
            val settings = js()
            val scheduledJob = testee.save(settings)

            val now = clock.update(now())
            testee.startProgress(randomJobId()) shouldBe false
            testee.startProgress(scheduledJob.id) shouldBe true

            val r = testee.get(scheduledJob.id)
            r.shouldNotBeNull()
            r.progress.startedAt shouldBe now
            r.updatedAt shouldBe now
        }

        should("update complete time when finishing progress") {
            val settings = js()
            val scheduledJob = testee.save(settings)

            val now = clock.update(now())
            testee.completeProgress(randomJobId()) shouldBe false
            testee.completeProgress(scheduledJob.id) shouldBe true

            val r = testee.get(scheduledJob.id)
            r.shouldNotBeNull()
            r.progress.completedAt shouldBe now
            r.updatedAt shouldBe now
        }

        should("update progress step") {
            val settings = js()
            val scheduledJob = testee.save(settings)

            val now = clock.update(now())
            testee.stepProgress(randomJobId()) shouldBe false
            testee.stepProgress(scheduledJob.id) shouldBe true

            val r = testee.get(scheduledJob.id)
            r.shouldNotBeNull()
            r.progress.step shouldBe 1
            r.updatedAt shouldBe now

            testee.stepProgress(scheduledJob.id, 2) shouldBe true
            testee.stepProgress(scheduledJob.id, 2) shouldBe true

            val r2 = testee.get(scheduledJob.id)
            r2.shouldNotBeNull()
            r2.progress.step shouldBe 5
            r2.updatedAt shouldBe now
        }

        should("find next for varios names and/or status") {
            val kjobId = id()
            repeat(5) {
                clock.update(now())
                val settings = js()
                testee.save(settings)
            }
            repeat(2) {
                clock.update(now())
                val settings = js()
                val scheduledJob = testee.save(settings)
                testee.update(scheduledJob.id, null, kjobId, JobStatus.RUNNING, null, 0)
            }
            repeat(3) {
                clock.update(now())
                val settings = js()
                val scheduledJob = testee.save(settings)
                testee.update(scheduledJob.id, null, null, JobStatus.ERROR, null, 2)
            }
            repeat(1) {
                clock.update(now())
                val settings = js()
                val scheduledJob = testee.save(settings)
                testee.update(scheduledJob.id, null, null, JobStatus.FAILED, null, 10)
            }
            repeat(1) {
                clock.update(now())
                val settings = js(name = "job-name-2")
                val scheduledJob = testee.save(settings)
                testee.update(scheduledJob.id, null, kjobId, JobStatus.RUNNING, null, 0)
            }
            repeat(3) {
                clock.update(now())
                val settings = js(name = "job-name-2")
                testee.save(settings)
            }

            testee.findNext(emptySet(), setOf(JobStatus.CREATED), 100).count() shouldBe 8
            testee.findNext(emptySet(), setOf(JobStatus.CREATED), 5).count() shouldBe 5
            testee.findNext(emptySet(), setOf(JobStatus.CREATED), 100).first() shouldBe testee.findNextOne(emptySet(), setOf(JobStatus.CREATED))

            testee.findNext(setOf("test-job"), setOf(JobStatus.RUNNING, JobStatus.ERROR), 100)
                    .filter { it.settings.name == "test-job" && (it.status == JobStatus.RUNNING || it.status == JobStatus.ERROR) }
                    .count() shouldBe 5

            testee.findNext(setOf("test-job", "job-name-2"), setOf(JobStatus.RUNNING), 100)
                    .filter { it.status == JobStatus.RUNNING }
                    .count() shouldBe 3

            testee.findNext(setOf("test-job", "job-name-2"), setOf(JobStatus.FAILED), 100)
                    .filter { it.status == JobStatus.FAILED }
                    .count() shouldBe 1
        }
    }

}