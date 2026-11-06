package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel

/**
 * What the Pomodoro page and the frame's notification permission prompt share: the page's
 * starts and retries, which may ask for the permission, and a count of the prompt's answers,
 * which tells the page to read the permission again.
 */
class NotificationPrompts {
    val pomodoroStarts = Channel<Unit>(Channel.UNLIMITED)
    val retries = Channel<Unit>(Channel.UNLIMITED)
    var answers by mutableIntStateOf(0)
}
