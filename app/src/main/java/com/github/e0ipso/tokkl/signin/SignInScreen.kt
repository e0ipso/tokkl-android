package com.github.e0ipso.tokkl.signin

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerUrl
import kotlinx.coroutines.delay

/**
 * Sign-in with a server URL, which may carry a path, and the API token by
 * default or email and password. [onSignedIn] runs once the server has
 * accepted the credentials and the app has stored the server and the token.
 */
@Composable
fun SignInScreen(model: SignInModel, onSignedIn: () -> Unit, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }
    // Recomputed every second from the model's clock, so the countdown stays
    // right across recomposition and configuration changes. At zero the model
    // forgets the deadline.
    val secondsLeft by produceState(model.secondsUntilRetry(), state.retryAt) {
        while (true) {
            value = model.secondsUntilRetry()
            if (value == 0L) break
            delay(COUNTDOWN_TICK_MS)
        }
        if (state.retryAt != null) model.cooldownEnded()
    }
    SignInForm(
        state = state,
        secondsLeft = secondsLeft,
        onServerUrlChange = model::onServerUrlChange,
        onMethodChange = model::onMethodChange,
        onApiTokenChange = model::onApiTokenChange,
        onEmailChange = model::onEmailChange,
        onPasswordChange = model::onPasswordChange,
        onSubmit = model::submit,
        modifier = modifier,
    )
    val keyboard = LocalSoftwareKeyboardController.current
    val failure = state.failure
    if (failure is SignInResult.CleartextNotConfirmed || failure is SignInResult.UntrustedCertificate) {
        // The keyboard would leave the dialog little room.
        LaunchedEffect(failure) { keyboard?.hide() }
    }
    when (failure) {
        is SignInResult.CleartextNotConfirmed -> CleartextWarning(
            server = failure.server,
            onConfirm = model::confirmCleartext,
            onCancel = model::dismissPrompt,
        )
        is SignInResult.UntrustedCertificate -> CertificatePrompt(
            certificate = failure.certificate,
            changed = failure.changed,
            onTrust = model::trustCertificate,
            onCancel = model::dismissPrompt,
        )
        else -> Unit
    }
}

@Composable
private fun SignInForm(
    state: SignInState,
    secondsLeft: Long,
    onServerUrlChange: (String) -> Unit,
    onMethodChange: (SignInMethod) -> Unit,
    onApiTokenChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val errors = state.inputErrors
    val canSubmit = !state.submitting && secondsLeft == 0L
    val submitFromKeyboard = KeyboardActions(onDone = { if (canSubmit) onSubmit() })
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.sign_in_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text(stringResource(R.string.sign_in_server_url)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (InputError.SERVER_URL in errors) R.string.sign_in_error_server_url else R.string.sign_in_server_url_hint,
                        ),
                    )
                },
                isError = InputError.SERVER_URL in errors,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            MethodChoice(selected = state.method, onSelect = onMethodChange)
            when (state.method) {
                SignInMethod.API_TOKEN -> SecretField(
                    value = state.apiToken,
                    onValueChange = onApiTokenChange,
                    label = R.string.sign_in_api_token,
                    hint = R.string.sign_in_api_token_hint,
                    error = R.string.sign_in_error_api_token.takeIf { InputError.API_TOKEN in errors },
                    keyboardActions = submitFromKeyboard,
                )
                SignInMethod.EMAIL_PASSWORD -> {
                    val emailError = when {
                        InputError.EMAIL in errors -> R.string.sign_in_error_email
                        InputError.EMAIL_COLON in errors -> R.string.sign_in_error_email_colon
                        else -> null
                    }
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = { Text(stringResource(R.string.sign_in_email)) },
                        supportingText = emailError?.let { { Text(stringResource(it)) } },
                        isError = emailError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecretField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = R.string.sign_in_password,
                        hint = R.string.sign_in_password_hint,
                        error = R.string.sign_in_error_password.takeIf { InputError.PASSWORD in errors },
                        keyboardActions = submitFromKeyboard,
                    )
                }
            }
            state.failure?.let { failureMessage(it, state.method) }?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (secondsLeft > 0) {
                val seconds = secondsLeft.toInt()
                Text(
                    text = pluralStringResource(R.plurals.sign_in_retry_countdown, seconds, seconds),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Button(onClick = onSubmit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.submitting) R.string.sign_in_submitting else R.string.sign_in_submit))
            }
        }
    }
}

