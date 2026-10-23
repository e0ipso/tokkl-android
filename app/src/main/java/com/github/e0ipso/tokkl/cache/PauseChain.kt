package com.github.e0ipso.tokkl.cache

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.SecureRandom

/**
 * The signed-in account's pause chain (docs/PRD.md T1), as the web app keeps it in
 * ../tokkl/packages/web/src/components/pauseChain.ts. Pause stops the running entry and keeps
 * its fields here. Resume starts a clone of them, a new entry, and never reopens a stopped one.
 * The chain lives in the cache's database, so each transition commits with the queue change it
 * makes, survives the process, and goes with the account at sign-out. One chain at a time, in the
 * row [slot] 0.
 *
 * @property chainId tells this chain from every earlier one, so an action shown for an ended chain
 *   never acts on a newer one. Random and never 0.
 * @property revision goes up with each Pause and Resume. An action carries the revision it was
 *   shown at and acts only while the chain still has it, so a second tap or an old surface does
 *   nothing.
 * @property epoch goes up with each transition and with each queue answer while the chain lasts.
 *   A refresh whose server read began before it changed may predate the change, so it leaves the
 *   chain alone.
 * @property stoppedSeconds the whole seconds the stopped segments store, each as its captured Stop
 *   snaps it. A segment too short to keep adds none.
 * @property runningId the running clone's id, or null while paused.
 * @property workspaceId where Resume starts the clone: the last stopped segment's workspace.
 * @property fields what Resume copies: the last stopped segment's fields and the edits made while
 *   paused.
 */
@Entity(tableName = "pause_chain")
data class PauseChain(
    @PrimaryKey val slot: Int = 0,
    val chainId: Long,
    val revision: Long,
    val epoch: Long,
    val stoppedSeconds: Long,
    val runningId: Long?,
    val workspaceId: Long,
    @Embedded(prefix = "snapshot_") val fields: EntryFields,
)

/** The [PauseChain.chainId] and [PauseChain.epoch] a server read began at, or null without a chain. */
data class ChainMark(val chainId: Long, val epoch: Long)

/**
 * The phone's timer session: a random [id] that every timer control carries in its
 * [TimerTarget]. Sign-out deletes it with the cache, and the next session gets a new one, so a
 * notification or widget action shown before sign-out never acts after it.
 */
@Entity(tableName = "timer_session")
data class TimerSession(@PrimaryKey val slot: Int = 0, val id: Long)

private val random = SecureRandom()

/** A random id that is never 0, for a chain or a session. */
internal fun newTimerId(): Long = generateSequence { random.nextLong() }.first { it != 0L }
