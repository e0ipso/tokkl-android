package com.github.e0ipso.tokkl

import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * No app data may leave the phone through Android backup or device transfer
 * (docs/PRD.md S8). Reads the manifest that the Android Gradle plugin merged
 * for this variant and the backup rule files it points at.
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

    @Test
    fun fullBackupContentExcludesEveryDomainForApi26To30() {
        val rules = xmlResource(application().androidAttribute("fullBackupContent"))
        assertEquals("full-backup-content", rules.tagName)
        assertExcludesEveryDomain("<full-backup-content>", rules)
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

    private fun application(): Element {
        val path = testConfig().getProperty("android_merged_manifest")
            ?: error("test_config.properties has no android_merged_manifest")
        val manifest = parse(File(path)).documentElement
        return manifest.childElements().single { it.tagName == "application" }
    }

    private fun testConfig(): Properties {
        val stream = javaClass.classLoader!!.getResourceAsStream(TEST_CONFIG)
            ?: error("$TEST_CONFIG is missing; unitTests.isIncludeAndroidResources must be on")
        return stream.use { Properties().apply { load(it) } }
    }

    /** Resolves `@xml/name` to the source file. The app has a single resource directory. */
    private fun xmlResource(reference: String): Element {
        assertTrue("$reference is not an @xml/ reference", reference.startsWith("@xml/"))
        return parse(File("src/main/res/xml/${reference.removePrefix("@xml/")}.xml")).documentElement
    }

    private fun Element.androidAttribute(name: String): String {
        assertTrue("<$tagName> has no android:$name", hasAttributeNS(ANDROID_NS, name))
        return getAttributeNS(ANDROID_NS, name)
    }

    private fun Element.childElements(): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<Element>()
    }

    private fun parse(file: File) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)

    private companion object {
        const val TEST_CONFIG = "com/android/tools/test_config.properties"
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        val ALL_DOMAINS = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}
