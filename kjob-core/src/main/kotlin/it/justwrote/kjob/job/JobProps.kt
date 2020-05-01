package it.justwrote.kjob.job

import it.justwrote.kjob.Job
import it.justwrote.kjob.Prop
import it.justwrote.kjob.internal.utils.Generated

@Suppress("UNCHECKED_CAST")
class JobProps<J : Job> internal constructor(private val data: Map<String, Any>) {
    operator fun <T> get(key: Prop<J, T>): T = data[key.name] as T

    @Generated
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val props = other as JobProps<J>
        return data == props.data
    }

    @Generated
    override fun hashCode(): Int {
        return data.hashCode()
    }

    @Generated
    override fun toString(): String {
        return "JobProps$data"
    }
}