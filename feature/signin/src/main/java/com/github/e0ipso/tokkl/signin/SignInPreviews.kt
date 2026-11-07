package com.github.e0ipso.tokkl.signin

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

// Design-time previews of sign-in, with each method and a sample server.

@PreviewLightDark
@Composable
private fun SignInApiTokenPreview() {
    SignInPreview(SignInState(serverUrl = "https://tokkl.home.example"))
}

@PreviewLightDark
@Composable
private fun SignInPasswordPreview() {
    SignInPreview(SignInState(serverUrl = "https://tokkl.home.example", method = SignInMethod.EMAIL_PASSWORD))
}

@Composable
private fun SignInPreview(state: SignInState) {
    TokklTheme {
        SignInContent(
            state = state,
            secondsLeft = 0,
            actions = SignInActions(
                onServerUrlChange = {},
                onMethodChange = {},
                onApiTokenChange = {},
                onEmailChange = {},
                onPasswordChange = {},
                onSubmit = {},
                onConfirmCleartext = {},
                onTrustCertificate = {},
                onDismissPrompt = {},
            ),
        )
    }
}
