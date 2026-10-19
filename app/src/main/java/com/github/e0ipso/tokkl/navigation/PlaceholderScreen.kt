package com.github.e0ipso.tokkl.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.e0ipso.tokkl.ui.PageTitle
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent

/**
 * A page whose feature is not built yet: its [title] and a [message] that says what the page
 * will hold, so no item in the bottom bar or the More sheet leads nowhere. Projects (TKA-0031),
 * clients (TKA-0032), tags (TKA-0033) and the trash (TKA-0053) replace theirs.
 */
@Composable
fun PlaceholderScreen(@StringRes title: Int, @StringRes message: Int, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize(), contentWindowInsets = ScrollingScreenInsets) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .screenContent()
                .padding(vertical = TokklTheme.spacing.space2),
        ) {
            PageTitle(stringResource(title))
            Spacer(Modifier.height(TokklTheme.spacing.underTopBar))
            Text(text = stringResource(message), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
