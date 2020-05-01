package it.justwrote.kjob.repository.mongo.structure

internal enum class JobSettingsStructure(val key: String) {
    ID("_id"),
    NAME("name"),
    PROPERTIES("properties")
}