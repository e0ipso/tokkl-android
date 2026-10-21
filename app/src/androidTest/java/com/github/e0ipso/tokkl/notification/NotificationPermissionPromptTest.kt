package com.github.e0ipso.tokkl.notification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.DeviceShell
import com.github.e0ipso.tokkl.R
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The notification permission's question at the first timer start in the app (docs/PRD.md N5).
 * The test stands in for the system's permission dialog through the activity result registry,
 * because a test cannot take the permission back once another test allowed it: revoking it kills
 * the app's process, which is the test's own. So the question is asked with a record of the test's
 * own and with the permission not allowed yet, whatever the device holds.
 */
class NotificationPermissionPromptTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val starts = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val requests = CopyOnWriteArrayList<Intent>()
    private val requestCodes = CopyOnWriteArrayList<Int>()
    private val allows = CopyOnWriteArrayList<Unit>()
    private val settingsOpened = CopyOnWriteArrayList<Unit>()

    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            requests += contract.createIntent(context, input)
            requestCodes += requestCode
        }
    }

    private val record = object : NotificationPermissionAsk.AskRecord {
        @Volatile
        var asked = false

        override fun asked(): Boolean = asked

        override fun markAsked() {
            asked = true
        }
    }

    private val refused get() = context.getString(R.string.notification_permission_refused)

    private fun show() {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        val ask = NotificationPermissionAsk(Build.VERSION.SDK_INT, record) { false }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                NotificationPermissionPrompt(starts, ask, openSettings = { settingsOpened += Unit }, onAllowed = { allows += Unit })
            }
        }
        compose.waitUntil(TIMEOUT_MS) { starts.subscriptionCount.value > 0 }
        assertEquals("opening the app without an in-app start asks nothing", 0, requests.size)
    }

    private fun start() {
        check(starts.tryEmit(Unit))
        compose.waitForIdle()
    }

    private fun answer(allowed: Boolean) {
        compose.runOnUiThread { registry.dispatchResult(requestCodes.last(), allowed) }
        compose.waitForIdle()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun theFirstStartAsksAndARefusalSaysWhatIsLostAndOffersTheSettings() {
        show()

        start()
        compose.waitUntil(TIMEOUT_MS) { requests.size == 1 }
        val request = requests.single()
        assertEquals(ActivityResultContracts.RequestMultiplePermissions.ACTION_REQUEST_PERMISSIONS, request.action)
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            request.getStringArrayExtra(ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSIONS)?.toList(),
        )

        answer(allowed = false)
        compose.onNodeWithText(refused).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.notification_permission_settings)).performClick()
        compose.waitUntil(TIMEOUT_MS) { settingsOpened.size == 1 }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(refused).fetchSemanticsNodes().isEmpty() }

        start()
        Thread.sleep(SETTLE_MS)
        compose.waitForIdle()
        assertEquals("a later start does not ask again", 1, requests.size)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun anAllowShowsNoMessage() {
        show()
        start()
        compose.waitUntil(TIMEOUT_MS) { requests.size == 1 }

        answer(allowed = true)
        Thread.sleep(SETTLE_MS)
        compose.waitForIdle()

        assertEquals(0, compose.onAllNodesWithText(refused).fetchSemanticsNodes().size)
        assertEquals("a grant makes the running notification visible", 1, allows.size)
    }

    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.S_V2)
    fun belowAndroid13AStartAsksNothing() {
        show()

        start()
        Thread.sleep(SETTLE_MS)
        compose.waitForIdle()

        assertEquals(emptyList<Intent>(), requests)
    }

    /**
     * The message's action opens the app's own notification settings, a screen the device has. The
     * shell resolves it, because package visibility can hide the Settings app from the app.
     */
    @Test
    fun theSettingsActionOpensAScreenTheDeviceHas() {
        val intent = notificationSettingsIntent(context)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        val resolved = DeviceShell.run("cmd package resolve-activity --brief -a ${intent.action}").trim().lines().last()
        assertTrue("resolved to $resolved", resolved.contains('/'))
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L

        /** Longer than the prompt takes to read its record and ask. */
        const val SETTLE_MS = 1_000L
    }
}
