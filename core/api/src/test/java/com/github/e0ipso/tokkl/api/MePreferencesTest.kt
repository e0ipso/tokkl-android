package com.github.e0ipso.tokkl.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** How the typed answers read the JSON the server sends: each key by the type its reader expects. */
class MePreferencesTest {

    @Test
    fun eachKeyReadsOnlyAsItsOwnJsonType() {
        val preferences = mePreferences("""{"s":"9","n":9,"b":true,"nul":null}""")

        assertEquals("9", preferences.string("s"))
        assertNull("a number is no string", preferences.string("n"))
        assertEquals(9.0, preferences.number("n"))
        assertNull("a quoted numeral is no number", preferences.number("s"))
        assertEquals(true, preferences.boolean("b"))
        assertNull(preferences.boolean("s"))
        assertTrue("null counts as present", preferences.has("nul"))
        assertFalse(preferences.has("missing"))
    }

    /** JavaScript's truthiness, as the web app reads `collapseTimeEntries`. */
    @Test
    fun truthinessFollowsJavaScript() {
        val preferences = mePreferences("""{"f":false,"zero":0,"empty":"","s":"x","one":1,"o":{},"nul":null}""")

        assertEquals(listOf(false, false, false), listOf("f", "zero", "empty").map(preferences::truthy))
        assertEquals(listOf(true, true, true), listOf("s", "one", "o").map(preferences::truthy))
        assertNull(preferences.truthy("nul"))
        assertNull(preferences.truthy("missing"))
    }

    @Test
    fun favoritesAreNoneWhenLeftOutAndNullWhenTheyDoNotDecode() {
        assertEquals(emptyList<ServerFavorite>(), mePreferences("{}").favorites())
        assertNull(mePreferences("""{"favorites":"no"}""").favorites())
        assertEquals("Write", mePreferences("""{"favorites":[{"description":"Write"}]}""").favorites()!!.single().description)
    }

    @Test
    fun theBoundsReadTheFirstDayOrNoneAndRefuseAnythingElse() {
        assertEquals(ApiResult.Success(EntryBounds(LocalDate.of(2026, 1, 2))), entryBounds(body("\"2026-01-02\"")))
        assertEquals(ApiResult.Success(EntryBounds(null)), entryBounds(body("null")))
        for (value in listOf("\"soon\"", "5", "{}")) {
            val read = entryBounds(body(value))
            assertTrue(value, read is ApiResult.Failure && read.error is ApiError.InvalidResponse)
        }
        assertTrue(entryBounds(JsonObject(emptyMap())) is ApiResult.Failure)
    }

    private fun body(firstDate: String) = Json.parseToJsonElement("""{"first_date":$firstDate}""") as JsonObject
}
