package com.github.e0ipso.tokkl.tile

import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.platform.io
import com.github.e0ipso.tokkl.timerstate.TimerControlsSource
import kotlinx.coroutines.flow.map
import org.koin.dsl.module

/** What the Quick Settings tile shows, and its refreshes. */
val tileFeatureModule = module {
    single {
        RunningTileContents(
            controls = get<TimerControlsSource>().controls,
            entries = get<AccountCache>().entries.map { rows -> rows.map { it.entry } },
        )
    }
    single {
        TileRefresh(network = get(), session = get(), cache = get(), account = get(), scope = get(), io = io())
    }
}
