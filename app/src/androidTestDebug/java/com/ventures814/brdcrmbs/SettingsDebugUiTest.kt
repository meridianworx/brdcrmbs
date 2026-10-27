package com.ventures814.brdcrmbs

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.ventures814.brdcrmbs.ui.screens.SettingsScreen
import com.ventures814.brdcrmbs.ui.screens.SettingsDevelopmentContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import org.junit.Rule
import org.junit.Test

class SettingsDebugUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun retainedDevelopmentContentStillProvidesQaControlsAndReminderDiagnostics() {
        compose.setContent { BrdcrmbsTheme { Surface {
            Column(Modifier.verticalScroll(rememberScrollState())) { SettingsDevelopmentContent() }
        } } }
        compose.onNodeWithText("DEVELOPER / TESTING").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Progress Test Data").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Seed Test Week").performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithText("Seed Progress test data?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Reset Test Data").performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()
        compose.onNodeWithText("Remove seeded test data?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Reminder diagnostics").assertExists()
        compose.onNodeWithText("Reconcile reminders now").assertHasClickAction()
    }

    @Test fun normalDebugSettingsOmitsAllDevelopmentContent() {
        compose.setContent { BrdcrmbsTheme { Surface { SettingsScreen() } } }
        compose.onNodeWithText("About").performScrollTo().assertIsDisplayed()
        for (label in listOf("DEVELOPER / TESTING", "Progress Test Data", "Seed Test Week",
            "Seed Active Fast", "Reset Test Data", "Reminder diagnostics", "Reconcile reminders now")) {
            compose.onNodeWithText(label, useUnmergedTree = true).assertDoesNotExist()
        }
        compose.onNodeWithText("You make the plan. The app helps you follow it.").performScrollTo().assertIsDisplayed()
    }
}
