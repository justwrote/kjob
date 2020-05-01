package it.justwrote.kjob.repository

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import it.justwrote.kjob.utils.MutableClock
import java.time.Clock
import java.time.ZoneId
import java.util.*

abstract class LockRepositoryContract : ShouldSpec() {
    protected fun id(): UUID = UUID.randomUUID()

    protected var now = now()
    protected val clock = MutableClock(Clock.fixed(now, ZoneId.systemDefault()))

    protected abstract val testee: LockRepository

    protected abstract suspend fun deleteAll()

    init {
        beforeTest {
            now = clock.update(now())
        }

        afterTest {
            deleteAll()
        }

        should("create a new lock on calling 'ping'") {
            val id = id()
            val r = testee.ping(id)
            r.id shouldBe id
            r.updatedAt shouldBe now
        }

        should("update 'updated_at' if lock already exists") {
            val id = id()
            val r1 = testee.ping(id)
            r1.id shouldBe id
            r1.updatedAt shouldBe now

            val now2 = now()
            clock.update(now2)
            val r2 = testee.ping(id)

            r1 shouldNotBe r2
            r1.id shouldBe r2.id
            r2.updatedAt shouldBe now2
        }

        should("return true if lock exists") {
            val id = id()
            testee.ping(id)
            val r = testee.exists(id)
            r shouldBe true
        }
    }

}