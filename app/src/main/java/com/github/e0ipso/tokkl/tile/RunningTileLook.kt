package com.github.e0ipso.tokkl.tile

import android.content.Context
import android.service.quicksettings.Tile
import androidx.annotation.DrawableRes
import com.github.e0ipso.tokkl.R

/**
 * How the Quick Settings tile draws [RunningTileContent]: its [state] (a `Tile.STATE_*`), the
 * [label] and [subtitle] it shows, its [icon] and what TalkBack reads. The label and the icon
 * differ between running and not, so the state never rests on the colour alone.
 */
data class RunningTileLook(
    val state: Int,
    val label: String,
    val subtitle: String,
    @param:DrawableRes val icon: Int,
    val contentDescription: String,
)

fun runningTileLook(content: RunningTileContent, context: Context): RunningTileLook =
    when (content) {
        is RunningTileContent.Running -> {
            val description = content.entry.description.ifBlank { context.getString(R.string.tile_no_description) }
            RunningTileLook(
                state = Tile.STATE_ACTIVE,
                label = description,
                subtitle = context.getString(R.string.tile_running_subtitle),
                icon = R.drawable.ic_stop,
                contentDescription = context.getString(R.string.tile_running_description, description),
            )
        }
        is RunningTileContent.Continue -> {
            val description = content.last.description.ifBlank { context.getString(R.string.tile_no_description) }
            RunningTileLook(
                state = Tile.STATE_INACTIVE,
                label = context.getString(R.string.tile_continue),
                subtitle = description,
                icon = R.drawable.ic_play,
                contentDescription = context.getString(R.string.tile_continue_description, description),
            )
        }
        RunningTileContent.Nothing -> RunningTileLook(
            state = Tile.STATE_UNAVAILABLE,
            label = context.getString(R.string.tile_name),
            subtitle = context.getString(R.string.tile_nothing_subtitle),
            icon = R.drawable.ic_play,
            contentDescription = context.getString(R.string.tile_nothing_description),
        )
    }
