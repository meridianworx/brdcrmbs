package com.ventures814.brdcrmbs

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ventures814.brdcrmbs.ui.screens.SettingsScreen
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import org.junit.Rule
import org.junit.Test

class SettingsDebugUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun realSettingsKeepsDebugQaControlsReachableBelowProductionGroups() {
        compose.setContent { BrdcrmbsTheme { Surface { SettingsScreen() } } }
        compose.onNodeWithText("DEVELOPER / TESTING").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Progress Test Data").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Seed Test Week").performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithText("Seed Progress test data?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Reset Test Data").performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithText("Remove seeded test data?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
    }
}
