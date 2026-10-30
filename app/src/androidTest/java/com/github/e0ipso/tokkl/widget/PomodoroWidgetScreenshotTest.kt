package com.github.e0ipso.tokkl.widget

import android.graphics.Rect
import android.view.View
import android.widget.TextView
import android.view.ViewGroup
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaults
import com.github.e0ipso.tokkl.timerstate.PhaseKind
import com.github.e0ipso.tokkl.timerstate.PhaseTransition
import com.github.e0ipso.tokkl.timerstate.TimerPomodoro
import com.github.e0ipso.tokkl.timerstate.TimerPomodoroPhase
import com.github.e0ipso.tokkl.ui.screenshot.ReferenceImages
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Fixed server data through production content, real RemoteViews and the platform widget host. */
@SignedOut
abstract class PomodoroWidgetScreenshotTest(private val dark: Boolean, private val scale: Float) {
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(SystemFontScale(scale))

    @Test fun allSizesStatesAndControlsFitAndMatchReferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val themed = context.createConfigurationContext(configuration)
        val failures = mutableListOf<Throwable>()
        NativeWidget(PomodoroWidgetReceiver::class.java).use { host ->
            host.awaitDescription(context.getString(R.string.widget_sign_in))
            host.awaitUpdatesSettled()
            assertEquals(0, host.provider.updatePeriodMillis)
            assertEquals("Pomodoro", host.provider.loadLabel(context.packageManager))
            for ((width, height) in listOf(196 to 100, 298 to 222, 375 to 338, 110 to 40)) {
                for ((name, state) in fixtures()) {
                    host.resize(width, height)
                    val views = runBlocking { Fixture(state).compose(themed, size = DpSize(width.dp, height.dp)) }
                    host.showInConfiguration(views, configuration)
                    val filename = "pomodoro_widget_${if (dark) "dark" else "light"}_${if (scale == 1f) "normal" else "largest"}_${width}x${height}_$name"
                    // Capture every state, including one whose geometry assertion needs correction.
                    runCatching {
                        host.assertTextFitsVertically()
                        if (width >= 196) host.checkAccessibility()
                        when (state) {
                            is PomodoroWidgetState.Active -> {
                                if (width >= 196) assertPhaseWordsFit()
                                assertTrue("$filename has a clock", host.hasDescription(context.getString(if (state.ended) R.string.pomodoro_overtime else R.string.pomodoro_remaining)))
                                if (width >= 260) assertCompleteControl(context.getString(R.string.pomodoro_end))
                                val next = context.getString(when (state.session.upcomingPhase) {
                                    PhaseKind.WORK -> R.string.pomodoro_start_work
                                    PhaseKind.SHORT_REST -> R.string.pomodoro_start_short_rest
                                    PhaseKind.LONG_REST -> R.string.pomodoro_start_long_rest
                                })
                                assertCompleteControl(next, if (width < 180) 36 else 48)
                            }
                            is PomodoroWidgetState.Idle -> {
                                assertCompleteControl(context.getString(R.string.pomodoro_start), if (width < 180) 36 else 48)
                                if (width >= 260) assertCompleteControl(context.getString(R.string.pomodoro_widget_edit_description))
                            }
                            else -> Unit
                        }
                    }.exceptionOrNull()?.let { failures += AssertionError("$filename layout/accessibility: ${it.message}", it) }
                    runCatching { ReferenceImages.assertMatches(filename, host.capture()) }.exceptionOrNull()?.let(failures::add)
                }
            }
        }
        if (failures.isNotEmpty()) throw AssertionError("${failures.size} Pomodoro widget captures/checks need review", failures.first()).apply { failures.drop(1).forEach(::addSuppressed) }
    }

    private fun assertPhaseWordsFit() {
        onView(isRoot()).check { root, error ->
            if (error != null) throw error
            fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
                (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            val heading = descendants(checkNotNull(root)).filterIsInstance<TextView>().first()
            assertEquals("the phase words remain visible: ${heading.text}", 0, heading.layout.getEllipsisCount(0))
        }
    }

    private fun assertCompleteControl(description: String, minimumDp: Int = 48) {
        onView(isRoot()).check { root, error ->
            if (error != null) throw error
            fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
                (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            val target = descendants(checkNotNull(root)).firstOrNull { it.contentDescription?.toString() == description }
            assertNotNull("$description is present", target)
            val button = generateSequence(checkNotNull(target)) { it.parent as? View }.first { it.isClickable }
            val visible = Rect()
            assertTrue("$description is visible", button.getGlobalVisibleRect(visible))
            assertTrue("$description fits vertically: ${visible.height()}/${button.height}", visible.height() >= button.height - 1)
            assertTrue("$description fits horizontally: ${visible.width()}/${button.width}", visible.width() >= button.width - 1)
            val minimum = (minimumDp * button.resources.displayMetrics.density).toInt()
            assertTrue("$description keeps its touch target", button.height >= minimum - 1 && button.width >= minimum - 1)
        }
    }

    private class Fixture(private val state: PomodoroWidgetState) : GlanceAppWidget() {
        override suspend fun provideGlance(context: Context, id: GlanceId) {
            provideContent { GlanceTheme(colors = PomodoroColors) { PomodoroWidgetContent(state, NOW, ticking = false) } }
        }
    }

    private fun fixtures(): List<Pair<String, PomodoroWidgetState>> {
        val entry = PomodoroWidgetEntry(1, EntryFields("Write screenshot scenarios", 7, 9, listOf("tests", "docs"), true), "Tokkl for Android", "#0b83d9", "Widgets")
        val active = PhaseKind.entries.flatMap { kind ->
            val minutes = when (kind) { PhaseKind.WORK -> 25; PhaseKind.SHORT_REST -> 5; PhaseKind.LONG_REST -> 15 }
            val start = NOW.minusSeconds(65)
            val phase = TimerPomodoroPhase(kind, start, if (kind == PhaseKind.WORK) 1 else if (kind == PhaseKind.LONG_REST) 4 else 2, start.plusSeconds(minutes * 60L))
            val session = TimerPomodoro(25, 5, 15, 4, phase,
                if (kind == PhaseKind.WORK) PhaseKind.SHORT_REST else PhaseKind.WORK,
                when (kind) { PhaseKind.WORK -> PhaseTransition.WORK_TO_SHORT_REST; PhaseKind.SHORT_REST -> PhaseTransition.SHORT_REST_TO_WORK; PhaseKind.LONG_REST -> PhaseTransition.LONG_REST_TO_WORK },
                if (kind == PhaseKind.LONG_REST) 4 else 2, 42, start)
            val current = PomodoroWidgetState.Active("fixture", 17, session, start, phase.plannedEnd, false, entry)
            listOf(kind.name.lowercase() to current, "${kind.name.lowercase()}_done" to current.copy(start = NOW.minusSeconds(minutes * 60L + 65), end = NOW.minusSeconds(65), ended = true))
        }
        return active + listOf(
            "idle" to PomodoroWidgetState.Idle("fixture", PomodoroDefaults.DEFAULT, entry),
            "signed_out" to PomodoroWidgetState.NoAccount,
            "unsupported" to PomodoroWidgetState.Unsupported,
        )
    }

    private companion object { val NOW: Instant = Instant.parse("2026-10-29T08:00:00Z") }
}

@SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
class LightPomodoroWidgetScreenshotTest : PomodoroWidgetScreenshotTest(false, 1f)
@SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
class DarkPomodoroWidgetScreenshotTest : PomodoroWidgetScreenshotTest(true, 1f)
@SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
class LightLargestPomodoroWidgetScreenshotTest : PomodoroWidgetScreenshotTest(false, SystemFontScale.LARGEST)
@SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
class DarkLargestPomodoroWidgetScreenshotTest : PomodoroWidgetScreenshotTest(true, SystemFontScale.LARGEST)
