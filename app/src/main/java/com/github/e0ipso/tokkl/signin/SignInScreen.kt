package com.github.e0ipso.tokkl.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.trust.CertificatePrompt
import com.github.e0ipso.tokkl.trust.CleartextWarning
import com.github.e0ipso.tokkl.ui.Brand
import com.github.e0ipso.tokkl.ui.BrandSize
import com.github.e0ipso.tokkl.ui.ErrorBanner
import com.github.e0ipso.tokkl.ui.PageTitle
import com.github.e0ipso.tokkl.ui.RadioOptionRow
import com.github.e0ipso.tokkl.ui.Scene
import com.github.e0ipso.tokkl.ui.StickerCard
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
fun SignInScreen(model: SignInViewModel, onSignedIn: () -> Unit, modifier: Modifier = Modifier) {
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
    if (failure is SignInResult.CleartextNotConfirmed || failure is SignInResult.UntrustedCertificate ||
        (failure != null && failureMessage(failure, state.method) != null)
    ) {
        // The keyboard would leave the dialog, or the banner's message, little room.
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

/** The large brand on Tokkl's scene, as on Tokkl's web sign-in, and the form on a card under it. */
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
    Scene(modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = ScrollingScreenInsets,
        ) { innerPadding ->
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
                verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.betweenGroups),
            ) {
                Brand(BrandSize.LARGE, Modifier.align(Alignment.CenterHorizontally))
                FormCard {
                    PageTitle(stringResource(R.string.sign_in_title))
                    OutlinedTextField(
                        value = state.serverUrl,
                        onValueChange = onServerUrlChange,
                        label = { Text(stringResource(R.string.sign_in_server_url)) },
                        supportingText = {
                            SupportingLine(
                                hint = R.string.sign_in_server_url_hint,
                                error = R.string.sign_in_error_server_url.takeIf { InputError.SERVER_URL in errors },
                                errors = listOf(R.string.sign_in_error_server_url),
                            )
                        },
                        isError = InputError.SERVER_URL in errors,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.keptAboveKeyboard().fillMaxWidth(),
                    )
                    MethodChoice(selected = state.method, onSelect = onMethodChange)
                    Group {
                        Credentials(
                            state = state,
                            onApiTokenChange = onApiTokenChange,
                            onEmailChange = onEmailChange,
                            onPasswordChange = onPasswordChange,
                            keyboardActions = submitFromKeyboard,
                        )
                    }
                    Group {
                        state.failure?.let { failureMessage(it, state.method) }?.let { message ->
                            ErrorBanner(message, Modifier.fillMaxWidth())
                        }
                        if (secondsLeft > 0) {
                            val seconds = secondsLeft.toInt()
                            Text(
                                text = pluralStringResource(R.plurals.sign_in_retry_countdown, seconds, seconds),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        TokklButton(onClick = onSubmit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
                            val label = if (state.submitting) R.string.sign_in_submitting else R.string.sign_in_submit
                            Text(stringResource(label))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The form on a surface card in Tokkl's frame, so no texture sits behind a field. Its parts are
 * the title and the groups: the server URL, the sign-in method, the credentials and the actions,
 * each the spacing between groups apart.
 */
@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    StickerCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(TokklTheme.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.betweenGroups),
            content = content,
        )
    }
}

/** One group of the form, its parts the spacing inside a group apart. */
@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup), content = content)
}

/** Radio buttons: the selected one differs in shape, not only in colour. */
@Composable
private fun MethodChoice(selected: SignInMethod, onSelect: (SignInMethod) -> Unit) {
    Group(Modifier.selectableGroup()) {
        Text(
            text = stringResource(R.string.sign_in_method),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        // Each row is a 48dp touch target, so the rows need no gap between them.
        Column {
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
}

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
        SignInResult.OtherAccount -> stringResource(R.string.sign_in_error_other_account)
        SignInResult.AnswerNotStored -> stringResource(R.string.sign_in_error_answer_not_stored)
        is SignInResult.CleartextNotConfirmed, is SignInResult.UntrustedCertificate, SignInResult.SignedIn -> null
    }

private val METHOD_LABELS = listOf(
    SignInMethod.API_TOKEN to R.string.sign_in_method_api_token,
    SignInMethod.EMAIL_PASSWORD to R.string.sign_in_method_email_password,
)

private const val COUNTDOWN_TICK_MS = 250L
