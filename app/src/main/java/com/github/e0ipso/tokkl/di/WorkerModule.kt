package com.github.e0ipso.tokkl.di

import com.github.e0ipso.tokkl.cache.BackgroundSendWorker
import com.github.e0ipso.tokkl.cache.RunningCheckWorker
import com.github.e0ipso.tokkl.platform.io
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module

/** The queue's WorkManager workers, which KoinWorkerFactory makes with their dependencies. */
internal val workerModule = module {
    worker { params -> BackgroundSendWorker(androidContext(), params.get(), get(), get(), get(), io()) }
    worker { params -> RunningCheckWorker(androidContext(), params.get(), get(), get(), get(), io()) }
}
