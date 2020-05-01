package it.justwrote.kjob.repository.mongo.structure

internal enum class JobProgressStructure(val key: String) {
    STEP("step"),
    MAX("max"),
    STARTED_AT("startedAt"),
    COMPLETED_AT("completedAt")
}