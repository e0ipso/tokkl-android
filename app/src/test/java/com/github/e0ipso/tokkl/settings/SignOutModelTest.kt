package com.github.e0ipso.tokkl.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class SignOutModelTest {

    private var unsent = 0
    private var countFails = false
    private var clears = 0
    private var clearSucceeds = true

    private fun model(clear: suspend () -> Boolean = { clears++; clearSucceeds }) =
        SignOutModel(
            unsentCount = { if (countFails) throw IllegalStateException("disk I/O error") else unsent },
            clear = clear,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

    @Test
    fun anEmptyQueueSignsOutWithoutAsking() {
        val model = model()

        model.signOut()

        assertEquals(1, clears)
        assertEquals(SignOutModel.State(), model.state.value)
    }

    @Test
    fun unsentChangesAskFirstWithTheirCount() {
        unsent = 3
        val model = model()

        model.signOut()

        assertEquals(SignOutModel.State(unsentToConfirm = 3), model.state.value)
        assertEquals("nothing is cleared before the answer", 0, clears)
    }

    @Test
    fun cancelKeepsEverything() {
        unsent = 1
        val model = model()
        model.signOut()

        model.cancel()

        assertEquals(SignOutModel.State(), model.state.value)
        assertEquals(0, clears)
    }

    @Test
    fun confirmingSignsOut() {
        unsent = 2
        val model = model()
        model.signOut()

        model.confirm()

        assertEquals(1, clears)
        assertEquals(SignOutModel.State(), model.state.value)
    }

    @Test
    fun aFailedClearSaysSoAndATapTriesAgain() {
        clearSucceeds = false
        val model = model()

        model.signOut()
        assertEquals(SignOutModel.State(failed = true), model.state.value)

        clearSucceeds = true
        model.signOut()
        assertEquals(2, clears)
        assertEquals(SignOutModel.State(), model.state.value)
    }

    @Test
    fun aQueueThatCannotBeReadSignsNothingOut() {
        countFails = true
        val model = model()

        model.signOut()

        assertEquals(0, clears)
        assertEquals(SignOutModel.State(failed = true), model.state.value)
    }

    @Test
    fun tapsWhileSigningOutStartNoSecondSignOut() {
        val finish = CompletableDeferred<Boolean>()
        val model = model(clear = { clears++; finish.await() })

        model.signOut()
        assertEquals(SignOutModel.State(signingOut = true), model.state.value)
        model.signOut()
        model.confirm()
        assertEquals(1, clears)

        finish.complete(true)
        assertEquals(SignOutModel.State(), model.state.value)
    }

    @Test
    fun confirmAndCancelDoNothingWithoutTheWarning() {
        val model = model()

        model.confirm()
        model.cancel()

        assertEquals(0, clears)
        assertEquals(SignOutModel.State(), model.state.value)
    }
}
