package com.github.e0ipso.tokkl.tile

import com.github.e0ipso.tokkl.platform.io
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The Quick Settings tile's refreshes. */
val tileFeatureModule = module {
    single { TileRefresh(androidContext(), get(), get(), get(), get(), io()) }
}
