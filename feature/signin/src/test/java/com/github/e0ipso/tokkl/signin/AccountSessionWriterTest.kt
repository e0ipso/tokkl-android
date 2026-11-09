package com.github.e0ipso.tokkl.signin

import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.cache.SignInPause
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.session.CurrentAccount
import com.github.e0ipso.tokkl.session.SignedInListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

/**
 * The order a sign-in changes the account in, each step recorded with the account context it ran
 * in, so the begin of the new account shows between clearing the timer state and storing the
 * session.
 */
class AccountSessionWriterTest {

    private val steps = mutableListOf<String>()
    private val account = CurrentAccount(CoroutineScope(Dispatchers.Unconfined))
    private val paused = MutableStateFlow(true)

    private fun step(name: String) {
        steps += "$name in account ${account.context.value.number}"
    }

    private val queue = object : SignInPause {
        override val signInNeeded: StateFlow<Boolean> = paused

        override fun signInRefused() = throw AssertionError("nothing refuses")

        override fun resume() {
            step("resume the queue")
            paused.value = false
        }
    }

    private fun writer() = AccountSessionWriter(
        storeSettings = { step("store the settings of ${it.timeZone}") },
        clearTimerState = { step("clear the timer state") },
        account = account,
        storeSession = { server, token -> step("store $server with $token") },
        signedIn = listOf(SignedInListener { step("first listener") }, SignedInListener { step("second listener") }),
        queue = queue,
        followTimerSync = { step("follow the timer sync") },
    )

    @Test
    fun aSignInOverARefusedTokenRunsEveryStepInOrder() = runTest {
        writer().save(SERVER, "tok-123", SETTINGS)

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
        paused.value = false

        writer().save(SERVER, "tok-123", SETTINGS)

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
