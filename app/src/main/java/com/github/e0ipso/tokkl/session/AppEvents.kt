package com.github.e0ipso.tokkl.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What MainActivity's lifecycle and the default network tell the screens' view models. */
enum class AppEvent {
    /** MainActivity started: back from the background, or again after a configuration change. */
    CAME_TO_FOREGROUND,

    /** MainActivity stopped for a trip to the background, not for a configuration change. */
    WENT_TO_BACKGROUND,

    /** The default network reports that it reaches the internet, while MainActivity is started. Each report counts. */
    ONLINE,

    /** The network came back after the phone was offline, while MainActivity is started. Once per return. */
    RECONNECTED,
}

/**
 * Carries [AppEvent]s from MainActivity's foreground observer to the view models that collect
 * [events]. An event reaches only the view models that exist when it happens. A view model made
 * later loads afresh anyway. Nothing is sent while MainActivity is stopped.
 */
class AppEvents {
    private val mutableEvents = MutableSharedFlow<AppEvent>(extraBufferCapacity = BUFFER)
    val events: SharedFlow<AppEvent> = mutableEvents.asSharedFlow()

    /** Call it on the main thread. */
    fun send(event: AppEvent) {
        mutableEvents.tryEmit(event)
    }

    private companion object {
        const val BUFFER = 64
    }
}
