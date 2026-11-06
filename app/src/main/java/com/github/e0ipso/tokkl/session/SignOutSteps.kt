package com.github.e0ipso.tokkl.session

import android.app.NotificationManager
import android.content.Context
import com.github.e0ipso.tokkl.api.SharedHttpClient
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.BackgroundSend
import com.github.e0ipso.tokkl.cache.ChangeSender
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.capabilities.CapabilitiesSource
import com.github.e0ipso.tokkl.capabilities.CapabilitiesStore
import com.github.e0ipso.tokkl.notification.RunningNotificationSync
import com.github.e0ipso.tokkl.pomodoro.AccountPomodoroNotification
import com.github.e0ipso.tokkl.pomodoro.AlarmClockPomodoroScheduler
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsSetting
import com.github.e0ipso.tokkl.pomodoro.PomodoroDefaultsStore
import com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseAlarm
import com.github.e0ipso.tokkl.pomodoro.PomodoroSessionNotifier
import com.github.e0ipso.tokkl.reminder.ReminderSetting
import com.github.e0ipso.tokkl.reminder.ReminderStore
import com.github.e0ipso.tokkl.reminder.ReminderSync
import com.github.e0ipso.tokkl.shortcuts.FavoriteShortcutSync
import com.github.e0ipso.tokkl.sync.DiscardedChanges
import com.github.e0ipso.tokkl.sync.DiscardedChangesStore
import com.github.e0ipso.tokkl.sync.LastAnswer
import com.github.e0ipso.tokkl.timer.TimerViewSetting
import com.github.e0ipso.tokkl.timer.TimerViewStore
import com.github.e0ipso.tokkl.timerstate.AccountTimerStates
import com.github.e0ipso.tokkl.timerstate.TimerStateSource
import com.github.e0ipso.tokkl.timerstate.TimerStateStore
import com.github.e0ipso.tokkl.timerstate.TimerSync
import com.github.e0ipso.tokkl.timerstate.TimerSyncFollower
import com.github.e0ipso.tokkl.trust.SessionTrust
import com.github.e0ipso.tokkl.widget.PomodoroWidgetFieldsStore

/**
 * The steps of sign-out (docs/PRD.md S6), in the order [SignOut] runs them. It forgets the server
 * and the token with its Keystore key first, so the app sends nothing new, and retires the timer
 * state, so a late answer cannot write it or the cache again. It stops the timer event stream,
 * its reconnects and its service. Then it cancels the queue's background work and every call in
 * flight, waits for the refresh and the queue's drain to end, and clears the timer state's store
 * and the cache with the queue, the Pomodoro alarm, its ringing and its notification, the running
 * timer's notification, the launcher shortcuts, the account settings, the capabilities, the Timer
 * page's view, the Pomodoro defaults, the reminder and its settings, the trust decisions, the last
 * answer and the count of discarded changes. MainActivity then drops its view models, and with
 * them the history's older days and what every other screen loaded. The theme and the sign-in
 * cooldowns stay: they belong to the phone and to a server, not to the account.
 *
 * What loads from disk or follows the cache comes in as [Lazy], so making the steps loads nothing.
 */
internal class SignOutSteps(
    private val context: Context,
    private val session: Session,
    private val timerStates: AccountTimerStates,
    private val pomodoroAlarm: PomodoroPhaseAlarm,
    private val timerSync: Lazy<TimerSync<TimerStateSource>>,
    private val timerSyncFollower: TimerSyncFollower,
    private val pomodoroNotification: AccountPomodoroNotification,
    private val accountSettings: Lazy<AccountSettingsSource>,
    private val sender: Lazy<ChangeSender>,
    private val cache: Lazy<AccountCache>,
    private val reminder: Lazy<ReminderSync>,
    private val reminderSettings: Lazy<ReminderSetting>,
    private val runningNotification: Lazy<RunningNotificationSync>,
    private val favoriteShortcuts: Lazy<FavoriteShortcutSync>,
    private val capabilities: Lazy<CapabilitiesSource>,
    private val alarmClock: AlarmClockPomodoroScheduler,
    private val timerView: Lazy<TimerViewSetting>,
    private val pomodoroDefaults: Lazy<PomodoroDefaultsSetting>,
    private val sessionTrust: SessionTrust,
    private val lastAnswer: Lazy<LastAnswer>,
    private val discarded: Lazy<DiscardedChanges>,
    private val stores: Stores,
) {

    /** The stores sign-out clears directly. */
    class Stores(
        val timerState: TimerStateStore,
        val pomodoroWidgetFields: PomodoroWidgetFieldsStore,
        val reminder: ReminderStore,
        val accountSettings: AccountSettingsStore,
        val capabilities: CapabilitiesStore,
        val timerView: TimerViewStore,
        val pomodoroDefaults: PomodoroDefaultsStore,
        val serverTrust: ServerTrustStore,
        val discardedChanges: DiscardedChangesStore,
    )

    val steps: List<suspend () -> Unit> = listOf(
        { session.clear() },
        { timerStates.source.retire() },
        // With the held state retired, nothing arms or rings the account's phase again.
        { pomodoroAlarm.release() },
        // Before the stores clear, so no stream or reconnect of this account outlives them.
        {
            timerSync.value.stop()
            timerSyncFollower.stopService()
        },
        { pomodoroNotification.current.forget() },
        // ID 3 is shared with the session notification, including a non-ringing form.
        // Keep this independent of service cleanup so a failed stop still removes it.
        { context.getSystemService(NotificationManager::class.java).cancel(PomodoroSessionNotifier.NOTIFICATION_ID) },
        { BackgroundSend.cancel(context) },
        { RunningCheck.cancelAndWait(context) },
        { SharedHttpClient.cancelAll() },
        { accountSettings.value.forget() },
        { sender.value.stop() },
        { stores.timerState.clear() },
        { stores.pomodoroWidgetFields.clear() },
        {
            cache.value.clear()
            pomodoroNotification.renew()
        },
        { reminder.value.forget() },
        {
            reminderSettings.value.forget()
            stores.reminder.clear()
        },
        { runningNotification.value.forget() },
        { favoriteShortcuts.value.forget() },
        { stores.accountSettings.clear() },
        {
            capabilities.value.forget()
            stores.capabilities.clear()
            alarmClock.watchTimerSync()
        },
        {
            timerView.value.forget()
            stores.timerView.clear()
        },
        {
            pomodoroDefaults.value.forget()
            stores.pomodoroDefaults.clear()
        },
        { stores.serverTrust.clear() },
        { sessionTrust.forget() },
        { lastAnswer.value.forget() },
        {
            discarded.value.dismiss()
            stores.discardedChanges.clear()
        },
    )
}