/** Radio buttons: the selected one differs in shape, not only in colour. */
@Composable
private fun MethodChoice(selected: SignInMethod, onSelect: (SignInMethod) -> Unit) {
    Column(Modifier.selectableGroup()) {
        Text(
            text = stringResource(R.string.sign_in_method),
            style = MaterialTheme.typography.titleMedium,
        )
        for ((method, label) in METHOD_LABELS) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = method == selected, role = Role.RadioButton, onClick = { onSelect(method) }),
            ) {
                RadioButton(selected = method == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(text = stringResource(label), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * A masked field for the API token or the password. The password
 * transformation also turns off copy and cut and marks the field as a password
 * for accessibility services.
 */
@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes hint: Int,
    @StringRes error: Int?,
    keyboardActions: KeyboardActions,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        supportingText = { Text(stringResource(error ?: hint)) },
        isError = error != null,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = keyboardActions,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Warns that a plain `http://` server would get the token or password
 * unencrypted. Neither a tap outside nor Back closes it. Continuing takes two
 * steps: ticking that the user understands, then the continue button.
 */
@Composable
private fun CleartextWarning(server: ServerUrl, onConfirm: () -> Unit, onCancel: () -> Unit) {
    var understood by rememberSaveable(server.toString()) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = {
            Text(
                text = stringResource(R.string.sign_in_http_warning_title),
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.sign_in_http_warning_body, server.toString()))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = understood, role = Role.Checkbox, onValueChange = { understood = it }),
                ) {
                    Checkbox(checked = understood, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.sign_in_http_warning_understood))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = understood) {
                Text(stringResource(R.string.sign_in_http_warning_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.sign_in_warning_cancel))
            }
        },
    )
}

/**
 * Asks whether to trust a certificate that no system or user CA signed, and
 * shows its SHA-256 fingerprint and subject so the user can compare them with
 * the server's. [changed] says the user had trusted another certificate for
 * this server. A tap outside or Back cancels, which trusts nothing.
 */
@Composable
private fun CertificatePrompt(certificate: ServerCertificate, changed: Boolean, onTrust: () -> Unit, onCancel: () -> Unit) {
    val server = if (':' in certificate.host) "[${certificate.host}]:${certificate.port}" else "${certificate.host}:${certificate.port}"
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = stringResource(if (changed) R.string.sign_in_certificate_changed_title else R.string.sign_in_certificate_title),
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(if (changed) R.string.sign_in_certificate_changed_body else R.string.sign_in_certificate_body, server))
                Text(stringResource(R.string.sign_in_certificate_fingerprint), style = MaterialTheme.typography.titleSmall)
                Text(certificate.displaySha256, fontFamily = FontFamily.Monospace)
                Text(stringResource(R.string.sign_in_certificate_subject), style = MaterialTheme.typography.titleSmall)
                Text(certificate.subject)
            }
        },
        confirmButton = {
            TextButton(onClick = onTrust) {
                Text(stringResource(R.string.sign_in_certificate_trust))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.sign_in_warning_cancel))
            }
        },
    )
}

/** The message under the form, or null for a result that the screen shows in a dialog or not at all. */
@Composable
private fun failureMessage(failure: SignInResult, method: SignInMethod): String? =
    when (failure) {
        SignInResult.BadCredentials -> stringResource(
            if (method == SignInMethod.API_TOKEN) R.string.sign_in_error_wrong_api_token else R.string.sign_in_error_wrong_password,
        )
        is SignInResult.RateLimited -> stringResource(R.string.sign_in_error_rate_limited)
        SignInResult.Unreachable -> stringResource(R.string.sign_in_error_unreachable)
        is SignInResult.UnexpectedStatus -> stringResource(R.string.sign_in_error_unexpected_status, failure.status)
        SignInResult.NotTokkl -> stringResource(R.string.sign_in_error_not_tokkl)
        is SignInResult.HostNotAllowed -> stringResource(R.string.sign_in_error_host_not_allowed, failure.origin)
        SignInResult.NoApiToken -> stringResource(R.string.sign_in_error_no_api_token)
        SignInResult.StorageFailed -> stringResource(R.string.sign_in_error_storage)
        SignInResult.AnswerNotStored -> stringResource(R.string.sign_in_error_answer_not_stored)
        is SignInResult.CleartextNotConfirmed, is SignInResult.UntrustedCertificate, SignInResult.SignedIn -> null
    }

private val METHOD_LABELS = listOf(
    SignInMethod.API_TOKEN to R.string.sign_in_method_api_token,
    SignInMethod.EMAIL_PASSWORD to R.string.sign_in_method_email_password,
)

private const val COUNTDOWN_TICK_MS = 250L
