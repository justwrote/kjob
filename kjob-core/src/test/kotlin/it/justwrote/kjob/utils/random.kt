package it.justwrote.kjob.utils

import kotlin.random.Random

fun Random.alphanumeric(): Sequence<Char> {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return sequence {
        while (true) yield(chars[nextInt(chars.length)])
    }
}

fun Random.nextAlphanumericString(length: Int): String = alphanumeric().take(length).joinToString("")