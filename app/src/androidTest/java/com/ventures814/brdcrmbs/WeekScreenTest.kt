package com.ventures814.brdcrmbs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ventures814.brdcrmbs.ui.screens.WeekScreen
import com.ventures814.brdcrmbs.domain.PlanningData
import com.ventures814.brdcrmbs.ui.week.presentWeek
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import org.junit.Rule
import org.junit.Test

class WeekScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyWeekRetainsAllSectionsAndAddAffordancesWithoutSampleData() {
        compose.setContent {
            BrdcrmbsTheme { WeekScreen(presentWeek(testRange, 0, PlanningData(), testZone), {}) }
        }
        compose.onAllNodesWithText("Nothing planned").assertCountEquals(3)
        compose.onAllNodesWithText("+ Add").assertCountEquals(3)
        compose.onNodeWithText("Meals (0)").assertIsDisplayed()
        compose.onNodeWithText("Eggs + avocado").assertDoesNotExist()
    }

    @Test
    fun daySelectionRestoresAndFiltersPlansByDate() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { BrdcrmbsTheme { SampleWeekScreen() } }
        compose.onNodeWithContentDescription("Mon 19, October 2026").assertIsSelected()
        compose.onNodeWithContentDescription("Tue 20, October 2026").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Mon 19, October 2026").assertIsNotSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Tue 20, October 2026").assertIsSelected()
        compose.onNodeWithText("Oct 19, 7:00 PM → Oct 20, 11:00 AM").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Eggs + avocado").assertDoesNotExist()
    }

    @Test
    fun allSampleRowsAreReachableByScrolling() {
        compose.setContent { BrdcrmbsTheme { SampleWeekScreen() } }
        listOf(
            "Oct 19, 7:00 PM → Oct 20, 11:00 AM", "Eggs + avocado", "Chicken + vegetables",
            "Salmon + salad", "Bakal Gym — Upper Body", "20-minute walk"
        ).forEach { title ->
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        }
    }
}
