package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.MergedManifest.application
import com.github.e0ipso.tokkl.MergedManifest.xmlResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * No app data may leave the phone through Android backup or device transfer
 * (docs/PRD.md S8). Reads the manifest that the Android Gradle plugin merged
 * for this variant and the data extraction rules it points at.
 */
class BackupRulesTest {

    @Test
    fun mergedManifestTurnsBackupOff() {
        assertEquals("false", application().androidAttribute("allowBackup"))
    }

    @Test
    fun dataExtractionRulesExcludeEveryDomainFromEveryTransferMode() {
        val rules = xmlResource(application().androidAttribute("dataExtractionRules"))
        assertEquals("data-extraction-rules", rules.tagName)
        for (mode in listOf("cloud-backup", "device-transfer", "cross-platform-transfer")) {
            val sections = rules.childElements().filter { it.tagName == mode }
            assertEquals("<$mode> sections", 1, sections.size)
            assertExcludesEveryDomain("<$mode>", sections.single())
        }
    }

    private fun assertExcludesEveryDomain(where: String, section: Element) {
        val children = section.childElements()
        assertTrue("$where has an <include>", children.none { it.tagName == "include" })
        val excluded = children
            .filter { it.tagName == "exclude" && it.getAttribute("path") == "." }
            .map { it.getAttribute("domain") }
            .toSet()
        assertEquals("$where whole-domain excludes", ALL_DOMAINS, excluded)
    }

    private companion object {
        val ALL_DOMAINS = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}
