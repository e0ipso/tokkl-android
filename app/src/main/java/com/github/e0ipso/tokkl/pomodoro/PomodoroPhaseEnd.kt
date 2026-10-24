package com.github.e0ipso.tokkl.pomodoro

import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import java.time.Instant

/**
 * A phase whose planned end the alarm has reached. The phase itself stays until the user confirms.
 *
 * @property phaseStartedAt the running phase's start, so a later phase is not treated as this one.
 */
data class ReachedPhaseEnd(val phaseKind: String, val phaseStartedAt: Instant)

/** The last [ReachedPhaseEnd] the alarm recorded, shared by the receiver and the page. */
interface PomodoroPhaseEndRecord {
    fun read(): ReachedPhaseEnd?

    fun write(reached: ReachedPhaseEnd)

    fun clear()
}

/** The record the unit tests keep in memory. */
internal class MemoryPomodoroPhaseEnds : PomodoroPhaseEndRecord {
    private var value: ReachedPhaseEnd? = null

    override fun read(): ReachedPhaseEnd? = value

    override fun write(reached: ReachedPhaseEnd) {
        value = reached
    }

    override fun clear() {
        value = null
    }
}

/**
 * The same record on disk, so the process the alarm started still has it after that process is
 * gone. Sign-out (TKA-0253) clears it with the alarm. The backup rules leave the file out.
 *
 * The first call loads the preferences file, so it reads the disk.
 */
class PomodoroPhaseEndStore(context: Context) : PomodoroPhaseEndRecord {
    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    override fun read(): ReachedPhaseEnd? {
        if (!preferences.contains(KEY_KIND)) return null
        val kind = preferences.getString(KEY_KIND, null) ?: return null
        return ReachedPhaseEnd(kind, Instant.ofEpochMilli(preferences.getLong(KEY_STARTED, 0)))
    }

    @Throws(IOException::class)
    override fun write(reached: ReachedPhaseEnd) = commit {
        putString(KEY_KIND, reached.phaseKind)
        putLong(KEY_STARTED, reached.phaseStartedAt.toEpochMilli())
    }

    @Throws(IOException::class)
    override fun clear() = commit {
        remove(KEY_KIND)
        remove(KEY_STARTED)
    }

    private fun commit(change: SharedPreferences.Editor.() -> Unit) {
        if (!preferences.edit().apply(change).commit()) throw IOException("Could not write $FILE_NAME")
    }

    companion object {
        const val FILE_NAME = "pomodoro_phase_alarm"

        private const val KEY_KIND = "phase_kind"
        private const val KEY_STARTED = "phase_started_at"
    }
}
