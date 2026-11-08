package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.BackgroundSendWorker
import com.github.e0ipso.tokkl.cache.RunningCheckWorker
import com.github.e0ipso.tokkl.platform.io
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module

/** The queue's WorkManager workers, which KoinWorkerFactory makes with their dependencies. */
internal val workerModule = module {
    worker { params ->
        BackgroundSendWorker(
            context = androidContext(),
            params = params.get(),
            sender = get(),
            runs = get(),
            workManager = get(),
            io = io(),
        )
    }
    worker { params ->
        RunningCheckWorker(
            context = androidContext(),
            params = params.get(),
            cache = get(),
            session = get(),
            results = get(),
            io = io(),
        )
    }
}
