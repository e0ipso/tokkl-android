package com.github.e0ipso.tokkl.navigation

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.Flow

/**
 * The running dot that a feature puts on the item of its top-level page [route] in the bar and
 * the rail while [shown] says so, such as Timer's while an entry runs. TalkBack reads [label] as
 * the item's state, so the dot is not the only sign. Each feature binds its own in Koin, and the
 * signed-in frame reads every one.
 */
class PageBadge(val route: Route, @param:StringRes val label: Int, val shown: Flow<Boolean>)
