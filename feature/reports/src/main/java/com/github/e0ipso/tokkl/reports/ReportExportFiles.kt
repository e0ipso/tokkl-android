package com.github.e0ipso.tokkl.reports

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.time.Clock
import java.util.UUID

/**
 * Only completed CSV files live under shared/, the FileProvider's sole root. Staging files are
 * never exposed. At most four 16 MiB files in each area, pruned on export; files expire after a
 * day and sign-out removes both areas. The provider also refuses expired reads.
 */
class ReportExportFiles(private val root: File, private val clock: Clock) {
    class Pending internal constructor(val staging: File, val directory: File, filename: String) : Closeable {
        val completed = File(directory, filename)

        override fun close() {
            staging.delete()
        }
    }

    fun prepare(filename: String): Pending = synchronized(LOCK) {
        require(
            filename.matches(
                Regex("tokkl-(summary|detailed)-[0-9]{4}-[0-9]{2}-[0-9]{2}-to-[0-9]{4}-[0-9]{2}-[0-9]{2}\\.csv"),
            ),
        )
        val staging = File(root, "staging")
        val shared = File(root, "shared")
        if (!staging.madeDirectory() || !shared.madeDirectory()) {
            throw IOException("Cannot create export directory")
        }
        prune(staging, MAX_FILES - 1)
        prune(shared, MAX_FILES)
        val id = UUID.randomUUID().toString()
        val part = File(staging, "$id.part")
        if (!part.createNewFile()) throw IOException("Cannot create export file")
        Pending(part, File(shared, id), filename)
    }

    /** Must run inside the account lease. fsync/close precede this atomic rename. */
    fun publish(pending: Pending, length: Long): File = synchronized(LOCK) {
        if (!pending.staging.isFile || pending.staging.length() != length || length > MAX_BYTES) {
            throw IOException("Incomplete export file")
        }
        validateCsv(pending.staging)
        prune(File(root, "shared"), MAX_FILES - 1)
        val directory = pending.directory
        if (!directory.mkdirs()) throw IOException("Cannot create export destination")
        if (!pending.staging.renameTo(pending.completed)) {
            directory.delete()
            throw IOException("Cannot finish export file")
        }
        if (!pending.completed.setLastModified(clock.millis()) || !directory.setLastModified(clock.millis())) {
            discard(pending.completed)
            throw IOException("Cannot date export file")
        }
        pending.completed
    }

    fun discard(file: File) = synchronized(LOCK) {
        if (file.parentFile?.parentFile == File(root, "shared")) {
            file.delete()
            file.parentFile?.delete()
        }
    }

    @Throws(IOException::class)
    fun clear() = synchronized(LOCK) {
        if (root.exists() && !root.deleteRecursively()) throw IOException("Cannot clear exports")
    }

    private fun prune(directory: File, keep: Int) {
        val files = directory.listFiles() ?: throw IOException("Cannot list exports")
        val ordered = files.sortedByDescending { it.lastModified() }
        ordered.forEachIndexed { index, file ->
            if (index >= keep || clock.millis() - file.lastModified() >= MAX_AGE_MILLIS) {
                if (!file.deleteRecursively()) throw IOException("Cannot prune exports")
            }
        }
    }

    companion object {
        const val DIRECTORY = "report_exports"
        const val MAX_BYTES = 16L * 1024 * 1024
        const val MAX_FILES = 4
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

        // A JVM lock, not a Mutex: the account's sign-in and sign-out steps clear the files
        // without suspending, while exports write and discard them on other threads.
        private val LOCK = Any()

        /** Validate UTF-8 and CSV quoting without rewriting a byte or retaining report rows. */
        internal fun validateCsv(file: File) {
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            InputStreamReader(file.inputStream(), decoder).buffered().use { reader ->
                val checker = CsvChecker()
                while (true) {
                    val next = reader.read()
                    if (next == -1) break
                    checker.read(next.toChar())
                }
                checker.finish()
            }
        }
    }
}

/** True when this is a directory, after making it if it was missing. */
private fun File.madeDirectory(): Boolean = isDirectory || mkdirs()

/** Reads an export one character at a time and fails on content that is not a CSV export. */
private class CsvChecker {
    private var quoted = false
    private var afterQuote = false
    private var fieldStart = true
    private var first = true

    fun read(char: Char) {
        if (first && char == '\uFEFF') return
        checkContent(char)
        checkQuoting(char)
    }

    fun finish() {
        if (quoted) throw IOException("Incomplete CSV quoting")
    }

    /** An HTML or JSON body, or a NUL, is no CSV. */
    private fun checkContent(char: Char) {
        if (first && !char.isWhitespace()) {
            if (char in listOf('<', '{', '[')) throw IOException("Unexpected export content")
            first = false
        }
        if (char == '\u0000') throw IOException("Unexpected export content")
    }

    private fun checkQuoting(char: Char) {
        when {
            quoted -> if (char == '"') {
                quoted = false
                afterQuote = true
            }

            afterQuote && char == '"' -> {
                quoted = true
                afterQuote = false
            }

            char == ',' || char == '\r' || char == '\n' -> {
                fieldStart = true
                afterQuote = false
            }

            afterQuote -> throw IOException("Invalid CSV quoting")

            char == '"' && fieldStart -> {
                quoted = true
                fieldStart = false
            }

            char == '"' -> throw IOException("Invalid CSV quoting")

            else -> fieldStart = false
        }
    }
}
