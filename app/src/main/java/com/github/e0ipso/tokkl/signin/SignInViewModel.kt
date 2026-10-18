package com.github.e0ipso.tokkl.signin

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.Dispatchers

/**
 * Keeps the sign-in form, and an attempt in flight, across configuration
 * changes. The secrets stay in this object's memory: nothing here goes into
 * saved instance state.
 */
class SignInViewModel(application: Application) : AndroidViewModel(application) {

    val model = SignInModel(
        // Runs on the IO thread, since the stores touch the disk. The settings go first. If the
        // session write then fails, the app stays signed out and the next sign-in replaces them.
        signIn = SignIn(
            session = { server, token, settings ->
                (application as TokklApplication).accountSettings.store(settings)
                Session(application).save(server, token)
            },
        ),
        scope = viewModelScope,
        io = Dispatchers.IO,
        clock = SystemClock::elapsedRealtime,
    )
}
