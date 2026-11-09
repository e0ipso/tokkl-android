package com.github.e0ipso.tokkl.widget

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.github.e0ipso.tokkl.cache.AccountCache
import com.github.e0ipso.tokkl.cache.RunningCheck
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.platform.CurrentWorkManager
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.timerstate.TimerControls
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.Clock
import kotlin.coroutines.CoroutineContext

/**
 * What the home screen widget shows (docs/PRD.md N3): the running entry or paused timer (T1)
 * of [timerControls], the favorites from the cache, the favorites capability and the account's
 * default workspace, all from the phone. Cached history and ID mappings share the Timer
 * page's day rule. App opens and the existing running check reread the day even offline or
 * when an unchanged current entry produces no Room write. No widget work is scheduled.
 *
 * @param account the name the signed-in account's timer controls carry, read with each state.
 *   A sign-in changes [timerControls], so the state reads the new name.
 * @param clock the clock read on each widget event.
 */
class WidgetStateSource(
    application: Application,
    cache: AccountCache,
    accountSettings: Flow<AccountSettings?>,
    capabilities: Flow<Capabilities>,
    timerControls: Flow<TimerControls>,
    account: () -> String,
    workManager: CurrentWorkManager,
    clock: Clock,
    io: CoroutineContext,
) {

    val state: Flow<WidgetState> = run {
        val openings = callbackFlow {
            val callbacks = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    trySend(Unit)
                }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
            application.registerActivityLifecycleCallbacks(callbacks)
            trySend(Unit)
            awaitClose { application.unregisterActivityLifecycleCallbacks(callbacks) }
        }
        val refreshes = merge(
            openings,
            workManager.get().getWorkInfosForUniqueWorkFlow(RunningCheck.WORK_NAME).map { Unit },
        )
        val history = combine(
            cache.timeEntries,
            cache.entryIdsChanged,
            refreshes,
        ) { entries, _, _ -> entries to cache.serverIdsOf(entries) }
        val catalog = combine(
            cache.projects,
            cache.tasks,
            history,
        ) { projects, tasks, history -> Triple(projects, tasks, history) }
        combine(
            accountSettings,
            capabilities,
            timerControls,
            cache.favorites,
            catalog,
        ) { settings, flags, controls, favorites, (projects, tasks, history) ->
            val (entries, mappings) = history
            widgetState(
                settings?.defaultWorkspaceId, account(), controls, favorites, flags.favorites, projects, tasks,
                entries = entries, settings = settings, now = clock.instant(), serverIdOf = mappings,
            )
        }.flowOn(io).distinctUntilChanged()
    }
}
