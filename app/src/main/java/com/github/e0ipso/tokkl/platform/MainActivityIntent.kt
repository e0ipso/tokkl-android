package com.github.e0ipso.tokkl.platform

import android.content.Context
import android.content.Intent

/**
 * The class name of the app's main activity. The app shell declares that activity, and the code
 * below the shell opens it by this name, since it cannot see the class.
 */
const val MAIN_ACTIVITY = "com.github.e0ipso.tokkl.MainActivity"

/** An intent for the app's main activity, the same as `Intent(context, MainActivity::class.java)`. */
fun mainActivityIntent(context: Context): Intent = Intent().setClassName(context, MAIN_ACTIVITY)
