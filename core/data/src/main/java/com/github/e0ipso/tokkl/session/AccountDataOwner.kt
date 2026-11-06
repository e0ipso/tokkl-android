package com.github.e0ipso.tokkl.session

/**
 * A component that keeps the signed-in account's data on the phone, and forgets it when the
 * account signs out (docs/PRD.md S6). [SignOut] runs every owner once, in [signOutOrder].
 */
interface AccountDataOwner {

    /** Where this owner's step runs in sign-out. Each owner has its own. */
    val signOutOrder: SignOutOrder

    /** Forgets this owner's part of the account. A failure leaves it for the next sign-out. */
    suspend fun forgetAccount()
}

/**
 * The steps of sign-out, in the order [SignOut] runs them. The session goes first, so the app
 * sends nothing new, and the timer state retires next, so a late answer cannot write it or the
 * cache again. Then sign-out stops what still sends: the timer event stream, the queue's
 * background work and every call in flight, the refresh and the queue's drain. Last, it clears
 * the stores. The theme and the sign-in cooldowns stay: they belong to the phone and to a server,
 * not to the account.
 */
enum class SignOutOrder {
    /** Forgets the server and the token with its Keystore key, so the app counts as signed out. */
    SESSION,

    /** Retires the account's timer state, so a late answer cannot write it or the cache again. */
    TIMER_STATE,

    /** With the held state retired, nothing arms or rings the account's phase again. */
    POMODORO_ALARM,

    /** Before the stores clear, so no stream or reconnect of this account outlives them. */
    TIMER_SYNC,

    POMODORO_NOTIFICATION,

    /**
     * Notification ID 3 is shared with the session notification, including a non-ringing form.
     * A step of its own, so a failed stop of the service still removes it.
     */
    POMODORO_NOTIFICATION_ID,

    BACKGROUND_SEND,
    RUNNING_CHECK,
    HTTP_CALLS,

    /** Shows no settings and waits for a running refresh, which then stores nothing. */
    ACCOUNT_REFRESH,

    /** Waits for the queue's drain to end. */
    CHANGE_SENDER,

    TIMER_STATE_STORE,
    POMODORO_WIDGET_FIELDS,
    CACHE,
    REMINDER,
    REMINDER_SETTING,
    RUNNING_NOTIFICATION,
    FAVORITE_SHORTCUTS,
    ACCOUNT_SETTINGS,
    CAPABILITIES,
    TIMER_VIEW,
    POMODORO_DEFAULTS,
    SERVER_TRUST,
    SESSION_TRUST,
    LAST_ANSWER,
    DISCARDED_CHANGES,
}

/** A sign-out step that belongs to no single component, such as cancelling work or calls. */
class SignOutStep(override val signOutOrder: SignOutOrder, private val step: suspend () -> Unit) : AccountDataOwner {
    override suspend fun forgetAccount() = step()
}
