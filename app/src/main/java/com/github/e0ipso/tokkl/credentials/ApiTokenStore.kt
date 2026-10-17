package com.github.e0ipso.tokkl.credentials

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the API token encrypted with an AES-GCM key in the Android Keystore
 * (docs/PRD.md S5). The key never leaves the Keystore. The app-private file
 * holds the IV and the ciphertext, never the plain token.
 *
 * The store has no backup rules of its own: the app keeps all of its data out
 * of backup and device transfer, and another phone could not decrypt the file
 * anyway.
 *
 * Calls do disk and Keystore I/O, so make them off the main thread.
 */
class ApiTokenStore(context: Context) {

    private val file = AtomicFile(File(context.filesDir, FILE_NAME))

    /** Encrypts [token] and replaces the stored one. */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun save(token: String) {
        synchronized(LOCK) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // The Keystore picks a random IV for every encryption.
            cipher.init(Cipher.ENCRYPT_MODE, key() ?: createKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            write(byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + ciphertext)
        }
    }

    /**
     * The stored token, or null when there is none or it no longer decrypts,
     * for example because the key is gone or the file was changed. Either way
     * the user has to sign in again.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun load(): String? {
        synchronized(LOCK) {
            val bytes = try {
                file.readFully()
            } catch (e: FileNotFoundException) {
                return null
            }
            if (bytes.size < HEADER_SIZE || bytes[0] != FORMAT_VERSION) return null
            val ivEnd = HEADER_SIZE + (bytes[1].toInt() and 0xFF)
            if (bytes.size < ivEnd + TAG_BITS / 8) return null
            val key = key() ?: return null
            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, HEADER_SIZE, ivEnd - HEADER_SIZE))
                String(cipher.doFinal(bytes, ivEnd, bytes.size - ivEnd), Charsets.UTF_8)
            } catch (e: GeneralSecurityException) {
                null
            }
        }
    }

    /** Deletes the ciphertext and the Keystore key. Signing out calls this (TKA-0038). */
    @Throws(GeneralSecurityException::class)
    fun clear() {
        synchronized(LOCK) {
            file.delete()
            keyStore().deleteEntry(KEY_ALIAS)
        }
    }

    private fun write(bytes: ByteArray) {
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            throw e
        }
    }

    private fun key(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as SecretKey?

    /** Keystore keys cannot be exported: only the Keystore ever sees the key material. */
    private fun createKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(KEY_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply { init(spec) }
            .generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    internal companion object {
        const val FILE_NAME = "api_token.bin"
        const val KEY_ALIAS = "api_token"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128

        /** The file is the format version, the IV length, the IV and the ciphertext with its GCM tag. */
        private const val FORMAT_VERSION: Byte = 1
        private const val HEADER_SIZE = 2

        /** One lock for every instance, since they share the file and the key. */
        private val LOCK = Any()
    }
}
