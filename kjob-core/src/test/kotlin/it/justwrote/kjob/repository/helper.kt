package it.justwrote.kjob.repository

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

fun now(clock: Clock = Clock.systemUTC()): Instant = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)