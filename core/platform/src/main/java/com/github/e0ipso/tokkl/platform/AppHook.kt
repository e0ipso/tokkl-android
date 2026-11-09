package com.github.e0ipso.tokkl.platform

/**
 * What one module does when the process starts, when MainActivity comes to the foreground, and
 * when the user allows notifications. Each module declares its hooks as classes of their own,
 * binds them to this interface in its Koin module, and gives each an [order]: the hooks run from
 * the lowest order up, and the modules space their orders by ten, so a hook fits between two
 * others. The app's AppHookOrderTest lists the whole order. TokklApplication runs every
 * [processStart] once, in order, on the main thread; MainActivity runs every [foregroundStart] on
 * each of its starts, a rotation's too, and every [notificationsAllowed] after a grant, each in
 * order on the I/O dispatcher. A hook loads its component when it first runs, so the process
 * loads each one at its own step.
 */
interface AppHook {

    /** Where this hook runs among the others, lowest first. */
    val order: Int

    fun processStart() = Unit

    suspend fun foregroundStart() = Unit

    suspend fun notificationsAllowed() = Unit
}
