package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiCallCancellation
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.api.TogglApiClient
import com.github.e0ipso.tokkl.capabilities.csvEndpointAbsent
import com.github.e0ipso.tokkl.session.ExportAccount
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReportExportState {
    data object Idle : ReportExportState
    data object Preparing : ReportExportState
    data class Ready(val file: File) : ReportExportState
    data object Sharing : ReportExportState
    data class Failed(val error: ApiError? = null, val noTargets: Boolean = false) : ReportExportState
}

/** One export at a time, independent of report pagination and protected by an account lease. */
class ReportExportController(
    private val client: () -> TogglApiClient?,
    private val files: ReportExportFiles,
    private val account: ExportAccount,
    private val csvMissing: (ReportCsvKind) -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineContext,
) {
    private val mutableState = MutableStateFlow<ReportExportState>(ReportExportState.Idle)
    val state: StateFlow<ReportExportState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var cancellation: ApiCallCancellation? = null
    private var lease: ExportAccount.Lease? = null

    /** Main thread only. Failed attempts may be retried, but pending exports cannot be duplicated. */
    fun start(request: ReportCsvRequest) {
        if (state.value !is ReportExportState.Idle && state.value !is ReportExportState.Failed) return
        val cancellation = ApiCallCancellation()
        val lease = account.open { cancellation.cancel() }
        this.cancellation = cancellation
        this.lease = lease
        mutableState.value = ReportExportState.Preparing
        job = scope.launch {
            var pending: ReportExportFiles.Pending? = null
            var completed: File? = null
            var retained = false
            try {
                val outcome = withContext(io) {
                    val staged = lease.current { files.prepare(request.filename) }
                        ?: return@withContext ReportExportState.Idle
                    pending = staged
                    cancellation.check()
                    val api = client() ?: return@withContext ReportExportState.Failed(ApiError.AuthFailed)
                    val answer = FileOutputStream(staged.staging).use { output ->
                        val result = api.downloadCsv(
                            api.server.app("report", "${request.kind.path}.csv"), request.body(),
                            output, ReportExportFiles.MAX_BYTES, cancellation,
                        )
                        if (result is ApiResult.Success) { output.flush(); output.fd.sync() }
                        result
                    }
                    when (answer) {
                        is ApiResult.Failure -> {
                            if (answer.error.csvEndpointAbsent()) lease.current { csvMissing(request.kind) }
                            ReportExportState.Failed(answer.error)
                        }
                        is ApiResult.Success -> {
                            cancellation.check()
                            completed = lease.current { files.publish(staged, answer.value) }
                            completed?.let { ReportExportState.Ready(it) } ?: ReportExportState.Idle
                        }
                    }
                }
                lease.current {
                    mutableState.value = outcome
                    retained = outcome is ReportExportState.Ready
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                lease.current { mutableState.value = ReportExportState.Failed() }
            } catch (e: SecurityException) {
                lease.current { mutableState.value = ReportExportState.Failed() }
            } finally {
                cancellation.cancel()
                withContext(NonCancellable + io) {
                    pending?.close()
                    if (!retained) completed?.let(files::discard)
                }
                if (!retained) {
                    lease.close()
                    if (this@ReportExportController.lease === lease) {
                        this@ReportExportController.lease = null
                        if (state.value == ReportExportState.Preparing) mutableState.value = ReportExportState.Idle
                    }
                }
            }
        }
    }

    /**
     * Claims the ready result before launching, so rotation cannot reopen the chooser. The
     * launch and account mutation are serialized. A former account's file never reaches Android.
     */
    fun launchShare(launch: (File) -> Unit) {
        val ready = state.value as? ReportExportState.Ready ?: return
        val current = lease ?: return
        val launched = current.current {
            mutableState.value = ReportExportState.Sharing
            launch(ready.file)
            true
        } ?: false
        if (!launched) cancel()
    }

    /** Chooser cancellation is ordinary completion. Keep its file for asynchronous receivers. */
    fun shareFinished() {
        lease?.close()
        lease = null
        mutableState.value = ReportExportState.Idle
    }

    fun shareFailed() {
        lease?.close()
        lease = null
        mutableState.value = ReportExportState.Failed()
    }

    fun noShareTarget() {
        lease?.close()
        lease = null
        mutableState.value = ReportExportState.Failed(noTargets = true)
    }

    fun cancel() {
        cancellation?.cancel()
        job?.cancel()
        lease?.close()
        lease = null
        val ready = state.value as? ReportExportState.Ready
        if (ready != null) scope.launch(io) { files.discard(ready.file) }
        mutableState.value = ReportExportState.Idle
    }
}
