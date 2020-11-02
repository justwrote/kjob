package it.justwrote.kjob

fun <Job : KJob, Config : KJob.Configuration> kjob(
        factory: KJobFactory<Job, Config>,
        configure: Config.() -> Unit = {}
): KJob {
    return factory.create(configure)
}