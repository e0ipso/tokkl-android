package com.github.e0ipso.tokkl.lifecycle

import com.github.e0ipso.tokkl.di.appModules
import com.github.e0ipso.tokkl.platform.AppHook
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.core.annotation.KoinInternalApi
import org.koin.dsl.koinApplication

/**
 * The order the modules' hooks run in at process start, on MainActivity's starts and after a
 * notification grant. Each module declares its hook's order beside the hook; this is the whole
 * order in one place.
 */
class AppHookOrderTest {

    @OptIn(KoinInternalApi::class)
    @Test
    fun theHooksRunInThisOrder() {
        val koin = koinApplication { modules(appModules) }.koin
        val hooks = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()
            .filter { it.primaryType == AppHook::class }
            .map { definition -> checkNotNull(definition.qualifier) { "a hook without a name" } }
            .map { name -> name.value to koin.get<AppHook>(name).order }

        assertEquals("two hooks share an order", hooks.size, hooks.map { it.second }.distinct().size)
        assertEquals(
            listOf(
                "hook-refresh-requests",
                "hook-running-notification",
                "hook-pomodoro-alarm",
                "hook-pomodoro-notification",
                "hook-reminder",
                "hook-timer-widget",
                "hook-pomodoro-widget",
                "hook-favorite-shortcuts",
                "hook-running-check",
                "hook-timer-sync",
            ),
            hooks.sortedBy { it.second }.map { it.first },
        )
    }
}
