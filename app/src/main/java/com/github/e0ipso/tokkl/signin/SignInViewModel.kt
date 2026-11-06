package com.github.e0ipso.tokkl.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.e0ipso.tokkl.session.AccountSettingsStore
import com.github.e0ipso.tokkl.session.ServerConfigStore
import com.github.e0ipso.tokkl.session.ServerTrustStore
import kotlin.coroutines.CoroutineContext

/**
 * Keeps the sign-in form, and an attempt in flight, across configuration
 * changes. The secrets stay in this object's memory: nothing here goes into
 * saved instance state.
 */
class SignInViewModel(
    session: SessionWriter,
    servers: ServerConfigStore,
    accountSettings: AccountSettingsStore,
    trust: ServerTrustStore,
    cooldowns: SignInCooldownStore,
    io: CoroutineContext,
) : ViewModel() {

    val model = SignInModel(
        // Runs on the IO thread, since the stores touch the disk.
        signIn = SignIn(
            session = session,
            // Recheck persisted identity on every submit, including a form restored after the
            // process died. The in-memory form's reauthentication marker may have been lost.
            signedInAccount = {
                servers.load()?.let { server ->
                    SignInAgain(server, accountSettings.load()?.userId)
                }
            },
        ),
        trust = trust,
        cooldowns = cooldowns,
        scope = viewModelScope,
        io = io,
        clock = System::currentTimeMillis,
    )
}
