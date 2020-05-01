package it.justwrote.kjob.repository.mongo

import com.mongodb.MongoWriteException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.provided.ProjectConfig
import it.justwrote.kjob.repository.now
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.*

class MongoRepositorySpec : ShouldSpec() {

    private val databaseName = "test-" + UUID.randomUUID()

    private var clock = Clock.fixed(Instant.parse("2020-02-22T22:22:22.222Z"), ZoneId.systemDefault())

    private fun create(value: Int): Counter =
            Counter(ObjectId.get().toHexString(), value, now(clock))

    data class Counter(val id: String, val value: Int, val updatedAt: Instant)

    val mongoClient = ProjectConfig.newMongoClient()

    private val testee: MongoRepository<String, Counter> = object : MongoRepository<String, Counter>(mongoClient.getDatabase(databaseName).getCollection("counter")) {

        override fun encode(value: Counter): Document =
                Document()
                        .append("_id", value.id)
                        .append("value", value.value)
                        .append("updated_at", value.updatedAt)

        override fun decode(document: Document): Counter =
                Counter(document.getString("_id"), document.getInteger("value"), document.getDate("updated_at").toInstant())

        override fun keyOf(value: Counter): String = value.id
    }

    override fun afterSpec(spec: Spec) {
        super.afterSpec(spec)
        runBlocking { testee.deleteAll() }
    }

    init {
        val counter1 = create(10)
        val counter2 = create(20)
        val counter3 = create(30)

        should("insert a new entity") {
            val r = testee.create(counter1)
            r shouldBe counter1

            testee.size() shouldBe 1
        }

        should("fail to insert if entity does already exist") {
            val exception = shouldThrow<MongoWriteException> {
                testee.create(counter1)
            }
            exception.code shouldBe 11000
        }

        should("get an inserted entity") {
            testee.create(counter2)
            val r = testee.findOne(counter2.id)
            r shouldBe counter2

            testee.size() shouldBe 2
        }

        should("get nothing if entity could not be found") {
            val isEmpty = testee.findOne("abc") == null
            isEmpty shouldBe true
        }

        should("create or update a entity") {
            val r = testee.createOrUpdate(counter3)
            r shouldBe counter3

            clock = Clock.fixed(now(), ZoneId.systemDefault())

            val updatedCounter = counter3.copy(value = -10, updatedAt = now(clock))
            val r2 = testee.createOrUpdate(updatedCounter)
            r2 shouldBe updatedCounter

            val r3 = testee.findOne(counter3.id)
            r3 shouldBe updatedCounter
        }

        should("update an entity") {
            val updatedCounter = counter2.copy(value = -20)
            testee.update(updatedCounter) shouldBe true
            testee.findOne(counter2.id) shouldBe updatedCounter

            val randomCounter = create(0)
            testee.update(randomCounter) shouldBe false
            testee.findOne(randomCounter.id) shouldBe null
        }


    }

}