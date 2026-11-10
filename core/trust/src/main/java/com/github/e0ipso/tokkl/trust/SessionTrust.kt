package com.github.e0ipso.tokkl.trust

import androidx.compose.runtime.Immutable
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.api.TrustListener
import com.github.e0ipso.tokkl.session.AccountDataOwner
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.session.SignOutPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.CoroutineContext

/**
 * The trust refusals of the signed-in session's requests, which the
 * navigation host shows above whatever screen is open. A refused request sent
 * nothing, so no credential left the phone.
 *
 * A refusal the user can answer, an [ApiError.TrustQuestion], opens its
 * prompt: the certificate prompt or the http warning that sign-in shows.
 * [accept] stores the pin or the confirmation in [trust], which the shared
 * client reads, then asks [refreshes] to send the refused request again. [cancel] closes the prompt but keeps the
 * refusal, so requests stay refused and the host shows a way back through
 * [reopen]. A certificate that does not name the server's host opens no
 * prompt, since no answer fixes it: the host shows only its notice. The same
 * refusal again, as every refresh brings, leaves a closed prompt closed. Any
 * answer from the server clears the refusal.
 */
class SessionTrust(
    private val trust: ServerTrust,
    private val refreshes: AccountRefreshRequests,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : TrustListener,
    AccountDataOwner {

    override val signOutPhase = SignOutPhase.CLEAR_DATA

    override suspend fun forgetAccount() = forget()

    /**
     * [refusal] is the latest refusal, or null while requests pass.
     * [promptOpen] says whether its prompt shows. Otherwise the host offers
     * the way back to it.
     */
    @Immutable
    data class State(val refusal: ApiError.TrustRefused? = null, val promptOpen: Boolean = false)

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    override fun refused(refusal: ApiError.TrustRefused) {
        mutableState.update {
            if (it.refusal == refusal) {
                it
            } else {
                State(
                    refusal,
                    promptOpen = refusal is ApiError.TrustQuestion,
                )
            }
        }
    }

    override fun answered() {
        mutableState.update { if (it.refusal == null) it else State() }
    }

    /**
     * The user trusts what the prompt showed: the exact certificate, or the
     * plain http URL. A failed write keeps the refusal and the way back to the
     * prompt.
     */
    fun accept() {
        val refusal = mutableState.value.refusal as? ApiError.TrustQuestion ?: return
        mutableState.update { it.copy(promptOpen = false) }
        scope.launch {
            val stored = withContext(io) {
                try {
                    when (refusal) {
                        is ApiError.CleartextNotConfirmed -> trust.confirmCleartext(refusal.server)
                        is ApiError.UntrustedCertificate -> trust.pinCertificate(refusal.certificate)
                    }
                    true
                } catch (_: IOException) {
                    false
                }
            }
            if (stored) {
                mutableState.update { if (it.refusal == refusal) State() else it }
                refreshes.refresh()
            }
        }
    }

    /** Closes the prompt. Requests stay refused until the user accepts. */
    fun cancel() {
        mutableState.update { it.copy(promptOpen = false) }
    }

    /** Drops the refusal and its prompt. Signing out calls this, so the next account starts clean. */
    fun forget() {
        mutableState.value = State()
    }

    fun reopen() {
        mutableState.update { if (it.refusal is ApiError.TrustQuestion) it.copy(promptOpen = true) else it }
    }
}
