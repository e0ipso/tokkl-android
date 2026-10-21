package com.github.e0ipso.tokkl.shortcuts

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.github.e0ipso.tokkl.R

/**
 * The app's dynamic shortcuts, which the launcher lists on a long press of the app icon. Each one
 * opens [StartFavoriteActivity] with its favorite's id.
 */
class LauncherShortcuts(private val context: Context) {

    private val manager: ShortcutManager get() = context.getSystemService(ShortcutManager::class.java)

    /** How many shortcuts the launcher takes. */
    val limit: Int by lazy { manager.maxShortcutCountPerActivity }

    /** The shortcuts the launcher has, in their order. */
    fun published(): List<FavoriteShortcut> =
        manager.dynamicShortcuts.sortedBy { it.rank }.map { info ->
            FavoriteShortcut(info.id, info.shortLabel.toString(), (info.longLabel ?: info.shortLabel).toString())
        }

    /**
     * Replaces the shortcuts with [shortcuts], ranked in their order. While Android holds back an
     * app that set them too often, nothing changes, and the next change to the favorites or the
     * next process sets them.
     */
    fun publish(shortcuts: List<FavoriteShortcut>) {
        if (shortcuts.isEmpty()) {
            manager.removeAllDynamicShortcuts()
        } else {
            manager.setDynamicShortcuts(shortcuts.mapIndexed { rank, shortcut -> info(shortcut, rank) })
        }
    }

    private fun info(shortcut: FavoriteShortcut, rank: Int): ShortcutInfo =
        ShortcutInfo.Builder(context, shortcut.id)
            .setShortLabel(shortcut.shortLabel)
            .setLongLabel(shortcut.longLabel)
            .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_favorite))
            .setIntent(StartFavoriteActivity.intent(context, shortcut.id))
            .setRank(rank)
            .build()
}
