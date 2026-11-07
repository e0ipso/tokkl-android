package com.github.e0ipso.tokkl.session

/**
 * A component that keeps the signed-in account's data on the phone, and forgets it when the
 * account signs out (docs/PRD.md S6). [SignOut] runs every owner once, phase by phase, in
 * [signOutPhase] order.
 */
interface AccountDataOwner {

    /** The phase of sign-out this owner's step runs in. */
    val signOutPhase: SignOutPhase

    /** Forgets this owner's part of the account. A failure leaves it for the next sign-out. */
    suspend fun forgetAccount()
}

/**
 * The phases of sign-out, in the order [SignOut] runs them. Each phase starts once every step of
 * the one before has ended. The steps of one phase run one after another in no set order, so a
 * component whose parts need an order runs them in its own step.
 *
 * The theme and the sign-in cooldowns stay: they belong to the phone and to a server, not to the
 * account.
 */
enum class SignOutPhase {
    /** Forgets the server and the token with its Keystore key: the app counts as signed out and sends nothing new. */
    FORGET_SESSION,

    /** Retires what an answer writes to, the account's timer state, so a late answer changes nothing. */
    RETIRE_STATE,

    /**
     * Stops what still runs for the account: alarms, the event stream with its reconnects and
     * service, the notifications' observers, background work and every call in flight.
     */
    STOP_WORK,

    /** Waits for a running refresh, which then stores nothing. A refresh can start a drain, so it ends first. */
    FINISH_REFRESH,

    /** Waits for the queue's drain to end. */
    FINISH_SENDING,

    /** Clears the cache and its queue, which the notifications, widgets and shortcuts follow. */
    CLEAR_CACHE,

    /** Clears the rest of what the account left: stores, notifications, shortcuts, settings and trust. */
    CLEAR_DATA,
}

/** A sign-out step that belongs to no single component, such as cancelling calls. */
class SignOutStep(override val signOutPhase: SignOutPhase, private val step: suspend () -> Unit) : AccountDataOwner {
    override suspend fun forgetAccount() = step()
}
