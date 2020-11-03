package io.kotest.provided

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.Defaults
import de.flapdoodle.embed.mongo.config.MongodConfig
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import io.kotest.core.config.AbstractProjectConfig
import org.slf4j.LoggerFactory

// Code is executed before and after test engine is started.
// see https://github.com/kotest/kotest/blob/master/doc/reference.md#project-config

object ProjectConfig : AbstractProjectConfig() {

    private val mongo = lazy {
        val host = "localhost"
        val port = Network.getFreeServerPort()

        val logger = LoggerFactory.getLogger(javaClass.name)

        val runtimeConfig = Defaults.runtimeConfigFor(Command.MongoD, logger).build()

        val starter = MongodStarter.getInstance(runtimeConfig)
        val exe = starter.prepare(MongodConfig.builder()
                .version(Version.Main.PRODUCTION)
                .net(Net(host, port, Network.localhostIsIPv6()))
                .build())

        val mongod = exe.start()
        exe to mongod
    }

    fun newMongoClient(): MongoClient {
        val host = mongo.value.second.config.net().bindIp
        val port = mongo.value.second.config.net().port
        return MongoClients.create("mongodb://$host:$port/?uuidRepresentation=STANDARD")
    }

    override fun afterAll() {
        super.afterAll()
        if (mongo.isInitialized()) {
            mongo.value.first.stop()
            mongo.value.second.stop()
        }
    }
}