package com.github.e0ipso.tokkl.tile

import com.github.e0ipso.tokkl.platform.io
import org.koin.dsl.module

/** The Quick Settings tile's refreshes. */
val tileFeatureModule = module {
    single {
        TileRefresh(network = get(), session = get(), cache = get(), results = get(), scope = get(), io = io())
    }
}
