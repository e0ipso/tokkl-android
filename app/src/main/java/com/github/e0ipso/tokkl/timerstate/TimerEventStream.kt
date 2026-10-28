package com.github.e0ipso.tokkl.timerstate

import android.database.sqlite.SQLiteException
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiJson
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import java.io.IOException
import java.io.InputStream

// `GET /api/app/timer/events` of Tokkl 1.10.0, read by hand from the shared client's response body
// so the app needs no SSE library. The server sends `event: timer` with the state as one line of
// JSON on connect and after every change, and `event: keep-alive` with an empty `data:` line every
// 25 s (../tokkl/packages/api/src/services/timerStreams.ts at v1.10.0).

/** How one hold of the timer event stream ended, which decides what [TimerSync] does next. */
sealed interface StreamEnd {

    /**
     * The server ended the response. It does so at a restart, when a newer stream of the same user
     * takes this one's place, and when the credential stops signing in, in which case the
     * reconnect gets the 403.
     */
    data object Ended : StreamEnd

    /**
     * No answer, a dropped or cancelled connection, a status that retrying may fix, a body that is
     * not an event stream, a state the phone cannot read, or a disk failure ([error] null). The
     * held state stays, and the stream connects again later.
     */
    data class Failed(val error: ApiError?) : StreamEnd

    /** 401 or 403: the server refuses the account's credential, and retrying cannot fix that. */
    data object Refused : StreamEnd

    /** No account is signed in, so there is no stream to hold. */
    data object SignedOut : StreamEnd
}

/**
 * Holds the timer event stream of [source]'s account through [api] until the stream ends or [link]
 * is cancelled, and gives each state it sends to [source]. Returns how it ended. Blocks on the
 * network and the disk, so call it off the main thread.
 *
 * @param api the signed-in account's client, or null when signed out.
 */
internal fun holdTimerEvents(api: TogglApiClient?, source: TimerStateSource, link: TimerSyncLink): StreamEnd {
    api ?: return StreamEnd.SignedOut
    return try {
        // Before the request: the server builds the first event's state before it answers.
        val first = source.begin()
        val answer = api.stream(api.server.app("timer", "events"), link.cancellation) { body ->
            link.connected()
            readTimerEvents(body, source, link, first)
        }
        when (answer) {
            is ApiResult.Success -> answer.value
            is ApiResult.Failure -> if (answer.error.refusesCredential()) StreamEnd.Refused else StreamEnd.Failed(answer.error)
        }
    } catch (e: SQLiteException) {
        // The cache could not be read or written. The next connection tries again.
        StreamEnd.Failed(null)
    }
}

/** Tokkl answers a bad credential with 403. A proxy in front of it may answer 401. */
private fun ApiError.refusesCredential(): Boolean = this == ApiError.AuthFailed || (this as? ApiError.Http)?.status == UNAUTHORIZED

/**
 * Reads [body] until it ends and gives each `timer` event's state to [source], against a
 * [TimerStateRead] taken before waiting for that event, starting with [first]. Every event and
 * comment tells [link] the stream is alive. A `keep-alive` event, a comment and an event of another
 * name change nothing else. A timer event whose data is not a state ends the read without touching
 * the held state, so the stream connects again. Once [link] is cancelled, nothing more is taken.
 *
 * @return [StreamEnd.Ended] when the body ends or [link] was cancelled, and [StreamEnd.Failed] for
 *   a state the phone cannot read. An [IOException] of [body] passes through.
 */
internal fun readTimerEvents(body: InputStream, source: TimerStateSource, link: TimerSyncLink, first: TimerStateRead): StreamEnd {
    var read = first
    val parser = EventStreamParser()
    val buffer = ByteArray(READ_BUFFER_BYTES)
    while (link.active) {
        val count = body.read(buffer)
        if (count == -1) return StreamEnd.Ended
        for (item in parser.feed(buffer, count)) {
            if (!link.active) return StreamEnd.Ended
            link.alive()
            if (item !is StreamItem.Event) continue
            if (item.name == TIMER_EVENT) {
                val state = try {
                    ApiJson.decodeFromString(TimerState.serializer(), item.data)
                } catch (e: IllegalArgumentException) {
                    // kotlinx.serialization's SerializationException is an IllegalArgumentException.
                    return StreamEnd.Failed(ApiError.InvalidResponse(e))
                }
                source.accept(read, state)
            }
            // Before waiting for the next event, so its state cannot undo a change made meanwhile.
            read = source.begin()
        }
    }
    return StreamEnd.Ended
}

