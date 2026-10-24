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
        for ((key, child) in value) {
            if (key in known) continue
            if (!available() || result.size == 12) {
                truncated = true
                break
            }
            if (key.length > 240) truncated = true
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
            is JsonArray -> {
                if (depth >= 3) return omitted()
                val parts = mutableListOf<String>()
                for (child in value) {
                    if (!available() || parts.size == 12) { parts += omitted(); break }
                    parts += render(child, depth + 1)
                }
                "[" + parts.joinToString(", ") + "]"
            }
            is JsonObject -> {
                if (depth >= 3) return omitted()
                val parts = mutableListOf<String>()
                for ((key, child) in value) {
                    if (!available() || parts.size == 12) { parts += omitted(); break }
                    if (key.length > 240) truncated = true
                    parts += text(humanizeReportKey(key)) + ": " + render(child, depth + 1)
                }
                "{" + parts.joinToString(", ") + "}"
            }
        }
    }

    private fun text(value: String): String {
        val limit = minOf(240, charactersLeft)
        val bounded = value.take(limit)
        charactersLeft -= bounded.length
        return if (value.length > limit) bounded + omitted() else bounded
    }

    private fun omitted(): String { truncated = true; return "…" }

    private fun quoted(value: String): String {
        // Bound before escaping; JSON quoting keeps quotes/newlines literal and Compose Text
        // never interprets markup. Escape bidi/control formatting so labels cannot be spoofed.
        val limited = value.take(240)
        if (limited.length != value.length) truncated = true
        val safe = limited.map { if (Character.getType(it) == Character.FORMAT.toInt()) "\\u%04x".format(it.code) else it.toString() }.joinToString("")
        return JsonPrimitive(safe).toString() + if (limited.length != value.length) "…" else ""
    }
}

internal fun humanizeReportKey(key: String): String = key.take(240)
    .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
    .replace('_', ' ').replace('-', ' ')
    .map { if (it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt()) ' ' else it }
    .joinToString("")
    .replaceFirstChar { it.uppercaseChar() }
