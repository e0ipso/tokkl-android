package com.github.e0ipso.tokkl.catalog

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.accessibility.checkAccessibility
import com.github.e0ipso.tokkl.accessibility.createAndroidComposeRule
import com.github.e0ipso.tokkl.catalog.CatalogSamples.CACHED_CLIENTS
import com.github.e0ipso.tokkl.catalog.CatalogSamples.CLIENTS
import com.github.e0ipso.tokkl.catalog.CatalogSamples.CLIENTS_LOADED
import com.github.e0ipso.tokkl.catalog.CatalogSamples.PROJECTS
import com.github.e0ipso.tokkl.catalog.CatalogSamples.PROJECTS_LOADED
import com.github.e0ipso.tokkl.catalog.CatalogSamples.TAGS
import com.github.e0ipso.tokkl.catalog.CatalogSamples.TAGS_LOADED
import com.github.e0ipso.tokkl.catalog.CatalogSamples.TASKS
import com.github.e0ipso.tokkl.catalog.CatalogSamples.TASKS_LOADED
import com.github.e0ipso.tokkl.clients.ClientsActions
import com.github.e0ipso.tokkl.clients.ClientsOverlay
import com.github.e0ipso.tokkl.clients.ClientsPageState
import com.github.e0ipso.tokkl.clients.ClientsScreen
import com.github.e0ipso.tokkl.feature.projects.R
import com.github.e0ipso.tokkl.model.Tag
import com.github.e0ipso.tokkl.model.TagId
import com.github.e0ipso.tokkl.model.WorkspaceId
import com.github.e0ipso.tokkl.projects.ProjectForm
import com.github.e0ipso.tokkl.projects.ProjectsActions
import com.github.e0ipso.tokkl.projects.ProjectsPageState
import com.github.e0ipso.tokkl.projects.ProjectsScreen
import com.github.e0ipso.tokkl.projects.TaskOverlay
import com.github.e0ipso.tokkl.session.ServerProblem
import com.github.e0ipso.tokkl.tags.TagsActions
import com.github.e0ipso.tokkl.tags.TagsOverlay
import com.github.e0ipso.tokkl.tags.TagsPageState
import com.github.e0ipso.tokkl.tags.TagsScreen
import com.github.e0ipso.tokkl.ui.theme.SystemFontScale
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The Projects, Clients and Tags pages, their forms, questions and offline states with
 * [CatalogSamples], at a font scale: no text is cut off, only a client's notes, which end in an
 * ellipsis on purpose. The screenshot tests compare the lists' look.
 */
abstract class CatalogFitTest(private val fontScale: Float) {
    @org.junit.After
    fun auditFinalAccessibilityState() {
        composeRule.checkAccessibility()
    }

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The font scale is set before the activity starts, so it never has to restart for it. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(
        SystemFontScale(fontScale, largestDisplaySize = fontScale == SystemFontScale.LARGEST),
    ).around(composeRule)

    @Test
    fun projectsList() {
        show {
            ProjectsScreen(
                ProjectsPageState(PROJECTS_LOADED, false, emptyList(), CACHED_CLIENTS),
                actions = ProjectsActions(),
            )
        }
        assertNoTextCutOff("the projects")
    }

    @Test
    fun projectsOffline() {
        show {
            ProjectsScreen(
                ProjectsPageState(PROJECTS_LOADED, true, emptyList(), CACHED_CLIENTS),
                actions = ProjectsActions(),
            )
        }
        assertNoTextCutOff("the projects offline")
    }

    @Test
    fun projectForm() {
        val editing = PROJECTS_LOADED.copy(overlay = ProjectForm.of(PROJECTS.first(), PROJECTS.size))
        show {
            ProjectsScreen(
                ProjectsPageState(editing, false, emptyList(), CACHED_CLIENTS),
                actions = ProjectsActions(),
            )
        }
        assertNoTextCutOff("the top of the project form")

        composeRule.onNode(hasText(string(R.string.project_save)) and hasClickAction()).performScrollTo()
        assertNoTextCutOff("the bottom of the project form")
    }

    @Test
    fun newProjectFormWithMistakes() {
        val creating = PROJECTS_LOADED.copy(overlay = ProjectForm.of(null, PROJECTS.size))
        show {
            ProjectsScreen(
                ProjectsPageState(creating, false, emptyList(), CACHED_CLIENTS),
                actions = ProjectsActions(),
            )
        }
        composeRule.onNode(hasText(string(R.string.project_create)) and hasClickAction())
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNode(hasText(string(R.string.project_name_missing)), useUnmergedTree = true).performScrollTo()

        assertNoTextCutOff("the new project form with its mistakes")
    }

    @Test
    fun projectFormWithTasks() {
        val editing = PROJECTS_LOADED.copy(overlay = ProjectForm.of(PROJECTS.first(), PROJECTS.size))
        show {
            ProjectsScreen(
                ProjectsPageState(editing, false, emptyList(), CACHED_CLIENTS, TASKS_LOADED),
                actions = ProjectsActions(),
            )
        }
        composeRule.onNode(
            hasContentDescription(string(R.string.project_task_delete_named, TASKS.last().name)),
        ).performScrollTo()

        assertNoTextCutOff("the tasks under the project form")
    }

