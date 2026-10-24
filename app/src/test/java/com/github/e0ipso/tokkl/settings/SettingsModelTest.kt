package com.github.e0ipso.tokkl.settings

import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaults
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.pomodoro.StoredPomodoroDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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

    @Test
    fun validatedSettingsEditsPersistLocallyWithoutAnApiClientOnToggl() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var stored: StoredPomodoroDefaults? = null
        val setting = PomodoroDefaultsSetting(
            initial = null, save = { stored = it }, client = { error("Toggl defaults must stay on the phone") },
            keepsPreferences = { false }, scope = scope, io = Dispatchers.Unconfined,
        )
        try {
            for (field in PomodoroDefaultField.entries) {
                val next = field.edited(setting.defaults.value, field.range.last.toString())!!
                setting.change(next).join()
                assertEquals(next, setting.defaults.value)
                assertEquals(StoredPomodoroDefaults(next, unsent = false), stored)
            }
            assertEquals(PomodoroDefaults(180, 60, 60, 12), setting.defaults.value)
        } finally { scope.cancel() }
    }
}
