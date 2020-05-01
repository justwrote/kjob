package it.justwrote.kjob.job

enum class JobStatus {
    CREATED, // after initial creation
    RUNNING, // picked up by kjob and started
    COMPLETE, // job completed successfully
    ERROR, // an error occurred while execution. Will be retried (might have some progress)
    FAILED // job failed after several retries
}