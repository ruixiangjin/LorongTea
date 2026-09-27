package com.lorongtea.mobile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lorongtea.mobile.data.StaffMenuItem
import com.lorongtea.mobile.data.Store
import com.lorongtea.mobile.ui.staff.StaffMenuScreen
import com.lorongtea.mobile.ui.staff.StaffMenuState
import com.lorongtea.mobile.ui.theme.LorongTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class StaffScreenRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun stockAdjustmentDraftSurvivesComposeStateRestoration() {
        val restoration = StateRestorationTester(compose)
        var saved: Triple<String, Int, String>? = null
        restoration.setContent {
            LorongTheme {
                StaffMenuScreen(
                    store = Store("1", "bukit-bintang", "Demo store"),
                    state = StaffMenuState(items = listOf(StaffMenuItem("9", "Tea", 1090, true, 12))),
                    refresh = {},
                    update = { _, _ -> },
                    adjust = { id, delta, reason -> saved = Triple(id, delta, reason) },
                    recover = {},
                    back = {},
                )
            }
        }
        compose.onNodeWithText(text(R.string.staff_adjust_stock)).performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.staff_stock_delta))).performTextInput("-2")
        compose.onNode(hasSetTextAction() and hasText(text(R.string.staff_stock_reason))).performTextInput("Damaged packaging")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("-2").assertExists()
        compose.onNodeWithText("Damaged packaging").assertExists()
        compose.onNodeWithText(text(R.string.staff_save)).performClick()
        compose.runOnIdle { assertEquals(Triple("9", -2, "Damaged packaging"), saved) }
    }

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
