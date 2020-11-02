import it.justwrote.kjob.*
import it.justwrote.kjob.extension.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

// Generic Extension Example

object MyGenericExtension : ExtensionId<MyGenericEx>

class MyGenericEx(private val config: Configuration, private val kjobConfig: BaseKJob.Configuration, private val kjob: BaseKJob<BaseKJob.Configuration>) : BaseExtension(MyGenericExtension) {
    class Configuration : BaseExtension.Configuration() {
        var myGenericConfigValue = 1
    }

    fun doStuff1() {
        // my extension logic
        println("My 'Generic' extension is doing some work.")
    }

    override fun start() {
        // initialize
    }

    override fun shutdown() {
        // close stuff
    }
}

object MyGenericModule : ExtensionModule<MyGenericEx, MyGenericEx.Configuration, BaseKJob<BaseKJob.Configuration>, BaseKJob.Configuration> {
    override val id: ExtensionId<MyGenericEx> = MyGenericExtension
    override fun create(configure: MyGenericEx.Configuration.() -> Unit, kjobConfig: BaseKJob.Configuration): (BaseKJob<BaseKJob.Configuration>) -> MyGenericEx {
        return { MyGenericEx(MyGenericEx.Configuration().apply(configure), kjobConfig, it) }
    }
}

// InMem Extension Example

object MyInMemExtension : ExtensionId<MyInMemEx>

class MyInMemEx(private val config: Configuration, private val inmemConfig: InMemKJob.Configuration, private val kjob: InMemKJob) : BaseExtension(MyInMemExtension) {
    class Configuration : BaseExtension.Configuration() {
        var myInMemConfigValue = 1
    }

    fun doStuff2() {
        // my extension logic
        println("My 'InMem' extension is doing some work.")
    }

    override fun start() {
        // initialize
    }

    override fun shutdown() {
        // close stuff
    }
}

object MyInMemModule : ExtensionModule<MyInMemEx, MyInMemEx.Configuration, InMemKJob, InMemKJob.Configuration> {
    override val id: ExtensionId<MyInMemEx> = MyInMemExtension
    override fun create(configure: MyInMemEx.Configuration.() -> Unit, kjobConfig: InMemKJob.Configuration): (InMemKJob) -> MyInMemEx {
        return { MyInMemEx(MyInMemEx.Configuration().apply(configure), kjobConfig, it) }
    }
}

// Mongo Extension Example

object MyMongoExtension : ExtensionId<MyMongoEx>

class MyMongoEx(private val config: Configuration, private val mongoConfig: MongoKJob.Configuration, private val kjob: MongoKJob) : BaseExtension(MyMongoExtension) {
    class Configuration : BaseExtension.Configuration() {
        var myMongoConfigValue = 1
    }

    fun doStuff3() {
        // my extension logic
        println("My 'Mongo' extension is doing some work.")
    }

    override fun start() {
        // initialize
    }

    override fun shutdown() {
        // close stuff
    }
}

object MyMongoModule : ExtensionModule<MyMongoEx, MyMongoEx.Configuration, MongoKJob, MongoKJob.Configuration> {
    override val id: ExtensionId<MyMongoEx> = MyMongoExtension
    override fun create(configure: MyMongoEx.Configuration.() -> Unit, kjobConfig: MongoKJob.Configuration): (MongoKJob) -> MyMongoEx {
        return { MyMongoEx(MyMongoEx.Configuration().apply(configure), kjobConfig, it) }
    }
}


/////////////////

fun main() {
    val kjob = kjob(InMem) {
        extension(MyGenericModule) {
            myGenericConfigValue = 42
        }
        extension(MyInMemModule) {
            myInMemConfigValue = 42
        }
        // This won't work since 'MyMongoModule' expects a Mongo KJob instance
//        extension(MyMongoModule) {
//            myMongoConfigValue = 42
//        }
    }.start()

    try {
        runBlocking {
            kjob(MyGenericExtension).doStuff1()
            kjob(MyInMemExtension).doStuff2()

            delay(1000) // This is just to prevent a premature shutdown
        }
    } finally {
        kjob.shutdown()
    }
}
