package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.di.appModules
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import org.koin.dsl.koinApplication

/** The top-level pages the app's features bind, as the navigation host gets them from Koin. */
internal val appTopLevelPages: TopLevelPages by lazy { koinApplication { modules(appModules) }.koin.get() }
