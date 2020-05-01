package it.justwrote.kjob

import it.justwrote.kjob.repository.JobRepository
import it.justwrote.kjob.repository.LockRepository
import it.justwrote.kjob.repository.inmem.InMemJobRepository
import it.justwrote.kjob.repository.inmem.InMemLockRepository
import java.time.Clock

class InMemKJob(config: Configuration) : BaseKJob<InMemKJob.Configuration>(config) {

    class Configuration : BaseKJob.Configuration() {
        /**
         * The timeout until a kjob instance is considered dead if no 'I am alive' notification occurred
         */
        var expireLockInMinutes = 5L
    }

    override val jobRepository: JobRepository = InMemJobRepository(Clock.systemUTC())

    override val lockRepository: LockRepository = InMemLockRepository(config, Clock.systemUTC())
}