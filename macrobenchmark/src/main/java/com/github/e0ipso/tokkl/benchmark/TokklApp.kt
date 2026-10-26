package com.github.e0ipso.tokkl.benchmark

import android.os.ParcelFileDescriptor
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.StringReader
import java.util.Properties
import java.util.regex.Pattern

/** The app the benchmarks start and measure. */
const val TARGET_PACKAGE = "com.canpicasoft.tokkl"

// A BySelector is a builder that each call changes, so each use gets a new one.

/** The Timer page's Start button, by the words TalkBack reads (timer_start_spoken). */
fun startButton(): BySelector = By.pkg(TARGET_PACKAGE).desc("Start timer")

/** The Timer page's Stop button (timer_stop_spoken). */
fun stopButton(): BySelector = By.pkg(TARGET_PACKAGE).desc("Stop timer")

/** Start or Stop, whichever the Timer page shows. */
private fun timerButton(): BySelector = By.pkg(TARGET_PACKAGE).desc(Pattern.compile("(Start|Stop) timer"))

/** The sign-in screen's title and button (sign_in_title, sign_in_submit). */
private fun signInScreen(): BySelector = By.pkg(TARGET_PACKAGE).text("Sign in")

/** Long enough for a sign-in and the first refresh on the CI emulator. */
const val UI_TIMEOUT_MS = 30_000L

/**
 * Waits until the app shows the Timer page, after signing in through the sign-in screen when
 * that shows instead. The account is the one scripts/instrumented-tests.sh creates on its Tokkl
 * server: the server URL and the email come as instrumentation arguments, and the API token from
 * the credentials file the script puts on the device, which only the shell user reads.
 */
fun UiDevice.awaitTimerPage() {
    val signedIn = wait({ device: UiDevice ->
        when {
            device.hasObject(timerButton()) -> true
            device.hasObject(signInScreen()) -> false
            else -> null
        }
    }, UI_TIMEOUT_MS) ?: error("neither the Timer page nor the sign-in screen showed")
    if (!signedIn) signIn()
    checkNotNull(wait(Until.findObject(timerButton()), UI_TIMEOUT_MS)) { "the Timer page did not show after signing in" }
}

/** Signs in with the API token over plain http, past the warning that asks twice. */
private fun UiDevice.signIn() {
    val arguments = InstrumentationRegistry.getArguments()
    val url = checkNotNull(arguments.getString("tokklBaseUrl")) { "run the benchmarks through scripts/instrumented-tests.sh" }
    val fields = wait(Until.findObjects(By.pkg(TARGET_PACKAGE).clazz(EditText::class.java)), UI_TIMEOUT_MS)
    check(fields != null && fields.size >= 2) { "the sign-in screen shows no server URL and API token fields" }
    // The fields come in screen order, and the API token is the default method.
    fields[0].text = url
    fields[1].text = apiToken(checkNotNull(arguments.getString("tokklCredentialsFile")))
    clickText("Sign in")
    clickText("I understand that my API token or password travels unencrypted")
    clickText("Continue without encryption")
}

/**
 * Clicks the control that shows [text] once it shows. Compose puts a button's text on a node
 * under the clickable one, and a merged row's on the row itself.
 */
private fun UiDevice.clickText(text: String) {
    val control = wait({ device: UiDevice ->
        device.findObject(By.pkg(TARGET_PACKAGE).clickable(true).text(text))
            ?: device.findObject(By.pkg(TARGET_PACKAGE).clickable(true).hasDescendant(By.text(text)))
    }, UI_TIMEOUT_MS) ?: error("\"$text\" did not show")
    control.click()
}

/** The API token from the credentials file at [path], read as the shell user. */
private fun apiToken(path: String): String {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val text = automation.executeShellCommand("cat $path").use { descriptor ->
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText()
    }
    return checkNotNull(Properties().apply { load(StringReader(text)) }.getProperty("tokklApiToken")) {
        "$path holds no tokklApiToken"
    }
}
