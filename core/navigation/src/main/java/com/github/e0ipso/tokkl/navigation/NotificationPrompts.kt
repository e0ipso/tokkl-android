package com.github.e0ipso.tokkl.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel

/**
 * What a screen shares with the signed-in frame's notification permission prompt: the [starts]
 * and [retries] it makes, which may ask for the permission, and a count of the prompt's
 * [answers], which tells the screen to read the permission again. The Pomodoro page uses it; the
 * Timer page's starts reach the prompt through its view model.
 */
class NotificationPrompts {
    val starts = Channel<Unit>(Channel.UNLIMITED)
    val retries = Channel<Unit>(Channel.UNLIMITED)
    var answers by mutableIntStateOf(0)
}
