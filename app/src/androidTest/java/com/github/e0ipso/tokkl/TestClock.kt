package com.github.e0ipso.tokkl

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The app's [Clock] in the instrumented tests. It tells the system time in UTC, as the app's own
 * clock does, until a test sets [at]; then it tells [at] until a test changes it or sets it back
 * to null.
 */
class TestClock : Clock() {

    @Volatile
    var at: Instant? = null

    override fun instant(): Instant = at ?: Instant.now()

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = if (zone == getZone()) this else Zoned(zone)

    private inner class Zoned(private val zone: ZoneId) : Clock() {
        override fun instant(): Instant = this@TestClock.instant()
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = this@TestClock.withZone(zone)
    }
}
