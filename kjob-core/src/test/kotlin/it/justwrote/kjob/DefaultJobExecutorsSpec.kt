package it.justwrote.kjob

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import it.justwrote.kjob.utils.waitSomeTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch

class DefaultJobExecutorsSpec : ShouldSpec() {

    val config = KJob.Configuration().apply {
        blockingMaxJobs = 1
        nonBlockingMaxJobs = 1
    }

    private fun newTestee(): JobExecutors = autoClose(object : AutoCloseable, JobExecutors by DefaultJobExecutors(config) {
        override fun close() {
            shutdown()
        }
    })

    init {
        should("execute coroutine when resources are available again") {
            forAll(*newTestee().dispatchers.map { row(it.value) }.toTypedArray()) { dispatcher ->
                dispatcher.shouldNotBeNull()
                val latch1 = CountDownLatch(1)
                val latch2 = CountDownLatch(2)
                dispatcher.canExecute() shouldBe true
                CoroutineScope(dispatcher.coroutineDispatcher).launch {
                    latch1.await()
                }
                dispatcher.canExecute() shouldBe false
                CoroutineScope(dispatcher.coroutineDispatcher).launch {
                    latch2.countDown()
                }
                latch2.waitSomeTime(50) shouldBe false
                latch1.countDown()
                latch2.waitSomeTime()
            }
        }
    }
}
