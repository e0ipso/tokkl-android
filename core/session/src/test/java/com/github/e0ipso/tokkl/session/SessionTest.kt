package com.github.e0ipso.tokkl.session

import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.storage.ClearableStore
import com.github.e0ipso.tokkl.storage.InMemoryValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.GeneralSecurityException

/** The session keeps the signed-in client in memory between changes of account. */
class SessionTest {

    private val servers = InMemoryValueStore<ServerUrl>(SERVER)
    private val tokens = CountingTokens("tok-123")
    private val session = Session(
        servers = servers,
        tokens = tokens,
        http = TestApi(SERVER).http,
        io = Dispatchers.Unconfined,
        trustListener = null,
        successListener = null,
    )

    @Test
    fun theTokenIsReadOnceForEveryCallUntilTheAccountChanges() = runTest {
        val client = session.api()

        assertNotNull(client)
        assertTrue(session.isSignedIn())
        assertSame(client, session.api())
        assertEquals("one decrypt", 1, tokens.loads)
    }

    @Test
    fun savingAnAccountReadsTheNewOne() = runTest {
        val before = session.api()

        session.save(OTHER, "tok-456")

        val after = session.api()
        assertEquals(OTHER, after?.client?.server)
        assertTrue(before !== after)
        assertEquals(2, tokens.loads)
    }

    @Test
    fun clearingSignsOutAtOnce() = runTest {
        assertTrue(session.isSignedIn())

        session.clear()

        assertFalse(session.isSignedIn())
        assertNull(session.api())
    }

    @Test
    fun aReadThatFailsKeepsNothing() = runTest {
        tokens.failing = true
        assertNull(session.api())
        assertFalse(session.isSignedIn())

        tokens.failing = false

        assertNotNull("the next call reads again", session.api())
    }

    @Test
    fun aFailedSaveLeavesTheAppSignedOut() = runTest {
        assertTrue(session.isSignedIn())
        tokens.failing = true

        runCatching { session.save(OTHER, "tok-456") }

        tokens.failing = false
        assertFalse("the server was cleared first", session.isSignedIn())
    }

    @Test
    fun withoutAServerTheTokenIsNotRead() = runTest {
        servers.clear()

        assertFalse(session.isSignedIn())
        assertEquals(0, tokens.loads)
    }

    /** A token store that counts its reads and can fail like the Keystore. */
    private class CountingTokens(private var token: String?) : ClearableStore<String> {
        var loads = 0
        var failing = false

        override suspend fun load(): String? {
            loads++
            if (failing) throw GeneralSecurityException("Keystore")
            return token
        }

        override suspend fun save(value: String) {
            if (failing) throw IOException("disk full")
            token = value
        }

        override suspend fun clear() {
            token = null
        }
    }

    private companion object {
        val SERVER = ServerUrl.parse("https://tokkl.example/")!!
        val OTHER = ServerUrl.parse("https://other.example/")!!
    }
}
