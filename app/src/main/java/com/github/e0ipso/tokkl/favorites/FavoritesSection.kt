package com.github.e0ipso.tokkl.favorites

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.EntryFields
import com.github.e0ipso.tokkl.catalog.ServerProblem
import com.github.e0ipso.tokkl.catalog.problemText
import com.github.e0ipso.tokkl.picker.rememberOffline
import com.github.e0ipso.tokkl.reports.projectMark
import com.github.e0ipso.tokkl.ui.ErrorBanner
import com.github.e0ipso.tokkl.ui.TokklGhostButton
import com.github.e0ipso.tokkl.ui.theme.TokklTheme

/**
 * The favorites from [model] under the timer (docs/PRD.md T5), as the web app's TimerPage.tsx
 * lists them: one button each that starts it with [onStart] in one tap, offline too. Edit shows
 * each favorite with buttons that move it up or down or remove it, and a button that pins
 * [timerFields], what the timer shows. Those changes need the server, and offline the edit view
 * says so and turns them off. Without favorites on the server, as on Toggl Track, nothing shows.
 */
@Composable
fun Favorites(model: FavoritesModel, timerFields: EntryFields?, onStart: (EntryFields) -> Unit, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    FavoritesSection(
        state = state,
        timerFields = timerFields,
        onStart = onStart,
        onAdd = model::add,
        onRemove = model::remove,
        onMove = model::move,
        modifier = modifier,
    )
}

/**
 * The favorites in [state], or nothing when the server keeps none. Each label says what the
 * button does to TalkBack, and the edit view's state shows in words, never in colour alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritesSection(
    state: FavoritesState,
    timerFields: EntryFields?,
    onStart: (EntryFields) -> Unit,
    onAdd: (EntryFields) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (index: Int, later: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.available) return
    var editing by rememberSaveable { mutableStateOf(false) }
    val spacing = TokklTheme.spacing
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.inGroup)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.favorites_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            val spoken = stringResource(if (editing) R.string.favorites_done_spoken else R.string.favorites_edit_spoken)
            TextButton(onClick = { editing = !editing }, modifier = Modifier.semantics { contentDescription = spoken }) {
                Text(stringResource(if (editing) R.string.favorites_done else R.string.favorites_edit))
            }
        }
        if (editing) {
            EditFavorites(state, timerFields, onAdd, onRemove, onMove)
        } else if (state.favorites.isEmpty()) {
            Text(
                text = stringResource(R.string.favorites_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                verticalArrangement = Arrangement.spacedBy(spacing.inGroup),
            ) {
                for (favorite in state.favorites) FavoriteButton(favorite) { onStart(favorite.fields) }
            }
        }
    }
}

/** A favorite's button: its project's dot, its name and its project, as the web app's `.favorite-start`. */
@Composable
private fun FavoriteButton(favorite: FavoriteItem, onClick: () -> Unit) {
    val name = favoriteName(favorite)
    val project = favorite.labels.project.takeIf { favorite.fields.description.isNotEmpty() }
    val spoken = stringResource(R.string.favorites_start_spoken, spokenName(name, project))
    TokklGhostButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = spoken }) {
        if (favorite.fields.projectId != null) {
            val dot = projectMark(favorite.labels.projectColor, TokklTheme.extraColors.projectMix, MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.size(TokklTheme.spacing.space3)) { drawCircle(dot) }
            Spacer(Modifier.width(TokklTheme.spacing.space2))
        }
        Text(name)
        if (project != null) {
            Text(
                text = project,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = TokklTheme.spacing.space2),
            )
        }
    }
}

/**
 * The list with up, down and remove beside each favorite, and the button that pins
 * [timerFields]. Offline, or while a change is on its way, the buttons are off, and offline a
 * message says why.
 */
@Composable
private fun EditFavorites(
    state: FavoritesState,
    timerFields: EntryFields?,
    onAdd: (EntryFields) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (index: Int, later: Boolean) -> Unit,
) {
    val offline = rememberOffline()
    val enabled = !offline && !state.saving
    val problem = state.problem
    if (offline || (problem as? FavoritesProblem.Server)?.problem == ServerProblem.Unreachable) {
        Notice(stringResource(R.string.favorites_offline))
    } else if (problem is FavoritesProblem.Server) {
        ErrorBanner(problemText(problem.problem))
    }
    for ((index, favorite) in state.favorites.withIndex()) {
        val name = favoriteName(favorite)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            EditButton(R.drawable.ic_chevron_up, stringResource(R.string.favorites_move_up, name), enabled && index > 0) { onMove(index, false) }
            EditButton(R.drawable.ic_chevron_down, stringResource(R.string.favorites_move_down, name), enabled && index < state.favorites.lastIndex) {
                onMove(index, true)
            }
            EditButton(R.drawable.ic_close, stringResource(R.string.favorites_remove, name), enabled) { onRemove(index) }
        }
    }
    val pinnable = timerFields?.takeIf { it.description.isNotBlank() || it.projectId != null }
    when {
        state.full -> Notice(pluralStringResource(R.plurals.favorites_full, MAX_FAVORITES, MAX_FAVORITES))
        pinnable == null -> Text(
            text = stringResource(R.string.favorites_pin_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    TokklGhostButton(onClick = { pinnable?.let(onAdd) }, enabled = enabled && !state.full && pinnable != null) {
        Text(stringResource(R.string.favorites_pin))
    }
}

@Composable
private fun EditButton(@DrawableRes icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = description)
    }
}

/** A sentence in a polite live region, so TalkBack reads it when it appears, after a warning icon. */
@Composable
private fun Notice(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(TokklTheme.spacing.space2)) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The description, else the project, else Untitled, as TimerPage.tsx names a favorite. */
@Composable
private fun favoriteName(favorite: FavoriteItem): String =
    favorite.fields.description.ifEmpty { favorite.labels.project ?: stringResource(R.string.favorites_untitled) }

@Composable
private fun spokenName(name: String, project: String?): String =
    project?.let { stringResource(R.string.favorites_with_project, name, it) } ?: name
