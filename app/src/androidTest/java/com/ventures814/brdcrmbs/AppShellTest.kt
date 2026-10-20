package com.ventures814.brdcrmbs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
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
            "Week" to "Your weekly planning space will appear here.",
            "Progress" to "Your adherence history will appear here.",
            "Settings" to "Your app settings will appear here.",
            "Today" to "Your daily fasting, meals, and activity will appear here."
        )
        screens.forEach { (tab, description) ->
            compose.onNode(isSelectable() and hasText(tab)).performClick().assertIsSelected()
            compose.onNodeWithText(description).assertIsDisplayed()
        }

        compose.onNode(isSelectable() and hasText("Week")).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(isSelectable() and hasText("Week")).assertIsSelected()
        compose.onNodeWithText("Your weekly planning space will appear here.").assertIsDisplayed()
    }
}
