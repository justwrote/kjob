package it.justwrote.kjob.repository.mongo

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.reactivestreams.client.MongoClient
import it.justwrote.kjob.MongoKJob
import it.justwrote.kjob.job.Lock
import it.justwrote.kjob.repository.LockRepository
import it.justwrote.kjob.repository.mongo.structure.LockStructure
import kotlinx.coroutines.reactive.awaitSingle
import org.bson.Document
import java.time.Clock
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

internal class MongoLockRepository(
        mongoClient: MongoClient,
        internal val conf: MongoKJob.Configuration,
        private val clock: Clock
) : MongoRepository<UUID, Lock>(mongoClient.getDatabase(conf.databaseName).getCollection(conf.lockCollection)), LockRepository {

    constructor(mongoClient: MongoClient, clock: Clock, conf: MongoKJob.Configuration.() -> Unit)
            : this(mongoClient, MongoKJob.Configuration().apply(conf), clock)

    override suspend fun ensureIndexes() {
        val options = IndexOptions().name("updated_at_ttl").background(true).expireAfter(conf.expireLockInMinutes, TimeUnit.MINUTES)
        collection.createIndex(Indexes.ascending(LockStructure.UPDATED_AT.key), options).awaitSingle()
    }

    override fun encode(value: Lock): Document =
            Document()
                    .append(LockStructure.ID.key, value.id)
                    .append(LockStructure.UPDATED_AT.key, value.updatedAt)


    override fun decode(document: Document): Lock =
            Lock(
                    document.get(LockStructure.ID.key, UUID::class.java),
                    document.getDate(LockStructure.UPDATED_AT.key).toInstant())

    override fun keyOf(value: Lock): UUID = value.id

    override suspend fun ping(id: UUID): Lock {
        val now = Instant.now(clock)
        val lock = Lock(id, now)
        return createOrUpdate(lock)
    }

    override suspend fun exists(id: UUID): Boolean {
        return collection.countDocuments(byId(id)).awaitSingle().let { it == 1L }
    }
}