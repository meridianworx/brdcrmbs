package com.ventures814.brdcrmbs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.ui.BrdcrmbsApp
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AboutUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val purpose = "brdcrmbs helps you plan and follow your own fasting, meals, and workouts / activities, then review how closely you followed your plan."
    private val philosophy = "You make the plan. The app helps you follow it."
    @Suppress("DEPRECATION")
    private fun installedVersion(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return "Version ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}"
    }
    private fun show(large: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else density.fontScale)) {
                BrdcrmbsTheme { Surface(color = MaterialTheme.colorScheme.background) {
                    Box(if (large) Modifier.width(320.dp).height(480.dp) else Modifier.fillMaxSize()) { BrdcrmbsApp() }
                } }
            }
        }
        compose.onNode(isSelectable() and hasText("Settings")).performClick()
        compose.onNodeWithText("About").performScrollTo().assertHasClickAction().performClick()
    }
    private fun hiddenTools() {
        for (text in listOf("DEVELOPER / TESTING", "Progress Test Data", "Seed Test Week", "Seed Active Fast",
            "Reset Test Data", "Reminder diagnostics", "Reconcile reminders now")) {
            compose.onNodeWithText(text, useUnmergedTree = true).assertDoesNotExist()
        }
    }
    @Test fun aboutShowsInstalledMetadataAndContentWithBothBackPaths() {
        show()
        for (text in listOf("About", "brdcrmbs", purpose, philosophy, installedVersion())) {
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
        for (tab in listOf("Today", "Week", "Progress", "Settings")) compose.onNode(isSelectable() and hasText(tab)).assertDoesNotExist()
        hiddenTools()
        compose.onNodeWithContentDescription("Back").performScrollTo().performClick()
        compose.onNode(isSelectable() and hasText("Settings")).assertIsSelected()
        hiddenTools()
        compose.onNodeWithText("About").performScrollTo().performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(isSelectable() and hasText("Settings")).assertIsSelected()
    }
    @Test fun narrowDoubleFontContentScrollsWithoutClipping() {
        show(large = true)
        for (text in listOf("About", "brdcrmbs", purpose, philosophy, installedVersion())) {
            val node = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { result ->
                assertFalse(result.didOverflowHeight)
                assertEquals(result.layoutInput.text.length, result.getLineEnd(result.lineCount - 1))
                for (line in 0 until result.lineCount) {
                    assertFalse(result.isLineEllipsized(line))
                    assertTrue(result.getLineLeft(line) >= -1f)
                    assertTrue(result.getLineRight(line) <= result.size.width + 1f)
                }
            }
        }
        compose.onNode(hasScrollAction()).assertExists()
        hiddenTools()
    }
}
