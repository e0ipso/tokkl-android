package com.github.e0ipso.tokkl.cache

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/** Stores instants as epoch milliseconds, dates as ISO `YYYY-MM-DD` text and lists as JSON arrays. */
internal class CacheConverters {

    @TypeConverter
    fun toInstant(epochMillis: Long?): Instant? = epochMillis?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toDate(text: String?): LocalDate? = text?.let(LocalDate::parse)

    @TypeConverter
    fun fromDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toNames(json: String): List<String> = Json.decodeFromString(NAMES, json)

    @TypeConverter
    fun fromNames(names: List<String>): String = Json.encodeToString(NAMES, names)

    @TypeConverter
    fun toIds(json: String): List<Long> = Json.decodeFromString(IDS, json)

    @TypeConverter
    fun fromIds(ids: List<Long>): String = Json.encodeToString(IDS, ids)

    private companion object {
        val NAMES = ListSerializer(String.serializer())
        val IDS = ListSerializer(Long.serializer())
    }
}
