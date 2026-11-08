package com.github.e0ipso.tokkl.api

/**
 * The API of the signed-in account, or null when signed out. The first call after process start,
 * sign-in or sign-out reads the stored session from the disk and the Keystore, on the IO
 * dispatcher, so any thread may call it.
 */
fun interface ApiClientProvider {
    suspend operator fun invoke(): TokklApi?
}
