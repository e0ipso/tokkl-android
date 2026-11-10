package com.github.e0ipso.tokkl.navhost

import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.cache.ChangeQueue
import com.github.e0ipso.tokkl.cache.PendingChange
import com.github.e0ipso.tokkl.cache.QueueStorage
import com.github.e0ipso.tokkl.cache.QueueTables
import com.github.e0ipso.tokkl.cache.ServerSpans
import com.github.e0ipso.tokkl.cache.ShortStopUndo
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.session.AccountRefreshRequests
import com.github.e0ipso.tokkl.trust.SessionTrust
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException
import java.time.Clock

/** The short Stop's Undo and its dismissal, when the phone's storage refuses the write. */
class SignedInViewModelTest {

    /** Storage that refuses every write, as a full disk does. */
    private val refusing = object : QueueStorage {
        override val unsentCount: Flow<Int> = emptyFlow()
        override val heldChanges: Flow<List<PendingChange>> = emptyFlow()
        override val heldCount: Flow<Int> = emptyFlow()
        override val latestShortStop: Flow<ShortStopUndo?> = emptyFlow()

        override fun <T> transaction(edit: QueueTables.() -> T): T = throw IOException("disk full")
    }

    private val noTrust = object : ServerTrust {
        override fun pinnedSha256(host: String, port: Int): String? = null

        override fun isCleartextConfirmed(server: ServerUrl): Boolean = false

        override fun confirmCleartext(server: ServerUrl) = Unit

        override fun pinCertificate(certificate: ServerCertificate) = Unit
    }

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val model = SignedInViewModel(
        badges = emptyList(),
        changes = ChangeQueue(refusing, Dispatchers.Unconfined, {}, Clock.systemUTC(), object : ServerSpans {}),
        trust = SessionTrust(noTrust, AccountRefreshRequests(), scope, Dispatchers.Unconfined),
        scope = scope,
    )

    /** The snackbar's Undo or its dismissal runs in the frame's effect, where a throw would end the app. */
    @Test
    fun anUndoOrDismissalTheStorageRefusesThrowsNothing() = runBlocking {
        model.undoShortStop(EntryId(5))
        model.dismissShortStop(EntryId(5))
    }
}
