package com.github.e0ipso.tokkl.navhost

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import com.github.e0ipso.tokkl.clients.ClientsRoute
import com.github.e0ipso.tokkl.model.EntryId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.navigation.EntryOpening
import com.github.e0ipso.tokkl.navigation.Route
import com.github.e0ipso.tokkl.navigation.contentKey
import com.github.e0ipso.tokkl.navigation.routeSerializers
import com.github.e0ipso.tokkl.pomodoro.PomodoroRoute
import com.github.e0ipso.tokkl.projects.ProjectsRoute
import com.github.e0ipso.tokkl.reports.ReportsRoute
import com.github.e0ipso.tokkl.settings.LicencesRoute
import com.github.e0ipso.tokkl.settings.SettingsRoute
import com.github.e0ipso.tokkl.signin.SignInRoute
import com.github.e0ipso.tokkl.sync.NeedsAttentionRoute
import com.github.e0ipso.tokkl.tags.TagsRoute
import com.github.e0ipso.tokkl.timer.FullscreenTimerRoute
import com.github.e0ipso.tokkl.timer.TemplatesRoute
import com.github.e0ipso.tokkl.trash.TrashRoute
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The back stack the navigation host saves comes back as it was, every screen of every feature
 * with what it opens, through the serializer the host saves it with.
 */
class SavedBackStackTest {

    private val serializer = NavBackStackSerializer(PolymorphicSerializer(Route::class))

    /** The routes' serializers as the host registers them, from every feature the app's graph binds. */
    private val json = Json { serializersModule = routeSerializers(appFeatureEntries) }

    @Test
    fun everyScreenComesBackFromTheSavedBackStack() {
        val stack = NavBackStack(
            SignInRoute(),
            SignInRoute(server = "https://tokkl.example", userId = 7),
            Route.Timer,
            FullscreenTimerRoute,
            PomodoroRoute,
            ReportsRoute,
            ProjectsRoute,
            ClientsRoute,
            TagsRoute,
            TrashRoute,
            TemplatesRoute,
            SettingsRoute,
            LicencesRoute,
            NeedsAttentionRoute,
            Route.Entry(EntryOpening.Edit(EntryId(3)), id = "a"),
            Route.Entry(EntryOpening.Running(EntryId(4), WorkspaceId(5), startSeconds = 1_700_000_000, 123), id = "b"),
            Route.Entry(EntryOpening.New(epochDay = 20_400, noTimes = true), id = "c"),
            Route.Entry(EntryOpening.New(), id = "d"),
        )

        val saved = json.encodeToString(serializer, stack)
        val restored = json.decodeFromString(serializer, saved)

        assertEquals(stack.toList(), restored.toList())
        assertEquals(stack.map { it.contentKey }, restored.map { it.contentKey })
    }
}
