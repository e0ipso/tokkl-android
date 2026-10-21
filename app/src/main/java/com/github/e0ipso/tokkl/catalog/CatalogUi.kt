package com.github.e0ipso.tokkl.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.ui.ErrorBanner
import com.github.e0ipso.tokkl.ui.StickerCard
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/** What [change] did, in the web app's words, such as "Created Website". */
@Composable
fun changeText(change: CatalogChange): String = when (change) {
    is CatalogChange.Created -> stringResource(R.string.catalog_created, change.name)
    is CatalogChange.Saved -> stringResource(R.string.catalog_saved, change.name)
    is CatalogChange.Archived -> stringResource(R.string.catalog_archived, change.name)
    is CatalogChange.Restored -> stringResource(R.string.catalog_restored, change.name)
    is CatalogChange.Renamed -> stringResource(R.string.catalog_renamed, change.name)
    is CatalogChange.Deleted -> stringResource(R.string.catalog_deleted, change.name)
}

/** What [problem] means for the user, in a sentence. */
@Composable
fun problemText(problem: ServerProblem): String = when (problem) {
    ServerProblem.Unreachable -> stringResource(R.string.catalog_error_unreachable)
    ServerProblem.AuthFailed -> stringResource(R.string.catalog_error_auth)
    ServerProblem.TrustRefused -> stringResource(R.string.catalog_error_trust)
    is ServerProblem.Refused -> problem.reason
    is ServerProblem.Unexpected -> problem.status?.let { stringResource(R.string.catalog_error_status, it) }
        ?: stringResource(R.string.catalog_error_unreadable)
}

/**
 * Says that the page's changes need the server, which the phone cannot reach now: a warning icon,
 * [title] as a heading and [message], in Tokkl's frame, with Try again. The icon and the words
 * carry the state, never colour alone, and the message is a polite live region, so TalkBack
 * announces it when the connection drops.
 */
@Composable
fun ServerNeededNotice(title: String, message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    StickerCard(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(TokklTheme.spacing.space4),
            horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3),
        ) {
            Icon(painter = painterResource(R.drawable.ic_warning), contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                TokklGhostButton(onClick = onRetry) { Text(stringResource(R.string.catalog_retry)) }
            }
        }
    }
}

/**
 * Why the page could not load or change something, other than an unreachable server, which
 * [ServerNeededNotice] covers: [problem] in an [ErrorBanner], with Try again when [onRetry] is set.
 */
@Composable
fun ProblemBanner(problem: ServerProblem, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
        ErrorBanner(problemText(problem))
        if (onRetry != null) {
            TokklGhostButton(onClick = onRetry) { Text(stringResource(R.string.catalog_retry)) }
        }
    }
}

/**
 * An item's details on one line that wraps, such as its client and the word Archived, in the
 * secondary text colour, with a middle dot between them. The dots are drawn, not read: TalkBack
 * reads the details alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailLine(details: List<String>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space1)) {
        val separator = stringResource(R.string.catalog_detail_separator)
        details.forEachIndexed { index, detail ->
            if (index > 0) {
                Text(separator, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics {})
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
