package com.github.e0ipso.tokkl.timer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.e0ipso.tokkl.SustainedRepeat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Repeat the original assertions with a fresh account and orchestrator process each time. */
@RunWith(AndroidJUnit4::class)
@SustainedRepeat
class DescriptionSuggestionsRepeatTest {
    private val scenario = DescriptionSuggestionsServerTest()

    @get:Rule val rules = scenario.rules
    @Before fun setUp() = scenario.signInToANewAccount()
    @After fun cleanUp() = scenario.cleanUp()

    @Test fun repeat01() = scenario.suggestionsFromRecentEntriesShowWhileTypingOfflineAndAPickFillsEveryField()
    @Test fun repeat02() = scenario.suggestionsFromRecentEntriesShowWhileTypingOfflineAndAPickFillsEveryField()
    @Test fun repeat03() = scenario.suggestionsFromRecentEntriesShowWhileTypingOfflineAndAPickFillsEveryField()
    @Test fun repeat04() = scenario.suggestionsFromRecentEntriesShowWhileTypingOfflineAndAPickFillsEveryField()
    @Test fun repeat05() = scenario.suggestionsFromRecentEntriesShowWhileTypingOfflineAndAPickFillsEveryField()
    @Test fun repeat06() = scenario.suggestionsFromRecentEntriesShowWhileTypingOfflineAndAPickFillsEveryField()
    @Test fun repeat07() = scenario.suggestionsFromRecentEntriesShowWhileTypingOfflineAndAPickFillsEveryField()
    @Test fun repeat08() = scenario.suggestionsFromRecentEntriesShowWhileTypingOfflineAndAPickFillsEveryField()
    @Test fun repeat09() = scenario.suggestionsFromRecentEntriesShowWhileTypingOfflineAndAPickFillsEveryField()
    @Test fun repeat10() = scenario.suggestionsFromRecentEntriesShowWhileTypingOfflineAndAPickFillsEveryField()
}
