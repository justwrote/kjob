package it.justwrote.kjob

fun <Job : KJob, Config : KJob.Configuration> kjob(
        factory: KJobFactory<Job, Config>,
        configure: Config.() -> Unit = {}
): Job {
    return factory.create(configure)
}