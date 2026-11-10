package com.github.e0ipso.tokkl.api

/** The user's trust decisions in memory, for tests. Starts with [confirmed] already confirmed. */
class InMemoryServerTrust(vararg confirmed: ServerUrl) : ServerTrust {

    val cleartextConfirmed: MutableSet<ServerUrl> = confirmed.toMutableSet()

    /** Pinned SHA-256 fingerprints by `host:port`. */
    val pins: MutableMap<String, String> = mutableMapOf()

    override fun isCleartextConfirmed(server: ServerUrl): Boolean = server in cleartextConfirmed

    override fun confirmCleartext(server: ServerUrl) {
        cleartextConfirmed += server
    }

    override fun pinnedSha256(host: String, port: Int): String? = pins["$host:$port"]

    override fun pinCertificate(certificate: ServerCertificate) {
        pins["${certificate.host}:${certificate.port}"] = certificate.sha256
    }
}
