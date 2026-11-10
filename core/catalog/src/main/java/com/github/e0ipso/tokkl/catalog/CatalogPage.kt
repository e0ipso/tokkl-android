package com.github.e0ipso.tokkl.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.github.e0ipso.tokkl.session.ServerProblem
import com.github.e0ipso.tokkl.ui.TokklTopBar
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent
import kotlinx.coroutines.launch

/**
 * The frame of a page that manages projects, clients or tags: the top bar with [title], and the
 * back button when [onBack] is given, as for a form; under it one scrolling column in the
 * screen's margins that makes room for the keyboard; and a snackbar that says [done], the text of
 * the change the server last accepted, then calls [onDoneShow]. [lazyContent] replaces the
 * scrolling column for pages with long lists, keeping the same frame and screen margins.
 */
@Composable
fun CatalogPage(
    title: String,
    done: String?,
    onDoneShow: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    lazyContent: (LazyListScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(done) {
        if (done != null) {
            // Its own job, so clearing the change does not cancel the message.
            scope.launch { snackbar.showSnackbar(done) }
            onDoneShow()
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TokklTopBar(title, onBack = onBack) },
        contentWindowInsets = ScrollingScreenInsets,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        val body = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            // The keyboard covers the bottom bar, whose height the frame has consumed, so this
            // adds the rest of the keyboard's height while a field has focus.
            .imePadding()
        if (lazyContent != null) {
            LazyColumn(
                modifier = body.screenContent(),
                contentPadding = PaddingValues(
                    top = TokklTheme.spacing.underTopBar,
                    bottom = TokklTheme.spacing.space2,
                ),
                content = {
                    lazyContent()
                    item(key = "catalog-navigation-inset") { Spacer(Modifier.navigationBarsPadding()) }
                },
            )
        } else {
            Column(
                modifier = body
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .screenContent()
                    .padding(top = TokklTheme.spacing.underTopBar, bottom = TokklTheme.spacing.space2),
                content = content,
            )
        }
    }
}

@Composable
fun CatalogNotices(
    serverNeeded: Boolean,
    neededTitle: String,
    neededMessage: String,
    loadProblem: ServerProblem?,
    loading: Boolean,
    loadingText: String,
    changeProblem: ServerProblem?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notice = serverNeeded || loadProblem != null || loading
    if (!notice && changeProblem == null) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TokklTheme.spacing.betweenGroups)) {
        when {
            serverNeeded -> ServerNeededNotice(title = neededTitle, message = neededMessage, onRetry = onRetry)
            loadProblem != null -> ProblemBanner(loadProblem, onRetry = onRetry)
            loading -> Text(loadingText, style = MaterialTheme.typography.bodyLarge)
        }
        changeProblem?.let { ProblemBanner(it) }
    }
}
