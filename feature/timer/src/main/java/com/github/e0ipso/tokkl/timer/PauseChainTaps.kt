package com.github.e0ipso.tokkl.timer

import com.github.e0ipso.tokkl.timerstate.PauseActions
import com.github.e0ipso.tokkl.timerstate.TimerActionResult
import com.github.e0ipso.tokkl.timerstate.TimerControls
import com.github.e0ipso.tokkl.timerstate.TimerTarget
import com.github.e0ipso.tokkl.timerstate.target
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pause, Resume and Stop while paused, for [TimerViewModel]. They belong to the server's pause
 * chain and go to [actions] at once, at the revision the timer was shown at, so a stale tap gets a
 * refusal. Until the server's state shows, the timer keeps what it showed and ignores other taps.
 * A refusal or no answer becomes a notice on [notices]. Without [actions] they do nothing.
 *
 * @param sendQueued sends the queue now and waits for it, so a description typed before Pause
 *   reaches the server before the Pause does.
 * @param resumed called once the server has resumed, as a timer start.
 */
internal class PauseChainTaps(
    private val taps: TimerTapState,
    private val actions: PauseActions?,
    private val fields: RunningFieldEdits,
    private val sendQueued: suspend () -> Unit,
    private val notices: TimerNoticeBoard,
    private val resumed: () -> Unit,
) {

    /** See [TimerViewModel.pause]. */
    fun pause() {
        val actions = actions ?: return
        if (taps.local.value.pending != null) return
        val shown = taps.shown() as? TimerState.Running ?: return
        val running = taps.controls() as? TimerControls.Running ?: return
        if (!running.pausable || shown.entryId != running.entry.id) return
        val typed = taps.editFor(shown)?.takeIf { !it.sent }
        if (typed != null) fields.send(shown, typed.fields)
        act(running.target, done = { it.target != running.target }) {
            if (typed != null) {
                fields.awaitWrites()
                sendQueued()
            }
            actions.pause()
        }
    }

    /** See [TimerViewModel.resume]. */
    fun resume() {
        val actions = actions ?: return
        if (taps.local.value.pending != null) return
        if (taps.shown() !is TimerState.Paused) return
        val paused = taps.controls() as? TimerControls.Paused ?: return
        act(paused.target, done = { it.target != paused.target }, onDone = resumed) {
            actions.resume(paused.target.revision)
        }
    }

    /** Ends the paused chain on the server, at the revision it was shown at. */
    fun discard() {
        val actions = actions ?: return
        val paused = taps.controls() as? TimerControls.Paused ?: return
        taps.beginTap(TapTrace.STOP) { it is TimerState.Idle }
        act(paused.target, done = { it.target != paused.target }) { actions.discard(paused.target.revision) }
    }

    /**
     * Sends a pause chain action for what [target] showed, ignoring other taps until the server
     * answers and its state shows, as [done] tells.
     */
    private fun act(
        target: TimerTarget,
        done: (TimerControls) -> Boolean,
        onDone: () -> Unit = {
        },
        send: suspend () -> TimerActionResult,
    ) {
        val acting = PendingTap.Acting(target)
        taps.local.update { it.copy(pending = acting) }
        taps.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val result = send()
                if (result == TimerActionResult.Done) {
                    onDone()
                    taps.settle(done)
                } else {
                    notices.failed(result)
                }
            } finally {
                taps.local.update { if (it.pending == acting) it.copy(pending = null) else it }
            }
        }
    }
}
