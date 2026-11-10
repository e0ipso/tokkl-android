package com.github.e0ipso.tokkl.reports

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Plain text only: no HTML, links, expressions, inferred units or money conversion. */
data class ReportExtraField(val name: String, val value: String)

/**
 * A single budget covers the whole report, including every group and bucket. Limit traversal
 * before descending, not after serializing an arbitrary nested object. Truncation is explicit.
 * Recognized values are decoded separately and never subject to these presentation limits.
 */
internal class ReportExtraProjection {
    private var nodesLeft = 128
    private var charactersLeft = 12_000
    var truncated: Boolean = false
        private set

    fun fields(value: JsonObject, known: Set<String>): List<ReportExtraField> {
        val result = mutableListOf<ReportExtraField>()
        for ((key, child) in value.filterKeys { it !in known }) {
            if (!available() || result.size == MAX_ENTRIES) {
                truncated = true
                break
            }
            if (key.length > MAX_TEXT_CHARACTERS) truncated = true
            val name = text(humanizeReportKey(key))
            result += ReportExtraField(name, render(child, 0))
        }
        return result
    }

    private fun available(): Boolean = nodesLeft > 0 && charactersLeft > 0

    private fun render(value: JsonElement, depth: Int): String {
        if (!available()) return omitted()
        nodesLeft--
        return when (value) {
            is JsonPrimitive -> text(if (value.isString) quoted(value.content) else value.content)

            is JsonArray -> if (depth >= MAX_DEPTH) {
                omitted()
            } else {
                "[" + renderParts(value) { child -> render(child, depth + 1) }.joinToString(", ") + "]"
            }

            is JsonObject -> if (depth >= MAX_DEPTH) {
                omitted()
            } else {
                val parts = renderParts(value.entries) { (key, child) ->
                    if (key.length > MAX_TEXT_CHARACTERS) truncated = true
                    text(humanizeReportKey(key)) + ": " + render(child, depth + 1)
                }
                "{" + parts.joinToString(", ") + "}"
            }
        }
    }

    private fun <T> renderParts(items: Iterable<T>, part: (T) -> String): List<String> {
        val parts = mutableListOf<String>()
        for (item in items) {
            if (!available() || parts.size == MAX_ENTRIES) {
                parts += omitted()
                break
            }
            parts += part(item)
        }
        return parts
    }

    private fun text(value: String): String {
        val limit = minOf(MAX_TEXT_CHARACTERS, charactersLeft)
        val bounded = value.take(limit)
        charactersLeft -= bounded.length
        return if (value.length > limit) bounded + omitted() else bounded
    }

    private fun omitted(): String {
        truncated = true
        return "…"
    }

    private fun quoted(value: String): String {
        // Bound before escaping; JSON quoting keeps quotes/newlines literal and Compose Text
        // never interprets markup. Escape bidi/control formatting so labels cannot be spoofed.
        val limited = value.take(MAX_TEXT_CHARACTERS)
        if (limited.length != value.length) truncated = true
        val safe = limited.map {
            if (Character.getType(it) == Character.FORMAT.toInt()) {
                "\\u%04x".format(it.code)
            } else {
                it.toString()
            }
        }.joinToString("")
        return JsonPrimitive(safe).toString() + if (limited.length != value.length) "…" else ""
    }
}

internal fun humanizeReportKey(key: String): String = key.take(MAX_TEXT_CHARACTERS)
    .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
    .replace('_', ' ').replace('-', ' ')
    .map { if (it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt()) ' ' else it }
    .joinToString("")
    .replaceFirstChar { it.uppercaseChar() }

/** The most entries an object or a list shows before the rest is left out. */
private const val MAX_ENTRIES = 12

/** The longest key or text value shown. */
private const val MAX_TEXT_CHARACTERS = 240

/** How deep nested objects and lists show. */
private const val MAX_DEPTH = 3
