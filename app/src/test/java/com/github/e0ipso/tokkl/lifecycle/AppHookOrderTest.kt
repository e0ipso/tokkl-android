package com.github.e0ipso.tokkl.lifecycle

import com.github.e0ipso.tokkl.di.appModules
import com.github.e0ipso.tokkl.platform.AppHook
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.dsl.koinApplication

/**
 * The order the modules' hooks run in at process start, on MainActivity's starts and after a
 * notification grant. Each module declares its hook's order beside the hook; this is the whole
 * order in one place.
 */
class AppHookOrderTest {

    @Test
    fun theHooksRunInThisOrder() {
        val koin = koinApplication { modules(appModules) }.koin
        val hooks = koin.getAll<AppHook>().map { it::class.java.simpleName to it.order }

        assertEquals("two hooks share an order", hooks.size, hooks.map { it.second }.distinct().size)
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
            hooks.sortedBy { it.second }.map { it.first },
        )
    }
}
