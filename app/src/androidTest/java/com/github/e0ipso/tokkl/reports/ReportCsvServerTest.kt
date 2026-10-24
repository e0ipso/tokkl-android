package com.github.e0ipso.tokkl.reports

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.TestAccounts
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ApiCallCancellation
import com.github.e0ipso.tokkl.orFail
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.time.DurationFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real server CSV bytes through publication and Android's actual read-only content provider. */
class ReportCsvServerTest {
    @get:Rule val session = SessionRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val scope = MainScope()
    private val files = ReportExportFiles(File(context.cacheDir, ReportExportFiles.DIRECTORY))
    private lateinit var fixture: ReportAccount
    private lateinit var exporter: ReportExportController

    @Before
    fun prepareReportAccount() {
        fixture = ReportAccount.create()
        Session(context).save(TokklTestServer.url, fixture.account.apiToken)
        exporter = ReportExportController(
            client = { fixture.account.client }, files = files, account = Session.exports,
            csvMissing = {},
            scope = scope, io = Dispatchers.IO,
        )
    }

    @After
    fun removeDisposableAccountAndExports() {
        instrumentation.runOnMainSync {
            if (::exporter.isInitialized) exporter.cancel()
            scope.cancel()
        }
        try {
            if (::fixture.isInitialized) TestAccounts.delete(fixture.account)
        } finally {
            Session(context).clear()
            SessionRule.signOut()
            files.clear()
        }
    }

    @Test
    fun summarySharesTheFilteredServerBytesAndSignOutRevokesThePublishedFile() {
        exportAndRead(ReportCsvKind.SUMMARY)
    }

    @Test
    fun detailedSharesTheFilteredServerBytesAndSignOutRevokesThePublishedFile() {
        exportAndRead(ReportCsvKind.DETAILED)
    }

    private fun exportAndRead(kind: ReportCsvKind) {
        val selected = fixture.project("Café, \"quoted\"", "#125b50")
        val excluded = fixture.project("Excluded project", "#cc3366")
        fixture.entry(fixture.today.atTime(9, 0), 901, "Résumé, \"quoted\"\nsecond line", selected)
        fixture.entry(fixture.today.atTime(10, 0), 901, "Excluded description", excluded)
        val request = ReportCsvRequest(
            kind, ReportRange(fixture.today, fixture.today), ReportFilters(projects = listOf(selected)),
            ReportsServer(fixture.account.client).workspace().orFail("read report workspace"), DurationFormat.DECIMAL,
        )
        val expected = ByteArrayOutputStream().use { output ->
            fixture.account.client.downloadCsv(
                fixture.account.client.server.app("report", "${kind.path}.csv"), request.body(),
                output, ReportExportFiles.MAX_BYTES, ApiCallCancellation(),
            ).orFail("read the web app's ${kind.path} CSV")
            output.toByteArray()
        }
        val text = expected.toString(Charsets.UTF_8)
        assertTrue("the selected project's Unicode reaches the server CSV", text.contains("Café"))
        assertFalse("the project filter applies to the whole export", text.contains("Excluded"))
        if (kind == ReportCsvKind.DETAILED) assertTrue(text.contains("Résumé"))

        instrumentation.runOnMainSync { exporter.start(request) }
        val result = runBlocking {
            withTimeout(30_000) { exporter.state.first { it !is ReportExportState.Preparing } }
        }
        assertTrue("export must finish successfully, got $result", result is ReportExportState.Ready)
        val file = (result as ReportExportState.Ready).file
        assertEquals(request.filename, file.name)
        assertArrayEquals("publication preserves every server byte", expected, file.readBytes())
        var shares = 0
        lateinit var send: Intent
        instrumentation.runOnMainSync {
            val launch: (File) -> Unit = { shares++; send = ReportCsvShare.intent(context, it) }
            exporter.launchShare(launch)
            exporter.launchShare(launch)
        }
        assertEquals("a ready result can launch sharing only once", 1, shares)
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/csv", send.type)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send.flags)
        @Suppress("DEPRECATION")
        val uri = checkNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("content", uri.scheme)
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        assertEquals("text/csv", context.contentResolver.getType(uri))
        val received = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertArrayEquals("the receiver reads the same completed CSV", expected, received)
        assertFileRejected { context.contentResolver.openFileDescriptor(uri, "w")?.close() }

        instrumentation.runOnMainSync { exporter.shareFinished() }
        // Chooser completion retains the file for an asynchronous receiver; sign-out removes it.
        assertTrue(file.isFile)
        Session(context).clear()
        assertFalse(file.exists())
        assertFileRejected { context.contentResolver.openInputStream(uri)?.close() }
    }

    private fun assertFileRejected(open: () -> Unit) {
        var rejected = false
        try { open() } catch (_: FileNotFoundException) { rejected = true }
        assertTrue("the content provider must reject this read or write", rejected)
    }
}