    @Test
    fun projectFormWithTasksOffline() {
        val editing = PROJECTS_LOADED.copy(overlay = ProjectForm.of(PROJECTS.first(), PROJECTS.size))
        show {
            ProjectsScreen(
                ProjectsPageState(editing, true, emptyList(), CACHED_CLIENTS, TASKS_LOADED),
                actions = ProjectsActions(),
            )
        }
        composeRule.onNode(hasText(string(R.string.project_tasks_server_needed_message))).performScrollTo()

        assertNoTextCutOff("the tasks under the project form offline")
    }

    @Test
    fun taskForm() {
        val editing = PROJECTS_LOADED.copy(overlay = ProjectForm.of(PROJECTS.first(), PROJECTS.size))
        val tasks = TASKS_LOADED.copy(overlay = TaskOverlay.Edit(TASKS[1]))
        show {
            ProjectsScreen(
                ProjectsPageState(editing, false, emptyList(), CACHED_CLIENTS, tasks),
                actions = ProjectsActions(),
            )
        }
        assertNoTextCutOff("the form that edits a task")
    }

    @Test
    fun taskDeleteQuestion() {
        val editing = PROJECTS_LOADED.copy(overlay = ProjectForm.of(PROJECTS.first(), PROJECTS.size))
        val tasks = TASKS_LOADED.copy(overlay = TaskOverlay.ConfirmDelete(TASKS[1]))
        show {
            ProjectsScreen(
                ProjectsPageState(editing, false, emptyList(), CACHED_CLIENTS, tasks),
                actions = ProjectsActions(),
            )
        }
        assertNoTextCutOff("the question before deleting a task")
    }

    @Test
    fun clientsList() {
        show {
            ClientsScreen(
                ClientsPageState(CLIENTS_LOADED, false, emptyList()),
                actions = ClientsActions(),
            )
        }
        assertNoTextCutOff("the clients")
    }

    @Test
    fun clientArchiveQuestion() {
        val asking = CLIENTS_LOADED.copy(overlay = ClientsOverlay.ConfirmArchive(CLIENTS.first()))
        show { ClientsScreen(ClientsPageState(asking, false, emptyList()), actions = ClientsActions()) }
        assertNoTextCutOff("the question before archiving a client")
    }

    @Test
    fun clientForm() {
        val editing = CLIENTS_LOADED.copy(overlay = ClientsOverlay.Edit(CLIENTS.first()))
        show { ClientsScreen(ClientsPageState(editing, false, emptyList()), actions = ClientsActions()) }
        assertNoTextCutOff("the client form")
    }

    @Test
    fun clientsOffline() {
        show {
            ClientsScreen(
                ClientsPageState(CatalogState(loadProblem = ServerProblem.Unreachable), true, CACHED_CLIENTS),
                actions = ClientsActions(),
            )
        }
        assertNoTextCutOff("the clients offline")
    }

    @Test
    fun tagsList() {
        show { TagsScreen(TagsPageState(TAGS_LOADED, false, emptyList()), actions = TagsActions()) }
        assertNoTextCutOff("the tags")
    }

    @Test
    fun tagDeleteQuestion() {
        val asking = TAGS_LOADED.copy(overlay = TagsOverlay.ConfirmDelete(TAGS.first()))
        show { TagsScreen(TagsPageState(asking, false, emptyList()), actions = TagsActions()) }
        assertNoTextCutOff("the question before deleting a tag")
    }

    @Test
    fun tagRenameForm() {
        val renaming = TAGS_LOADED.copy(overlay = TagsOverlay.Rename(TAGS[1]))
        show { TagsScreen(TagsPageState(renaming, false, emptyList()), actions = TagsActions()) }
        assertNoTextCutOff("the form that renames a tag")
    }

    @Test
    fun tagsOffline() {
        val cached = listOf(Tag(TagId(1), WorkspaceId(7), "focus"), Tag(TagId(2), WorkspaceId(7), "meeting"))
        show {
            TagsScreen(
                TagsPageState(CatalogState(loadProblem = ServerProblem.Unreachable), true, cached),
                actions = TagsActions(),
            )
        }
        assertNoTextCutOff("the tags offline")
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { TokklTheme(darkTheme = false) { content() } }
        composeRule.waitForIdle()
    }

    /** No text shown, in the page or in a dialog over it, is cut off in height. */
    private fun assertNoTextCutOff(where: String) {
        assertEquals(fontScale, composeRule.activity.resources.configuration.fontScale)
        composeRule.waitForIdle()
        val nodes = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        )
            .fetchSemanticsNodes()
        for (node in nodes) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val layout = layouts.firstOrNull() ?: continue
            if (layout.layoutInput.overflow == TextOverflow.Ellipsis) continue
            val text = node.config.getOrNull(
                SemanticsProperties.Text,
            )?.joinToString { it.text } ?: layout.layoutInput.text.text
            assertFalse("text cut off in $where: $text", layout.didOverflowHeight)
        }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(
            id,
            *args,
        )
}

class UsualFontCatalogFitTest : CatalogFitTest(fontScale = 1f)

class LargestFontCatalogFitTest : CatalogFitTest(fontScale = SystemFontScale.LARGEST)
