package com.github.e0ipso.tokkl.api

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only [SharedHttpClient] may build an HTTP client, so the host guard and the
 * other security settings cover every request. The test reads the constant
 * pool of every compiled app class, so an import alias or a fully qualified
 * name cannot hide a use.
 */
class SingleHttpClientTest {

    private val classes: Map<String, Set<String>> by lazy { appClassReferences() }

    @Test
    fun noAppClassButSharedHttpClientBuildsAnHttpClient() {
        assertTrue("the scan found the app's classes", "com/github/e0ipso/tokkl/MainActivity" in classes)

        val offenders = classes
            .filterKeys { !isSharedHttpClient(it) }
            .flatMap { (name, references) -> references.filter(::isForbidden).map { "$name uses $it" } }

        assertTrue("Use SharedHttpClient.instance instead:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /** Shows the scan sees a client being built, so the test above cannot pass by finding nothing. */
    @Test
    fun scanSeesTheSharedClientBeingBuilt() {
        val shared = classes.filterKeys(::isSharedHttpClient).values.flatten()

        assertTrue(shared.any { it == "okhttp3/OkHttpClient\$Builder.<init>" && isForbidden(it) })
    }

    private fun isSharedHttpClient(className: String): Boolean =
        className == SHARED || className.startsWith("$SHARED\$")

    private fun isForbidden(reference: String): Boolean =
        FORBIDDEN.any { prefix ->
            reference == prefix ||
                (prefix.endsWith("/") && reference.startsWith(prefix)) ||
                reference.startsWith("$prefix.")
        }

    /** Class name to the classes and members its constant pool refers to, as `owner` or `owner.member`. */
    private fun appClassReferences(): Map<String, Set<String>> {
        val location = File(SharedHttpClient::class.java.protectionDomain!!.codeSource.location.toURI())
        val classFiles: Map<String, ByteArray> = if (location.isDirectory) {
            location.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".class") }
                .associate { it.relativeTo(location).invariantSeparatorsPath.removeSuffix(".class") to it.readBytes() }
        } else {
            ZipFile(location).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.endsWith(".class") }
                    .associate { it.name.removeSuffix(".class") to jar.getInputStream(it).use { s -> s.readBytes() } }
            }
        }
        return classFiles
            .filterKeys { it.startsWith(APP_PACKAGE) }
            .mapValues { (_, bytes) -> references(bytes) }
    }

    /** Reads the class and member references from a class file's constant pool (JVMS 4.4). */
    private fun references(bytes: ByteArray): Set<String> {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        check(input.readInt() == CLASS_MAGIC) { "not a class file" }
        input.readUnsignedShort()
        input.readUnsignedShort()
        val count = input.readUnsignedShort()
        val utf8 = arrayOfNulls<String>(count)
        val classNameIndex = IntArray(count)
        val nameAndTypeNameIndex = IntArray(count)
        val classEntries = mutableListOf<Int>()
        val memberEntries = mutableListOf<Pair<Int, Int>>()
        var index = 1
        while (index < count) {
            when (val tag = input.readUnsignedByte()) {
                TAG_UTF8 -> utf8[index] = input.readUTF()
                TAG_INTEGER, TAG_FLOAT -> input.readInt()
                TAG_LONG, TAG_DOUBLE -> {
                    input.readLong()
                    index++ // Takes two entries.
                }
                TAG_CLASS -> {
                    classNameIndex[index] = input.readUnsignedShort()
                    classEntries += index
                }
                TAG_STRING, TAG_METHOD_TYPE, TAG_MODULE, TAG_PACKAGE -> input.readUnsignedShort()
                TAG_FIELD_REF, TAG_METHOD_REF, TAG_INTERFACE_METHOD_REF ->
                    memberEntries += input.readUnsignedShort() to input.readUnsignedShort()
                TAG_NAME_AND_TYPE -> {
                    nameAndTypeNameIndex[index] = input.readUnsignedShort()
                    input.readUnsignedShort()
                }
                TAG_METHOD_HANDLE -> {
                    input.readUnsignedByte()
                    input.readUnsignedShort()
                }
                TAG_DYNAMIC, TAG_INVOKE_DYNAMIC -> {
                    input.readUnsignedShort()
                    input.readUnsignedShort()
                }
                else -> error("unknown constant pool tag $tag")
            }
            index++
        }
        val classNames = classEntries.map { utf8[classNameIndex[it]]!! }
        val members = memberEntries.map { (owner, nameAndType) ->
            utf8[classNameIndex[owner]] + "." + utf8[nameAndTypeNameIndex[nameAndType]]
        }
        return (classNames + members).toSet()
    }

    private companion object {
        const val APP_PACKAGE = "com/github/e0ipso/tokkl/"
        const val SHARED = "com/github/e0ipso/tokkl/api/SharedHttpClient"

        /** Ways to build an HTTP client or open a connection without the shared client. */
        val FORBIDDEN = listOf(
            "okhttp3/OkHttpClient\$Builder",
            "okhttp3/OkHttpClient.<init>",
            "okhttp3/OkHttpClient.newBuilder",
            "java/net/URL.openConnection",
            "java/net/URL.openStream",
            "java/net/URLConnection",
            "java/net/HttpURLConnection",
            "javax/net/ssl/HttpsURLConnection",
            "java/net/http/",
            "java/net/Socket.<init>",
            "javax/net/SocketFactory.createSocket",
            "javax/net/ssl/SSLSocketFactory.createSocket",
            "android/webkit/WebView",
            "android/app/DownloadManager",
        )

        const val CLASS_MAGIC = 0xCAFEBABE.toInt()
        const val TAG_UTF8 = 1
        const val TAG_INTEGER = 3
        const val TAG_FLOAT = 4
        const val TAG_LONG = 5
        const val TAG_DOUBLE = 6
        const val TAG_CLASS = 7
        const val TAG_STRING = 8
        const val TAG_FIELD_REF = 9
        const val TAG_METHOD_REF = 10
        const val TAG_INTERFACE_METHOD_REF = 11
        const val TAG_NAME_AND_TYPE = 12
        const val TAG_METHOD_HANDLE = 15
        const val TAG_METHOD_TYPE = 16
        const val TAG_DYNAMIC = 17
        const val TAG_INVOKE_DYNAMIC = 18
        const val TAG_MODULE = 19
        const val TAG_PACKAGE = 20
    }
}
