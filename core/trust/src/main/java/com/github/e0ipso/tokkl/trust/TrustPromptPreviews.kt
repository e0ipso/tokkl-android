package com.github.e0ipso.tokkl.trust

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

// Design-time previews of the trust prompts, for a sample server and certificate.

@PreviewLightDark
@Composable
private fun CleartextWarningPreview() {
    TokklTheme { CleartextWarning(SAMPLE_SERVER, onConfirm = {}, onCancel = {}) }
}

@PreviewLightDark
@Composable
private fun CertificatePromptPreview() {
    TokklTheme { CertificatePrompt(SAMPLE_CERTIFICATE, changed = true, onTrust = {}, onCancel = {}) }
}

@PreviewLightDark
@Composable
private fun TrustRefusedNoticePreview() {
    TokklTheme { TrustRefusedNotice(ApiError.UntrustedCertificate(SAMPLE_CERTIFICATE, changed = false), onReview = {}) }
}

private val SAMPLE_SERVER: ServerUrl = checkNotNull(ServerUrl.parse("http://192.168.1.20:8080"))

private val SAMPLE_CERTIFICATE = ServerCertificate(
    host = "tokkl.home.example",
    port = 443,
    sha256 = "4f1d7c2a9b3e8d6f0a5c1e7b2d9f4a6c8e3b0d5f7a2c9e1b4d6f8a0c3e5b7d9f",
    subject = "CN=tokkl.home.example",
)
