package com.github.e0ipso.tokkl.trust

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.api.ApiError
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerUrl

// The prompts about trusting a server, for sign-in and for any screen of a
// signed-in session. Their text lives in string resources.

/** The prompt for [refusal]: the http warning or the certificate prompt. */
@Composable
fun TrustPrompt(refusal: ApiError.TrustRefused, onAccept: () -> Unit, onCancel: () -> Unit) {
    when (refusal) {
        is ApiError.CleartextNotConfirmed -> CleartextWarning(refusal.server, onConfirm = onAccept, onCancel = onCancel)
        is ApiError.UntrustedCertificate -> CertificatePrompt(refusal.certificate, refusal.changed, onTrust = onAccept, onCancel = onCancel)
    }
}

/**
 * Says that requests to the server are on hold after the user cancelled a
 * trust prompt, with a button that opens the prompt again. Text, not colour,
 * carries the message.
 */
@Composable
fun TrustRefusedNotice(refusal: ApiError.TrustRefused, onReview: () -> Unit, modifier: Modifier = Modifier) {
    val message = when (refusal) {
        is ApiError.CleartextNotConfirmed -> R.string.trust_refused_http
        is ApiError.UntrustedCertificate -> R.string.trust_refused_certificate
    }
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)) {
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onReview, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.trust_refused_review))
            }
        }
    }
}

/**
 * Warns that a plain `http://` server would get the token or password
 * unencrypted. Neither a tap outside nor Back closes it. Continuing takes two
 * steps: ticking that the user understands, then the continue button.
 */
@Composable
fun CleartextWarning(server: ServerUrl, onConfirm: () -> Unit, onCancel: () -> Unit) {
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
            TrustActions(risky = R.string.sign_in_http_warning_continue, riskyEnabled = understood, onRisky = onConfirm, onCancel = onCancel)
        },
    )
}

/**
 * Asks whether to trust a certificate that no system or user CA signed, and
 * shows its SHA-256 fingerprint and subject so the user can compare them with
 * the server's. [changed] says the user had trusted another certificate for
 * this server. Neither a tap outside nor Back closes it: the user picks Cancel
 * or Trust.
 */
@Composable
fun CertificatePrompt(certificate: ServerCertificate, changed: Boolean, onTrust: () -> Unit, onCancel: () -> Unit) {
    val server = if (':' in certificate.host) "[${certificate.host}]:${certificate.port}" else "${certificate.host}:${certificate.port}"
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
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
            TrustActions(risky = R.string.sign_in_certificate_trust, riskyEnabled = true, onRisky = onTrust, onCancel = onCancel)
        },
    )
}

/**
 * The two actions of a trust prompt in one slot, stacked full width, so they
 * cannot overlap at any font scale. AlertDialog's own button row in material3
 * 1.4.0 placed Cancel over a Continue that wrapped to two lines at 200%. Cancel
 * comes first. Neither button is filled, so the risky action is never the
 * call to action.
 */
@Composable
private fun TrustActions(@StringRes risky: Int, riskyEnabled: Boolean, onRisky: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sign_in_warning_cancel), textAlign = TextAlign.Center)
        }
        TextButton(onClick = onRisky, enabled = riskyEnabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(risky), textAlign = TextAlign.Center)
        }
    }
}

