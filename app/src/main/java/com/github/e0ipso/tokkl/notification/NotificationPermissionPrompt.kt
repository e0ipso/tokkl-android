package com.github.e0ipso.tokkl.notification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.e0ipso.tokkl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Asks for the notification permission at the first timer start made in the app (docs/PRD.md
 * N5), as [ask] decides, for each item [starts] gives after the start. The timer never waits for
 * the answer. A refusal shows a snackbar that says what is lost and offers the app's notification
 * settings through [openSettings]. The snackbar is a polite live region, as Material's
 * SnackbarHost makes each one, so TalkBack reads it, and it stays for Material's long duration.
 */
@Composable
fun NotificationPermissionPrompt(
    starts: Flow<Unit>,
    ask: NotificationPermissionAsk,
    modifier: Modifier = Modifier,
    openSettings: (Context) -> Unit = ::openNotificationSettings,
    onAllowed: () -> Unit = {},
) {
    val context = LocalContext.current
    val host = remember { SnackbarHostState() }
    var refused by rememberSaveable { mutableStateOf(false) }
    val allowedNow by rememberUpdatedState(onAllowed)
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) allowedNow() else refused = true
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(starts, ask, lifecycle) {
        // A start finishing as the app goes to the background waits until it is on screen again.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            starts.collect {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && withContext(Dispatchers.IO) { ask.atStartInApp() }) {
                    request.launch(Manifest.permission.POST_NOTIFICATIONS)
                    // Once launched, persist even if a rotation or a background trip cancels collection.
                    withContext(NonCancellable + Dispatchers.IO) { ask.requested() }
                }
            }
        }
    }
    val message = stringResource(R.string.notification_permission_refused)
    val settingsLabel = stringResource(R.string.notification_permission_settings)
    val settings by rememberUpdatedState(openSettings)
    LaunchedEffect(refused) {
        if (!refused) return@LaunchedEffect
        val result = host.showSnackbar(
            message = message,
            actionLabel = settingsLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) settings(context)
        refused = false
    }
    SnackbarHost(host, modifier)
}

/** Opens the system's notification settings for this app. */
fun openNotificationSettings(context: Context) {
    context.startActivity(notificationSettingsIntent(context))
}

internal fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
