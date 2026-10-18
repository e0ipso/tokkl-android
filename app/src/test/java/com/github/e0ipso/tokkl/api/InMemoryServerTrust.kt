package com.github.e0ipso.tokkl.api

/** The user's trust decisions in memory, for tests. Starts with [confirmed] already confirmed. */
class InMemoryServerTrust(vararg confirmed: ServerUrl) : ServerTrust {

    val cleartextConfirmed: MutableSet<ServerUrl> = confirmed.toMutableSet()

    override fun isCleartextConfirmed(server: ServerUrl): Boolean = server in cleartextConfirmed

    override fun confirmCleartext(server: ServerUrl) {
        cleartextConfirmed += server
    }
}
