package com.github.e0ipso.tokkl.session

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.github.e0ipso.tokkl.time.DurationFormat
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The store over an in-memory preferences file, since the JVM tests have no Android runtime. */
class AccountSettingsStoreTest {

    private val file = InMemoryPreferences()

    // The unit test android.jar keeps constructors working, and the store asks the context for
    // nothing but its preferences file.
    private val store = AccountSettingsStore(
        object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                assertEquals(AccountSettingsStore.FILE_NAME, name)
                return file
            }
        },
    )

    @Test
    fun eachDurationFormatReadsBackAsSaved() {
        for (format in DurationFormat.entries) {
            val settings = AccountSettings("Europe/Madrid", DayOfWeek.MONDAY, timeResolutionMinutes = 15.0, format)

            store.save(settings)

            assertEquals(format.togglName, file.all["duration_format"])
            assertEquals(settings, store.load())
        }
    }

    @Test
    fun settingsStoredWithoutADurationFormatReadAsImproved() {
        // What an app version from before the duration format left in the file.
        file.edit().putString("timezone", "Europe/Madrid").putInt("beginning_of_week", 1).commit()

        assertEquals(AccountSettings("Europe/Madrid", DayOfWeek.MONDAY, null), store.load())
        assertEquals(DurationFormat.IMPROVED, store.load()!!.durationFormat)
    }

    /** A workspace id above 2^53, which a JavaScript number could not hold, reads back whole. */
    @Test
    fun theDefaultWorkspaceReadsBackAsSavedAndGoesWhenNull() {
        val big = 9_007_199_254_740_993L
        val settings = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = 5.0, defaultWorkspaceId = big)

        store.save(settings)

        assertEquals(big, file.all["default_workspace_id"])
        assertEquals(settings, store.load())

        store.save(settings.copy(defaultWorkspaceId = null))

        assertFalse("no stale workspace stays", file.contains("default_workspace_id"))
        assertNull(store.load()!!.defaultWorkspaceId)
    }

    @Test
    fun theAccountIdReadsBackAsSavedAndIsClearedWithTheAccount() {
        val settings = AccountSettings("UTC", DayOfWeek.MONDAY, null, userId = 7)
        store.save(settings)
        assertEquals(7L, store.load()!!.userId)
        store.clear()
        assertNull(store.load())
        assertFalse(file.contains("user_id"))
    }

    @Test
    fun theClockFormatReadsBackAsSaved() {
        for (twelveHour in listOf(true, false)) {
            val settings = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = null, twelveHour = twelveHour)

            store.save(settings)

            assertEquals(settings, store.load())
        }
    }

    @Test
    fun theGroupingReadsBackAsSavedAndSettingsWithoutItGroup() {
        for (collapse in listOf(false, true)) {
            val settings = AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = null, collapseSimilar = collapse)

            store.save(settings)

            assertEquals(settings, store.load())
        }
        file.edit().remove("collapse_similar").commit()
        assertTrue("settings stored before the app read the preference group, as the web app does", store.load()!!.collapseSimilar)
    }

    @Test
    fun clearRemovesTheDurationFormat() {
        store.save(AccountSettings("UTC", DayOfWeek.SUNDAY, null, DurationFormat.DECIMAL))

        store.clear()

        assertTrue("the file is empty", file.all.isEmpty())
        assertNull(store.load())
    }
}

/** A preferences file in memory. A commit clears first, then applies the edits, as Android does. */
private class InMemoryPreferences : SharedPreferences {

    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        values[key] as Set<String>? ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as Float? ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue

    override fun contains(key: String): Boolean = key in values

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clearFirst = false

        override fun putString(key: String, value: String?) = put(key, value)

        override fun putStringSet(key: String, values: Set<String>?) = put(key, values?.toSet())

        override fun putInt(key: String, value: Int) = put(key, value)

        override fun putLong(key: String, value: Long) = put(key, value)

        override fun putFloat(key: String, value: Float) = put(key, value)

        override fun putBoolean(key: String, value: Boolean) = put(key, value)

        override fun remove(key: String) = put(key, null)

        override fun clear(): Editor {
            clearFirst = true
            return this
        }

        override fun commit(): Boolean {
            if (clearFirst) values.clear()
            for ((key, value) in changes) {
                if (value == null) values.remove(key) else values[key] = value
            }
            return true
        }

        override fun apply() {
            commit()
        }

        private fun put(key: String, value: Any?): Editor {
            changes[key] = value
            return this
        }
    }
}
