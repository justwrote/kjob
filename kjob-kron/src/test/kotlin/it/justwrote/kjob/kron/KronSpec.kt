package it.justwrote.kjob.kron

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import it.justwrote.kjob.InMem
import it.justwrote.kjob.KronJob
import it.justwrote.kjob.kjob
import it.justwrote.kjob.utils.waitSomeTime
import java.util.concurrent.CountDownLatch

class KronSpec : ShouldSpec() {

    object EverySecond : KronJob("every-second", "* * * ? * * *")

    init {
        should("create a fully working kjob instance with Kron extension") {
            val kjob = kjob(InMem) {
                extension(KronModule)
            }.start()

            val latch = CountDownLatch(1)
            kjob(Kron).kron(EverySecond) {
                execute {
                    latch.countDown()
                }
            }

            latch.waitSomeTime(3500) shouldBe true
            kjob.shutdown()
        }
    }
}