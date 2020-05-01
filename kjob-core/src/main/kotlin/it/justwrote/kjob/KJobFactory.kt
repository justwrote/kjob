package it.justwrote.kjob

interface KJobFactory<out Job : KJob, Config : KJob.Configuration> {
    fun create(configure: Config.() -> Unit): Job
}