package it.justwrote.kjob

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import it.justwrote.kjob.repository.JobRepository
import it.justwrote.kjob.repository.LockRepository
import it.justwrote.kjob.repository.mongo.MongoJobRepository
import it.justwrote.kjob.repository.mongo.MongoLockRepository
import kotlinx.coroutines.runBlocking
import org.bson.UuidRepresentation
import java.time.Clock

class MongoKJob(config: Configuration) : BaseKJob<MongoKJob.Configuration>(config) {

    init {
        if (config.expireLockInMinutes * 60 <= config.keepAliveExecutionPeriodInSeconds)
            error("The lock expires before a new 'keep alive' has been scheduled. That will not work.")
    }

    class Configuration : BaseKJob.Configuration() {
        /**
         * The mongoDB specific connection string
         */
        var connectionString: String = "mongodb://localhost"

        /**
         * If a client is specified the 'connectionString' will be ignored.
         * Useful if you already have a shared client.
         */
        var client: MongoClient? = null

        /**
         * The database where the kjob related collections will be created
         */
        var databaseName = "kjob"

        /**
         * The collection for all jobs
         */
        var jobCollection = "kjob-jobs"

        /**
         * The collection for kjob locks
         */
        var lockCollection = "kjob-locks"

        /**
         * Using the TTL feature of mongoDB to expire a lock (which means that after
         * this time a kjob instance is considered dead if no 'I am alive' notification occurred)
         */
        var expireLockInMinutes = 5L
    }

    private val client = config.client ?: buildClient()

    private fun buildClient(): MongoClient =
            MongoClients.create(MongoClientSettings
                    .builder()
                    .applyConnectionString(ConnectionString(config.connectionString))
                    .uuidRepresentation(UuidRepresentation.STANDARD)
                    .build())


    override val jobRepository: JobRepository = MongoJobRepository(
            client,
            config,
            Clock.systemUTC()
    )

    override val lockRepository: LockRepository = MongoLockRepository(
            client,
            config,
            Clock.systemUTC()
    )

    override fun start(): KJob = runBlocking {
        (jobRepository as MongoJobRepository).ensureIndexes()
        (lockRepository as MongoLockRepository).ensureIndexes()
        super.start()
    }
}