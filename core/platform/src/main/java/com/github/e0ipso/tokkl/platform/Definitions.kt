package com.github.e0ipso.tokkl.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import org.koin.core.scope.Scope

/** The IO dispatcher, for the Koin definitions of the app and its features. */
fun Scope.io(): CoroutineDispatcher = get(IoDispatcher)

/**
 * The scope a view model gets, as `viewModelScope` makes it by default: the main thread, and a
 * failed child does not cancel the others. The view model cancels it when it is cleared.
 */
fun viewModelCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/**
 * How a view model shares a flow that its screen reads: while the screen reads it, and for five
 * seconds after, so a rotation does not start it over.
 */
val whileShown: SharingStarted = SharingStarted.WhileSubscribed(stopTimeoutMillis = SHOWN_TIMEOUT_MILLIS)

/** How long a view model keeps sharing a screen's state after the screen stops reading it. */
private const val SHOWN_TIMEOUT_MILLIS = 5_000L
