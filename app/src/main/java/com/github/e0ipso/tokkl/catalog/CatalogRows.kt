package com.github.e0ipso.tokkl.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/** What a catalog page shows when the server has none of its items: [title] over [message]. */
@Composable
internal fun CatalogEmpty(title: String, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.inGroup)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

/** One row of a catalog list: [content] across the row, usually the part a tap opens and a button after it. */
@Composable
internal fun CatalogListRow(content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space3),
        modifier = Modifier.fillMaxWidth().padding(vertical = TokklTheme.spacing.space1),
        content = content,
    )
}

/**
 * The [active] items, then the [archived] ones under the [archivedHeading] heading, each list with
 * a divider between its rows. A list with no items shows nothing, heading included.
 */
@Composable
internal fun <T> ActiveThenArchived(active: List<T>, archived: List<T>, archivedHeading: String, row: @Composable (T) -> Unit) {
    if (active.isNotEmpty()) DividedRows(active, row)
    if (archived.isNotEmpty()) {
        Column {
            Text(
                text = archivedHeading,
                style = TokklTheme.sectionHeadingTextStyle,
                modifier = Modifier
                    .padding(bottom = TokklTheme.spacing.inGroup)
                    .semantics { heading() },
            )
            DividedRows(archived, row)
        }
    }
}

@Composable
private fun <T> DividedRows(rows: List<T>, row: @Composable (T) -> Unit) {
    Column {
        rows.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            row(item)
        }
    }
}
