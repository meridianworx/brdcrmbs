package com.ventures814.brdcrmbs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ventures814.brdcrmbs.ui.BrdcrmbsApp
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import org.junit.Rule
import org.junit.Test

class AppShellTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tabsShowTheirScreensAndRestoreSelection() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { BrdcrmbsTheme { BrdcrmbsApp() } }

        compose.onNode(isSelectable() and hasText("Today")).assertIsSelected()
        val screens = listOf(
            "Week" to "My Week",
            "Progress" to "PROGRESS",
            "Settings" to "Saved Meals",
            "Today" to "Today"
        )
        screens.forEach { (tab, description) ->
            compose.onNode(isSelectable() and hasText(tab)).performClick().assertIsSelected()
            if (tab == "Today" || tab == "Progress") compose.onNode(hasText(description) and isHeading()).assertIsDisplayed()
            else compose.onNodeWithText(description).assertIsDisplayed()
        }

        compose.onNode(isSelectable() and hasText("Week")).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(isSelectable() and hasText("Week")).assertIsSelected()
        compose.onNodeWithText("My Week").assertIsDisplayed()
    }
}
