package it.justwrote.kjob.internal

sealed class JobResult

internal object JobSuccessful : JobResult()
internal data class JobError(val throwable: Throwable) : JobResult()