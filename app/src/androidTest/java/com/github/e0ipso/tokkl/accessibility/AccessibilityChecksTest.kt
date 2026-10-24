package com.github.e0ipso.tokkl.accessibility

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityViewCheckException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

/** A pin for the actual ATF integration: semantics assertions alone cannot satisfy this test. */
@SdkSuppress(minSdkVersion = 34)
class AccessibilityChecksTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anActionOnAnUnlabelledControlFailsBeforeItClicks() {
        var clicked = false
        compose.setContent {
            Box(Modifier.size(64.dp).testTag("unlabelled").clickable { clicked = true }
                .semantics { contentDescription = "" })
        }
        assertThrows(AccessibilityViewCheckException::class.java) {
            compose.onNodeWithTag("unlabelled").performClick()
        }
        assertFalse("ATF rejects the screen before the action", clicked)
    }

    @Test
    fun aStaticUnlabelledControlFailsAnExplicitCheck() {
        compose.setContent {
            Box(Modifier.size(64.dp).clickable { }.semantics { contentDescription = "" })
        }
        assertThrows(AccessibilityViewCheckException::class.java) { compose.checkAccessibility() }
    }
}
