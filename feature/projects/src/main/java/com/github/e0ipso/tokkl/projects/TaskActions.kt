package com.github.e0ipso.tokkl.projects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** What the task list's controls do. */
class TaskActions(
    val onRetry: () -> Unit = {},
    val onCreate: (String) -> Unit = {},
    val onAskToEdit: (ProjectTask) -> Unit = {},
    val onSave: (name: String, done: Boolean) -> Unit = { _, _ -> },
    val onAskToDelete: (ProjectTask) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onDoneShow: () -> Unit = {},
)
