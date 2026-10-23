// Cases copied verbatim, in order, from ../tokkl/packages/common/fixtures/pomodoroSession.json
// at tokkl commit bed61cb1e9e3179efdc0e29b82bc8ab9e663b71e (Tokkl 1.8.1).
// The unchanged JSON is in src/test/resources/time/pomodoroSession.json. This dispatch follows
// packages/common/pomodoroSession.test.ts at the same commit, with one JVM test per case.
package com.github.e0ipso.tokkl.time

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeParseException

@RunWith(Parameterized::class)
class PomodoroTest(private val name: String, private val case: JsonObject) {
    @Test
    fun goldenCase() {
        val input = case.getValue("input").jsonObject
        val options = case["options"]?.jsonObject
        val defaults = DEFAULT_POMODORO_SESSION_CONFIG
        val config = PomodoroSessionConfig(
            workMinutes = options?.get("workMinutes")?.jsonPrimitive?.double ?: defaults.workMinutes,
            shortRestMinutes = options?.get("shortRestMinutes")?.jsonPrimitive?.double ?: defaults.shortRestMinutes,
            longRestMinutes = options?.get("longRestMinutes")?.jsonPrimitive?.double ?: defaults.longRestMinutes,
            longRestEvery = options?.get("longRestEvery")?.jsonPrimitive?.double ?: defaults.longRestEvery,
        )
        val expected = case.getValue("expected")
        if (expected is JsonObject && "throws" in expected) {
            assertEquals("RangeError", expected.text("throws"))
            assertThrows(name, IllegalArgumentException::class.java) { run(input, config) }
        } else {
            assertEquals(name, expectedValue(input.text("function"), expected), run(input, config))
        }
    }

    private fun run(input: JsonObject, config: PomodoroSessionConfig): Any? = when (input.text("function")) {
        "startSession" -> startSession(input.instant("at"))
        "plannedEnd" -> plannedEnd(input.getValue("phase").jsonObject.toPhase(), config)
        "overtimeSeconds" -> overtimeSeconds(input.getValue("phase").jsonObject.toPhase(), input.instant("at"), config)
        "transitionKind" -> transitionKind(input.getValue("phase").jsonObject.toPhase(), config)
        "nextPhase" -> nextPhase(input.getValue("phase").jsonObject.toPhase(), input.instant("at"), config)
        "pomodoroEntrySpan" -> pomodoroEntrySpan(
            input.instant("start"),
            input.instant("end"),
            input.getValue("minutes").jsonPrimitive.double,
            ZoneId.of(input.text("timeZone")),
        )
        else -> error("Unknown fixture function: ${input.text("function")}")
    }

    private fun expectedValue(function: String, expected: JsonElement): Any? {
        if (expected == JsonNull) return null
        return when (function) {
            "startSession" -> expected.jsonObject.toPhase()
            "plannedEnd" -> Instant.parse(expected.jsonPrimitive.content)
            "overtimeSeconds" -> expected.jsonPrimitive.double
            "transitionKind" -> expected.jsonPrimitive.content
            "nextPhase" -> expected.jsonObject.let {
                PhaseChange(
                    it.getValue("next").jsonObject.toPhase(),
                    it.text("transition"),
                    it.text("skipped").toBooleanStrict(),
                )
            }
            "pomodoroEntrySpan" -> expected.jsonObject.let { SnappedSpan(it.instant("start"), it.instant("stop")) }
            else -> error("Unknown fixture function: $function")
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun cases(): List<Array<Any>> {
            val json = checkNotNull(PomodoroTest::class.java.getResourceAsStream("/time/pomodoroSession.json"))
                .bufferedReader().use { it.readText() }
            val cases = Json.parseToJsonElement(json).jsonArray
            check(cases.size == 64) { "Expected every upstream case" }
            return cases.map {
                val case = it.jsonObject
                arrayOf("${case.getValue("input").jsonObject.text("function")}: ${case.text("name")}", case)
            }
        }
    }
}

private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

// java.time has no invalid Instant. Map the fixture's invalid Date to an Instant outside the
// JavaScript Date range, so the production rule itself must reject it, rather than the parser.
private fun JsonObject.instant(key: String): Instant = try {
    Instant.parse(text(key))
} catch (_: DateTimeParseException) {
    Instant.MAX
}

private fun JsonObject.toPhase(): SessionPhase =
    SessionPhase(text("kind"), instant("startedAt"), getValue("completed").jsonPrimitive.double)
