package com.github.e0ipso.tokkl.session

import android.content.Context
import com.github.e0ipso.tokkl.api.TogglApiClient
import org.koin.core.context.GlobalContext

/**
 * The app's [Session], with the trust and last-answer listeners the app gives it. Named like the
 * constructor it replaced, so the tests keep their `Session(context)` call sites. [context] is
 * unused: the app's Koin graph holds the one Session.
 */
@Suppress("FunctionName", "UNUSED_PARAMETER")
fun Session(context: Context): Session = GlobalContext.get().get()

/**
 * The transport under the signed-in account's API, or null when signed out, for tests that send a
 * request the app does not send itself.
 */
fun Session.client(): TogglApiClient? = api()?.client
