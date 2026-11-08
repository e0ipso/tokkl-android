package com.github.e0ipso.tokkl.session

/** The account's default workspace, read each time it is asked, such as [AccountSettingsSource]. */
fun interface DefaultWorkspace {

    /** The workspace of a new entry, or null while no settings are stored. */
    fun defaultWorkspaceId(): Long?
}
