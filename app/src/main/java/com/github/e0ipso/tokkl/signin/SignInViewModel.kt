package com.github.e0ipso.tokkl.signin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.TokklApplication
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import kotlinx.coroutines.Dispatchers

/**
 * Keeps the sign-in form, and an attempt in flight, across configuration
 * changes. The secrets stay in this object's memory: nothing here goes into
 * saved instance state.
 */
class SignInViewModel(application: Application) : AndroidViewModel(application) {

    val model = SignInModel(
        // Runs on the IO thread, since the stores touch the disk.
        signIn = SignIn(
            session = (application as TokklApplication)::storeSignIn,
            // Recheck persisted identity on every submit, including a form restored after the
            // process died. The in-memory form's reauthentication marker may have been lost.
            signedInAccount = {
                ServerConfigStore(application).load()?.let { server ->
                    SignInAgain(server, AccountSettingsStore(application).load()?.userId)
                }
            },
        ),
        trust = ServerTrustStore(application),
        cooldowns = SignInCooldownStore(application),
        scope = viewModelScope,
        io = Dispatchers.IO,
        clock = System::currentTimeMillis,
    )
}
