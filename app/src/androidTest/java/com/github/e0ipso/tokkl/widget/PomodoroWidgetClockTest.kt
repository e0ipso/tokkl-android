package com.github.e0ipso.tokkl.widget

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.timerstate.*
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@SignedOut
class PomodoroWidgetClockTest {
    @get:Rule val session = SessionRule()

    @Test fun nativeCountdownAndOvertimeTickWithoutProviderUpdates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        NativeWidget(PomodoroWidgetReceiver::class.java).use { host ->
            host.awaitDescription(context.getString(R.string.widget_sign_in))
            host.awaitUpdatesSettled()
            host.resize(298, 222)
            for (ended in listOf(false, true)) {
                val now = Instant.now()
                val end = if (ended) now.minusSeconds(65) else now.plusSeconds(120)
                val start = end.minusSeconds(1500)
                val phase = TimerPomodoroPhase(PhaseKind.WORK, start.plusSeconds(90), 1, end.plusSeconds(90))
                // Server boundaries differ by 90 seconds. The state supplies corrected boundaries.
                val server = TimerPomodoro(25, 5, 15, 4, phase, PhaseKind.SHORT_REST, PhaseTransition.WORK_TO_SHORT_REST, 2, 42, phase.start)
                val state = PomodoroWidgetState.Active("fixture", 17, server, start, end, ended, PomodoroWidgetEntry(1, EntryFields("Focus"), null, null, null))
                val before = SystemClock.elapsedRealtime() + Duration.between(Instant.now(), end).toMillis()
                val views = runBlocking { Fixture(state).compose(context, size = DpSize(298.dp, 222.dp)) }
                host.showInConfiguration(views, Configuration(context.resources.configuration))
                host.awaitDescription(context.getString(if (ended) R.string.pomodoro_overtime else R.string.pomodoro_remaining))
                assertEquals("the native clock uses the corrected planned end", before.toDouble(), host.clockBases().values.single().toDouble(), 150.0)
                host.assertTextFitsVertically()
                host.assertClockTicksWithoutUpdates()
            }
        }
    }

    private class Fixture(private val state: PomodoroWidgetState.Active) : GlanceAppWidget() {
        override suspend fun provideGlance(context: Context, id: GlanceId) {
            provideContent { GlanceTheme(colors = PomodoroColors) { PomodoroWidgetContent(state) } }
        }
    }
}
