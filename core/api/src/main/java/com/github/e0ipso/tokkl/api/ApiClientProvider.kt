package com.github.e0ipso.tokkl.api

/**
 * The API client of the signed-in account, or null when signed out. The first call after process
 * start, sign-in or sign-out reads the stored session from the disk and the Keystore, so call it
 * off the main thread.
 */
fun interface ApiClientProvider {
    operator fun invoke(): TogglApiClient?
}
