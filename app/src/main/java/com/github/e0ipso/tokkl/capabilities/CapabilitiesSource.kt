package com.github.e0ipso.tokkl.capabilities

import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.session.Me
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The server's capabilities for the whole process, and the one place features read them from.
 * They refresh with each `GET /api/v9/me` of the account settings refresh, and persist.
 *
 * @param initial the stored capabilities, or null when none are stored.
 * @param save writes the capabilities, throwing [IOException] when it cannot.
 */
class CapabilitiesSource(initial: Capabilities?, private val save: (Capabilities) -> Unit) {

    private val mutableState = MutableStateFlow(initial ?: Capabilities.NONE)

    /** What the disk holds, so a refresh after a failed write writes again. */
    private var stored: Capabilities? = initial

    /** The latest capabilities: the stored ones after a restart, [Capabilities.NONE] before any refresh. */
    val state: StateFlow<Capabilities> = mutableState.asStateFlow()

    /**
     * Sets the flags from [me], the refresh's `GET /api/v9/me`, and one `GET /api/app/trash`,
     * and stores them. A failed write keeps the new flags for this process. Blocks on the network
     * and the disk, so call it off the main thread. Returns the new flags.
     */
    @Synchronized
    fun refresh(api: TogglApiClient, me: Me): Capabilities = update(mutableState.value.updatedWith(me, probeTrash(api)))

    /**
     * A trash call outside the probe got the probe's answer for a server without the trash, a 404
     * or a body that is not a list. Turns the trash and restore flags off and stores them, as the
     * probe would. Flags that already say so, as after a sign-out, stay as they are and unwritten.
     * Writes to the disk, so call it off the main thread.
     */
    @Synchronized
    fun trashMissing() {
        val current = mutableState.value
        if (current.trash || current.restore) update(current.copy(trash = false, restore = false))
    }

    private fun update(next: Capabilities): Capabilities {
        if (next != stored) {
            try {
                save(next)
                stored = next
            } catch (e: IOException) {
                // The next refresh writes them again.
            }
        }
        mutableState.value = next
        return next
    }

    /**
     * Goes back to [Capabilities.NONE], after a refresh that is running. Signing out calls this,
     * then deletes the stored flags.
     */
    @Synchronized
    fun forget() {
        mutableState.value = Capabilities.NONE
        stored = null
    }
}
