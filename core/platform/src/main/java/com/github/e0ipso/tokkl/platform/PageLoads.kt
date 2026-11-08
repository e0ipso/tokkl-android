package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What a page that shows what it loads from the server does as its screen reads its state
 * ([stateInPage]). Each call comes on the thread of the view model's scope. The page's choices,
 * such as an open form or the range on screen, live in its view model and outlast these calls: a
 * page that shows again loads again for the same choices.
 */
interface PageLoads {

    /** A screen started reading the page: the first time, or after none read it for five seconds. */
    fun shown()

    /** The app came back to the foreground while a screen read the page. */
    fun returned()

    /** The network reaches the internet anew while a screen reads the page. */
    fun reconnected()
}

/**
 * Shares this page state while a screen reads it and for five seconds after ([whileShown]), so a
 * rotation loads nothing, and tells [loads] when to load. A screen that stops reading for longer,
 * as when another activity or the background hides it, shows the page again through
 * [PageLoads.shown]; a trip to the background while the state is still shared loads again through
 * [PageLoads.returned]. The page goes with its view model, when its route leaves the back stack.
 */
fun <T> Flow<T>.stateInPage(scope: CoroutineScope, initial: T, app: AppForeground, loads: PageLoads): StateFlow<T> =
    channelFlow {
        launch { app.returns.collect { loads.returned() } }
        launch { app.reconnects.collect { loads.reconnected() } }
        loads.shown()
        this@stateInPage.collect { send(it) }
    }.stateIn(scope, whileShown, initial)
