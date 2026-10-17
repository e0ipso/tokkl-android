package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.MergedManifest.application
import com.github.e0ipso.tokkl.MergedManifest.xmlResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The app reaches Tokkl over plain HTTP and over HTTPS with a CA the user
 * installed (docs/PRD.md S2 and S4). A self-signed certificate is pinned in
 * app code (TKA-0021), so the config itself holds no pin or certificate.
 */
class NetworkSecurityConfigTest {

    @Test
    fun manifestReferencesTheNetworkSecurityConfig() {
        assertEquals("@xml/network_security_config", application().androidAttribute("networkSecurityConfig"))
    }

    @Test
    fun baseConfigPermitsCleartextAndTrustsSystemAndUserCas() {
        val baseConfigs = config().childElements().filter { it.tagName == "base-config" }
        assertEquals("<base-config> sections", 1, baseConfigs.size)
        val baseConfig = baseConfigs.single()
        assertEquals("true", baseConfig.getAttribute("cleartextTrafficPermitted"))

        val anchors = baseConfig.childElements().single { it.tagName == "trust-anchors" }
        val sources = anchors.childElements()
            .filter { it.tagName == "certificates" }
            .map { it.getAttribute("src") }
        assertEquals(listOf("system", "user"), sources.sorted())
    }

    @Test
    fun configHoldsNoPinOrCertificate() {
        val elements = config().selfAndDescendants()
        assertTrue("the config has a <pin-set>", elements.none { it.tagName == "pin-set" || it.tagName == "pin" })
        val certificateSources = elements.filter { it.tagName == "certificates" }.map { it.getAttribute("src") }
        assertTrue(
            "the config trusts a certificate resource: $certificateSources",
            certificateSources.all { it == "system" || it == "user" },
        )
    }

    private fun config(): Element {
        val config = xmlResource(application().androidAttribute("networkSecurityConfig"))
        assertEquals("network-security-config", config.tagName)
        return config
    }
}
