package com.github.e0ipso.tokkl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * TokklTheme hands Tokkl's corners to MaterialTheme, so Material components draw them
 * (docs/PRD.md, Visual direction): 10dp for fields (`--radius-md`), 16dp for cards and dialogs
 * (`--radius-lg`).
 */
class ShapesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun materialThemeAndItsComponentsGetTokklCorners() {
        lateinit var shapes: Shapes
        lateinit var field: Shape
        lateinit var card: Shape
        lateinit var dialog: Shape
        composeRule.setContent {
            TokklTheme {
                shapes = MaterialTheme.shapes
                field = OutlinedTextFieldDefaults.shape
                card = CardDefaults.shape
                dialog = AlertDialogDefaults.shape
            }
        }
        composeRule.waitForIdle()

        assertEquals("MaterialTheme.shapes", TokklShapes, shapes)
        assertEquals("text field", RoundedCornerShape(10.dp), field)
        assertEquals("card", RoundedCornerShape(16.dp), card)
        assertEquals("dialog", RoundedCornerShape(16.dp), dialog)
    }
}
