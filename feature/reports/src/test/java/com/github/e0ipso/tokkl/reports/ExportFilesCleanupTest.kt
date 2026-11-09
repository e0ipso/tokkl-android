package com.github.e0ipso.tokkl.reports

import com.github.e0ipso.tokkl.api.ApiCredentials
import com.github.e0ipso.tokkl.api.InMemoryServerTrust
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.api.TestApi
import com.github.e0ipso.tokkl.capabilities.ReportCsvKind
import com.github.e0ipso.tokkl.model.Capabilities
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.session.CurrentAccount
import com.github.e0ipso.tokkl.session.capabilitiesOf
import com.github.e0ipso.tokkl.time.DurationFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** No report export outlives the account it was made for. */
class ExportFilesCleanupTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aSignInOrASignOutDeletesTheFinishedExports() = runTest {
        val root = folder.newFolder(ReportExportFiles.DIRECTORY)
        val cleanup = ExportFilesCleanup(ReportExportFiles(root, Clock.systemUTC()))

        root.resolve("shared").mkdirs()
        cleanup.signedIn()
        assertFalse(root.exists())

        folder.newFolder(ReportExportFiles.DIRECTORY, "shared")
        cleanup.forgetAccount()
        assertFalse(root.exists())
    }

    @Test
    fun anExportWhoseAccountEndsWhileItDownloadsIsCancelledAndPublishesNothing() {
        val release = CountDownLatch(1)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                release.await(10, TimeUnit.SECONDS)
                return MockResponse.Builder().code(200).body("Description\nold account\n").build()
            }
        }
        server.start()
        val testApi = TestApi(null)
        try {
            val url = ServerUrl.parse(server.url("/").toString())!!
            testApi.stored = url
            testApi.trust = InMemoryServerTrust(url)
            val api = testApi.api(url, ApiCredentials.ApiToken("test"))
            val root = folder.newFolder(ReportExportFiles.DIRECTORY)
            val account = CurrentAccount(CoroutineScope(Dispatchers.Unconfined))
            val exports = ReportExportController(
                client = { api },
                files = ReportExportFiles(root, Clock.systemUTC()),
                account = account,
                csvMissing = capabilitiesOf(Capabilities.NONE)::csvMissing,
                scope = CoroutineScope(Dispatchers.Unconfined),
                io = Dispatchers.IO,
            )

            exports.start(request())
            assertNotNull(server.takeRequest(10, TimeUnit.SECONDS))
            account.begin()
            release.countDown()
            runBlocking { withTimeout(10_000) { exports.state.first { it == ReportExportState.Idle } } }

            assertEquals(ReportExportState.Idle, exports.state.value)
            assertTrue("nothing was published", root.resolve("shared").listFiles().isNullOrEmpty())
        } finally {
            release.countDown()
            server.close()
        }
    }

    private fun request(): ReportCsvRequest {
        val day = LocalDate.parse("2026-11-09")
        return ReportCsvRequest(
            kind = ReportCsvKind.DETAILED,
            range = ReportRange(day, day),
            filters = ReportFilters(),
            workspace = ReportWorkspace(WorkspaceId(7), 1, 15),
            durationFormat = DurationFormat.IMPROVED,
        )
    }
}
