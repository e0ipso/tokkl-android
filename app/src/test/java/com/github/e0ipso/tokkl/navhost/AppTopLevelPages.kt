package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.di.appModules
import com.github.e0ipso.tokkl.navigation.FeatureNavigation
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import org.koin.dsl.koinApplication

private val appKoin by lazy { koinApplication { modules(appModules) }.koin }

/** The top-level pages the app's features name, as MainActivity gets them from Koin. */
internal val appTopLevelPages: TopLevelPages by lazy { appKoin.get() }

/** What every feature gives the navigation, as MainActivity gets it from Koin. */
internal val appFeatures: FeatureNavigation by lazy { appKoin.get() }
