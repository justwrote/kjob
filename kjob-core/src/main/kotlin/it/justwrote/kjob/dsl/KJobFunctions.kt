package it.justwrote.kjob.dsl

import it.justwrote.kjob.BaseJob

@JobDslMarker
class KJobFunctions<J : BaseJob, JC: JobContext<J>> internal constructor(internal val executeFn: suspend JC.() -> Unit) {
    internal var errorFn: suspend ErrorJobContext.() -> Unit = { }
    internal var completeFn: suspend CompletionJobContext.() -> Unit = { }

    /**
     * Defines the code that will be executed if the execution of the job failed with an exception
     */
    fun onError(block: suspend ErrorJobContext.() -> Unit): KJobFunctions<J, JC> {
        errorFn = block
        return this
    }

    /**
     * Defines the code that will be executed if the execution is successful
     */
    fun onComplete(block: suspend CompletionJobContext.() -> Unit): KJobFunctions<J, JC> {
        completeFn = block
        return this
    }
}