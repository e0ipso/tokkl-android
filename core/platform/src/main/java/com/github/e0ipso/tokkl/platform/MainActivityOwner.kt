package com.github.e0ipso.tokkl.platform

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * The app's application, which names the main activity. The app shell declares that activity, and
 * the code below the shell cannot see its class, so it asks the application for it, as WorkManager
 * asks the application for its configuration.
 */
interface MainActivityOwner {

    /** The class of the activity the launcher opens. */
    val mainActivity: Class<out Activity>
}

/** An intent for the app's main activity, the same as `Intent(context, MainActivity::class.java)`. */
fun mainActivityIntent(context: Context): Intent {
    val owner = checkNotNull(context.applicationContext as? MainActivityOwner) {
        "The application does not implement MainActivityOwner"
    }
    return Intent(context, owner.mainActivity)
}
