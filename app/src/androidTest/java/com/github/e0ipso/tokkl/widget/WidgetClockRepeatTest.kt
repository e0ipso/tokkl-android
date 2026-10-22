package com.github.e0ipso.tokkl.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every repetition must pass; these are independent cases, not retries after a failure. */
@RunWith(AndroidJUnit4::class)
class WidgetClockRepeatTest {
    private val scenario = WidgetServerTest()

    @get:Rule val rules = scenario.rules
    @Before fun setUp() = scenario.prepareAccountAndWidget()
    @After fun cleanUp() = scenario.cleanUp()

    @Test fun repeat01() = scenario.nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod()
    @Test fun repeat02() = scenario.nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod()
    @Test fun repeat03() = scenario.nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod()
    @Test fun repeat04() = scenario.nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod()
    @Test fun repeat05() = scenario.nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod()
    @Test fun repeat06() = scenario.nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod()
    @Test fun repeat07() = scenario.nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod()
    @Test fun repeat08() = scenario.nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod()
    @Test fun repeat09() = scenario.nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod()
    @Test fun repeat10() = scenario.nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod()
}
