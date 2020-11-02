package it.justwrote.kjob.extension

import it.justwrote.kjob.KJob

abstract class BaseExtension(override val id: ExtensionId<*>): Extension {
    open class Configuration: Extension.Configuration()
}

interface ExtensionId<Ex: Extension> {
    fun name(): String = this.javaClass.simpleName
}

interface Extension {
    open class Configuration
    val id: ExtensionId<*>
    fun start(): Unit {}
    fun shutdown(): Unit {}
}

interface ExtensionModule<Ex : Extension, ExConfig : Extension.Configuration, Kj: KJob, KjConfig: KJob.Configuration> {
    val id: ExtensionId<Ex>
    fun create(configure: ExConfig.() -> Unit, kjobConfig: KjConfig): (Kj) -> Ex
}