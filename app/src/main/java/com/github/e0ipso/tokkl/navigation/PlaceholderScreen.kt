package com.github.e0ipso.tokkl.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import com.github.e0ipso.tokkl.ui.TokklTopBar
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent

/**
 * A page whose feature is not built yet: its [title] and a [message] that says what the page
 * will hold, so no item in the bottom bar or the More sheet leads nowhere. Only the trash still
 * shows one, until TKA-0053 replaces it.
 */
@Composable
fun PlaceholderScreen(@StringRes title: Int, @StringRes message: Int, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TokklTopBar(stringResource(title)) },
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
            Text(text = stringResource(message), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
