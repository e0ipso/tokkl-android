package com.github.e0ipso.tokkl.reports

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock

/** A sign-in or sign-out ends every export of the account before it and deletes its files. */
class ExportAccountTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aSignInEndsTheLeasesCancelsTheirDownloadsAndDeletesTheFiles() {
        val root = folder.newFolder(ReportExportFiles.DIRECTORY)
        val account = ExportAccount(ReportExportFiles(root, Clock.systemUTC()))
        var cancelled = 0
        val lease = account.open { cancelled++ }
        lease.current { root.resolve("shared").mkdirs() }

        account.signedIn()

        assertEquals("the download was cancelled", 1, cancelled)
        assertNull("the old lease publishes nothing", lease.current { "published" })
        assertFalse("the finished exports are gone", root.exists())
    }

    @Test
    fun aSignOutDoesTheSame() = runTest {
        val root = folder.newFolder(ReportExportFiles.DIRECTORY)
        val account = ExportAccount(ReportExportFiles(root, Clock.systemUTC()))
        var cancelled = 0
        val lease = account.open { cancelled++ }

        account.forgetAccount()

        assertEquals(1, cancelled)
        assertNull(lease.current { "published" })
        assertFalse(root.exists())
    }
}
