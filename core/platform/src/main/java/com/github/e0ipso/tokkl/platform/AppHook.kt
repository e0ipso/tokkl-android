package com.github.e0ipso.tokkl.platform

/**
 * What one module does when the process starts, when MainActivity comes to the foreground, and
 * when the user allows notifications. Each module binds its own hooks in its Koin module, under a
 * name of its own. TokklApplication runs every [processStart] once, in [order], on the main
 * thread; MainActivity runs every [foregroundStart] on each of its starts, a rotation's too, and
 * every [notificationsAllowed] after a grant, each in [order] on the I/O dispatcher. A hook loads
 * its component when it first runs, so the process loads each one at its own step.
 */
class AppHook(
    val order: AppHookOrder,
    val processStart: () -> Unit = {},
    val foregroundStart: suspend () -> Unit = {},
    val notificationsAllowed: suspend () -> Unit = {},
)

/** The order the hooks run in, which is the order of the entries. */
enum class AppHookOrder {
    /** The refreshes that the refresh's own components ask for, from any process. */
    REFRESH_REQUESTS,

    /** The running timer's notification, which later hooks may change. */
    RUNNING_NOTIFICATION,

    /** The Pomodoro phase alarm. */
    POMODORO_ALARM,

    /** The Pomodoro session's notification. */
    POMODORO_NOTIFICATION,

    /** The long-timer reminder. */
    REMINDER,

    /** The Timer home screen widget. */
    TIMER_WIDGET,

    /** The Pomodoro home screen widget. */
    POMODORO_WIDGET,

    /** The favorites' launcher shortcuts. */
    FAVORITE_SHORTCUTS,

    /** The background check of the running entry. */
    RUNNING_CHECK,

    /** The timer sync service, which MainActivity's starts start when it should run. */
    TIMER_SYNC,
}
