package com.github.e0ipso.tokkl.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** What work started for an account can still do once the account has gone. */
class AccountContextTest {

    /** The application's scope. */
    private val app = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val account = CurrentAccount(app)

    @Test
    fun aReplyFromBeforeSignOutCannotWriteAndTheNextAccountsCan() {
        val before = account.context.value
        var cached: String? = "old account"

        account.end()
        cached = null
        before.ifCurrent { cached = "late old reply" }
        assertNull(cached)

        account.begin()
        account.context.value.ifCurrent { cached = "new account" }
        assertEquals("new account", cached)
    }

    @Test
    fun aSignInEndsTheAccountBeforeItsWorkAndCancelsIt() = runBlocking {
        val before = account.context.value
        val ended = CompletableDeferred<Unit>()
        before.onEnd { ended.complete(Unit) }
        val work = before.launch { CompletableDeferred<Unit>().await() }

        account.begin()

        assertFalse(before.isCurrent)
        assertTrue("the account's work is cancelled", work.isCancelled)
        assertTrue(ended.isCompleted)
        assertTrue(account.context.value.isCurrent)
        assertEquals(before.number + 1, account.context.value.number)
    }

    @Test
    fun cancellingTheAppScopeCancelsTheAccountsWork() {
        val work = account.context.value.launch { CompletableDeferred<Unit>().await() }

        app.cancel()

        assertTrue("the account's work ends with the app's scope", work.isCancelled)
    }

    @Test
    fun anEndedAccountLeavesNothingUnderTheAppScope() {
        val before = account.context.value
        val work = before.launch { CompletableDeferred<Unit>().await() }
        assertEquals("the account runs under the app's scope", 1, app.coroutineContext.job.children.count())

        account.begin()

        assertTrue(work.isCancelled)
        assertEquals("only the next account stays under the app's scope", 1, app.coroutineContext.job.children.count())
        var started = false
        before.launch { started = true }
        assertFalse("an ended account's work does not start", started)
        app.cancel()
    }

    @Test
    fun theAccountEndsOnlyOnceAWriteOnItsWayHasReturned() {
        val context = account.context.value
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val order = mutableListOf<String>()
        val writer = thread {
            context.ifCurrent {
                writing.countDown()
                assertTrue(release.await(10, TimeUnit.SECONDS))
                synchronized(order) { order += "written" }
            }
        }
        assertTrue(writing.await(10, TimeUnit.SECONDS))
        val signOut = thread {
            account.end()
            synchronized(order) { order += "ended" }
        }
        while (signOut.state != Thread.State.BLOCKED) Thread.onSpinWait()
        assertEquals("sign-out waits for the write", emptyList<String>(), synchronized(order) { order.toList() })

        release.countDown()
        writer.join()
        signOut.join()

        assertEquals(listOf("written", "ended"), order)
        assertNull(context.ifCurrent { "after" })
    }
}
