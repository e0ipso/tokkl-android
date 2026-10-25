package com.github.e0ipso.tokkl.navigation

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Each top-level page's hue, as NAV in Tokkl's App.tsx gives each section one since tokkl d012228
 * (TT-0299): Timer and Pomodoro orange, Reports sky, Projects violet, Settings yellow, and More's
 * pink for the other pages on the More sheet. Reports keeps sky while it sits on that sheet.
 * TabHueTest checks that the bar and the rail draw them.
 */
class TopLevelPageHueTest {

    @Test
    fun eachPageHasItsSectionsHue() {
        val expected = mapOf(
            TopLevelPage.TIMER to Color(0xFFF05A00),
            TopLevelPage.POMODORO to Color(0xFFF05A00),
            TopLevelPage.REPORTS to Color(0xFF00B9FA),
            TopLevelPage.PROJECTS to Color(0xFF8722FF),
            TopLevelPage.TEMPLATES to Color(0xFFFF008C),
            TopLevelPage.CLIENTS to Color(0xFFFF008C),
            TopLevelPage.TAGS to Color(0xFFFF008C),
            TopLevelPage.TRASH to Color(0xFFFF008C),
            TopLevelPage.SETTINGS to Color(0xFFFFE600),
        )
        assertEquals(TopLevelPage.entries.toSet(), expected.keys)
        for ((page, fill) in expected) assertEquals("$page's hue", fill, page.hue.fill)
    }

}
