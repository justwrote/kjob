package it.justwrote.kjob

interface KJobFactory<Kj : KJob, KjConfig : KJob.Configuration> {
    fun create(configure: KjConfig.() -> Unit): KJob
}