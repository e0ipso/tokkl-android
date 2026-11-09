package com.github.e0ipso.tokkl.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private val account = CurrentAccount()

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
        val work = before.launchIn(CoroutineScope(Dispatchers.Unconfined)) { CompletableDeferred<Unit>().await() }

        account.begin()

        assertFalse(before.isCurrent)
        assertTrue("the account's work is cancelled", work.isCancelled)
        assertTrue(ended.isCompleted)
        assertTrue(account.context.value.isCurrent)
        assertEquals(before.number + 1, account.context.value.number)
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
