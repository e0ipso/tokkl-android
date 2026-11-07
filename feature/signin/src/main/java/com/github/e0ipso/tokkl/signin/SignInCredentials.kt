package com.github.e0ipso.tokkl.signin

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.github.e0ipso.tokkl.feature.signin.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

/** The API token field, or the email and password fields, for the chosen method. */
@Composable
internal fun Credentials(
    state: SignInState,
    onApiTokenChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    keyboardActions: KeyboardActions,
) {
    val errors = state.inputErrors
    when (state.method) {
        SignInMethod.API_TOKEN -> SecretField(
            value = state.apiToken,
            onValueChange = onApiTokenChange,
            label = R.string.sign_in_api_token,
            hint = R.string.sign_in_api_token_hint,
            error = R.string.sign_in_error_api_token,
            showError = InputError.API_TOKEN in errors,
            keyboardActions = keyboardActions,
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
                supportingText = {
                    SupportingLine(
                        hint = R.string.sign_in_email_hint,
                        error = emailError,
                        errors = listOf(R.string.sign_in_error_email, R.string.sign_in_error_email_colon),
                    )
                },
                isError = emailError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.keptAboveKeyboard().fillMaxWidth(),
            )
            SecretField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = R.string.sign_in_password,
                hint = R.string.sign_in_password_hint,
                error = R.string.sign_in_error_password,
                showError = InputError.PASSWORD in errors,
                keyboardActions = keyboardActions,
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
    @StringRes error: Int,
    showError: Boolean,
    keyboardActions: KeyboardActions,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        supportingText = { SupportingLine(hint = hint, error = error.takeIf { showError }, errors = listOf(error)) },
        isError = showError,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = keyboardActions,
        modifier = Modifier.keptAboveKeyboard().fillMaxWidth(),
    )
}

/**
 * Brings a focused field, with its supporting line, into view whenever the keyboard's height
 * changes, one frame later, once the form's IME padding has shrunk the scroll area. The scroll
 * area keeps a focused field in view on its own only when the field was wholly inside it before
 * the keyboard opened and no other scroll was running. On tokkl-api36 at font scale 2.0, a
 * password field scrolled to end within 1dp of the bottom edge stayed under the keyboard.
 */
@Composable
internal fun Modifier.keptAboveKeyboard(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val keyboard = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        snapshotFlow { keyboard.getBottom(density) }
            .filter { it > 0 }
            .collectLatest {
                withFrameNanos {}
                requester.bringIntoView()
            }
    }
    return bringIntoViewRequester(requester).onFocusChanged { focused = it.isFocused }
}

/**
 * A field's supporting line: [error] when there is one, else [hint]. It always takes the height of
 * the tallest of [hint] and [errors], every error the field can show, at the current width and
 * font scale, so showing or clearing an error never moves the fields below. The lines not shown
 * are laid out invisible and left out of semantics, so TalkBack reads only the one that shows.
 */
@Composable
internal fun SupportingLine(@StringRes hint: Int, @StringRes error: Int?, @StringRes errors: List<Int>) {
    val shown = error ?: hint
    Box {
        for (line in listOf(hint) + errors) {
            if (line != shown) Text(stringResource(line), Modifier.alpha(0f).clearAndSetSemantics {})
        }
        Text(stringResource(shown))
    }
}

/** The message under the form, or null for a result that the screen shows in a dialog or not at all. */
