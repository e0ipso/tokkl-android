package com.github.e0ipso.tokkl.reports

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * The range on the Reports page as the page keeps it in its saved state, so that a new process
 * restoring the page asks for the range it showed: its first and last day, and the preset it came
 * from, none for a custom range or one stepped to.
 */
@Serializable
internal data class SavedRange(val fromEpochDay: Long, val toEpochDay: Long, val preset: RangePreset? = null) {

    fun selection() = RangeSelection(
        ReportRange(LocalDate.ofEpochDay(fromEpochDay), LocalDate.ofEpochDay(toEpochDay)),
        preset,
    )

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(selection: RangeSelection) =
            SavedRange(selection.range.from.toEpochDay(), selection.range.to.toEpochDay(), selection.preset)

        /** The range [text] saved, or null for one an older version saved in a shape this one cannot read. */
        fun decode(text: String): RangeSelection? = try {
            json.decodeFromString(serializer(), text).selection()
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
