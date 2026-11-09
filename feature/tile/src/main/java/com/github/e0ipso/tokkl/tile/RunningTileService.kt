package com.github.e0ipso.tokkl.tile

import android.database.sqlite.SQLiteException
import android.graphics.drawable.Icon
import android.service.quicksettings.TileService
import com.github.e0ipso.tokkl.cache.RunningEntryChanges
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.timernotice.TimerFailureNotice
import com.github.e0ipso.tokkl.timerstate.SurfaceTap
import com.github.e0ipso.tokkl.timerstate.TimerSurfaceActions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * The Quick Settings tile (docs/PRD.md N4). While a timer runs it is active, with the entry's
 * description and a stop icon, and a tap stops the timer. While the timer is paused (T1) it is
 * inactive, says so under the description, with a play icon, and a tap resumes it. While it
 * runs or is paused, the subtitle keeps that state and says to hold: a long press opens
 * [TileControlsActivity], with Stop and, where offered, Pause, or Resume and Stop, for the timer
 * the tile showed. With neither, the tile is inactive, says it continues the last entry, with a
 * play icon, and a tap continues that entry. The label and the icon carry the state, never the
 * colour alone.
 *
 * It reads the running entry from the phone's cache and the pause chain from the server's timer
 * state the phone last received. Stop and Continue go through the change queue, so it reacts at
 * once, also offline. Resume goes to the server, and one that fails, offline included, says why
 * in a toast while the tile keeps showing the paused timer. The tile is in the default,
 * non-active mode: the system binds it each time the Quick Settings panel shows it and calls
 * [onStartListening], where it reads the cache, and while it listens it follows the cache, so a
 * start or stop from any surface shows.
 * `requestListeningState` does nothing for a tile in this mode, and an active tile would not hear
 * of the panel opening. [TileRefresh] reads the current entry on opening, at most once a minute,
 * while the cache continues to drive the display and taps without waiting for that read.
 * See https://developer.android.com/develop/ui/views/quicksettings-tiles#select-a-listening-mode
 * and [requestListeningState]'s active-mode-only API contract. No application observer is needed:
 * each listening interval starts a fresh cache collection, cancelled in [onStopListening].
 */
class RunningTileService : TileService() {

    private val contents: RunningTileContents by inject()
    private val changes: RunningEntryChanges by inject()
    private val surfaceActions: TimerSurfaceActions by inject()
    private val tileRefresh: TileRefresh by inject()
    private val appScope: CoroutineScope by inject()
    private val io: CoroutineDispatcher by inject(IoDispatcher)
    private val failures: TimerFailureNotice by inject()

    /** Cancelled in [onDestroy], with the listening interval's collection. */
    private val scope = MainScope()
    private var listening: Job? = null

    /** What the tile shows, null until this service first sets it. Read and written on the main thread. */
    private var shown: RunningTileContent? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch { contents.content.distinctUntilChanged().collect(::show) }
        tileRefresh.onStartListening()
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    /**
     * Acts on what this service has shown. Before its first cache emission, a tile retained by
     * SystemUI may show an older state; never guess that tap's action from newer cache contents.
     * The change goes to the queue, or the server, in the application scope, so the service
     * ending does not cancel it.
     */
    override fun onClick() {
        super.onClick()
        val showing = shown ?: return
        appScope.launch(io) {
            try {
                val tap = tapTile(showing, surfaceActions, changes)
                if (tap is SurfaceTap.Request) tap.send()?.let { failures.show(it) }
            } catch (_: SQLiteException) {
                // The disk refused the change. Nothing changed, and the tile still says so.
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun show(content: RunningTileContent) {
        val tile = qsTile ?: return
        val look = runningTileLook(content, this)
        tile.state = look.state
        tile.label = look.label
        tile.subtitle = look.subtitle
        tile.icon = Icon.createWithResource(this, look.icon)
        tile.contentDescription = look.contentDescription
        tile.updateTile()
        shown = content
    }
}
