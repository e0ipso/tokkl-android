package com.github.e0ipso.tokkl.capabilities

/**
 * Whether the signed-in server offers one capability, read each time it is asked, such as
 * [CapabilitiesSource.keepsPreferences].
 */
fun interface CapabilityCheck {
    fun offered(): Boolean
}

/**
 * Hears that the server refused a request for a capability it was thought to offer, such as
 * [CapabilitiesSource.trashMissing], so the app stops offering it.
 */
fun interface CapabilityMissing {
    fun missing()
}
