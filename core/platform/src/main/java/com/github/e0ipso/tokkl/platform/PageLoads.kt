package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * What a page that shows what it loads from the server does as its screen reads its state
 * ([stateInPage]). Each call comes on the thread of the view model's scope.
 */
interface PageLoads {

    /** A screen started reading the page: the first time, or after none read it for five seconds. */
    fun shown()

    /** The app came back to the foreground while a screen read the page. */
    fun returned()

    /** The network reaches the internet anew while a screen reads the page. */
    fun reconnected()

    /** No screen has read the page for five seconds, and the app is still in the foreground: the page went. */
    fun left()
}

/**
 * Shares this page state while a screen reads it and for five seconds after, as [whileShown]
 * does, so a rotation loads nothing, and tells [loads] when to load. A trip to the background
 * stops the screen reading, but the app is not in the foreground then, so the page has not left,
 * and its return loads the page again: through [PageLoads.returned] while the state is still
 * shared, through [PageLoads.shown] once it stopped.
 */
fun <T> Flow<T>.stateInPage(scope: CoroutineScope, initial: T, app: AppForeground, loads: PageLoads): StateFlow<T> =
    channelFlow {
        launch { app.returns.collect { loads.returned() } }
        launch { app.reconnects.collect { loads.reconnected() } }
        loads.shown()
        this@stateInPage.collect { send(it) }
    }.stateIn(scope, WhileShownPage(app, loads::left), initial)

/**
 * [whileShown], which calls [left] before it stops sharing when the app is in the foreground then,
 * so the page's state can reset while it is still shared.
 */
private class WhileShownPage(private val app: AppForeground, private val left: () -> Unit) : SharingStarted {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun command(subscriptionCount: StateFlow<Int>): Flow<SharingCommand> {
        var started = false
        return subscriptionCount.transformLatest { count ->
            if (count > 0) {
                started = true
                emit(SharingCommand.START)
            } else if (started) {
                delay(SHOWN_TIMEOUT_MILLIS)
                if (app.foreground.value) left()
                emit(SharingCommand.STOP)
            }
        }.distinctUntilChanged()
    }
}
