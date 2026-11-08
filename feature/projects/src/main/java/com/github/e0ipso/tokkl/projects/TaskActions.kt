package com.github.e0ipso.tokkl.projects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.github.e0ipso.tokkl.model.Task

/** What the task list's controls do. */
class TaskActions(
    val onRetry: () -> Unit = {},
    val onCreate: (String) -> Unit = {},
    val onAskToEdit: (Task) -> Unit = {},
    val onSave: (name: String, done: Boolean) -> Unit = { _, _ -> },
    val onAskToDelete: (Task) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onDoneShow: () -> Unit = {},
)
