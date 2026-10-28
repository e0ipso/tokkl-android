package com.github.e0ipso.tokkl.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Settings page's sign-out (docs/PRD.md S6). While the queue holds changes the server has not
 * received, [signOut] first asks, with their count, and [cancel] keeps everything. With an empty
 * queue it signs out at once. Call it on the main thread.
 *
 * @param unsentCount reads how many changes the queue holds.
 * @param clear signs out and clears the phone, returning false when a step failed.
 * @param scope outlives every screen, so leaving the app does not stop a sign-out half way.
 */
class SignOutModel(
    private val unsentCount: suspend () -> Int,
    private val clear: suspend () -> Boolean,
    private val scope: CoroutineScope,
) {

    /**
     * [unsentToConfirm] is the count the warning shows, or null when it is closed. [signingOut]
     * holds from the tap until the phone is clear or a step failed, and [failed] says that the
     * last sign-out did not finish: the queue could not be read, or something stayed behind.
     */
    data class State(val unsentToConfirm: Int? = null, val signingOut: Boolean = false, val failed: Boolean = false)

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    /** Signs out, or asks first when the queue holds unsent changes. */
    fun signOut() {
        if (mutableState.value.signingOut || mutableState.value.unsentToConfirm != null) return
        mutableState.value = State(signingOut = true)
        scope.launch {
            val unsent = try {
                unsentCount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Without the count, signing out could drop changes without a word.
                mutableState.value = State(failed = true)
                return@launch
            }
            if (unsent > 0) {
                mutableState.value = State(unsentToConfirm = unsent)
            } else {
                clearPhone()
            }
        }
    }

    /** The user accepts losing the unsent changes. */
    fun confirm() {
        if (mutableState.value.unsentToConfirm == null) return
        mutableState.value = State(signingOut = true)
        scope.launch { clearPhone() }
    }

    /** Closes the warning. Nothing changes. */
    fun cancel() {
        if (mutableState.value.unsentToConfirm == null) return
        mutableState.value = State()
    }

    private suspend fun clearPhone() {
        mutableState.value = State(failed = !clear())
    }
}
