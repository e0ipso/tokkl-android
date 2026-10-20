package com.github.e0ipso.tokkl.ui.theme

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.github.e0ipso.tokkl.LoopbackRelay
import com.github.e0ipso.tokkl.MainActivity
import com.github.e0ipso.tokkl.R
import com.github.e0ipso.tokkl.SessionRule
import com.github.e0ipso.tokkl.SignedOut
import com.github.e0ipso.tokkl.TokklTestServer
import com.github.e0ipso.tokkl.api.ServerCertificate
import com.github.e0ipso.tokkl.api.ServerUrl
import com.github.e0ipso.tokkl.awaitText
import com.github.e0ipso.tokkl.awaitTimerPage
import com.github.e0ipso.tokkl.session.ServerTrustStore
import com.github.e0ipso.tokkl.session.Session
import com.github.e0ipso.tokkl.signin.assertFieldErrorsLeaveTheFormInPlace
import com.github.e0ipso.tokkl.signin.assertServerUrlErrorFitsTheHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * The Anybody headlines and the text around them at Android's largest font scale, 200%: no
 * text is cut off, leaves the screen or overlaps another. The sign-in form and the settings
 * scroll, so each of their parts has to fit once scrolled into view. So do the trust prompts,
 * from sign-in and over the Timer page, and the notice left after cancelling one. The Timer
 * page's idle timer and its button for a past entry fit above its history, and so does each part
 * of the entry form, scrolled into view. The bottom bar's five items fit side by side, each label
 * whole. With the keyboard open, the sign-in field being typed in stays above it.
 *
 * ClockAndTotalStylesFitTest covers the clock and totals styles in wrapping text.
 */
class LargestFontScaleTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * The session and the font scale are set before the activity starts, so it never has to
     * restart. Tests start signed in unless marked [SignedOut]. A test marked [RefusedSession]
     * starts signed in to a server whose requests wait for the user to trust it.
     */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SessionRule())
        .around(RefusedSessionRule())
        .around(SystemFontScale(SystemFontScale.LARGEST))
        .around(composeRule)

    @Test
    fun timerPageFits() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitTimerPage()
        // The test account has no entries, so the history says so once the cache has loaded.
        composeRule.awaitText(string(R.string.history_empty_title), NETWORK_TIMEOUT_MS)

        assertFitWithoutOverlap(*timerPageParts().toTypedArray(), text(R.string.history_empty_title))
        assertLabelsWhole()
    }

    @Test
    fun pastEntryFormFits() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitTimerPage()
        button(R.string.timer_add_entry).performClick()
        composeRule.waitUntil("the form opens", NETWORK_TIMEOUT_MS) {
            composeRule.onAllNodes(hasSetTextAction() and hasText(string(R.string.entry_start))).fetchSemanticsNodes().isNotEmpty()
        }

        assertFitWhenScrolledTo(
            pinned = listOf(
                "back button" to composeRule.onNode(hasContentDescription(string(R.string.back)) and hasClickAction()),
                "heading" to composeRule.onNode(hasText(string(R.string.entry_form_new)) and isHeading()),
            ),
            blocks = listOf(
                "description field" to field(R.string.entry_description),
                "project button" to composeRule.onNode(hasContentDescription(string(R.string.entry_no_project_spoken)) and hasClickAction()),
                "tags button" to composeRule.onNode(hasContentDescription(string(R.string.entry_no_tags_spoken)) and hasClickAction()),
                "billable" to switch(R.string.entry_billable),
                "date button" to composeRule.onNode(
                    hasContentDescription(string(R.string.entry_date_spoken, ""), substring = true) and hasClickAction(),
                ),
                "start field" to field(R.string.entry_start),
                "end field" to field(R.string.entry_end),
                "duration field" to field(R.string.entry_duration),
                "cancel button" to button(R.string.entry_cancel),
                "save button" to button(R.string.entry_save),
            ),
            texts = listOf(
                fieldLabel(R.string.entry_description),
                inside(R.string.entry_billable, isToggleable()),
                fieldLabel(R.string.entry_start),
                fieldLabel(R.string.entry_end),
                fieldLabel(R.string.entry_duration),
                inside(R.string.entry_cancel, hasClickAction()),
                inside(R.string.entry_save, hasClickAction()),
            ),
        )
    }

    @Test
    fun settingsFit() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.settings))
        composeRule.onNodeWithText(string(R.string.settings)).performClick()
        composeRule.awaitText(string(R.string.settings_theme))

        assertFitWhenScrolledTo(
            pinned = listOf("heading" to composeRule.onNode(hasText(string(R.string.settings)) and isHeading())),
            blocks = listOf(
                "theme heading" to composeRule.onNode(hasText(string(R.string.settings_theme)) and isHeading()),
                "follow the system choice" to choice(R.string.settings_theme_system),
                "light choice" to choice(R.string.settings_theme_light),
                "dark choice" to choice(R.string.settings_theme_dark),
                "wallpaper colours switch" to switch(R.string.settings_dynamic_colour),
                "Account heading" to composeRule.onNode(hasText(string(R.string.settings_account)) and isHeading()),
                "Sign out row" to button(R.string.settings_sign_out),
                "About heading" to composeRule.onNode(hasText(string(R.string.settings_about)) and isHeading()),
                "Licences row" to button(R.string.licences),
            ),
            texts = listOf(
                inside(R.string.settings_theme_system, isSelectable()),
                inside(R.string.settings_theme_light, isSelectable()),
                inside(R.string.settings_theme_dark, isSelectable()),
                inside(R.string.settings_sign_out, hasClickAction()),
                inside(R.string.licences, hasClickAction()),
                inside(R.string.settings_dynamic_colour, isToggleable()),
                inside(R.string.settings_dynamic_colour_summary, isToggleable()),
            ),
        )
    }

    @Test
    fun licencesHeadingsFit() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.settings))
        composeRule.onNodeWithText(string(R.string.settings)).performClick()
        button(R.string.licences).performScrollTo().performClick()
        composeRule.awaitText(string(R.string.licence_anybody_title))

        assertFitWithoutOverlap(
            "Back button" to composeRule.onNodeWithContentDescription(string(R.string.back)),
            text(R.string.licences),
            heading(R.string.licence_anybody_title),
            text(R.string.licence_anybody_licence),
        )
    }

    @SignedOut
    @Test
    fun signInWithApiTokenFits() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_server_url))

        assertFitWhenScrolledTo(
            blocks = signInBlocks("API token field" to field(R.string.sign_in_api_token)),
            texts = signInTexts() + listOf(
                fieldLabel(R.string.sign_in_api_token),
                text(R.string.sign_in_api_token_hint),
            ),
        )
    }

    @SignedOut
    @Test
    fun signInWithEmailAndPasswordFits() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_server_url))
        choice(R.string.sign_in_method_email_password).performClick()

        assertFitWhenScrolledTo(
            blocks = signInBlocks(
                "email field" to field(R.string.sign_in_email),
                "password field" to field(R.string.sign_in_password),
            ),
            texts = signInTexts() + listOf(
                fieldLabel(R.string.sign_in_email),
                text(R.string.sign_in_email_hint),
                fieldLabel(R.string.sign_in_password),
                text(R.string.sign_in_password_hint),
            ),
        )
    }

    /**
     * The error that replaces the email hint after an empty email. At this size the hint takes two
     * lines and the error one, and the supporting line keeps the taller one's height, so the
     * password field stays where it was. The offset is from the email field to the password
     * field, so a scroll or the keyboard moving both does not count.
     */
    @SignedOut
    @Test
    fun emailErrorFits() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_server_url))
        choice(R.string.sign_in_method_email_password).performClick()
        field(R.string.sign_in_server_url).performScrollTo().performTextInput(TokklTestServer.url.toString())
        field(R.string.sign_in_password).performScrollTo().performTextInput(TokklTestServer.password)
        val before = passwordOffset()
        composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()).performScrollTo().performClick()
        composeRule.awaitText(string(R.string.sign_in_error_email))
        assertEquals("the password field moved when the email error showed", before.value, passwordOffset().value, SLACK.value)

        assertFitWhenScrolledTo(
            blocks = signInBlocks(
                "email field" to field(R.string.sign_in_email),
                "password field" to field(R.string.sign_in_password),
            ),
            texts = listOf(fieldLabel(R.string.sign_in_email), text(R.string.sign_in_error_email)),
        )
    }

    /**
     * The message for a server whose certificate does not name the host in the URL. The URL
     * names `localhost`, which a relay on the phone's loopback carries to the harness's TLS
     * proxy, whose certificate names only IP addresses.
     */
    @SignedOut
    @Test
    fun certificateHostMessageFits() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_server_url))
        val proxy = TokklTestServer.tlsUrl.api("me")
        LoopbackRelay(proxy.host, proxy.port).use { relay ->
            signInTo(ServerUrl.parse("https://localhost:${relay.localPort}") ?: error("not a URL"))
            val message = string(R.string.sign_in_error_certificate_host, "localhost")
            composeRule.awaitText(message, NETWORK_TIMEOUT_MS)

            assertFitWhenScrolledTo(
                blocks = signInBlocks("API token field" to field(R.string.sign_in_api_token)) +
                    ("host name message" to composeRule.onNodeWithText(message, useUnmergedTree = true)),
                texts = emptyList(),
            )
        }
    }

    /**
     * The keyboard opens over the bottom of the sign-in form. The password field starts where
     * the keyboard then shows, and has to move up and end above the keyboard's top edge.
     *
     * Only the form's IME padding may move it. MainActivity's adjustResize keeps the window in
     * place, so the compose root stays at the window's top. Without it, Android panned the whole
     * window up by 258 px on tokkl-api36, and the pan alone kept the field in view.
     *
     * The keyboard has to be closed, with no room taken, before the tap, so the move starts from
     * where the form rests without it. When the field ends under the keyboard, the message gives
     * the bottom of the form's scrolling area too: at the window's bottom, the keyboard's inset
     * never reached the form; above it, the form made room but did not scroll the field into it.
     */
    @SignedOut
    @Test
    fun focusedSignInFieldStaysAboveTheKeyboard() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_server_url))
        choice(R.string.sign_in_method_email_password).performClick()
        val password = field(R.string.sign_in_password)
        password.performScrollTo()
        composeRule.waitUntil("the keyboard is closed before the tap", KEYBOARD_TIMEOUT_MS) { keyboardClosed() }
        val before = password.bottomInWindow()

        password.performClick()
        val keyboardTop = awaitKeyboardTop()
        val after = awaitStill(password)
        val rootTop = composeRule.onRoot().fetchSemanticsNode().positionInWindow.y
        val formBottom = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInWindow.bottom

        assertTrue(
            "the password field ends at $before px before the keyboard opens, above its top at $keyboardTop px, so this test checks nothing",
            before > keyboardTop,
        )
        assertEquals("the window panned for the keyboard: the compose root starts at $rootTop px", 0f, rootTop, 0f)
        assertTrue(
            "the focused password field ends at $after px, under the keyboard's top at $keyboardTop px, and the form's scrolling area ends at $formBottom px",
            after <= keyboardTop,
        )
    }

    /**
     * A focused field that ends right at the form's bottom edge before the keyboard opens still
     * ends above the keyboard. Compose brings a focused field into view on its own only when it
     * was wholly inside the form, and left one that ended within a dp of the edge under the
     * keyboard. The password field ends 0, 1 and 2 px above the edge in these three tests.
     */
    @SignedOut
    @Test
    fun signInFieldEndingAtTheFormsEdgeStaysAboveTheKeyboard() = assertFieldAtTheEdgeStaysAboveTheKeyboard(pixels = 0)

    @SignedOut
    @Test
    fun signInFieldEndingAPixelAboveTheFormsEdgeStaysAboveTheKeyboard() = assertFieldAtTheEdgeStaysAboveTheKeyboard(pixels = 1)

    @SignedOut
    @Test
    fun signInFieldEndingTwoPixelsAboveTheFormsEdgeStaysAboveTheKeyboard() = assertFieldAtTheEdgeStaysAboveTheKeyboard(pixels = 2)

    /** Every sign-in field keeps its height and its place when its error shows or clears. */
    @SignedOut
    @Test
    fun signInFieldErrorsLeaveTheFormInPlace() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_server_url))

        composeRule.assertFieldErrorsLeaveTheFormInPlace()
    }

    /** The server URL error takes no more lines than the hint, two at this scale on tokkl-api36. */
    @SignedOut
    @Test
    fun signInServerUrlErrorFitsInTheHintsLines() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_server_url))

        composeRule.assertServerUrlErrorFitsTheHint()
    }

    private fun assertFieldAtTheEdgeStaysAboveTheKeyboard(pixels: Int) {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_server_url))
        choice(R.string.sign_in_method_email_password).performClick()
        val password = field(R.string.sign_in_password)
        val form = composeRule.onNode(hasScrollAction())
        // The field starts below the form's edge, so this scroll ends it right at the edge.
        password.performScrollTo()
        if (pixels > 0) form.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, pixels.toFloat()) }
        composeRule.waitUntil("the keyboard is closed before the tap", KEYBOARD_TIMEOUT_MS) { keyboardClosed() }
        val gap = form.fetchSemanticsNode().boundsInWindow.bottom - password.bottomInWindow()
        assertEquals("how far above the form's bottom edge the password field ends, in px", pixels.toFloat(), gap, EDGE_SLACK_PX)

        password.performClick()
        val keyboardTop = awaitKeyboardTop()
        val after = awaitStill(password)

        assertTrue(
            "the focused password field, $pixels px above the form's edge before, ends at $after px, under the keyboard's top at $keyboardTop px",
            after <= keyboardTop,
        )
    }

    /** Whether the keyboard is closed and takes no room at the window's bottom. */
    private fun keyboardClosed(): Boolean {
        val decor = composeRule.activity.window.decorView
        var closed = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val insets = ViewCompat.getRootWindowInsets(decor)
            closed = insets != null &&
                !insets.isVisible(WindowInsetsCompat.Type.ime()) &&
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom == 0
        }
        return closed
    }

    @SignedOut
    @Test
    fun httpWarningFromSignInFits() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_server_url))

        signInTo(TokklTestServer.url)
        composeRule.awaitText(string(R.string.sign_in_http_warning_title), NETWORK_TIMEOUT_MS)
        awaitKeyboardHidden()

        assertHttpWarningFits()
    }

    @SignedOut
    @Test
    fun certificatePromptFromSignInFits() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_server_url))

        signInTo(TokklTestServer.tlsUrl)
        composeRule.awaitText(string(R.string.sign_in_certificate_title), NETWORK_TIMEOUT_MS)
        awaitKeyboardHidden()

        assertCertificatePromptFits(R.string.sign_in_certificate_title, R.string.sign_in_certificate_body)
    }

    @RefusedSession(Refusal.UNCONFIRMED_HTTP)
    @Test
    fun httpWarningOverTheHomeScreenFits() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_http_warning_title), NETWORK_TIMEOUT_MS)

        assertHttpWarningFits()
    }

    @RefusedSession(Refusal.UNCONFIRMED_HTTP)
    @Test
    fun noticeAfterCancellingTheHttpWarningFits() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_http_warning_title), NETWORK_TIMEOUT_MS)

        button(R.string.sign_in_warning_cancel).performClick()
        composeRule.awaitText(string(R.string.trust_refused_http), NETWORK_TIMEOUT_MS)

        assertNoticeFits(R.string.trust_refused_http)
    }

    @RefusedSession(Refusal.CHANGED_CERTIFICATE)
    @Test
    fun certificatePromptOverTheHomeScreenFits() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_certificate_changed_title), NETWORK_TIMEOUT_MS)

        assertCertificatePromptFits(R.string.sign_in_certificate_changed_title, R.string.sign_in_certificate_changed_body)
    }

    @RefusedSession(Refusal.CHANGED_CERTIFICATE)
    @Test
    fun noticeAfterCancellingTheCertificatePromptFits() {
        assertEquals(SystemFontScale.LARGEST, composeRule.activity.resources.configuration.fontScale)
        composeRule.awaitText(string(R.string.sign_in_certificate_changed_title), NETWORK_TIMEOUT_MS)

        button(R.string.sign_in_warning_cancel).performClick()
        composeRule.awaitText(string(R.string.trust_refused_certificate), NETWORK_TIMEOUT_MS)

        assertNoticeFits(R.string.trust_refused_certificate)
    }

    /** The parts of the sign-in form that stack, which must not overlap. */
    private fun signInBlocks(
        vararg credentialFields: Pair<String, SemanticsNodeInteraction>,
    ): List<Pair<String, SemanticsNodeInteraction>> =
        listOf(
            brand(),
            "heading" to composeRule.onNode(hasText(string(R.string.sign_in_title)) and isHeading()),
            "server URL field" to field(R.string.sign_in_server_url),
            heading(R.string.sign_in_method),
            "API token choice" to choice(R.string.sign_in_method_api_token),
            "email and password choice" to choice(R.string.sign_in_method_email_password),
        ) + credentialFields + listOf(
            "sign-in button" to composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()),
        )

    /** Text inside the blocks, checked for clipping only. */
    private fun signInTexts(): List<Pair<String, SemanticsNodeInteraction>> =
        listOf(
            fieldLabel(R.string.sign_in_server_url),
            text(R.string.sign_in_server_url_hint),
            inside(R.string.sign_in_method_api_token, isSelectable()),
            inside(R.string.sign_in_method_email_password, isSelectable()),
            inside(R.string.sign_in_submit, hasClickAction()),
        )

    private fun field(@StringRes label: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(string(label)))

    /** How far below the email field's top the password field's top is. */
    private fun passwordOffset(): Dp =
        field(R.string.sign_in_password).getUnclippedBoundsInRoot().top - field(R.string.sign_in_email).getUnclippedBoundsInRoot().top

    /** A radio button, by its label. */
    private fun choice(@StringRes label: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(label)) and isSelectable())

    private fun switch(@StringRes label: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(label)) and isToggleable())

    private fun fieldLabel(@StringRes id: Int): Pair<String, SemanticsNodeInteraction> =
        inside(id, hasSetTextAction())

    /** The text node itself, inside a control that merges its children. */
    private fun inside(@StringRes id: Int, control: SemanticsMatcher): Pair<String, SemanticsNodeInteraction> =
        string(id).let { "$it text" to composeRule.onNode(hasText(it) and hasAnyAncestor(control), useUnmergedTree = true) }

    /**
     * The [pinned] nodes, the top bar's, show in full without a scroll and do not overlap. Then
     * scrolls to each other node in turn, which then has to show in full: inside the screen, clear
     * of the [pinned] nodes and, for text, not cut off. Then no two [blocks] overlap.
     */
    private fun assertFitWhenScrolledTo(
        blocks: List<Pair<String, SemanticsNodeInteraction>>,
        texts: List<Pair<String, SemanticsNodeInteraction>>,
        pinned: List<Pair<String, SemanticsNodeInteraction>> = emptyList(),
    ) {
        assertFitWithoutOverlap(*pinned.toTypedArray())
        val bar = pinned.map { (name, node) -> name to node.getBoundsInRoot() }
        val screen = composeRule.onRoot().getBoundsInRoot()
        for ((name, node) in blocks + texts) {
            node.performScrollTo()
            node.assertIsDisplayed()
            node.textLayout()?.let { assertFalse("text is cut off: $name", it.didOverflowHeight) }
            val box = node.getUnclippedBoundsInRoot()
            val inside = box.left >= screen.left && box.top >= screen.top &&
                box.right <= screen.right && box.bottom <= screen.bottom
            assertTrue("does not fit on the screen: $name", inside)
            for ((barName, barBox) in bar) assertFalse("$name is under $barName", box.overlaps(barBox))
        }
        // Unclipped bounds share one scroll position, so they compare even off screen.
        val bounds = blocks.map { (name, node) -> name to node.getUnclippedBoundsInRoot() }
        for ((i, first) in bounds.withIndex()) {
            for (second in bounds.drop(i + 1)) {
                assertFalse("${first.first} overlaps ${second.first}", first.second.overlaps(second.second))
            }
        }
    }

    private fun assertFitWithoutOverlap(vararg nodes: Pair<String, SemanticsNodeInteraction>) {
        val screen = composeRule.onRoot().getBoundsInRoot()
        val bounds = nodes.map { (name, node) ->
            node.assertIsDisplayed()
            node.textLayout()?.let { assertFalse("text is cut off: $name", it.didOverflowHeight) }
            val box = node.getBoundsInRoot()
            val inside = box.left >= screen.left && box.top >= screen.top &&
                box.right <= screen.right && box.bottom <= screen.bottom
            assertTrue("leaves the screen: $name", inside)
            name to box
        }
        for ((i, first) in bounds.withIndex()) {
            for (second in bounds.drop(i + 1)) {
                assertFalse("${first.first} overlaps ${second.first}", first.second.overlaps(second.second))
            }
        }
    }

    /** Types [server] and the account's API token into the sign-in form and submits it. */
    private fun signInTo(server: ServerUrl) {
        field(R.string.sign_in_server_url).performScrollTo().performTextInput(server.toString())
        field(R.string.sign_in_api_token).performScrollTo().performTextInput(TokklTestServer.apiToken)
        composeRule.onNode(hasText(string(R.string.sign_in_submit)) and hasClickAction()).performScrollTo().performClick()
    }

    /**
     * Typing opened the keyboard, which the sign-in screen hides once a prompt shows. The prompt
     * is measured after that, in the room the user then has.
     */
    private fun awaitKeyboardHidden() {
        val decor = composeRule.activity.window.decorView
        composeRule.waitUntil("the keyboard hides", NETWORK_TIMEOUT_MS) {
            var shown = true
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                shown = ViewCompat.getRootWindowInsets(decor)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            !shown
        }
        composeRule.waitForIdle()
    }

    /**
     * Waits until the keyboard takes up room and returns its top edge, in window pixels. Visible
     * is not enough: with show_ime_with_hard_keyboard at 0, tokkl-api36 reported the keyboard as
     * visible with a height of 0.
     */
    private fun awaitKeyboardTop(): Int {
        val decor = composeRule.activity.window.decorView
        var height = 0
        var top = 0
        composeRule.waitUntil("the keyboard shows", KEYBOARD_TIMEOUT_MS) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                height = ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                top = decor.height - height
            }
            height > 0
        }
        return top
    }

    /**
     * Waits until [node] has kept still for [STILL_MS], once the keyboard's animation and the
     * scroll it starts are over, and returns its bottom edge in window pixels.
     */
    private fun awaitStill(node: SemanticsNodeInteraction): Float {
        var bottom = Float.NaN
        var since = SystemClock.uptimeMillis()
        composeRule.waitUntil("the sign-in form stops moving", KEYBOARD_TIMEOUT_MS) {
            val now = node.bottomInWindow()
            if (now != bottom) {
                bottom = now
                since = SystemClock.uptimeMillis()
            }
            SystemClock.uptimeMillis() - since >= STILL_MS
        }
        return bottom
    }

    /** The node's bottom edge in window pixels, also when its scrolling parent clips it. */
    private fun SemanticsNodeInteraction.bottomInWindow(): Float =
        fetchSemanticsNode().let { it.positionInWindow.y + it.size.height }

    private fun assertHttpWarningFits() {
        val understood = string(R.string.sign_in_http_warning_understood)
        assertDialogFits(
            blocks = listOf(
                "title" to composeRule.onNode(hasText(string(R.string.sign_in_http_warning_title)) and isHeading()),
                "checkbox" to composeRule.onNode(hasText(understood) and isToggleable()),
                "cancel button" to button(R.string.sign_in_warning_cancel),
                "continue button" to button(R.string.sign_in_http_warning_continue),
            ),
            texts = listOf(
                text(R.string.sign_in_http_warning_title),
                "body" to composeRule.onNodeWithText(string(R.string.sign_in_http_warning_body, TokklTestServer.url.toString())),
                inside(R.string.sign_in_http_warning_understood, isToggleable()),
                inside(R.string.sign_in_warning_cancel, hasClickAction()),
                inside(R.string.sign_in_http_warning_continue, hasClickAction()),
            ),
        )
        assertTouchTargets(
            "cancel button" to button(R.string.sign_in_warning_cancel),
            "continue button" to button(R.string.sign_in_http_warning_continue),
        )
    }

    /** The fingerprint and the subject have to show in full, since the user compares them with the server's. */
    private fun assertCertificatePromptFits(@StringRes title: Int, @StringRes body: Int) {
        val endpoint = TokklTestServer.tlsUrl.api("me")
        val server = "${endpoint.host}:${endpoint.port}"
        assertDialogFits(
            blocks = listOf(
                "title" to composeRule.onNode(hasText(string(title)) and isHeading()),
                "fingerprint" to composeRule.onNodeWithText(TokklTestServer.tlsFingerprint),
                "subject" to composeRule.onNodeWithText(CERTIFICATE_SUBJECT),
                "cancel button" to button(R.string.sign_in_warning_cancel),
                "trust button" to button(R.string.sign_in_certificate_trust),
            ),
            texts = listOf(
                text(title),
                "body" to composeRule.onNodeWithText(string(body, server)),
                text(R.string.sign_in_certificate_fingerprint),
                text(R.string.sign_in_certificate_subject),
                inside(R.string.sign_in_warning_cancel, hasClickAction()),
                inside(R.string.sign_in_certificate_trust, hasClickAction()),
            ),
        )
        assertTouchTargets(
            "cancel button" to button(R.string.sign_in_warning_cancel),
            "trust button" to button(R.string.sign_in_certificate_trust),
        )
    }

    /** Each button is at least 48dp tall, the smallest touch target Android's accessibility guidance allows. */
    private fun assertTouchTargets(vararg buttons: Pair<String, SemanticsNodeInteraction>) {
        for ((name, node) in buttons) {
            val height = node.getUnclippedBoundsInRoot().let { it.bottom - it.top }
            assertTrue("$name is $height tall", height >= MIN_TOUCH_TARGET - SLACK)
        }
    }

    /** The notice sits over the Timer page, so it may cover none of the page's own parts or the bar either. */
    private fun assertNoticeFits(@StringRes message: Int) {
        assertFitWithoutOverlap(
            *timerPageParts().toTypedArray(),
            text(message),
            "review button" to button(R.string.trust_refused_review),
        )
        val label = inside(R.string.trust_refused_review, hasClickAction())
        label.second.textLayout()?.let { assertFalse("text is cut off: ${label.first}", it.didOverflowHeight) }
    }

    private fun button(@StringRes label: Int): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(label)) and hasClickAction())

    /**
     * The Timer page's parts that stack above its history, the idle timer's controls among them,
     * and the bottom bar's items, which must not overlap. The List and Calendar segments share
     * their border, so the switch counts as one part.
     */
    private fun timerPageParts(): List<Pair<String, SemanticsNodeInteraction>> =
        listOf(
            heading(R.string.nav_timer),
            // By its label alone: the field is disabled until the account settings load, and a
            // set-text matcher found no node then.
            "description field" to composeRule.onNode(hasText(string(R.string.entry_description))),
            "project button" to composeRule.onNode(hasContentDescription(string(R.string.entry_no_project_spoken)) and hasClickAction()),
            "tags button" to composeRule.onNode(hasContentDescription(string(R.string.entry_no_tags_spoken)) and hasClickAction()),
            "billable" to composeRule.onNode(hasText(string(R.string.entry_billable)) and isToggleable()),
            "start button" to composeRule.onNode(hasContentDescription(string(R.string.timer_start_spoken)) and hasClickAction()),
            "past entry button" to button(R.string.timer_add_entry),
            "view switch" to composeRule.onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup) and
                    hasAnyDescendant(hasText(string(R.string.timer_view_list))),
            ),
        ) + BAR_LABELS.map { label -> "${string(label)} item" to button(label) }

    /** Each timer control's, each segment's and each bar item's label shows in full, inside its control. */
    private fun assertLabelsWhole() {
        for ((label, control) in listOf(
            R.string.entry_project to hasClickAction(),
            R.string.entry_tags to hasClickAction(),
            R.string.entry_billable to isToggleable(),
            R.string.timer_start to hasClickAction(),
            R.string.timer_add_entry to hasClickAction(),
        )) {
            val (name, text) = inside(label, control)
            text.textLayout()?.let { assertFalse("text is cut off: $name", it.didOverflowHeight) }
            val box = composeRule.onNode(hasText(string(label)) and control).getBoundsInRoot()
            assertTrue("$name leaves its control", text.getBoundsInRoot().inside(box))
        }
        for (label in listOf(R.string.timer_view_list, R.string.timer_view_calendar)) {
            val (name, text) = inside(label, isSelectable())
            text.textLayout()?.let { assertFalse("text is cut off: $name", it.didOverflowHeight) }
            assertTrue("$name leaves its segment", text.getBoundsInRoot().inside(choice(label).getBoundsInRoot()))
        }
        for (label in BAR_LABELS) {
            val (name, text) = inside(label, hasClickAction())
            text.assertIsDisplayed()
            text.textLayout()?.let { assertFalse("text is cut off: $name", it.didOverflowHeight) }
            assertTrue("$name leaves its item", text.getBoundsInRoot().inside(button(label).getBoundsInRoot()))
        }
    }

    /** The brand, which TalkBack reads by its wordmark's description, the app's name. */
    private fun brand(): Pair<String, SemanticsNodeInteraction> =
        "brand" to composeRule.onNodeWithContentDescription(string(R.string.app_name))

    /**
     * Checks the open dialog, whose text scrolls between a fixed title and fixed buttons. Each
     * of [blocks] shows in full once scrolled into view, and no two of them overlap. Text in
     * [texts] wraps inside the dialog and is not cut off. Long text may be taller than the
     * scrolling area, since the user scrolls through it.
     */
    private fun assertDialogFits(
        blocks: List<Pair<String, SemanticsNodeInteraction>>,
        texts: List<Pair<String, SemanticsNodeInteraction>>,
    ) {
        val window = composeRule.onNode(isRoot() and hasAnyDescendant(isDialog())).getBoundsInRoot()
        val dialog = composeRule.onNode(isDialog()).getUnclippedBoundsInRoot()
        assertTrue("the dialog leaves its window", dialog.inside(window))
        val scrollingArea = composeRule.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).getBoundsInRoot()
        val fixed = mutableListOf("scrolling area" to scrollingArea)
        val scrolled = mutableListOf<Pair<String, SemanticsNodeInteraction>>()
        for ((name, node) in texts) {
            if (scrolls(node)) node.performScrollTo()
            node.assertIsDisplayed()
            node.textLayout()?.let { assertFalse("text is cut off: $name", it.didOverflowHeight) }
            val box = node.getUnclippedBoundsInRoot()
            assertTrue("wider than the dialog: $name", box.left >= dialog.left - SLACK && box.right <= dialog.right + SLACK)
        }
        for ((name, node) in blocks) {
            val scrolls = scrolls(node)
            if (scrolls) node.performScrollTo()
            node.assertIsDisplayed()
            val box = node.getUnclippedBoundsInRoot()
            assertTrue("does not show in full: $name", box.inside(if (scrolls) scrollingArea else dialog))
            if (scrolls) scrolled += name to node else fixed += name to box
        }
        // Scrolled blocks compare with each other at one scroll position. The scrolling area
        // clips them, so they cannot cover a fixed part when the area does not.
        val atOnePosition = scrolled.map { (name, node) -> name to node.getUnclippedBoundsInRoot() }
        for (group in listOf(fixed, atOnePosition)) {
            for ((i, first) in group.withIndex()) {
                for (second in group.drop(i + 1)) {
                    assertFalse(
                        "${first.first} ${first.second} overlaps ${second.first} ${second.second}",
                        first.second.overlaps(second.second),
                    )
                }
            }
        }
    }

    private fun scrolls(node: SemanticsNodeInteraction): Boolean =
        hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())

    private fun DpRect.inside(other: DpRect): Boolean =
        left >= other.left - SLACK && top >= other.top - SLACK && right <= other.right + SLACK && bottom <= other.bottom + SLACK

    /** The text node itself, not the button it may sit in, so its layout result is reachable. */
    private fun text(@StringRes id: Int): Pair<String, SemanticsNodeInteraction> =
        string(id).let { it to composeRule.onNodeWithText(it, useUnmergedTree = true) }

    /** A text node that TalkBack announces as a heading. */
    private fun heading(@StringRes id: Int): Pair<String, SemanticsNodeInteraction> =
        string(id).let { "$it heading" to composeRule.onNode(hasText(it) and isHeading(), useUnmergedTree = true) }

    /**
     * The layout result that semantics hands out is laid out again at the full available width,
     * so its didOverflowWidth is true for any text narrower than its container. Only the height
     * is compared: soft wrapping keeps every line inside the width.
     */
    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult? {
        if (SemanticsActions.GetTextLayoutResult !in fetchSemanticsNode().config) return null
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private fun string(@StringRes id: Int, vararg arguments: Any): String = composeRule.activity.getString(id, *arguments)

    private companion object {
        const val NETWORK_TIMEOUT_MS = 15_000L

        const val KEYBOARD_TIMEOUT_MS = 10_000L

        /** Longer than the keyboard's animation, so a field still moving does not count as still. */
        const val STILL_MS = 1_000L

        /** Positions in the window are floats from two layouts, so they match to within this. */
        const val EDGE_SLACK_PX = 0.5f

        /** Less than a pixel, for bounds that touch after rounding. */
        val SLACK = 0.5.dp

        val MIN_TOUCH_TARGET = 48.dp

        /** The bottom bar's items, in order. */
        val BAR_LABELS = listOf(R.string.nav_timer, R.string.reports, R.string.nav_projects, R.string.settings, R.string.nav_more)

        /** The subject of the certificate that scripts/instrumented-tests.sh makes. */
        const val CERTIFICATE_SUBJECT = "CN=Tokkl instrumented tests"
    }
}

/** A test that starts signed in to a server whose requests the app refuses until the user answers a trust prompt. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
private annotation class RefusedSession(val refusal: Refusal)

private enum class Refusal {
    /** The plain http test server, with no confirmation stored. */
    UNCONFIRMED_HTTP,

    /** The TLS proxy, with a pin from an earlier certificate stored. */
    CHANGED_CERTIFICATE,
}

/** Stores the session of a test marked [RefusedSession]. Order it after [SessionRule] and before the activity starts. */
private class RefusedSessionRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                description.getAnnotation(RefusedSession::class.java)?.let { store(it.refusal) }
                base.evaluate()
            }
        }

    private fun store(refusal: Refusal) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SessionRule.signOut()
        val server = when (refusal) {
            Refusal.UNCONFIRMED_HTTP -> TokklTestServer.url
            Refusal.CHANGED_CERTIFICATE -> TokklTestServer.tlsUrl.also {
                val endpoint = it.api("me")
                ServerTrustStore(context).pinCertificate(ServerCertificate(endpoint.host, endpoint.port, "00".repeat(32), subject = ""))
            }
        }
        Session(context).save(server, TokklTestServer.apiToken)
    }
}
