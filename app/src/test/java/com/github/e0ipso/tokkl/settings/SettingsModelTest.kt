package com.github.e0ipso.tokkl.settings

import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsModelTest {
    @Test
    fun everyFieldAcceptsBothInclusiveBounds() {
        val ranges = listOf(1..180, 1..60, 1..60, 1..12)
        for ((field, range) in PomodoroDefaultField.entries.zip(ranges)) {
            assertEquals(range, field.range)
            for (bound in listOf(range.first, range.last)) {
                assertEquals(bound, field.parse(bound.toString()))
                val result = field.edited(PomodoroDefaults.DEFAULT, bound.toString())
                assertNotNull(result)
                assertEquals(bound, field.value(result!!))
            }
        }
    }

    @Test
    fun invalidAndOutOfRangeDraftsNeverReachTheRepository() {
        for (field in PomodoroDefaultField.entries) {
            for (bad in listOf("", "0", "-1", "1.5", "NaN", "words", "2147483648", (field.range.last + 1).toString())) {
                assertNull("$field: $bad", field.parse(bad))
                assertNull("$field: $bad", field.edited(PomodoroDefaults.DEFAULT, bad))
            }
        }
    }

    @Test
    fun anEditPreservesEveryOtherCurrentDefault() {
        val latest = PomodoroDefaults(40, 8, 20, 3)
        for (editedField in PomodoroDefaultField.entries) {
            val result = editedField.edited(latest, "2")!!
            for (field in PomodoroDefaultField.entries) {
                assertEquals(if (field == editedField) 2 else field.value(latest), field.value(result))
            }
        }
    }

}
