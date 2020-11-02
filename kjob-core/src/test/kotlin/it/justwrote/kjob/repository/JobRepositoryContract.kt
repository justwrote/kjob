package it.justwrote.kjob.repository

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import it.justwrote.kjob.internal.scheduler.jp
import it.justwrote.kjob.internal.scheduler.js
import it.justwrote.kjob.internal.scheduler.sj
import it.justwrote.kjob.job.JobStatus.*
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
            val r = testee.save(settings, null)
            r shouldBe sj(id = r.id, createdAt = now, updatedAt = now, settings = settings)
        }

        should("return true if job exists") {
            val settings = js()
            val r = testee.save(settings, null)
            r.settings shouldBe settings
            testee.exist(settings.id) shouldBe true
        }

        should("return existing job") {
            val settings = js()
            val r = testee.save(settings, null)
            r.settings shouldBe settings
            testee.get(r.id) shouldBe r
        }

        should("update job") {
            val settings = js()
            val scheduledJob = testee.save(settings, null)
            val kjobId = id()
            val kjobId2 = id()

            testee.update(scheduledJob.id, null, kjobId, RUNNING, null, 1) shouldBe true
            val r = testee.get(scheduledJob.id)
            r.shouldNotBeNull()
            r.kjobId shouldBe kjobId
            r.status shouldBe RUNNING
            r.retries shouldBe 1
            r.statusMessage.shouldBeNull()

            testee.update(scheduledJob.id, null, id(), ERROR, null, 10) shouldBe false
            val r2 = testee.get(scheduledJob.id)
            r2.shouldNotBeNull()
            r.kjobId shouldBe kjobId
            r2.status shouldBe RUNNING
            r2.retries shouldBe 1
            r2.statusMessage.shouldBeNull()

            testee.update(scheduledJob.id, kjobId, null, ERROR, "message", 10) shouldBe true
            val r3 = testee.get(scheduledJob.id)
            r3.shouldNotBeNull()
            r3.kjobId.shouldBeNull()
            r3.status shouldBe ERROR
            r3.retries shouldBe 10
            r3.statusMessage shouldBe "message"


            val now = clock.update(now())
            testee.update(scheduledJob.id, kjobId, kjobId2, FAILED, "test", 100) shouldBe false
            testee.update(scheduledJob.id, null, kjobId2, CREATED, null, 0) shouldBe true
            val r4 = testee.get(scheduledJob.id)
            r4.shouldNotBeNull()
            r4.kjobId shouldBe kjobId2
            r4.status shouldBe CREATED
            r4.retries shouldBe 0
            r4.statusMessage.shouldBeNull()
            r4.updatedAt shouldBe now
        }

        should("reset a job") {
            val settings = js()
            val scheduledJob = testee.save(settings, null)
            val kjobId = id()

            testee.startProgress(scheduledJob.id) shouldBe true
            testee.setProgressMax(scheduledJob.id, 10) shouldBe true
            testee.stepProgress(scheduledJob.id, 1) shouldBe true
            testee.update(scheduledJob.id, null, kjobId, ERROR, "message", 10) shouldBe true

            val now = clock.update(now())
            testee.reset(scheduledJob.id, null) shouldBe false
            testee.reset(scheduledJob.id, kjobId) shouldBe true

            val r = testee.get(scheduledJob.id)
            r.shouldNotBeNull()
            r.kjobId.shouldBeNull()
            r.status shouldBe CREATED
            r.retries shouldBe 10
            r.statusMessage.shouldBeNull()
            r.updatedAt shouldBe now
            r.progress shouldBe jp()
        }

        should("update start time when starting progress") {
            val settings = js()
            val scheduledJob = testee.save(settings, null)

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
            val scheduledJob = testee.save(settings, null)

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
            val scheduledJob = testee.save(settings, null)

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
                testee.save(settings, null)
            }
            repeat(2) {
                clock.update(now())
                val settings = js()
                val scheduledJob = testee.save(settings, null)
                testee.update(scheduledJob.id, null, kjobId, RUNNING, null, 0)
            }
            repeat(3) {
                clock.update(now())
                val settings = js()
                val scheduledJob = testee.save(settings, null)
                testee.update(scheduledJob.id, null, null, ERROR, null, 2)
            }
            repeat(1) {
                clock.update(now())
                val settings = js()
                val scheduledJob = testee.save(settings, null)
                testee.update(scheduledJob.id, null, null, FAILED, null, 10)
            }
            repeat(1) {
                clock.update(now())
                val settings = js(name = "job-name-2")
                val scheduledJob = testee.save(settings, null)
                testee.update(scheduledJob.id, null, kjobId, RUNNING, null, 0)
            }
            repeat(3) {
                clock.update(now())
                val settings = js(name = "job-name-2")
                testee.save(settings, null)
            }

            testee.findNext(emptySet(), setOf(CREATED), 100).count() shouldBe 8
            testee.findNext(emptySet(), setOf(CREATED), 5).count() shouldBe 5
            testee.findNext(emptySet(), setOf(CREATED), 100).first() shouldBe testee.findNextOne(emptySet(), setOf(CREATED))

            testee.findNext(setOf("test-job"), setOf(RUNNING, ERROR), 100)
                    .filter { it.settings.name == "test-job" && (it.status == RUNNING || it.status == ERROR) }
                    .count() shouldBe 5

            testee.findNext(setOf("test-job", "job-name-2"), setOf(RUNNING), 100)
                    .filter { it.status == RUNNING }
                    .count() shouldBe 3

            testee.findNext(setOf("test-job", "job-name-2"), setOf(FAILED), 100)
                    .filter { it.status == FAILED }
                    .count() shouldBe 1
        }
    }

}