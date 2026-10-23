package com.ventures814.brdcrmbs

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.reminders.*
import com.ventures814.brdcrmbs.ui.settings.*
import com.ventures814.brdcrmbs.ui.screens.SettingsScreen
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import org.junit.*
import org.junit.Assert.*

@FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class RemindersUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun allFourControlsReflectPreferencesAndPermissionWithoutAutomaticRequests() {
        var prefs by mutableStateOf(ReminderPreferences())
        var requests = 0
        compose.setContent { BrdcrmbsTheme { Surface {
            RemindersContent(preferences = prefs, allowed = false, busy = false, error = null,
                back = {}, toggle = { type, on -> prefs = prefs.with(type, on) }, permission = { requests++ }, retry = {})
        } } }
        for (type in ReminderType.entries) compose.onNodeWithTag("reminder-${type.name}").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Notification permission").performScrollTo().assertIsDisplayed()
        assertEquals(0, requests)
        compose.onNodeWithText("Enable notifications").performScrollTo().performClick()
        assertEquals(1, requests)
    }
    @Test fun realSettingsRoutesToRemindersAndReturnsWithoutChangingSavedLibraries() {
        compose.setContent { BrdcrmbsTheme { Surface { SettingsScreen() } } }
        compose.onNodeWithText("Reminders").performScrollTo().performClick()
        compose.onNodeWithTag("reminder-FAST_START").assertIsDisplayed()
        compose.onNodeWithTag("reminder-FAST_END").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Saved Meals").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Saved Activities").assertIsDisplayed()
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion = 33)
    @Test fun firstEnableExplainsPermissionDenialKeepsOffAndDoesNotNag() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BrdcrmbsApplication
        // The test never revokes permission in-process (Android would kill instrumentation).
        Assume.assumeFalse(app.reminderBackend.allowed())
        Assume.assumeFalse(app.reminderSettings.asked)
        compose.setContent { BrdcrmbsTheme { Surface { RemindersScreen(androidx.compose.ui.Modifier, {}) } } }
        compose.onAllNodesWithText("Allow plan reminders?").assertCountEquals(0)
        compose.onNodeWithTag("reminder-MEAL").performScrollTo().performClick()
        compose.onNodeWithText("Allow plan reminders?").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithTag("reminder-MEAL").assertIsOff()
        assertFalse(app.reminderSettings.asked)
        compose.onAllNodesWithText("Allow plan reminders?").assertCountEquals(0)
        compose.onNodeWithTag("reminder-MEAL").performClick()
        compose.onNodeWithText("Continue").performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        try {
            compose.waitUntil(30_000) {
                automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_deny_button")?.isNotEmpty() == true
            }
            val deny = automation.rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_deny_button").first()
            assertTrue(deny.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            compose.waitUntil(30_000) { app.reminderSettings.asked }
            compose.onNodeWithTag("reminder-MEAL").assertIsOff()
            compose.onAllNodesWithText("Allow plan reminders?").assertCountEquals(0)
            assertFalse(app.reminderBackend.allowed())
            compose.onNodeWithTag("reminder-MEAL").performClick()
            compose.waitUntil(30_000) { automation.rootInActiveWindow?.packageName?.toString() == "com.android.settings" }
            automation.executeShellCommand("input keyevent KEYCODE_BACK").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
            }
            compose.waitUntil(30_000) { automation.rootInActiveWindow?.packageName?.toString() == "com.ventures814.brdcrmbs" }
            compose.onNodeWithTag("reminder-MEAL").assertIsOff()
        } finally {
            // Restore the test's app preference only; never revoke Android permission in-process.
            InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("reminder_preferences_permission", android.content.Context.MODE_PRIVATE)
                .edit().remove("permissionAsked").commit()
        }
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion = 33)
    @Test fun permissionGrantEnablesOnlyRequestedCategoryAndOffCancelsPreference() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as BrdcrmbsApplication
        Assume.assumeFalse(app.reminderBackend.allowed())
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        compose.setContent { BrdcrmbsTheme { Surface { RemindersScreen(androidx.compose.ui.Modifier, {}) } } }
        try {
            compose.onNodeWithTag("reminder-MEAL").performScrollTo().assertIsOff().performClick()
            compose.onNodeWithText("Continue").performClick()
            compose.waitUntil(30_000) { automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_allow_button")?.isNotEmpty() == true }
            val allow = automation.rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_allow_button").first()
            assertTrue(allow.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            compose.waitUntil(30_000) { app.reminderSettings.state.value.enabled == setOf(ReminderType.MEAL) }
            compose.onNodeWithTag("reminder-MEAL").assertIsOn()
            compose.onNodeWithText("Allowed").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("reminder-MEAL").performScrollTo().performClick()
            compose.waitUntil(30_000) { app.reminderSettings.state.value.enabled.isEmpty() }
        } finally {
            kotlinx.coroutines.runBlocking { app.reminderSettings.set(ReminderType.MEAL, false) }
            instrumentation.targetContext.getSharedPreferences("reminder_preferences_permission", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
