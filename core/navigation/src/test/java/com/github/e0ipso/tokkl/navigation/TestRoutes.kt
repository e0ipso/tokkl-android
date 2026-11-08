package com.github.e0ipso.tokkl.navigation

import kotlinx.serialization.Serializable

/** Routes as features declare them, for the navigation's own tests. */
@Serializable
internal data object PageRoute : Route

@Serializable
internal data object OtherPageRoute : Route

/** A screen a page opens above itself. */
@Serializable
internal data object AboveRoute : Route

/** A screen that hides the bar, as the fullscreen timer does. */
@Serializable
internal data object FocusedRoute : Route {
    override val focused: Boolean get() = true
}

@Serializable
internal data class StartRoute(val name: String? = null) : Route
