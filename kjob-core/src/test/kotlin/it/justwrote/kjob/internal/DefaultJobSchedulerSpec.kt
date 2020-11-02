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

class DefaultJobSchedulerSpec : ShouldSpec() {

    init {
        val jobRepositoryMock = mockk<JobRepository>()

        should("return a ScheduledJob if scheduling was successfully") {
            val slot = slot<JobSettings>()
            coEvery { jobRepositoryMock.exist(any()) } returns false
            coEvery { jobRepositoryMock.save(capture(slot), null) } answers { sj(settings = slot.captured) }
            val testee = DefaultJobScheduler(jobRepositoryMock)
            val result = testee.schedule(JobSettings("id-123", "test-job", mapOf("int-test" to 3, "string-test2" to "test")))
            result.shouldNotBeNull()
            result.settings.id shouldBe "id-123"
            result.settings.properties shouldContainExactly mapOf("int-test" to 3, "string-test2" to "test")
            result.settings.name shouldBe "test-job"
        }

        should("throw an exception if a job with the same id already exist") {
            coEvery { jobRepositoryMock.exist(any()) } returns true
            val testee = DefaultJobScheduler(jobRepositoryMock)

            shouldThrowMessage("Job 'test-job' with id 'test' has already been scheduled.") {
                testee.schedule(JobSettings("test", "test-job", mapOf()))
            }
        }
    }
}