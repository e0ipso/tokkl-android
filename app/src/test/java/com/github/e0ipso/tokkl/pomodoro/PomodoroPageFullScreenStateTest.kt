package com.github.e0ipso.tokkl.pomodoro

import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.cache.PomodoroSession
import com.github.e0ipso.tokkl.session.AccountSettings
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroPageFullScreenStateTest {
    @Test fun permissionResumeRefreshKeepsExactAlarmDenialIndependent() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val database = object : PomodoroSessionDatabase {
                override fun current(): PomodoroSession? = null
                override fun save(session: PomodoroSession) = error("no write expected")
                override fun delete() = error("no write expected")
                override fun createStopped(workspaceId: Long, fields: EntryFields, start: Instant, stop: Instant): Long = error("no write expected")
                override fun <T> transaction(block: () -> T): T = block()
            }
            val alarms = object : PomodoroAlarmScheduler {
                override val permissionDenied = true
                override fun schedule(triggerAt: Instant) = false
                override fun cancel() = Unit
                override fun watchSession(running: Boolean) = Unit
            }
            val store = PomodoroSessionStore(database, Dispatchers.Unconfined, accountTime = { null }, alarms = alarms)
            val page = PomodoroScreenModel(store, MutableStateFlow(PomodoroDefaults.DEFAULT),
                MutableStateFlow<AccountSettings?>(null), flowOf(0), flowOf(emptyList()), scope)
            page.fullScreenPermissionChanged(false)
            page.reload().join()
            assertTrue(page.state.value.fullScreenDenied)
            assertTrue(page.state.value.alarmDenied)
            page.fullScreenPermissionChanged(true)
            page.reload().join()
            assertFalse(page.state.value.fullScreenDenied)
            assertTrue(page.state.value.alarmDenied)
            assertFalse(page.state.value.busy)
        } finally { scope.cancel() }
    }
}
