package it.justwrote.kjob.internal.scheduler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import it.justwrote.kjob.utils.waitSomeTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService

class SimplePeriodSchedulerSpec : ShouldSpec() {

    class Flaky(var successLatch: CountDownLatch = CountDownLatch(0), var errorLatch: CountDownLatch = CountDownLatch(0), var isFlaky: Boolean = false) {
        fun run(): Unit {
            Thread.sleep(10)
            if (isFlaky) {
                errorLatch.countDown()
                error("Flaky is bad!")
            } else {
                successLatch.countDown()
            }
        }

        fun good(): Unit {
            isFlaky = false
        }

        fun bad(): Unit {
            isFlaky = true
        }
    }

    private fun newTestee(flaky: Flaky, scheduler: ScheduledExecutorService) = object : SimplePeriodScheduler(scheduler, 30) {
        fun start() = run { flaky.run() }
    }


    init {
        should("fail to start if it has already been shutdown") {
            val testee = newTestee(Flaky(), newScheduler())

            testee.shutdown()

            shouldThrow<IllegalStateException> {
                testee.start()
            }
        }

        should("restart task if error occurs") {
            val flaky = Flaky(CountDownLatch(1), CountDownLatch(1))
            val testee = newTestee(flaky, newScheduler())

            flaky.bad()
            testee.start()
            flaky.errorLatch.waitSomeTime() shouldBe true
            flaky.good()
            flaky.successLatch.waitSomeTime() shouldBe true
        }

        should("not execute further jobs after shutdown") {
            val flaky = Flaky(CountDownLatch(5))
            val testee = newTestee(flaky, newScheduler())

            testee.start()
            Thread.sleep(50)
            testee.shutdown()
            flaky.successLatch = CountDownLatch(1)
            flaky.successLatch.waitSomeTime(50) shouldBe false
        }
    }
}