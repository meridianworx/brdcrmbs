package com.ventures814.brdcrmbs

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.ventures814.brdcrmbs.backup.*
import com.ventures814.brdcrmbs.ui.screens.BackupScreen
import com.ventures814.brdcrmbs.ui.theme.BrdcrmbsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var launched: Intent? = null
    private var code = 0
    private var writes = 0
    private var restores = 0
    private var backs = 0
    private var invalid = false
    private val snapshot = richBackupSnapshot()
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            code = requestCode; launched = contract.createIntent(context, input)
        }
    }
    private val operations = object : BackupOperations {
        override suspend fun create() = PreparedBackup(BackupCodec.encode(snapshot), "brdcrmbs-backup-2026-10-25.json")
        override suspend fun read(uri: Uri): BackupSnapshot { require(!invalid); return snapshot }
        override suspend fun write(uri: Uri, backup: PreparedBackup) { writes++; require(!invalid) }
        override suspend fun restore(snapshot: BackupSnapshot): RestoreResult { restores++; return RestoreResult(true) }
    }
    private val model = BackupViewModel(operations)
    private fun show() {
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.setContent { BrdcrmbsTheme { CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            Surface(Modifier.fillMaxSize()) { BackupScreen(back = { backs++ }, model = model) }
        } } }
    }
    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun picked(uri: Uri? = Uri.parse("content://test/backup.json")) {
        compose.runOnIdle { registry.dispatchResult(code, if (uri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, uri?.let { Intent().setData(it) }) }
        compose.waitUntil(10_000) { !model.state.value.busy }
    }
    @Test fun createUsesNativeContractAndHandlesSuccessCancellationAndWriteFailure() {
        show(); click("createBackup")
        compose.waitUntil { launched != null }
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, launched!!.action)
        assertEquals("application/json", launched!!.type)
        assertEquals("brdcrmbs-backup-2026-10-25.json", launched!!.getStringExtra(Intent.EXTRA_TITLE))
        picked(); assertEquals(1, writes)
        assertEquals("Backup saved.", model.state.value.message)
        click("createBackup"); compose.waitUntil { model.state.value.request == null && model.state.value.busy }
        picked(null); assertEquals(1, writes)
        invalid = true; click("createBackup")
        compose.waitUntil { model.state.value.request == null && model.state.value.busy }
        picked(); assertTrue(model.state.value.failed); assertEquals(0, restores)
        compose.onNodeWithTag("backupFeedback").performScrollTo().assertIsDisplayed()
    }
    @Test fun openValidatesBeforeConfirmationAndOnlyExplicitConfirmationReplaces() {
        show(); click("openBackup"); compose.waitUntil { launched != null }
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, launched!!.action)
        picked(); assertEquals(0, restores)
        compose.onNodeWithText("Replace current data?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick(); assertEquals(0, restores)
        click("openBackup"); picked()
        compose.onNodeWithTag("confirmRestoreBackup").performClick()
        compose.waitUntil { !model.state.value.busy && model.state.value.preview == null }
        assertEquals(1, restores); assertEquals("Backup restored.", model.state.value.message)
        compose.onNodeWithTag("backupBack").performScrollTo().performClick(); assertEquals(1, backs)
    }
    @Test fun invalidAndCancelledSelectionsNeverOfferReplacement() {
        show(); invalid = true; click("openBackup"); compose.waitUntil { launched != null }; picked()
        assertTrue(model.state.value.failed)
        compose.onNodeWithTag("confirmRestoreBackup").assertDoesNotExist()
        assertEquals(0, restores)
        click("openBackup"); picked(null)
        assertEquals("Restore cancelled.", model.state.value.message); assertEquals(0, restores)
    }
}
