package it.justwrote.kjob.kron

import java.util.*

internal fun <T : Any> Optional<T>.toNullable(): T? = this.orElse(null)

@Suppress("UNCHECKED_CAST")
internal fun <K, V> Map<K, V?>.filterValuesNotNull() = filterValues { it != null } as Map<K, V>