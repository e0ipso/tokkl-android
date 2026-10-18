package com.github.e0ipso.tokkl.settings

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeSettingsModelTest {

    private val saved = mutableListOf<ThemeSettings>()
    private var failSaves = false

    private fun model(initial: ThemeSettings = ThemeSettings()) =
        ThemeSettingsModel(
            initial = initial,
            save = { if (failSaves) throw IOException("disk full") else saved += it },
            scope = CoroutineScope(Dispatchers.Unconfined),
            io = Dispatchers.Unconfined,
        )

    @Test
    fun startsWithTheStoredSettings() {
        val stored = ThemeSettings(ThemeMode.DARK, dynamicColor = true)

        assertEquals(stored, model(stored).state.value)
    }

    @Test
    fun eachChangeShowsAndIsSaved() {
        val model = model()

        model.onModeChange(ThemeMode.DARK)
        assertEquals(ThemeSettings(ThemeMode.DARK), model.state.value)
        model.onDynamicColorChange(true)
        assertEquals(ThemeSettings(ThemeMode.DARK, dynamicColor = true), model.state.value)
        model.onModeChange(ThemeMode.SYSTEM)

        assertEquals(
            listOf(
                ThemeSettings(ThemeMode.DARK),
                ThemeSettings(ThemeMode.DARK, dynamicColor = true),
                ThemeSettings(ThemeMode.SYSTEM, dynamicColor = true),
            ),
            saved,
        )
    }

    @Test
    fun choosingWhatIsStoredWritesNothing() {
        val model = model(ThemeSettings(ThemeMode.LIGHT))

        model.onModeChange(ThemeMode.LIGHT)

        assertEquals(emptyList<ThemeSettings>(), saved)
    }

    @Test
    fun aFailedWriteGoesBackToTheStoredSettings() {
        val model = model(ThemeSettings(ThemeMode.LIGHT))
        failSaves = true

        model.onModeChange(ThemeMode.DARK)

        assertEquals(ThemeSettings(ThemeMode.LIGHT), model.state.value)
    }
}
