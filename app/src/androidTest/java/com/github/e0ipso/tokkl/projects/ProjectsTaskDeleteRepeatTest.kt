package com.github.e0ipso.tokkl.projects

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Twenty consecutive real create/edit/delete scenarios in one instrumented batch. Sustained
 * runs reproduced the returning-keyboard race which isolated runs missed. Reuse the original
 * scenario, its rules and its disposable account so the assertions and cleanup cannot drift.
 * Each named test gets its own process from Android Test Orchestrator, as in the full suite.
 */
@RunWith(AndroidJUnit4::class)
class ProjectsTaskDeleteRepeatTest {
    private val scenario = ProjectsServerTest()

    @get:Rule
    val rules = scenario.rules

    @Before
    fun seedTheServerAndRefresh() = scenario.seedTheServerAndRefresh()

    @After
    fun cleanUp() = scenario.cleanUp()

    @Test fun repeat01() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat02() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat03() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat04() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat05() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat06() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat07() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat08() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat09() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat10() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat11() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat12() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat13() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat14() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat15() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat16() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat17() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat18() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat19() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
    @Test fun repeat20() = scenario.createEditAndDeleteATaskAndTheCacheFollowsEachChange()
}
