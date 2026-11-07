package com.github.e0ipso.tokkl.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.github.e0ipso.tokkl.storage.PreferencesFile

/**
 * Whether a timer start asks for the notification permission (docs/PRD.md N5). Android 13 and
 * later need POST_NOTIFICATIONS before the running timer's notification shows, so the first timer
 * start made in the app asks for it, after the start, and later starts never ask again. A start
 * from the widget or the tile does not count, because a permission request needs the app on
 * screen: only the app calls [atStartInApp]. Below Android 13 nothing is asked or recorded.
 *
 * @param sdkInt the device's Android version, [Build.VERSION.SDK_INT].
 * @param record whether the app has dealt with its one question.
 * @param granted whether the user allows the app's notifications now.
 */
class NotificationPermissionAsk(
    private val sdkInt: Int,
    private val record: AskRecord,
    private val granted: () -> Boolean,
) {

    /**
     * Called for each timer start made in the app, after the start. True when this start asks for
     * the permission. [requested] records a launched request, before its answer. Merely checking
     * does not consume the question if the screen goes away before it can launch. Notifications
     * already allowed count as handled too. Reads and writes the disk.
     */
    fun atStartInApp(): Boolean {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU || record.asked()) return false
        if (!granted()) return true
        record.markAsked()
        return false
    }

    /** Records that the platform request launched, whatever the eventual answer. Writes the disk. */
    fun requested() = record.markAsked()

    /** Whether the app has asked for the notification permission. */
    interface AskRecord {
        fun asked(): Boolean

        fun markAsked()
    }

    companion object {
        /** The question as the app asks it on this device. */
        fun forApp(context: Context): NotificationPermissionAsk {
            val app = context.applicationContext
            return NotificationPermissionAsk(Build.VERSION.SDK_INT, NotificationPermissionStore(app)) {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
}

/**
 * Records in its own private preferences file that the app has asked for the notification
 * permission. It belongs to the phone, not the account, so a sign-out keeps it, and it stays on
 * the phone, because the backup and data extraction rules leave out every domain.
 *
 * The first call loads the preferences file, so it reads the disk.
 */
class NotificationPermissionStore(context: Context) : NotificationPermissionAsk.AskRecord {

    private val file = PreferencesFile(context, FILE_NAME)

    override fun asked(): Boolean = file.preferences.getBoolean(KEY_ASKED, false)

    override fun markAsked() {
        file.commit("Could not record the notification permission request") { putBoolean(KEY_ASKED, true) }
    }

    private companion object {
        const val FILE_NAME = "notification_permission"
        const val KEY_ASKED = "asked"
    }
}
