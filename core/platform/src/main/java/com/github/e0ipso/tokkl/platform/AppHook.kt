package com.github.e0ipso.tokkl.platform

/**
 * What one module does when the process starts, when MainActivity comes to the foreground, and
 * when the user allows notifications. Each module declares its hooks as classes of their own,
 * binds them to this interface in its Koin module, and gives each the [StartupPhase] it runs in.
 * The hooks run in phase order, as [inStartupOrder] puts them, and the app's AppHookOrderTest
 * lists the whole order. TokklApplication runs every [processStart] once, in order, on the main
 * thread; MainActivity runs every [foregroundStart] on each of its starts, a rotation's too, and
 * every [notificationsAllowed] after a grant, each in order on the I/O dispatcher. A hook loads
 * its component when it first runs, so the process loads each one at its own step.
 */
interface AppHook {

    /** Where this hook runs among the others. No other hook shares it. */
    val phase: StartupPhase

    fun processStart() = Unit

    suspend fun foregroundStart() = Unit

    suspend fun notificationsAllowed() = Unit
}

/**
 * The steps of the app's startup, in the order they run. Each [AppHook] picks one, so core names
 * no feature, and one hook runs in each: two in one phase would leave their order unset, which
 * [inStartupOrder] refuses. The surfaces that show the running timer follow the cache before the
 * running check and the timer sync can write to it, so none misses a first change.
 */
enum class StartupPhase {
    /** Forwards the refreshes the other components ask for, before any of them asks. */
    FORWARD_REFRESHES,

    /** The running timer's notification, which later phases may change. */
    TIMER_NOTIFICATION,

    /** The Pomodoro phase alarm, armed from the held state before the notification shows the phase. */
    PHASE_ALARM,

    /** The Pomodoro session notification. */
    SESSION_NOTIFICATION,

    /** The running entry's reminder, after the notifications and before the widgets. */
    REMINDER,

    /** The Timer home screen widget's redraws. */
    TIMER_WIDGET,

    /** The Pomodoro home screen widget's redraws, after the Timer widget's. */
    POMODORO_WIDGET,

    /** The favorites as launcher shortcuts, after the widgets. */
    LAUNCHER_SHORTCUTS,

    /** The check of the running entry while a timer runs, once every surface follows the cache. */
    RUNNING_CHECK,

    /** Starts or stops the timer sync on MainActivity's starts, last. */
    TIMER_SYNC,
}

/**
 * These hooks in the order of their [AppHook.phase]. Throws [IllegalArgumentException] when two
 * share a phase, since nothing would order them.
 */
fun Collection<AppHook>.inStartupOrder(): List<AppHook> {
    val shared = groupBy { it.phase }.filterValues { it.size > 1 }
    require(shared.isEmpty()) {
        "two hooks share a startup phase: " +
            shared.entries.joinToString { (phase, hooks) -> "$phase ${hooks.map { it::class.simpleName }}" }
    }
    return sortedBy { it.phase }
}
