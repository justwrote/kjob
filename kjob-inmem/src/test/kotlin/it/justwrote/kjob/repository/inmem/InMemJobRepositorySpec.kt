package it.justwrote.kjob.repository.inmem

import io.kotest.assertions.throwables.shouldThrow
import it.justwrote.kjob.internal.scheduler.js
import it.justwrote.kjob.repository.JobRepository
import it.justwrote.kjob.repository.JobRepositoryContract
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.*

@ExperimentalCoroutinesApi
class InMemJobRepositorySpec : JobRepositoryContract() {

    override val testee: JobRepository = InMemJobRepository(clock)

    override fun randomJobId(): String = UUID.randomUUID().toString()

    private val inmemTestee = testee as InMemJobRepository

    override suspend fun deleteAll() {
        inmemTestee.deleteAll()
    }

    init {
        should("only allow unique job ids") {
            val job = js()

            testee.save(job, null)
            shouldThrow<IllegalArgumentException> {
                testee.save(job, null)
            }
        }

    }

}