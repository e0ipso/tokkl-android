package com.github.e0ipso.tokkl.navigation

import androidx.compose.ui.graphics.Color
import com.github.e0ipso.tokkl.ui.theme.SectionHues
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Each top-level page's hue, as NAV in Tokkl's App.tsx gives each section one since tokkl d012228
 * (TT-0299): Timer orange, Reports sky, Projects violet, Settings yellow, and More's pink for the
 * pages on the More sheet. TabHueTest checks that the bar and the rail draw them.
 */
class TopLevelPageHueTest {

    @Test
    fun eachPageHasItsSectionsHue() {
        val expected = mapOf(
            TopLevelPage.TIMER to Color(0xFFF05A00),
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

    @Test
    fun textOnEveryHueIsTheInkExceptWhiteOnViolet() {
        for (page in TopLevelPage.entries) {
            val onFill = if (page == TopLevelPage.PROJECTS) Color(0xFFFFFFFF) else Color(0xFF08061F)
            assertEquals("text on $page's hue", onFill, page.hue.onFill)
        }
    }

    @Test
    fun thePagesOnTheMoreSheetTakeMoresHue() {
        for (page in TopLevelPage.entries.filter { it.inMore }) assertEquals(SectionHues.more, page.hue)
    }
}
