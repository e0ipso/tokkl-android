package com.github.e0ipso.tokkl.trust

import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.api.TrustListener
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The trust refusals of the signed-in session's requests, which the
 * navigation host shows above whatever screen is open. A refused request sent
 * nothing, so no credential left the phone.
 *
 * A refusal the user can answer, an [ApiError.TrustQuestion], opens its
 * prompt: the certificate prompt or the http warning that sign-in shows.
 * [accept] stores the pin or the confirmation in [trust], which the shared
 * client reads, then runs [retry]. [cancel] closes the prompt but keeps the
 * refusal, so requests stay refused and the host shows a way back through
 * [reopen]. A certificate that does not name the server's host opens no
 * prompt, since no answer fixes it: the host shows only its notice. The same
 * refusal again, as every refresh brings, leaves a closed prompt closed. Any
 * answer from the server clears the refusal.
 *
 * @param retry sends the refused request again, such as the account settings
 *   refresh.
 * @param io where the writes to [trust] run.
 */
class SessionTrustModel(
    private val trust: ServerTrust,
    private val retry: () -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) : TrustListener {

    /**
     * [refusal] is the latest refusal, or null while requests pass.
     * [promptOpen] says whether its prompt shows. Otherwise the host offers
     * the way back to it.
     */
    data class State(val refusal: ApiError.TrustRefused? = null, val promptOpen: Boolean = false)

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    override fun refused(refusal: ApiError.TrustRefused) {
        mutableState.update { if (it.refusal == refusal) it else State(refusal, promptOpen = refusal is ApiError.TrustQuestion) }
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
                } catch (e: IOException) {
                    false
                }
            }
            if (stored) {
                mutableState.update { if (it.refusal == refusal) State() else it }
                retry()
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

    /** Opens the prompt again for the refusal the user cancelled. */
    fun reopen() {
        mutableState.update { if (it.refusal is ApiError.TrustQuestion) it.copy(promptOpen = true) else it }
    }
}
