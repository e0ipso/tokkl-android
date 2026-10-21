package com.github.e0ipso.tokkl.tile

import android.database.sqlite.SQLiteException
import android.graphics.drawable.Icon
import android.service.quicksettings.TileService
import com.github.e0ipso.tokkl.TokklApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The Quick Settings tile (docs/PRD.md N4). While a timer runs it is active, with the entry's
 * description and a stop icon, and a tap stops the timer. Otherwise it is inactive, says it
 * continues the last entry, with a play icon, and a tap continues that entry. The label and the
 * icon carry the state, never the colour alone.
 *
 * It reads the running entry from the phone's cache and taps go through the change queue, so it
 * reacts at once, also offline. The tile is in the default, non-active mode: the system binds it
 * each time the Quick Settings panel shows it and calls [onStartListening], where it reads the
 * cache, and while it listens it follows the cache, so a start or stop from any surface shows.
 * `requestListeningState` does nothing for a tile in this mode, and an active tile would not hear
 * of the panel opening, which TKA-0075 refreshes on.
 * See https://developer.android.com/develop/ui/views/quicksettings-tiles#select-a-listening-mode
 * and [requestListeningState]'s active-mode-only API contract. No application observer is needed:
 * each listening interval starts a fresh cache collection, cancelled in [onStopListening].
 */
class RunningTileService : TileService() {

    private val app get() = applicationContext as TokklApplication

    private val scope = MainScope()
    private var listening: Job? = null

    /** What the tile shows, null until this service first sets it. Read and written on the main thread. */
    private var shown: RunningTileContent? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch { content().distinctUntilChanged().collect(::show) }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    /**
     * Acts on what this service has shown. Before its first cache emission, a tile retained by
     * SystemUI may show an older state; never guess that tap's action from newer cache contents.
     * The change goes to the queue in a scope of its own, so the service ending does not cancel it.
     */
    override fun onClick() {
        super.onClick()
        val showing = shown ?: return
        val changes = app.changes
        CoroutineScope(Dispatchers.IO).launch {
            try {
                changes.tapTile(showing)
            } catch (e: SQLiteException) {
                // The disk refused the change. Nothing changed, and the tile still says so.
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun content(): Flow<RunningTileContent> =
        combine(app.cache.running, app.cache.entries) { running, entries -> runningTileContent(running?.entry, entries.map { it.entry }) }

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
