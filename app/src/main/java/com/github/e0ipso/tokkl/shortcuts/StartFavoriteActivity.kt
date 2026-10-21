package com.github.e0ipso.tokkl.shortcuts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.TokklApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A tap on a favorite's launcher shortcut (docs/PRD.md N6). It starts the favorite on the phone
 * and queues the start, as the Timer page's favorite does, without opening the app or waiting for
 * the network, and says so in a toast. The queue sends the start at once, or when the network
 * returns, and the running timer's notification follows the cache.
 *
 * Its window is transparent and takes no input. It stays foreground until Android shows the
 * confirmation, then finishes: finishing before the toast can suppress a background toast when
 * notifications are disabled. Keeping the activity alive also protects the local queue write.
 * A launcher starts a shortcut's intent as the app that published it, so it is not exported.
 */
class StartFavoriteActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.takeIf { it.action == ACTION_START_FAVORITE }?.getStringExtra(EXTRA_SHORTCUT_ID)
        if (id == null) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        val app = applicationContext as TokklApplication
        CoroutineScope(Dispatchers.Main).launch {
            val names = favoriteNames(app)
            val message = try {
                val started = withContext(Dispatchers.IO) {
                    startFavoriteShortcut(
                        id = id,
                        available = app.capabilities.state.value.favorites,
                        favorites = app.cache.favorites.first(),
                        projects = app.cache.projects.first(),
                        workspaceId = app.accountSettings.state.value?.defaultWorkspaceId,
                        names = names,
                        start = app.changes::start,
                    )
                }
                when (started) {
                    is ShortcutStart.Started -> app.getString(R.string.shortcut_started, started.name)
                    ShortcutStart.Gone -> app.getString(R.string.shortcut_gone)
                }
            } catch (e: SQLiteException) {
                // The disk refused the change, so nothing started.
                app.getString(R.string.shortcut_failed)
            }
            Toast.makeText(app, message, Toast.LENGTH_SHORT).apply {
                addCallback(object : Toast.Callback() {
                    override fun onToastShown() { finish() }
                    override fun onToastHidden() { finish() }
                })
                show()
            }
            // A system that refuses the toast must still release the transparent activity.
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, CONFIRMATION_TIMEOUT_MS)
        }
    }

    companion object {
        private const val CONFIRMATION_TIMEOUT_MS = 4_000L
        const val ACTION_START_FAVORITE = "com.github.e0ipso.tokkl.action.START_FAVORITE"
        const val EXTRA_SHORTCUT_ID = "com.github.e0ipso.tokkl.extra.SHORTCUT_ID"

        /** The intent of the favorite shortcut [id]. A launcher keeps it, so it carries only the id. */
        fun intent(context: Context, id: String): Intent =
            Intent(context, StartFavoriteActivity::class.java)
                .setAction(ACTION_START_FAVORITE)
                .putExtra(EXTRA_SHORTCUT_ID, id)
    }
}

/** The fixed words in a favorite's label, in the app's language now. */
internal fun favoriteNames(context: Context): FavoriteNames =
    FavoriteNames(
        untitled = context.getString(R.string.favorites_untitled),
        withProject = { name, project -> context.getString(R.string.favorites_with_project, name, project) },
    )
