package it.justwrote.kjob.repository.mongo.structure

internal enum class ScheduledJobStructure(val key: String) {
    ID("_id"),
    STATUS("status"),
    RUN_AT("run_at"),
    STATUS_CAUSE("status_cause"),
    RETRIES("retries"),
    KJOB_ID("kjob_id"),
    CREATED_AT("created_at"),
    UPDATED_AT("updated_at"),
    SETTINGS("settings"),
    PROGRESS("progress")
}