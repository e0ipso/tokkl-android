package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.model.Project
import com.github.e0ipso.tokkl.model.Task
import com.github.e0ipso.tokkl.session.AccountSettings
import kotlinx.coroutines.flow.StateFlow

/** The cached projects and tasks, for the timer's labels and its picks. */
internal data class Catalog(val projects: List<Project> = emptyList(), val tasks: List<Task> = emptyList())

/**
 * The timer state as of now, without labels, which the taps and the field edits act on: [local]
 * over the [cached] controls, in the default workspace of [settings], with Pause where
 * [pauseOffered] says the server takes it.
 */
internal class TimerSnapshot(
    private val cached: StateFlow<CachedControls>,
    private val local: StateFlow<TimerTaps>,
    private val settings: StateFlow<AccountSettings?>,
    private val pauseOffered: StateFlow<Boolean>,
) {
    fun now(): TimerState =
        timerState(cached.value, settings.value?.defaultWorkspaceId, local.value, pauseOffered.value)
}
