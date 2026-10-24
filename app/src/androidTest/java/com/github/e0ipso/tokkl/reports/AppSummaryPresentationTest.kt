package com.github.e0ipso.tokkl.reports

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.WindowCompat
import com.github.e0ipso.tokkl.accessibility.createAndroidComposeRule
import com.github.e0ipso.tokkl.api.ApiResult
import com.github.e0ipso.tokkl.session.AccountSettings
import com.github.e0ipso.tokkl.ui.theme.TokklTheme
import java.time.DayOfWeek
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Component acceptance tests for decoded server data; the seeded ReportsScreenTest covers HTTP. */
class AppSummaryPresentationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun explicitZeroIsVisibleAndMissingGroupMoneyIsAbsent() {
        show(decode())
        compose.onNode(hasText("Zero first") and hasText("0.00 XYZ")).performScrollTo().assertExists()
        compose.onNode(hasText("Missing second") and hasText("0.00 XYZ")).assertDoesNotExist()
        compose.onNode(hasText("Missing second")).performScrollTo().assertExists()
        val first = compose.onNode(hasText("Zero first")).getUnclippedBoundsInRoot()
        val second = compose.onNode(hasText("Missing second")).getUnclippedBoundsInRoot()
        assertTrue("server order, not duration order", first.top < second.top)
    }

    @Test
    fun unknownScalarListObjectAndRateDataShowUnderTheirOwnersWithoutInventedUnits() {
        val report = decode(BODY
            .replace("\"totals\":{\"seconds\":777", "\"totals\":{\"futureRate\":[{\"unit\":\"tokens\",\"value\":12.5}],\"seconds\":777")
            .replace("\"title\":\"Zero first\"", "\"title\":\"Zero first\",\"new_flag\":false")
            .replace("\"start\":\"2026-10-21\"", "\"start\":\"2026-10-21\",\"new_label\":\"<b>literal</b>\"")
            .replace("\"resolution\":\"day\"", "\"resolution\":\"day\",\"newScalar\":0"))
        show(report)
        compose.onNode(hasText("Additional report data")).performScrollTo().assertExists()
        compose.onNode(hasText("[{Unit: \"tokens\", Value: 12.5}]") and within("Totals"), useUnmergedTree = true)
            .performScrollTo().assertExists()
        compose.onNode(hasText("false") and within("Project: Zero first"), useUnmergedTree = true)
            .performScrollTo().assertExists()
        compose.onNode(hasText("\"<b>literal</b>\"") and within("Bucket starting 2026-10-21"), useUnmergedTree = true)
            .performScrollTo().assertExists()
        compose.onNode(hasText("0") and within("Report"), useUnmergedTree = true).performScrollTo().assertExists()
        compose.onNode(hasText("New flag") and within("Project: Missing second"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun ordinaryNewCurrenciesKeepMoneyFormattingAndMixedGroupsAreNotMislabelled() {
        show(decode(BODY.replace("{\"currency\":\"XYZ\",\"cents\":0}",
            "{\"currency\":\"XYZ\",\"cents\":0},{\"currency\":\"QXZ\",\"cents\":789}")))
        compose.onNode(hasText("7.89 QXZ")).performScrollTo().assertExists()
        compose.onNode(hasText("Zero first") and hasText("0.00 XYZ")).assertDoesNotExist()
        compose.onNode(hasText("Zero first")).performScrollTo().assertExists()
    }

    @Test
    fun omittedAdditionsHaveAnExplicitDisplayLimitNotice() {
        val extras = (1..20).joinToString(",") { "\"future_$it\":\"${"x".repeat(500)}\"" }
        val report = decode(BODY.replace("\"resolution\":\"day\"", "\"resolution\":\"day\",$extras"))
        assertTrue(report.additionalDataTruncated)
        show(report)
        compose.onNode(hasText("Some additional data was shortened for display.")).performScrollTo().assertExists()
    }

    private fun within(title: String) = hasAnyAncestor(hasAnyChild(hasText(title)))

    private fun show(report: SummaryReport) {
        compose.activity.runOnUiThread {
            WindowCompat.enableEdgeToEdge(compose.activity.window)
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent {
            TokklTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SummaryContent(report, AccountSettings("UTC", DayOfWeek.MONDAY, timeResolutionMinutes = null))
                }
            }
        }
    }

    private fun decode(body: String = BODY) = (decodeAppSummary(Json.parseToJsonElement(body).jsonObject) as ApiResult.Success).value

    private companion object {
        val BODY = """{
          "start_date":"2026-10-21","end_date":"2026-10-21","resolution":"day",
          "totals":{"seconds":777,"billable_seconds":60,"entry_count":2,"amounts":[{"currency":"XYZ","cents":0}]},
          "series":[{"start":"2026-10-21","seconds":777,"billable_seconds":60}],
          "groups":[
            {"id":2,"title":"Zero first","color":"#123456","seconds":60,"billable_seconds":60,"amount_cents":0,"sub_groups":[]},
            {"id":1,"title":"Missing second","color":null,"seconds":717,"billable_seconds":0,"sub_groups":[]}
          ]
        }"""
    }
}
