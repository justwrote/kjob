package it.justwrote.kjob

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.provided.ProjectConfig
import it.justwrote.kjob.utils.waitSomeTime
import java.util.concurrent.CountDownLatch
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

@ExperimentalTime
class MongoKJobSpec : ShouldSpec() {
    private val mongoClient = ProjectConfig.newMongoClient()

    object TestJob : Job("test-job")

    private fun newTestee(config: MongoKJob.Configuration): MongoKJob {
        val kjob = MongoKJob(config)
        afterSpec {
            kjob.shutdown()
        }
        return kjob
    }

    init {
        should("create a fully working mongo kjob instance") {
            val config = MongoKJob.Configuration().apply {
                client = mongoClient
            }
            val testee = newTestee(config)
            testee.start()
            val latch = CountDownLatch(2)
            testee.register(TestJob) {
                execute {
                    latch.countDown()
                }
            }

            testee.schedule(TestJob)
            testee.schedule(TestJob, 200.milliseconds)

            latch.waitSomeTime(1100) shouldBe true
        }

        should("fail to create instance if configuration is invalid") {
            val config = MongoKJob.Configuration().apply {
                client = mongoClient
                keepAliveExecutionPeriodInSeconds = 600 // 10 minutes
                expireLockInMinutes = 5 // 5 minutes
            }
            shouldThrow<IllegalStateException> {
                MongoKJob(config)
            }
        }
    }
}