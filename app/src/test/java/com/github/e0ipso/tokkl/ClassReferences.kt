package com.github.e0ipso.tokkl

import com.github.e0ipso.tokkl.api.SharedHttpClient
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipFile

/**
 * What compiled classes refer to, read from each class file's constant pool (JVMS 4.4). Tests
 * that forbid an API read the constant pool instead of the source, so an import alias or a fully
 * qualified name cannot hide a use.
 */
internal object ClassReferences {

    private const val APP_PACKAGE = "com/github/e0ipso/tokkl/"

    /** A field or method that a class refers to. [descriptor] is its JVM type, such as `()Ljava/time/ZoneId;`. */
    data class Member(val owner: String, val name: String, val descriptor: String) {
        override fun toString(): String = "$owner.$name$descriptor"
    }

    /** The classes and members that one class file refers to. */
    class References(val classes: Set<String>, val members: Set<Member>) {

        /** The classes, and the members as `owner.member` without their types. */
        val names: Set<String> by lazy { classes + members.map { "${it.owner}.${it.name}" } }
    }

    /** Every compiled app class, by name such as `com/github/e0ipso/tokkl/MainActivity`. */
    val app: Map<String, References> by lazy {
        read(SharedHttpClient::class.java).filterKeys { it.startsWith(APP_PACKAGE) }
    }

    /** Every class in the directory or jar that [type] was loaded from. */
    fun read(type: Class<*>): Map<String, References> {
        val location = File(type.protectionDomain!!.codeSource.location.toURI())
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
        return classFiles.mapValues { (_, bytes) -> references(bytes) }
    }

    private fun references(bytes: ByteArray): References {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        check(input.readInt() == CLASS_MAGIC) { "not a class file" }
        input.readUnsignedShort()
        input.readUnsignedShort()
        val count = input.readUnsignedShort()
        val utf8 = arrayOfNulls<String>(count)
        val classNameIndex = IntArray(count)
        val nameAndTypeNameIndex = IntArray(count)
        val nameAndTypeDescriptorIndex = IntArray(count)
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
                    nameAndTypeDescriptorIndex[index] = input.readUnsignedShort()
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
        val classes = classEntries.map { utf8[classNameIndex[it]]!! }.toSet()
        val members = memberEntries.map { (owner, nameAndType) ->
            Member(
                owner = utf8[classNameIndex[owner]]!!,
                name = utf8[nameAndTypeNameIndex[nameAndType]]!!,
                descriptor = utf8[nameAndTypeDescriptorIndex[nameAndType]]!!,
            )
        }.toSet()
        return References(classes, members)
    }

    private const val CLASS_MAGIC = 0xCAFEBABE.toInt()
    private const val TAG_UTF8 = 1
    private const val TAG_INTEGER = 3
    private const val TAG_FLOAT = 4
    private const val TAG_LONG = 5
    private const val TAG_DOUBLE = 6
    private const val TAG_CLASS = 7
    private const val TAG_STRING = 8
    private const val TAG_FIELD_REF = 9
    private const val TAG_METHOD_REF = 10
    private const val TAG_INTERFACE_METHOD_REF = 11
    private const val TAG_NAME_AND_TYPE = 12
    private const val TAG_METHOD_HANDLE = 15
    private const val TAG_METHOD_TYPE = 16
    private const val TAG_DYNAMIC = 17
    private const val TAG_INVOKE_DYNAMIC = 18
    private const val TAG_MODULE = 19
    private const val TAG_PACKAGE = 20
}
