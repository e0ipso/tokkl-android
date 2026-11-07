package com.github.e0ipso.tokkl.signin

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The 429 deadlines on the phone hold each server origin and its deadline, and nothing else. */
class SignInCooldownStoreTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = SignInCooldownStore(context)

    @Before
    @After
    fun forgetEverything() {
        store.load().keys.forEach(store::clear)
    }

    @Test
    fun keepsOneDeadlinePerOriginUntilCleared() {
        store.save("https://tokkl.example:443", 1_000L)
        store.save("http://10.0.2.2:8080", 2_000L)
        store.save("https://tokkl.example:443", 3_000L)

        assertEquals(mapOf("https://tokkl.example:443" to 3_000L, "http://10.0.2.2:8080" to 2_000L), SignInCooldownStore(context).load())
        val file = context.getSharedPreferences(SignInCooldownStore.FILE_NAME, Context.MODE_PRIVATE).all
        assertEquals("the file holds the origins and deadlines only", store.load(), file)

        store.clear("https://tokkl.example:443")

        assertEquals(mapOf("http://10.0.2.2:8080" to 2_000L), store.load())
    }
}
