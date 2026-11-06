package com.github.e0ipso.tokkl.navigation

/**
 * The app's top-level pages, in the order of NAV in Tokkl's App.tsx (docs/PRD.md B1). As on the
 * web app on a phone, the pages [inMore] sit on the More sheet, not in the bottom bar. Admin stays
 * out, since the app has no admin features. The navigation host gives each page its label, icon
 * and section hue.
 */
enum class TopLevelPage(val route: Route, val inMore: Boolean = false) {
    TIMER(Route.Timer),
    POMODORO(Route.Pomodoro),
    REPORTS(Route.Reports, inMore = true),
    PROJECTS(Route.Projects),
    TEMPLATES(Route.Templates, inMore = true),
    CLIENTS(Route.Clients, inMore = true),
    TAGS(Route.Tags, inMore = true),
    TRASH(Route.Trash, inMore = true),
    SETTINGS(Route.Settings),
    ;

    companion object {
        /** The page [route] opens, or null for a screen opened from a page, such as Licences. */
        fun of(route: Route): TopLevelPage? = entries.firstOrNull { it.route == route }
    }
}
