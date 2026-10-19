package com.github.e0ipso.tokkl.catalog

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.e0ipso.tokkl.ui.TokklTopBar
import com.github.e0ipso.tokkl.ui.theme.ScrollingScreenInsets
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import com.github.e0ipso.tokkl.ui.theme.screenContent
import kotlinx.coroutines.launch

/**
 * Ties [model] to its page while the page is on screen. Opens it, and closes it when the page goes
 * but not for a configuration change, so turning the phone sends no request. Loads again when the
 * phone comes back online ([offline] turns false) and when the app comes back to the foreground
 * after a stop that was not a configuration change.
 */
@Composable
fun CatalogPageEffects(model: CatalogPageModel, offline: Boolean) {
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(model) {
        model.open()
        onDispose { if (activity?.isChangingConfigurations != true) model.close() }
    }
    LaunchedEffect(model, offline) { if (!offline) model.reconnected() }
    DisposableEffect(model, lifecycle) {
        // Adding the observer replays ON_START at once, which the model ignores until a stop.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) model.wentToBackground()
                Lifecycle.Event.ON_START -> model.cameToForeground()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/**
 * The frame of a page that manages projects, clients or tags: the top bar with [title], and the
 * back button when [onBack] is given, as for a form; under it one scrolling column in the
 * screen's margins that makes room for the keyboard; and a snackbar that says [done], the text of
 * the change the server last accepted, then calls [onDoneShown].
 */
@Composable
fun CatalogPage(
    title: String,
    done: String?,
    onDoneShown: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(done) {
        if (done != null) {
            // Its own job, so clearing the change does not cancel the message.
            scope.launch { snackbar.showSnackbar(done) }
            onDoneShown()
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TokklTopBar(title, onBack = onBack) },
        contentWindowInsets = ScrollingScreenInsets,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                // The keyboard covers the bottom bar, whose height the frame has consumed, so this
                // adds the rest of the keyboard's height while a field has focus.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .screenContent()
                .padding(top = TokklTheme.spacing.underTopBar, bottom = TokklTheme.spacing.space2),
            content = content,
        )
    }
}

/**
 * Why the page cannot change anything now, or that it is loading: [ServerNeededNotice] while
 * [serverNeeded], else [loadProblem] with Try again, else [loadingText] while [loading]. Then the
 * reason the server refused the last change, [changeProblem]. Nothing when all is well.
 */
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
