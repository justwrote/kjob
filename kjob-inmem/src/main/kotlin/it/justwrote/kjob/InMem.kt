package it.justwrote.kjob

object InMem : KJobFactory<InMemKJob, InMemKJob.Configuration> {
    override fun create(configure: InMemKJob.Configuration.() -> Unit): InMemKJob {
        return InMemKJob(InMemKJob.Configuration().apply(configure))
    }
}