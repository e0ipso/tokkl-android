package com.github.e0ipso.tokkl.reports

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.e0ipso.tokkl.feature.reports.R
import com.github.e0ipso.tokkl.ui.FillScreenWindow
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.core.ui.R as UiR

/** Full-screen report choices with shared Cancel and Apply actions. */
@Composable
internal fun ReportDialog(
    @StringRes title: Int,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    canApply: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FillScreenWindow()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = TokklTheme.spacing.space1),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painterResource(UiR.drawable.ic_close),
                            contentDescription = stringResource(R.string.reports_cancel),
                        )
                    }
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    TextButton(onClick = onApply, enabled = canApply) {
                        Text(stringResource(R.string.reports_apply))
                    }
                }
                content()
            }
        }
    }
}
