package it.justwrote.kjob.utils

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class MutableClock(private var delegate: Clock) : Clock() {
    override fun withZone(zone: ZoneId?): Clock = delegate.withZone(zone)

    override fun getZone(): ZoneId = delegate.zone

    override fun instant(): Instant = delegate.instant()

    fun update(instant: Instant): Instant {
        delegate = fixed(instant, ZoneId.systemDefault())
        return instant
    }
}