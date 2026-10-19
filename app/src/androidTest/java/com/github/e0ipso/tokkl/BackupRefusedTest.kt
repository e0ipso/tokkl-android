package com.github.e0ipso.tokkl

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android's backup manager refuses to back up the app, because the manifest sets
 * android:allowBackup="false" (docs/PRD.md S8). The data extraction rules, which also cover
 * device transfer, keep every domain out as well, and BackupRulesTest checks them on the JVM.
 *
 * The backup manager judges each package only while backup is on. While it is off it refuses
 * every request without naming a package. So the test turns backup on for the request, and back
 * off afterwards if it was off.
 */
class BackupRefusedTest {
    private val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    @Test
    fun backupManagerRefusesToBackUpTheApp() {
        val wasEnabled = DeviceShell.run("bmgr enabled").contains("currently enabled")
        try {
            if (!wasEnabled) DeviceShell.run("bmgr enable true")
            val output = DeviceShell.run("bmgr backupnow $packageName")
            assertTrue(output, output.contains("Package $packageName with result: Backup is not allowed"))
        } finally {
            if (!wasEnabled) DeviceShell.run("bmgr enable false")
        }
    }
}
