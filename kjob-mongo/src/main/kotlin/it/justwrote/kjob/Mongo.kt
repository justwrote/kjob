package it.justwrote.kjob

object Mongo : KJobFactory<MongoKJob, MongoKJob.Configuration> {
    override fun create(configure: MongoKJob.Configuration.() -> Unit): MongoKJob {
        return MongoKJob(MongoKJob.Configuration().apply(configure))
    }
}