/** What a Server-Sent Events body holds, as [EventStreamParser] reads it. */
internal sealed interface StreamItem {

    /**
     * An event, at the blank line that ends its block. [name] is its `event:` field, or `message`
     * without one, and [data] its `data:` lines joined by line feeds.
     */
    data class Event(val name: String, val data: String) : StreamItem

    /** A line that starts with a colon. */
    data object Comment : StreamItem
}

/**
 * Splits a Server-Sent Events body into [StreamItem]s as the HTML standard's event stream
 * interpretation does, however the reads cut it: a read may end inside a line, between the CR and
 * LF of a line break, or inside a UTF-8 character, since each line is decoded only once it ends.
 * A line ends at LF, CR or CRLF. A block without a `data:` line dispatches nothing, and a block
 * the body ends inside is never dispatched, so a cut connection cannot deliver half a state.
 * `id:` and `retry:` lines are ignored, as Tokkl sends neither.
 *
 * @param maxLineBytes the longest line it keeps. A longer one fails the read with an
 *   [IOException], so a broken server cannot fill the phone's memory.
 */
internal class EventStreamParser(private val maxLineBytes: Int = MAX_LINE_BYTES) {

    private var line = ByteArray(INITIAL_LINE_BYTES)
    private var length = 0

    /** The last byte was a CR, so an LF that follows ends no further line. */
    private var afterCr = false

    private var name = ""

    /** The block's data lines, each followed by a line feed, or null before its first one. */
    private var data: StringBuilder? = null

    /** The items that the lines completed by the first [count] bytes of [bytes] hold, in order. */
    @Throws(IOException::class)
    fun feed(bytes: ByteArray, count: Int): List<StreamItem> {
        val items = ArrayList<StreamItem>(0)
        for (index in 0 until count) {
            val byte = bytes[index]
            if (byte == LF && afterCr) {
                afterCr = false
            } else if (byte == LF || byte == CR) {
                afterCr = byte == CR
                lineEnded(items)
            } else {
                afterCr = false
                append(byte)
            }
        }
        return items
    }

    private fun append(byte: Byte) {
        if (length == maxLineBytes) throw IOException("An event stream line is longer than $maxLineBytes bytes")
        if (length == line.size) line = line.copyOf(minOf(line.size * 2, maxLineBytes))
        line[length++] = byte
    }

    private fun lineEnded(items: MutableList<StreamItem>) {
        val text = String(line, 0, length, Charsets.UTF_8)
        length = 0
        when {
            text.isEmpty() -> dispatch(items)
            text[0] == ':' -> items += StreamItem.Comment
            else -> {
                val colon = text.indexOf(':')
                val field = if (colon < 0) text else text.substring(0, colon)
                val value = if (colon < 0) "" else text.substring(colon + 1).removePrefix(" ")
                when (field) {
                    "event" -> name = value
                    "data" -> (data ?: StringBuilder().also { data = it }).append(value).append('\n')
                }
            }
        }
    }

    private fun dispatch(items: MutableList<StreamItem>) {
        val block = data
        if (block != null) items += StreamItem.Event(name.ifEmpty { DEFAULT_EVENT }, block.substring(0, block.length - 1))
        name = ""
        data = null
    }

    private companion object {
        const val LF = '\n'.code.toByte()
        const val CR = '\r'.code.toByte()
        const val DEFAULT_EVENT = "message"
        const val INITIAL_LINE_BYTES = 1024

        /** A timer state is a few kilobytes; this leaves room for long descriptions and many tags. */
        const val MAX_LINE_BYTES = 1 shl 20
    }
}

private const val TIMER_EVENT = "timer"
private const val READ_BUFFER_BYTES = 8192
private const val UNAUTHORIZED = 401
