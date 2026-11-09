package com.github.e0ipso.tokkl.credentials

import android.content.Context
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * Runs against the real Android Keystore of the device. The file and key names are spelled out
 * here: a token stored by an earlier version must still load, so they must never change.
 */
class ApiTokenStoreTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ApiTokenStore(context, io = Dispatchers.IO)
    private val token = "test-token-${UUID.randomUUID()}"
    private val storedFile = File(context.filesDir, FILE_NAME)

    @Before
    @After
    fun clearStore() {
        runBlocking { store.clear() }
    }

    @Test
    fun loadsNullWhenNothingIsStored() {
        runBlocking { assertNull(store.load()) }
    }

    @Test
    fun savedTokenLoadsBack() = runBlocking<Unit> {
        store.save(token)

        assertEquals(token, ApiTokenStore(context, io = Dispatchers.IO).load())
    }

    @Test
    fun savingReplacesTheStoredToken() = runBlocking<Unit> {
        store.save("first-$token")
        store.save(token)

        assertEquals(token, store.load())
    }

    @Test
    fun storedBytesNeverContainThePlainToken() {
        runBlocking { store.save(token) }
        assertTrue("the store wrote no file", storedFile.length() > 0)

        val plain = token.toByteArray(Charsets.UTF_8)
        val forms = mapOf(
            "UTF-8" to plain,
            "UTF-16LE" to token.toByteArray(Charsets.UTF_16LE),
            "Base64" to Base64.encode(plain, Base64.NO_WRAP),
        )
        // Every file the app can write to on internal storage, not just the store's.
        val files = context.dataDir.walkTopDown().filter { it.isFile && it.canRead() }.toList()
        assertTrue(
            "$storedFile is not under the data directory",
            storedFile.canonicalFile in files.map { it.canonicalFile },
        )
        for (file in files) {
            val bytes = file.readBytes()
            for ((name, form) in forms) {
                assertFalse("$file holds the token as $name", bytes.contains(form))
            }
        }
    }

    @Test
    fun keyIsANonExportableAesGcmKeyInTheKeystore() {
        runBlocking { store.save(token) }

        val key = keyStore().getKey(KEY_ALIAS, null) as SecretKey
        assertNull("the key material is readable", key.encoded)
        val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEY_STORE)
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertEquals(KeyProperties.KEY_ALGORITHM_AES, key.algorithm)
        assertEquals(256, info.keySize)
        assertArrayEquals(arrayOf(KeyProperties.BLOCK_MODE_GCM), info.blockModes)
    }

    @Test
    fun clearDeletesTheKeyAndTheCiphertext() = runBlocking<Unit> {
        store.save(token)
        assertTrue(storedFile.exists())
        assertTrue(keyStore().containsAlias(KEY_ALIAS))

        store.clear()

        assertFalse("the ciphertext is still on disk", storedFile.exists())
        assertFalse("the key is still in the Keystore", keyStore().containsAlias(KEY_ALIAS))
        assertNull(store.load())
    }

    @Test
    fun changedCiphertextLoadsNull() = runBlocking<Unit> {
        store.save(token)
        val bytes = storedFile.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        storedFile.writeBytes(bytes)

        assertNull(store.load())
    }

    @Test
    fun ciphertextWithoutItsKeyLoadsNull() = runBlocking<Unit> {
        store.save(token)
        keyStore().deleteEntry(KEY_ALIAS)

        assertNull(store.load())
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun ByteArray.contains(needle: ByteArray): Boolean =
        (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }

    private companion object {
        const val FILE_NAME = "api_token.bin"
        const val KEY_ALIAS = "api_token"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
    }
}
