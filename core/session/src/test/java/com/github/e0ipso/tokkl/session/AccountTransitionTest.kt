package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ServerUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

/**
 * The order a sign-in changes the account in, each step recorded with the account context it ran
 * in, so the begin of the new account shows between clearing the timer state and storing the
 * session.
 */
class AccountTransitionTest {

    private val steps = mutableListOf<String>()
    private val account = CurrentAccount(CoroutineScope(Dispatchers.Unconfined))
    private var paused = true

    private fun step(name: String) {
        steps += "$name in account ${account.context.value.number}"
    }

    private fun transition() = AccountTransition(
        storeSettings = { step("store the settings of ${it.timeZone}") },
        clearTimerState = { step("clear the timer state") },
        account = account,
        storeSession = { server, token -> step("store $server with $token") },
        signedIn = listOf(SignedInListener { step("first listener") }, SignedInListener { step("second listener") }),
        queuePaused = { paused },
        resumeQueue = {
            step("resume the queue")
            paused = false
        },
        followTimerSync = { step("follow the timer sync") },
    )

    @Test
    fun aSignInOverARefusedTokenRunsEveryStepInOrder() = runTest {
        transition().signIn(SERVER, "tok-123", SETTINGS)

        assertEquals(
            listOf(
                "store the settings of Europe/Madrid in account 0",
                "clear the timer state in account 0",
                "store $SERVER with tok-123 in account 1",
                "first listener in account 1",
                "second listener in account 1",
                "resume the queue in account 1",
                "follow the timer sync in account 1",
            ),
            steps,
        )
    }

    @Test
    fun aQueueThatIsNotPausedIsNotResumed() = runTest {
        paused = false

        transition().signIn(SERVER, "tok-123", SETTINGS)

        assertEquals(
            listOf(
                "store the settings of Europe/Madrid in account 0",
                "clear the timer state in account 0",
                "store $SERVER with tok-123 in account 1",
                "first listener in account 1",
                "second listener in account 1",
                "follow the timer sync in account 1",
            ),
            steps,
        )
    }

    private companion object {
        val SERVER = checkNotNull(ServerUrl.parse("https://tokkl.example"))
        val SETTINGS = AccountSettings("Europe/Madrid", DayOfWeek.MONDAY, timeResolutionMinutes = null)
    }
}
