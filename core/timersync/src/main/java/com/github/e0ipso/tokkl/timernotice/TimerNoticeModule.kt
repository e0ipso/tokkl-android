package com.github.e0ipso.tokkl.timernotice

import com.github.e0ipso.tokkl.platform.io
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module

/**
 * The timer's server actions from outside the screens: the notification's and the widget's Pause,
 * Resume and Stop while paused, as expedited work that outlives the broadcast that asked for them.
 * What a failure says comes from the [TimerFailureNotice] a feature binds.
 */
val timerNoticeModule = module {
    worker { params -> TimerActionWorker(androidContext(), params.get(), get(), get(), io()) }
}
