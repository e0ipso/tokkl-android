package com.github.e0ipso.tokkl.api

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * One `GET /api/v9/me/preferences` answer. A refresh asks for it once and hands it to each
 * reader: the account settings, the synced preferences and the cache's favorites. Each reader
 * reads only its own keys, by the JSON type it expects, so a malformed key fails that reader
 * alone.
 */
class MePreferences internal constructor(private val body: JsonObject) {

    /** Whether the answer holds [key], whatever its value. */
    fun has(key: String): Boolean = key in body

    /** [key]'s value when it is a JSON string. */
    fun string(key: String): String? = (body[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** [key]'s value when it is a JSON number. */
    fun number(key: String): Double? = (body[key] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull

    /** [key]'s value when it is a JSON boolean. */
    fun boolean(key: String): Boolean? = (body[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

    /**
     * [key]'s value as JavaScript's truthiness reads it, or null when it is missing or null: false,
     * 0 and an empty string are false, and any other value, an object or a list included, is true.
     */
    fun truthy(key: String): Boolean? {
        val value = body[key] ?: return null
        if (value is JsonNull) return null
        val primitive = value as? JsonPrimitive ?: return true
        return when {
            primitive.isString -> primitive.content.isNotEmpty()
            primitive.booleanOrNull != null -> primitive.booleanOrNull
            else -> primitive.doubleOrNull?.let { it != 0.0 } ?: true
        }
    }

    /**
     * The `favorites`, in the user's order: none when Tokkl left the key out, as it does until
     * the user saves one, and null when they do not decode.
     */
    fun favorites(): List<ServerFavorite>? = try {
        Wire.decodeFromJsonElement(ServerPreferences.serializer(), body).favorites.orEmpty()
    } catch (_: IllegalArgumentException) {
        // kotlinx.serialization's SerializationException is an IllegalArgumentException.
        null
    }
}

/** `GET /api/app/bounds` read for the account's [firstDate] with an entry, null when it has none. */
data class EntryBounds(val firstDate: LocalDate?)

/** The bounds [body] holds, or [ApiError.InvalidResponse] when its `first_date` is neither null nor a date. */
internal fun entryBounds(body: JsonObject): ApiResult<EntryBounds> {
    val first = body["first_date"] as? JsonPrimitive
    if (first is JsonNull) return ApiResult.Success(EntryBounds(null))
    val date = try {
        first?.takeIf { it.isString }?.content?.let(LocalDate::parse)
    } catch (_: DateTimeParseException) {
        null
    }
    return date?.let { ApiResult.Success(EntryBounds(it)) }
        ?: ApiResult.Failure(ApiError.InvalidResponse(IllegalArgumentException("first_date is not a date")))
}
