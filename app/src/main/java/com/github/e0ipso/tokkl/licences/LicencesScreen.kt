package com.github.e0ipso.tokkl.licences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.TokklTopBar
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent

/** The licences of what the app bundles: so far the Anybody font, under the SIL OFL 1.1. */
@Composable
fun LicencesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    // res/raw/anybody_ofl.txt is the font's OFL.txt as shipped upstream, with CRLF line ends.
    val oflText = remember(resources) {
        resources.openRawResource(R.raw.anybody_ofl).bufferedReader().use { it.readText() }
            .replace("\r\n", "\n")
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TokklTopBar(stringResource(R.string.licences), onBack = onBack) },
        contentWindowInsets = ScrollingScreenInsets,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .screenContent()
                .padding(top = TokklTheme.spacing.underTopBar, bottom = TokklTheme.spacing.space2),
        ) {
            Text(
                text = stringResource(R.string.licence_anybody_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.licence_anybody_licence),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(TokklTheme.spacing.betweenGroups))
            LicenceText(oflText)
        }
    }
}

/**
 * [text] in the body size, with each line made only of dashes drawn as a rule. A line of dashes
 * set as text wraps onto a second line wherever the screen is narrower than it. Each paragraph's
 * hard-wrapped lines are joined, so it wraps at the screen's width instead of breaking early
 * ([licenceBlocks]). Semantics keeps the whole text as it ships, dashes included, as one node,
 * as the single Text did before.
 */
@Composable
private fun LicenceText(text: String) {
    val blocks = remember(text) { licenceBlocks(text) }
    Column(Modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
        for ((i, block) in blocks.withIndex()) {
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = TokklTheme.spacing.space2))
            Text(text = block, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
