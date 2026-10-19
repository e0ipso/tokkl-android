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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.cache.CachedTag
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
import com.github.e0ipso.tokkl.clients.ClientsContent
import com.github.e0ipso.tokkl.clients.ClientsOverlay
import com.github.e0ipso.tokkl.projects.ProjectForm
import com.github.e0ipso.tokkl.projects.ProjectsActions
import com.github.e0ipso.tokkl.projects.ProjectsContent
import com.github.e0ipso.tokkl.projects.TaskOverlay
import com.github.e0ipso.tokkl.tags.TagsActions
import com.github.e0ipso.tokkl.tags.TagsContent
import com.github.e0ipso.tokkl.tags.TagsOverlay
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
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The font scale is set before the activity starts, so it never has to restart for it. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SystemFontScale(fontScale)).around(composeRule)

    @Test
    fun projectsList() {
        show { ProjectsContent(PROJECTS_LOADED, offline = false, cachedProjects = emptyList(), clients = CACHED_CLIENTS, actions = ProjectsActions()) }
        assertNoTextCutOff("the projects")
    }

    @Test
    fun projectsOffline() {
        show { ProjectsContent(PROJECTS_LOADED, offline = true, cachedProjects = emptyList(), clients = CACHED_CLIENTS, actions = ProjectsActions()) }
        assertNoTextCutOff("the projects offline")
    }

    @Test
    fun projectForm() {
        val editing = PROJECTS_LOADED.copy(overlay = ProjectForm.Edit(PROJECTS.first()))
        show { ProjectsContent(editing, offline = false, cachedProjects = emptyList(), clients = CACHED_CLIENTS, actions = ProjectsActions()) }
        assertNoTextCutOff("the top of the project form")

        composeRule.onNode(hasText(string(R.string.project_save)) and hasClickAction()).performScrollTo()
        assertNoTextCutOff("the bottom of the project form")
    }

    @Test
    fun newProjectFormWithMistakes() {
        val creating = PROJECTS_LOADED.copy(overlay = ProjectForm.New)
        show { ProjectsContent(creating, offline = false, cachedProjects = emptyList(), clients = CACHED_CLIENTS, actions = ProjectsActions()) }
        composeRule.onNode(hasText(string(R.string.project_create)) and hasClickAction())
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNode(hasText(string(R.string.project_name_missing)), useUnmergedTree = true).performScrollTo()

        assertNoTextCutOff("the new project form with its mistakes")
    }

    @Test
    fun projectFormWithTasks() {
        val editing = PROJECTS_LOADED.copy(overlay = ProjectForm.Edit(PROJECTS.first()))
        show { ProjectsContent(editing, offline = false, cachedProjects = emptyList(), clients = CACHED_CLIENTS, actions = ProjectsActions(), tasks = TASKS_LOADED) }
        composeRule.onNode(hasContentDescription(string(R.string.project_task_delete_named, TASKS.last().name))).performScrollTo()

        assertNoTextCutOff("the tasks under the project form")
    }

    @Test
    fun projectFormWithTasksOffline() {
        val editing = PROJECTS_LOADED.copy(overlay = ProjectForm.Edit(PROJECTS.first()))
        show { ProjectsContent(editing, offline = true, cachedProjects = emptyList(), clients = CACHED_CLIENTS, actions = ProjectsActions(), tasks = TASKS_LOADED) }
        composeRule.onNode(hasText(string(R.string.project_tasks_server_needed_message))).performScrollTo()

        assertNoTextCutOff("the tasks under the project form offline")
    }

    @Test
    fun taskForm() {
        val editing = PROJECTS_LOADED.copy(overlay = ProjectForm.Edit(PROJECTS.first()))
        val tasks = TASKS_LOADED.copy(overlay = TaskOverlay.Edit(TASKS[1]))
        show { ProjectsContent(editing, offline = false, cachedProjects = emptyList(), clients = CACHED_CLIENTS, actions = ProjectsActions(), tasks = tasks) }
        assertNoTextCutOff("the form that edits a task")
    }

    @Test
    fun taskDeleteQuestion() {
        val editing = PROJECTS_LOADED.copy(overlay = ProjectForm.Edit(PROJECTS.first()))
        val tasks = TASKS_LOADED.copy(overlay = TaskOverlay.ConfirmDelete(TASKS[1]))
        show { ProjectsContent(editing, offline = false, cachedProjects = emptyList(), clients = CACHED_CLIENTS, actions = ProjectsActions(), tasks = tasks) }
        assertNoTextCutOff("the question before deleting a task")
    }

    @Test
    fun clientsList() {
        show { ClientsContent(CLIENTS_LOADED, offline = false, cachedClients = emptyList(), actions = ClientsActions()) }
        assertNoTextCutOff("the clients")
    }

    @Test
    fun clientArchiveQuestion() {
        val asking = CLIENTS_LOADED.copy(overlay = ClientsOverlay.ConfirmArchive(CLIENTS.first()))
        show { ClientsContent(asking, offline = false, cachedClients = emptyList(), actions = ClientsActions()) }
        assertNoTextCutOff("the question before archiving a client")
    }

    @Test
    fun clientForm() {
        val editing = CLIENTS_LOADED.copy(overlay = ClientsOverlay.Edit(CLIENTS.first()))
        show { ClientsContent(editing, offline = false, cachedClients = emptyList(), actions = ClientsActions()) }
        assertNoTextCutOff("the client form")
    }

    @Test
    fun clientsOffline() {
        show { ClientsContent(CatalogState(loadProblem = ServerProblem.Unreachable), offline = true, cachedClients = CACHED_CLIENTS, actions = ClientsActions()) }
        assertNoTextCutOff("the clients offline")
    }

    @Test
    fun tagsList() {
        show { TagsContent(TAGS_LOADED, offline = false, cachedTags = emptyList(), actions = TagsActions()) }
        assertNoTextCutOff("the tags")
    }

    @Test
    fun tagDeleteQuestion() {
        val asking = TAGS_LOADED.copy(overlay = TagsOverlay.ConfirmDelete(TAGS.first()))
        show { TagsContent(asking, offline = false, cachedTags = emptyList(), actions = TagsActions()) }
        assertNoTextCutOff("the question before deleting a tag")
    }

    @Test
    fun tagRenameForm() {
        val renaming = TAGS_LOADED.copy(overlay = TagsOverlay.Rename(TAGS[1]))
        show { TagsContent(renaming, offline = false, cachedTags = emptyList(), actions = TagsActions()) }
        assertNoTextCutOff("the form that renames a tag")
    }

    @Test
    fun tagsOffline() {
        val cached = listOf(CachedTag(1, 7, "focus"), CachedTag(2, 7, "meeting"))
        show { TagsContent(CatalogState(loadProblem = ServerProblem.Unreachable), offline = true, cachedTags = cached, actions = TagsActions()) }
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
        val nodes = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        for (node in nodes) {
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val layout = layouts.firstOrNull() ?: continue
            if (layout.layoutInput.overflow == TextOverflow.Ellipsis) continue
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: layout.layoutInput.text.text
            assertFalse("text cut off in $where: $text", layout.didOverflowHeight)
        }
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
}

class UsualFontCatalogFitTest : CatalogFitTest(fontScale = 1f)

class LargestFontCatalogFitTest : CatalogFitTest(fontScale = SystemFontScale.LARGEST)
