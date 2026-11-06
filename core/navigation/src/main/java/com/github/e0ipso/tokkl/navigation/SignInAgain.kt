package com.github.e0ipso.tokkl.navigation

import com.github.e0ipso.tokkl.api.ServerUrl

/**
 * The signed-in account, when the user signs in again because the server refused its API token
 * (docs/PRD.md O6). The phone keeps that account's cache and queued changes, so only a sign-in to
 * the same [server] and, when the phone knows it, the same [userId] may replace the token.
 */
data class SignInAgain(val server: ServerUrl, val userId: Long?)
