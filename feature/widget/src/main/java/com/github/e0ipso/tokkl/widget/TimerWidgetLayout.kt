package com.github.e0ipso.tokkl.widget

/** Both dimensions matter: a wide one-cell widget is still a strip. */
internal enum class TimerWidgetSize { STRIP, MEDIUM, LARGE }

internal fun timerWidgetSize(widthDp: Float, heightDp: Float): TimerWidgetSize = when {
    widthDp >= WidgetLayout.LARGE_WIDTH_DP && heightDp >= WidgetLayout.LARGE_HEIGHT_DP -> TimerWidgetSize.LARGE
    widthDp >= WidgetLayout.MEDIUM_WIDTH_DP && heightDp >= WidgetLayout.MEDIUM_HEIGHT_DP -> TimerWidgetSize.MEDIUM
    else -> TimerWidgetSize.STRIP
}

/** RemoteViews rows have a bounded child count. The fifth segment includes every remaining project. */
internal fun timerWidgetSegments(projects: List<WidgetDayProject>): List<WidgetDayProject> {
    if (projects.size <= WidgetLayout.MAX_SEGMENTS) return projects
    val rest = projects.drop(WidgetLayout.MAX_SEGMENTS - 1)
    return projects.take(WidgetLayout.MAX_SEGMENTS - 1) + WidgetDayProject(
        projectId = null, project = null, projectColor = null,
        stoppedSeconds = rest.sumOf { it.stoppedSeconds }, formattedStopped = "",
        running = rest.any { it.running },
    )
}

/** Reserve the template row first, then spend remaining space on project rows. */
internal fun timerWidgetProjectRows(heightDp: Float, size: TimerWidgetSize, fontScale: Float, active: Boolean, templates: Boolean,
    clockHeightDp: Float = WidgetLayout.clockRowHeight(size == TimerWidgetSize.LARGE, fontScale),
    todayHeightDp: Float = WidgetLayout.todayRowHeight(fontScale),
): Int {
    if (size == TimerWidgetSize.STRIP) return 0
    val scale = fontScale.coerceAtLeast(1f)
    val timer = 48f + if (active) clockHeightDp else 0f
    val report = if (size == TimerWidgetSize.LARGE) 10f + WidgetLayout.reportHeadingHeight(scale) + WidgetLayout.statsHeight(scale) else 10f + todayHeightDp + 14f
    val chips = if (templates && size == TimerWidgetSize.LARGE) 48f else 0f
    val row = if (size == TimerWidgetSize.LARGE) 28f * scale else 18f * scale
    return ((heightDp - 2 * WidgetLayout.PADDING_DP - timer - report - chips) / row).toInt().coerceIn(0, 5)
}
