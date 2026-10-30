package com.github.e0ipso.tokkl.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.glance.material3.ColorProviders
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.TimerTarget
import com.github.e0ipso.tokkl.time.DurationFormat
import com.github.e0ipso.tokkl.ui.screenshot.ReferenceImages
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklDarkColorScheme
import com.github.e0ipso.tokkl.ui.theme.TokklLightColorScheme
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Fixed cached inputs, real Glance RemoteViews, and the same platform host as the action tests. */
@SignedOut
abstract class TimerWidgetScreenshotTest(private val dark: Boolean, private val scale: Float) {
    @get:Rule val rules: RuleChain = RuleChain.outerRule(SessionRule()).around(SystemFontScale(scale))

    @Test fun allSizesAndStatesFitAndMatchReferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val themed = context.createConfigurationContext(configuration)
        val errors = mutableListOf<Throwable>()
        NativeWidget().use { host ->
            host.awaitDescription(context.getString(R.string.widget_sign_in))
            host.awaitUpdatesSettled()
            for ((width, height) in listOf(196 to 100, 298 to 222, 375 to 338, 110 to 40)) {
                for ((name, state) in fixtures()) {
                    host.resize(width, height)
                    val views = runBlocking { Fixture(state).compose(themed, size = DpSize(width.dp, height.dp)) }
                    // Glance chooses its night colours at application time, in the host's context.
                    host.showInConfiguration(views, configuration)
                    host.await("the $name fixture is laid out") { host.hasDescription(if (state == WidgetState.NoAccount) context.getString(R.string.widget_sign_in) else context.getString(R.string.widget_start_description).let {
                        when (name) { "running" -> context.getString(R.string.widget_stop_description); "paused" -> context.getString(R.string.widget_resume_description); else -> it }
                    }) }
                    val file = "timer_widget_${if (dark) "dark" else "light"}_${if (scale == 1f) "normal" else "largest"}_${width}x${height}_$name"
                    runCatching {
                        host.assertTextFitsVertically()
                        if (width >= 196) host.checkAccessibility()
                        if (name == "running" && width >= 260) {
                            val bases = host.clockBaseValues()
                            assertTrue("the day clock adds stopped time, excluding the pause-chain accumulation: $bases", kotlin.math.abs(bases[0] - bases[1] - 3600_000L) <= 150L)
                        }
                    }.exceptionOrNull()?.let { errors += AssertionError("$file layout: ${it.message}", it) }
                    val capture = host.capture(clockTexts = listOf("1:12:34", "2:12:34"))
                    runCatching { ReferenceImages.assertMatches(file, capture) }.exceptionOrNull()?.let(errors::add)
                }
            }
        }
        if (errors.isNotEmpty()) throw AssertionError("${errors.size} Timer widget references need review", errors.first()).apply { errors.drop(1).forEach(::addSuppressed) }
    }

    private class Fixture(private val state: WidgetState) : GlanceAppWidget() {
        override suspend fun provideGlance(context: Context, id: GlanceId) {
            provideContent {
                GlanceTheme(colors = ColorProviders(light = TokklLightColorScheme, dark = TokklDarkColorScheme)) { TimerWidgetContent(state) }
            }
        }
    }

    private fun fixtures(): List<Pair<String, WidgetState>> {
        val now = Instant.now()
        val start = now.minusSeconds(754)
        val target = TimerTarget.server(1, 42, "screenshot")
        val projects = listOf(
            WidgetDayProject(1, "Tokkl for Android", "#0b83d9", 3600, "1:00", true),
            WidgetDayProject(2, "Client site", "#9e5bd9", 1800, "0:30", false),
            WidgetDayProject(3, "Admin", "#d94182", 900, "0:15", false),
            WidgetDayProject(null, null, null, 900, "0:15", false),
        )
        val day = WidgetDayReport(LocalDate.of(2026, 10, 28), 7200, 3600, 7, projects, WidgetDayRunning(42, start, false), DurationFormat.IMPROVED)
        val favorites = listOf("Standup", "Code review", "Client call").map { WidgetFavorite(EntryFields(it), null, null) }
        val running = WidgetRunning(42, "Widget mock-ups", "Tokkl for Android", "Design", start, 3600, target, true, "#0b83d9")
        val ready = WidgetState.Ready(running, favorites, day = day)
        return listOf(
            "running" to ready,
            "paused" to ready.copy(running = null, paused = WidgetPaused("Widget mock-ups", "Tokkl for Android", "Design", 754, target, "#0b83d9"), day = day.copy(running = null)),
            "idle" to ready.copy(running = null, day = day.copy(running = null)),
            "signed_out" to WidgetState.NoAccount,
        )
    }
}

@SignedOut
@SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
class LightTimerWidgetScreenshotTest : TimerWidgetScreenshotTest(false, 1f)
@SignedOut
@SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
class DarkTimerWidgetScreenshotTest : TimerWidgetScreenshotTest(true, 1f)
@SignedOut
@SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
class LightLargestTimerWidgetScreenshotTest : TimerWidgetScreenshotTest(false, SystemFontScale.LARGEST)
@SignedOut
@SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
class DarkLargestTimerWidgetScreenshotTest : TimerWidgetScreenshotTest(true, SystemFontScale.LARGEST)
