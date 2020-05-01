package it.justwrote.kjob.repository.mongo

import com.mongodb.client.model.ReplaceOptions
import com.mongodb.reactivestreams.client.MongoCollection
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.bson.Document

internal abstract class MongoRepository<K, V>(protected val collection: MongoCollection<Document>) {

    companion object {
        private const val ID: String = "_id"
        private val REPLACE_UPSERT_OPERATION = ReplaceOptions().upsert(true)
    }

    open suspend fun ensureIndexes(): Unit {}
    protected abstract fun encode(value: V): Document
    protected abstract fun decode(document: Document): V
    protected abstract fun keyOf(value: V): K

    protected fun byId(key: K): Document = Document(ID, key)

    suspend fun create(value: V): V {
        val doc = encode(value)
        collection.insertOne(doc).awaitSingle()
        return value
    }

    suspend fun createOrUpdate(value: V): V {
        val doc = encode(value)
        collection.replaceOne(byId(keyOf(value)), doc, REPLACE_UPSERT_OPERATION).awaitSingle()
        return value
    }

    internal suspend fun deleteAll(): Unit {
        collection.deleteMany(Document()).awaitSingle()
    }

    suspend fun findOne(key: K): V? = collection.find(byId(key)).awaitFirstOrNull()?.let(::decode)

    internal suspend fun size(): Long = collection.countDocuments().awaitSingle()

    suspend fun update(value: V): Boolean =
            collection.replaceOne(byId(keyOf(value)), encode(value)).awaitSingle().modifiedCount == 1L
}