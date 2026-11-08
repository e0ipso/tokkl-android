package com.github.e0ipso.tokkl.navigation

import androidx.annotation.StringRes
import org.koin.core.context.GlobalContext

/** The top-level pages the app's features bind, as the navigation host gets them from Koin. */
val topLevelPages: TopLevelPages get() = GlobalContext.get().get()

/** The label of the top-level page this route opens, in the bar, the rail or the More sheet. */
@get:StringRes
val Route.label: Int get() = checkNotNull(topLevelPages.of(this)) { "$this is no top-level page" }.label
