package com.github.e0ipso.tokkl.widget

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import com.github.e0ipso.tokkl.platform.AppHook
import com.github.e0ipso.tokkl.storage.PreferencesFile
import com.github.e0ipso.tokkl.storage.ValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * Keeps the home screen widget (docs/PRD.md N3) in step with the phone. Every start, stop and edit
 * lands in the cache first, whichever surface makes it, so following the cache covers the app,
 * the notification, the tile, a shortcut, the widget's own taps and a refresh that finds a timer
 * started elsewhere.
 *
 * The key of what the widget last showed is kept on disk, so a new process updates the widget
 * only when the phone's state changed since, as when a tap on the widget started that process.
 * Nothing here schedules work or wakes the phone: the widget's clock is a Chronometer that the
 * launcher ticks, and the widget sets no update period.
 *
 * The sync lives as long as the process. A read of the state or a draw that the disk or the
 * database refuses does not end it: it waits, from [FIRST_RETRY_MS] doubling up to
 * [LAST_RETRY_MS], and tries again, and the newest key waits meanwhile. Any other failure, and
 * cancellation, still ends it.
 *
 * @param keys keys of what one widget provider shows. Collected on [io], since they read the disk.
 * @param shown the key of what the widget last showed, null for none.
 * @param redraw draws every placed widget again from the phone's state.
 */
class WidgetSync(
    private val keys: Flow<String>,
    private val shown: ValueStore<String>,
    private val redraw: WidgetRedraw,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {

    /** Follows the phone's state until the scope ends. Call once, at process start. */
    fun start(): Job = scope.launch(io) { follow() }

    /** Follows the phone's state until it ends, reading it again after a failed read. */
    suspend fun follow() {
        keys
            .retryWhen { failure, attempt ->
                val again = failure.isStorageFailure()
                if (again) delay(retryDelay(attempt))
                again
            }
            .distinctUntilChanged()
            .conflate()
            .collect(::applyUntilDone)
    }

    /** Updates the widgets unless they already show what [key] stands for. */
    suspend fun apply(key: String) {
        if (key == shown.load()) return
        redraw.redraw()
        try {
            shown.save(key)
        } catch (_: IOException) {
            // The widgets are right. A new process draws them once more, which costs nothing else.
        }
    }

    /** [apply], again after each failure of the disk or the database until it goes through. */
    private suspend fun applyUntilDone(key: String) {
        var attempt = 0L
        while (true) {
            try {
                apply(key)
                return
            } catch (_: IOException) {
                delay(retryDelay(attempt++))
            } catch (_: SQLiteException) {
                delay(retryDelay(attempt++))
            }
        }
    }

    private companion object {
        const val FIRST_RETRY_MS = 1_000L
        const val LAST_RETRY_MS = 120_000L
        const val DOUBLINGS = 7L

        /** The wait after failure [attempt], counted from 0: one second, doubling up to two minutes. */
        fun retryDelay(attempt: Long): Long =
            minOf(LAST_RETRY_MS, FIRST_RETRY_MS shl attempt.coerceAtMost(DOUBLINGS).toInt())

        /** The failures of the disk and the database, after which a read or a draw can go through later. */
        fun Throwable.isStorageFailure(): Boolean = this is IOException || this is SQLiteException
    }
}

/**
 * Draws every placed widget of one provider again. [GlanceRedraw] asks Glance; the unit tests
 * count the redraws without a launcher.
 */
fun interface WidgetRedraw {
    suspend fun redraw()
}

/** Redraws every placed [widget] through Glance. */
class GlanceRedraw(private val context: Context, private val widget: GlanceAppWidget) : WidgetRedraw {
    override suspend fun redraw() = widget.updateAll(context)
}

/**
 * Updates the Timer widget whenever what it shows changes, whichever surface changed it, from
 * process start, after the notifications and the reminder. It builds the widget state off the
 * main thread. Like every hook, it reads nothing of the graph, the context included, until it runs.
 */
class TimerWidgetHook(
    private val context: Lazy<Context>,
    private val source: Lazy<WidgetStateSource>,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : AppHook {
    override val order = 60

    override fun processStart() {
        val keys = flow { emitAll(source.value.state.map { it.key() }) }
        val context = context.value
        WidgetSync(keys, WidgetStore(context, io), GlanceRedraw(context, TokklWidget()), scope, io).start()
    }
}

/** The Pomodoro widget's redraws, after the Timer widget's. Its key is independent of the Timer widget. */
class PomodoroWidgetHook(
    private val context: Lazy<Context>,
    private val source: Lazy<PomodoroWidgetStateSource>,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : AppHook {
    override val order = 70

    override fun processStart() {
        val keys = flow { emitAll(source.value.state.map { it.key() }) }
        val context = context.value
        val shown = WidgetStore(context, io, keyName = WidgetStore.POMODORO_KEY)
        WidgetSync(keys, shown, GlanceRedraw(context, PomodoroWidget()), scope, io).start()
    }
}

/**
 * The key of what one provider last showed, in private preferences under [keyName]. The key is a hash,
 * so the file holds no description or name. It stays on the phone, because the backup and data
 * extraction rules leave out every domain.
 *
 * Each call reads or writes the file on the I/O dispatcher.
 */
class WidgetStore(context: Context, private val io: CoroutineContext, private val keyName: String = TIMER_KEY) :
    ValueStore<String> {

    private val file = PreferencesFile(context, FILE_NAME, io)

    override suspend fun load(): String? = file.read { getString(keyName, null) }

    @Throws(IOException::class)
    override suspend fun save(value: String) {
        file.commit { putString(keyName, value) }
    }

    companion object {
        private const val FILE_NAME = "widget"
        const val TIMER_KEY = "shown"
        const val POMODORO_KEY = "pomodoro_shown"
    }
}
