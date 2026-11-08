package com.github.e0ipso.tokkl.navhost

import androidx.compose.ui.graphics.Color
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Each top-level page's hue, as NAV in Tokkl's App.tsx gives each section one since tokkl d012228
 * (TT-0299): Timer and Pomodoro orange, Reports sky, Projects violet, Settings yellow, and More's
 * pink for the other pages on the More sheet. Reports keeps sky while it sits on that sheet.
 * TabHueTest checks that the bar and the rail draw them.
 */
class TopLevelPageHueTest {
    private val pages = appTopLevelPages

    @Test
    fun eachPageHasItsSectionsHue() {
        val expected = mapOf(
            Route.Timer to Color(0xFFF05A00),
            Route.Pomodoro to Color(0xFFF05A00),
            Route.Reports to Color(0xFF00B9FA),
            Route.Projects to Color(0xFF8722FF),
            Route.Templates to Color(0xFFFF008C),
            Route.Clients to Color(0xFFFF008C),
            Route.Tags to Color(0xFFFF008C),
            Route.Trash to Color(0xFFFF008C),
            Route.Settings to Color(0xFFFFE600),
        )
        assertEquals(pages.all.map { it.route }.toSet(), expected.keys)
        for ((route, fill) in expected) assertEquals("$route's hue", fill, pages.of(route)?.hue?.fill)
    }

    @Test
    fun reportsKeepsItsOwnHueOnTheMoreSheet() {
        assertSame(SectionHues.reports, pages.of(Route.Reports)?.hue)
    }
}
