package com.github.e0ipso.tokkl

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/** Shell commands on the test device, run as the shell user through UiAutomation. */
object DeviceShell {

    /** Runs [command] and returns its standard output once it has finished. */
    fun run(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }
}
