package com.github.e0ipso.tokkl.signin

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.trust.CertificatePrompt
import com.github.e0ipso.tokkl.trust.CleartextWarning
import com.github.e0ipso.tokkl.ui.Brand
import com.github.e0ipso.tokkl.ui.BrandSize
import com.github.e0ipso.tokkl.ui.PageTitle
import com.github.e0ipso.tokkl.ui.RadioOptionRow
import com.github.e0ipso.tokkl.ui.TokklButton
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent
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
    Scaffold(modifier = modifier.fillMaxSize(), contentWindowInsets = ScrollingScreenInsets) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                // The keyboard's full height, which covers the navigation bar. imePadding consumes
                // it, so navigationBarsPadding adds nothing while the keyboard is open.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .screenContent()
                .padding(vertical = TokklTheme.spacing.space6),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space4),
        ) {
            Brand(BrandSize.LARGE, Modifier.align(Alignment.CenterHorizontally))
            PageTitle(stringResource(R.string.sign_in_title))
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
                        // Always there, with the hint or the error, so an error does not move the fields below.
                        supportingText = { Text(stringResource(emailError ?: R.string.sign_in_email_hint)) },
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
            TokklButton(onClick = onSubmit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
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
            modifier = Modifier.semantics { heading() },
        )
        for ((method, label) in METHOD_LABELS) {
            RadioOptionRow(
                label = stringResource(label),
                selected = method == selected,
                onSelect = { onSelect(method) },
                textStyle = MaterialTheme.typography.bodyLarge,
            )
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

/** The message under the form, or null for a result that the screen shows in a dialog or not at all. */
@Composable
private fun failureMessage(failure: SignInResult, method: SignInMethod): String? =
    when (failure) {
        SignInResult.BadCredentials -> stringResource(
            if (method == SignInMethod.API_TOKEN) R.string.sign_in_error_wrong_api_token else R.string.sign_in_error_wrong_password,
        )
        is SignInResult.RateLimited -> stringResource(R.string.sign_in_error_rate_limited)
        SignInResult.Unreachable -> stringResource(R.string.sign_in_error_unreachable)
        is SignInResult.CertificateHostMismatch -> stringResource(R.string.sign_in_error_certificate_host, failure.host)
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
