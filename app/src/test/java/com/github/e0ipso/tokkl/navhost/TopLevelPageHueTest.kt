package com.github.e0ipso.tokkl.navhost

import androidx.compose.ui.graphics.Color
import com.github.e0ipso.tokkl.clients.ClientsRoute
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.pomodoro.PomodoroRoute
import com.github.e0ipso.tokkl.projects.ProjectsRoute
import com.github.e0ipso.tokkl.reports.ReportsRoute
import com.github.e0ipso.tokkl.settings.SettingsRoute
import com.github.e0ipso.tokkl.tags.TagsRoute
import com.github.e0ipso.tokkl.timer.TemplatesRoute
import com.github.e0ipso.tokkl.trash.TrashRoute
import org.junit.Assert.assertEquals
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
            PomodoroRoute to Color(0xFFF05A00),
            ReportsRoute to Color(0xFF00B9FA),
            ProjectsRoute to Color(0xFF8722FF),
            TemplatesRoute to Color(0xFFFF008C),
            ClientsRoute to Color(0xFFFF008C),
            TagsRoute to Color(0xFFFF008C),
            TrashRoute to Color(0xFFFF008C),
            SettingsRoute to Color(0xFFFFE600),
        )
        assertEquals(pages.all.map { it.route }.toSet(), expected.keys)
        for ((route, fill) in expected) assertEquals("$route's hue", fill, pages.of(route)?.hue?.fill)
    }
}
