package com.github.e0ipso.tokkl

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class LaunchScreenTest {
    @get:Rule(order = 0)
    val session = SessionRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsTheLaunchTextFromStringResources() {
        val activity = composeRule.activity
        composeRule.awaitText(activity.getString(R.string.launch_message))
        composeRule.onNodeWithText(activity.getString(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.launch_message)).assertIsDisplayed()
    }
}
