package it.justwrote.kjob.dsl

import it.justwrote.kjob.Job

@JobDslMarker
class KJobFunctions<J : Job> internal constructor(internal val executeFn: suspend JobContext<J>.() -> Unit) {
    internal var errorFn: suspend ErrorJobContext.() -> Unit = { }
    internal var completeFn: suspend CompletionJobContext.() -> Unit = { }

    /**
     * Defines the code that will be executed if the execution of the job failed with an exception
     */
    fun onError(block: suspend ErrorJobContext.() -> Unit): KJobFunctions<J> {
        errorFn = block
        return this
    }

    /**
     * Defines the code that will be executed if the execution is successful
     */
    fun onComplete(block: suspend CompletionJobContext.() -> Unit): KJobFunctions<J> {
        completeFn = block
        return this
    }
}