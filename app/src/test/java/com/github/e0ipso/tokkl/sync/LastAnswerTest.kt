package com.github.e0ipso.tokkl.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class LastAnswerTest {

    private val saved = mutableListOf<Instant?>()
    private var now: Instant = Instant.parse("2026-10-16T08:00:00Z")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    @Test
    fun eachAnswerTakesTheClockTimeAndSavesIt() {
        val answer = LastAnswer(initial = Instant.parse("2026-10-15T20:00:00Z"), save = { saved += it }, clock = clock)
        assertEquals(Instant.parse("2026-10-15T20:00:00Z"), answer.state.value)

        answer.succeeded()
        now = Instant.parse("2026-10-16T08:05:00Z")
        answer.succeeded()

        assertEquals(Instant.parse("2026-10-16T08:05:00Z"), answer.state.value)
        assertEquals(listOf(Instant.parse("2026-10-16T08:00:00Z"), Instant.parse("2026-10-16T08:05:00Z")), saved)
    }

    @Test
    fun aTimeNeverGoesBack() {
        val answer = LastAnswer(initial = Instant.parse("2026-10-16T09:00:00Z"), save = { saved += it }, clock = clock)

        answer.succeeded()

        assertEquals(Instant.parse("2026-10-16T09:00:00Z"), answer.state.value)
        assertEquals(emptyList<Instant?>(), saved)
    }

    @Test
    fun forgetClearsTheTimeAndTheFile() {
        val answer = LastAnswer(initial = Instant.parse("2026-10-15T20:00:00Z"), save = { saved += it }, clock = clock)

        answer.forget()

        assertNull(answer.state.value)
        assertEquals(listOf<Instant?>(null), saved)
    }
}
