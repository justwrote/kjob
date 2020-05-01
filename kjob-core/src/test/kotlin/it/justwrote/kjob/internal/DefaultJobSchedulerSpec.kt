package it.justwrote.kjob.internal

import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import it.justwrote.kjob.Job
import it.justwrote.kjob.internal.scheduler.sj
import it.justwrote.kjob.job.JobSettings
import it.justwrote.kjob.repository.JobRepository
import java.util.*

class DefaultJobSchedulerSpec : ShouldSpec() {

    object TestJob : Job("test-job") {
        val prop1 = integer("int-test")
        val prop2 = string("string-test1").nullable()
        val prop3 = string("string-test2")
    }

    init {
        val jobRepositoryMock = mockk<JobRepository>()

        should("return a ScheduledJob if scheduling was successfully") {
            val slot = slot<JobSettings>()
            coEvery { jobRepositoryMock.exist(any()) } returns false
            coEvery { jobRepositoryMock.save(capture(slot)) } answers { sj(settings = slot.captured) }
            val testee = DefaultJobScheduler(jobRepositoryMock)
            val result = testee.schedule(TestJob) {
                UUID.fromString(jobId) // test if default id is a uuid
                jobId = "test-123-id"
                props[it.prop1] = 22
                props[it.prop2] = null
                props[it.prop3] = "test"
            }
            result.shouldNotBeNull()
            result.settings.id shouldBe "test-123-id"
            result.settings.properties shouldContainExactly mapOf("int-test" to 22, "string-test2" to "test")
            result.settings.name shouldBe "test-job"
        }

        should("throw an exception if a job with the same id already exist") {
            coEvery { jobRepositoryMock.exist(any()) } returns true
            val testee = DefaultJobScheduler(jobRepositoryMock)

            shouldThrowMessage("Job 'test-job' with id 'test' has already been scheduled.") {
                testee.schedule(TestJob) {
                    jobId = "test"
                    // nothing
                }
            }
        }
    }
}