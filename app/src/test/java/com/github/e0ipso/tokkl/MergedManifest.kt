package com.github.e0ipso.tokkl

import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.w3c.dom.Element

/**
 * Reads the manifest that the Android Gradle plugin merged for the variant
 * under test, and the XML resources it points at. Needs
 * `unitTests.isIncludeAndroidResources`.
 */
internal object MergedManifest {

    private const val TEST_CONFIG = "com/android/tools/test_config.properties"

    /** The merged manifest's root `<manifest>` element. */
    fun manifest(): Element {
        val path = testConfig().getProperty("android_merged_manifest")
            ?: error("test_config.properties has no android_merged_manifest")
        return parseXml(File(path))
    }

    /** The merged manifest's `<application>` element. */
    fun application(): Element = manifest().childElements().single { it.tagName == "application" }

    /** Resolves `@xml/name` to the source file. The app has a single resource directory. */
    fun xmlResource(reference: String): Element {
        assertTrue("$reference is not an @xml/ reference", reference.startsWith("@xml/"))
        return parseXml(File("src/main/res/xml/${reference.removePrefix("@xml/")}.xml"))
    }

    private fun testConfig(): Properties {
        val stream = javaClass.classLoader!!.getResourceAsStream(TEST_CONFIG)
            ?: error("$TEST_CONFIG is missing; unitTests.isIncludeAndroidResources must be on")
        return stream.use { Properties().apply { load(it) } }
    }

    private fun parseXml(file: File): Element = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)
        .documentElement
}

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

internal fun Element.androidAttribute(name: String): String {
    assertTrue("<$tagName> has no android:$name", hasAttributeNS(ANDROID_NS, name))
    return getAttributeNS(ANDROID_NS, name)
}

internal fun Element.childElements(): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<Element>()
}

/** This element and every element below it, in document order. */
internal fun Element.selfAndDescendants(): List<Element> =
    listOf(this) + childElements().flatMap { it.selfAndDescendants() }
