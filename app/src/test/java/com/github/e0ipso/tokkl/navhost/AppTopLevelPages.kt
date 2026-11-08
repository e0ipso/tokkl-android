package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.di.FeatureScreens
import com.github.e0ipso.tokkl.di.appModules
import com.github.e0ipso.tokkl.navigation.FeatureEntries
import com.github.e0ipso.tokkl.navigation.TopLevelPages
import org.koin.dsl.koinApplication

private val appKoin by lazy { koinApplication { modules(appModules) }.koin }

/** The top-level pages the app's features name, as MainActivity gets them from Koin. */
internal val appTopLevelPages: TopLevelPages by lazy { appKoin.get() }

/** Every feature's screens, as MainActivity gets them from Koin. */
internal val appFeatureEntries: List<FeatureEntries> by lazy { appKoin.get(FeatureScreens) }
