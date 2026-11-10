package com.github.e0ipso.tokkl.lifecycle

import com.github.e0ipso.tokkl.di.appModules
import com.github.e0ipso.tokkl.platform.AppHook
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.dsl.koinApplication

/**
 * The order the modules' hooks run in at process start, on MainActivity's starts and after a
 * notification grant, as the app's composition root lists them.
 */
class AppHookOrderTest {

    @Test
    fun theHooksRunInThisOrder() {
        val koin = koinApplication { modules(appModules) }.koin

        val hooks = koin.get<AppHooks>().inOrder

        assertEquals(
            listOf(
                "RefreshRequestsHook",
                "RunningNotificationHook",
                "PomodoroAlarmHook",
                "PomodoroNotificationHook",
                "ReminderHook",
                "TimerWidgetHook",
                "PomodoroWidgetHook",
                "FavoriteShortcutsHook",
                "RunningCheckHook",
                "TimerSyncHook",
            ),
            hooks.map { it::class.java.simpleName },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun aBoundHookTheOrderLeavesOutIsRefused() {
        val listed = object : AppHook {}
        AppHooks(inOrder = listOf(listed), bound = listOf(listed, object : AppHook {}))
    }
}
