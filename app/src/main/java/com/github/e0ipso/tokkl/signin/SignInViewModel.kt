package com.github.e0ipso.tokkl.signin

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.session.Session
import kotlinx.coroutines.Dispatchers

/**
 * Keeps the sign-in form, and an attempt in flight, across configuration
 * changes. The secrets stay in this object's memory: nothing here goes into
 * saved instance state.
 */
class SignInViewModel(application: Application) : AndroidViewModel(application) {

    val model = SignInModel(
        // Session is built on the IO thread, since its stores touch the disk.
        signIn = SignIn(session = { server, token -> Session(application).save(server, token) }),
        scope = viewModelScope,
        io = Dispatchers.IO,
        clock = SystemClock::elapsedRealtime,
    )
}
