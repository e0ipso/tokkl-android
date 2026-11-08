package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.ui.WindowWidth

/**
 * This route, or Timer when it is a page in [unavailable], the pages the server lacks
 * (`TopLevelPages.unavailable`). A back stack restored from before a capability was lost, or a
 * direct open of a page, then shows Timer.
 */
fun Route.orTimerIfUnavailable(unavailable: Set<Route>): Route = if (this in unavailable) Route.Timer else this

/** Whether the stack holds a page in [unavailable]. */
internal fun List<Route>.holdsAnyOf(unavailable: Set<Route>): Boolean = any { it in unavailable }

/**
 * Whether the entry form on top of [stack] shows beside the Timer page below it: on an expanded
 * window ([width]) while the Timer page shows its List view ([listView]), so the history and the
 * form share the width. The Calendar view keeps the whole width for the week, so there the form
 * takes the screen, as on a phone.
 */
internal fun showsEntryBeside(width: WindowWidth, listView: Boolean, stack: List<Route>): Boolean =
    width == WindowWidth.EXPANDED && listView &&
        stack.lastOrNull() is Route.Entry && stack.getOrNull(stack.lastIndex - 1) == Route.Timer
