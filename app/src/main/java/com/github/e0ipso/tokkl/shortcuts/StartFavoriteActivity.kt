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
import com.github.e0ipso.tokkl.core.shared.R as SharedR
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.platform.IoDispatcher
import com.github.e0ipso.tokkl.session.AccountSettingsSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

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

    private val capabilities: CapabilitiesSource by inject()
    private val cache: AccountCache by inject()
    private val accountSettings: AccountSettingsSource by inject()
    private val changes: ChangeQueue by inject()
    private val scope: CoroutineScope by inject()
    private val io: CoroutineDispatcher by inject(IoDispatcher)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.takeIf { it.action == ACTION_START_FAVORITE }?.getStringExtra(EXTRA_SHORTCUT_ID)
        if (id == null) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        val app = applicationContext
        scope.launch {
            val names = favoriteNames(app)
            val message = try {
                val started = withContext(io) {
                    startFavoriteShortcut(
                        id = id,
                        available = capabilities.state.value.favorites,
                        favorites = cache.favorites.first(),
                        projects = cache.projects.first(),
                        workspaceId = accountSettings.state.value?.defaultWorkspaceId,
                        names = names,
                        start = changes::start,
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
        untitled = context.getString(SharedR.string.favorites_untitled),
        withProject = { name, project -> context.getString(SharedR.string.favorites_with_project, name, project) },
    )
