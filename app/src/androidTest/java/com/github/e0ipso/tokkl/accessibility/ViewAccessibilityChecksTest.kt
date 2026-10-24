package com.github.e0ipso.tokkl.accessibility

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityViewCheckException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pins runner enablement on native Views too, including APIs below Compose ATF's minimum. */
class ViewAccessibilityChecksTest {
    @get:Rule
    val activity = ActivityScenarioRule(ComponentActivity::class.java)

    @Test
    fun anEspressoActionRejectsAnUnlabelledNativeControlBeforeItClicks() {
        val buttonId = View.generateViewId()
        var clicked = false
        activity.scenario.onActivity { host ->
            val size = (64 * host.resources.displayMetrics.density).toInt()
            host.setContentView(FrameLayout(host).apply {
                addView(ImageButton(host).apply {
                    id = buttonId
                    setImageResource(android.R.drawable.ic_media_play)
                    contentDescription = ""
                    setOnClickListener { clicked = true }
                }, FrameLayout.LayoutParams(size, size))
            })
        }

        // Do not enable checks here. Removing the runner's enablement must break this pin.
        val failure = assertThrows(RuntimeException::class.java) {
            onView(withId(buttonId)).perform(click())
        }
        assertTrue(
            "the action failed because ATF rejected the control",
            generateSequence(failure as Throwable) { it.cause }
                .any { it is AccessibilityViewCheckException },
        )
        activity.scenario.onActivity {
            assertFalse("ATF rejects the native control before the action", clicked)
        }
    }
}
