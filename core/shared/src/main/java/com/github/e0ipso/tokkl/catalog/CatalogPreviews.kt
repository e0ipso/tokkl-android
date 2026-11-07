package com.github.e0ipso.tokkl.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

// Design-time previews of the catalog pages' shared parts, with sample text.

@PreviewLightDark
@Composable
private fun CatalogNoticesPreview() {
    TokklTheme {
        Surface {
            Column(
                Modifier.padding(TokklTheme.spacing.space4),
                verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.betweenGroups),
            ) {
                ServerNeededNotice(
                    title = "Changing projects needs the server",
                    message = "The phone shows the projects it keeps. Connect to change them.",
                    onRetry = {},
                )
                ProblemBanner(ServerProblem.Refused("Name has already been taken"), onRetry = {})
                CatalogEmpty(title = "No projects yet", message = "Projects you add show here.")
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun CatalogPagePreview() {
    TokklTheme {
        CatalogPage(title = "Projects", done = null, onDoneShow = {}, onBack = {}) {
            CatalogListRow { Text("Website") }
            CatalogListRow { Text("Graffiti wall") }
        }
    }
}
