package it.justwrote.kjob.repository.mongo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates
import com.mongodb.reactivestreams.client.MongoClient
import it.justwrote.kjob.MongoKJob
import it.justwrote.kjob.job.JobProgress
import it.justwrote.kjob.job.JobSettings
import it.justwrote.kjob.job.JobStatus
import it.justwrote.kjob.job.ScheduledJob
import it.justwrote.kjob.repository.JobRepository
import it.justwrote.kjob.repository.mongo.structure.JobProgressStructure
import it.justwrote.kjob.repository.mongo.structure.JobSettingsStructure
import it.justwrote.kjob.repository.mongo.structure.ScheduledJobStructure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Clock
import java.time.Instant
import java.util.*

internal class MongoJobRepository(
        mongoClient: MongoClient,
        internal val conf: MongoKJob.Configuration,
        private val clock: Clock
) : MongoRepository<ObjectId, ScheduledJob>(mongoClient.getDatabase(conf.databaseName).getCollection(conf.jobCollection)), JobRepository {

    constructor(mongoClient: MongoClient, clock: Clock, conf: MongoKJob.Configuration.() -> Unit)
            : this(mongoClient, MongoKJob.Configuration().apply(conf), clock)

    override suspend fun ensureIndexes() {
        val options = IndexOptions().name("unique_job_id").background(true).unique(true)
        val index = Indexes.ascending("""${ScheduledJobStructure.SETTINGS.key}.${JobSettingsStructure.ID.key}""")
        collection.createIndex(index, options).awaitSingle()
    }

    override fun encode(value: ScheduledJob): Document =
            Document()
                    .append(ScheduledJobStructure.ID.key, keyOf(value))
                    .append(ScheduledJobStructure.STATUS.key, value.status.name)
                    .append(ScheduledJobStructure.RUN_AT.key, value.runAt)
                    .append(ScheduledJobStructure.RETRIES.key, value.retries)
                    .append(ScheduledJobStructure.KJOB_ID.key, value.kjobId)
                    .append(ScheduledJobStructure.CREATED_AT.key, value.createdAt)
                    .append(ScheduledJobStructure.UPDATED_AT.key, value.updatedAt)
                    .append(ScheduledJobStructure.SETTINGS.key, encodeJob(value.settings))
                    .append(ScheduledJobStructure.PROGRESS.key, encodeJobProgress(value.progress))

    private fun encodeJob(value: JobSettings): Document =
            Document()
                    .append(JobSettingsStructure.ID.key, value.id)
                    .append(JobSettingsStructure.NAME.key, value.name)
                    .append(JobSettingsStructure.PROPERTIES.key, value.properties)

    private fun encodeJobProgress(value: JobProgress): Document =
            Document()
                    .append(JobProgressStructure.STEP.key, value.step)
                    .append(JobProgressStructure.MAX.key, value.max)
                    .append(JobProgressStructure.STARTED_AT.key, value.startedAt)
                    .append(JobProgressStructure.COMPLETED_AT.key, value.completedAt)

    override fun decode(document: Document): ScheduledJob =
            ScheduledJob(
                    document.getObjectId(ScheduledJobStructure.ID.key).toHexString(),
                    JobStatus.valueOf(document.getString(ScheduledJobStructure.STATUS.key)),
                    document.getDate(ScheduledJobStructure.RUN_AT.key)?.toInstant(),
                    document.getString(ScheduledJobStructure.STATUS_CAUSE.key),
                    document.getInteger(ScheduledJobStructure.RETRIES.key, 0),
                    document.get(ScheduledJobStructure.KJOB_ID.key, UUID::class.java),
                    document.getDate(ScheduledJobStructure.CREATED_AT.key).toInstant(),
                    document.getDate(ScheduledJobStructure.UPDATED_AT.key).toInstant(),
                    decodeJobSettings(document.get(ScheduledJobStructure.SETTINGS.key) as Document),
                    decodeJobProgress(document.get(ScheduledJobStructure.PROGRESS.key) as Document)
            )

    private fun decodeJobSettings(document: Document): JobSettings =
            JobSettings(
                    document.getString(JobSettingsStructure.ID.key),
                    document.getString(JobSettingsStructure.NAME.key),
                    decodeMap(document.get(JobSettingsStructure.PROPERTIES.key) as Document)
            )

    private fun decodeJobProgress(document: Document): JobProgress =
            JobProgress(
                    document.getLong(JobProgressStructure.STEP.key),
                    document.getLong(JobProgressStructure.MAX.key),
                    document.getDate(JobProgressStructure.STARTED_AT.key)?.toInstant(),
                    document.getDate(JobProgressStructure.COMPLETED_AT.key)?.toInstant()
            )

    private fun decodeMap(document: Document?): Map<String, Any> =
            document?.toMap().orEmpty()

    override fun keyOf(value: ScheduledJob): ObjectId = ObjectId(value.id)

    override suspend fun exist(jobId: String): Boolean =
            collection.countDocuments(
                    Filters.eq("${ScheduledJobStructure.SETTINGS.key}.${JobSettingsStructure.ID.key}", jobId)).awaitSingle() > 0

    override suspend fun get(id: String): ScheduledJob? = findOne(ObjectId(id))

    override suspend fun save(jobSettings: JobSettings, runAt: Instant?): ScheduledJob {
        val now = Instant.now(clock)
        val sj = ScheduledJob(ObjectId.get().toHexString(), JobStatus.CREATED, runAt, null, 0, null, now, now, jobSettings, JobProgress(0))
        return create(sj)
    }

    override suspend fun update(id: String, oldKjobId: UUID?, kjobId: UUID?, status: JobStatus, statusMessage: String?, retries: Int): Boolean {
        val filter =
                Filters.and(
                        Filters.eq(ObjectId(id)),
                        Filters.eq(ScheduledJobStructure.KJOB_ID.key, oldKjobId)
                )
        val update =
                Updates.combine(
                        Updates.set(ScheduledJobStructure.STATUS.key, status.name),
                        Updates.set(ScheduledJobStructure.STATUS_CAUSE.key, statusMessage),
                        Updates.set(ScheduledJobStructure.RETRIES.key, retries),
                        Updates.set(ScheduledJobStructure.KJOB_ID.key, kjobId),
                        Updates.set(ScheduledJobStructure.UPDATED_AT.key, Instant.now(clock))
                )
        val r = collection.updateOne(filter, update).awaitSingle()
        return r.wasAcknowledged() && r.modifiedCount == 1L
    }

    override suspend fun reset(id: String, oldKjobId: UUID?): Boolean {
        val filter =
                Filters.and(
                        Filters.eq(ObjectId(id)),
                        Filters.eq(ScheduledJobStructure.KJOB_ID.key, oldKjobId)
                )
        val reset =
                Updates.combine(
                        Updates.set(ScheduledJobStructure.STATUS.key, JobStatus.CREATED.name),
                        Updates.set(ScheduledJobStructure.STATUS_CAUSE.key, null),
                        Updates.set(ScheduledJobStructure.KJOB_ID.key, null),
                        Updates.set(ScheduledJobStructure.UPDATED_AT.key, Instant.now(clock)),
                        Updates.set(ScheduledJobStructure.PROGRESS.key, encodeJobProgress(JobProgress(0)))
                )
        val r = collection.updateOne(filter, reset).awaitSingle()
        return r.wasAcknowledged() && r.modifiedCount == 1L
    }

    override suspend fun startProgress(id: String): Boolean {
        val filter = Filters.eq(ObjectId(id))
        val update =
                Updates.combine(
                        Updates.set("${ScheduledJobStructure.PROGRESS.key}.${JobProgressStructure.STARTED_AT.key}", Instant.now(clock)),
                        Updates.set(ScheduledJobStructure.UPDATED_AT.key, Instant.now(clock))
                )
        val r = collection.updateOne(filter, update).awaitSingle()
        return r.wasAcknowledged() && r.modifiedCount == 1L
    }

    override suspend fun completeProgress(id: String): Boolean {
        val filter = Filters.eq(ObjectId(id))
        val update =
                Updates.combine(
                        Updates.set("${ScheduledJobStructure.PROGRESS.key}.${JobProgressStructure.COMPLETED_AT.key}", Instant.now(clock)),
                        Updates.set(ScheduledJobStructure.UPDATED_AT.key, Instant.now(clock))
                )
        val r = collection.updateOne(filter, update).awaitSingle()
        return r.wasAcknowledged() && r.modifiedCount == 1L
    }

    override suspend fun stepProgress(id: String, step: Long): Boolean {
        val filter = Filters.eq(ObjectId(id))
        val update =
                Updates.combine(
                        Updates.inc("${ScheduledJobStructure.PROGRESS.key}.${JobProgressStructure.STEP.key}", step),
                        Updates.set(ScheduledJobStructure.UPDATED_AT.key, Instant.now(clock))
                )
        val r = collection.updateOne(filter, update).awaitSingle()
        return r.wasAcknowledged() && r.modifiedCount == 1L
    }

    override suspend fun setProgressMax(id: String, max: Long): Boolean {
        val filter = Filters.eq(ObjectId(id))
        val update =
                Updates.combine(
                        Updates.set("${ScheduledJobStructure.PROGRESS.key}.${JobProgressStructure.MAX.key}", max),
                        Updates.set(ScheduledJobStructure.UPDATED_AT.key, Instant.now(clock))
                )
        val r = collection.updateOne(filter, update).awaitSingle()
        return r.wasAcknowledged() && r.modifiedCount == 1L
    }

    override suspend fun findNext(names: Set<String>, status: Set<JobStatus>, limit: Int): Flow<ScheduledJob> {
        val filter = if (names.isEmpty()) {
            Filters.`in`(ScheduledJobStructure.STATUS.key, status.map { it.name })
        } else {
            Filters.and(
                    Filters.`in`("${ScheduledJobStructure.SETTINGS.key}.${JobSettingsStructure.NAME.key}", names),
                    Filters.`in`(ScheduledJobStructure.STATUS.key, status.map { it.name }))
        }
        return collection.find(filter).limit(limit).asFlow().map { decode(it) }
    }
}