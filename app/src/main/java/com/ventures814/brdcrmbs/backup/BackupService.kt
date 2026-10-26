package com.ventures814.brdcrmbs.backup

import android.content.ContentResolver
import android.net.Uri
import android.util.AtomicFile
import com.ventures814.brdcrmbs.data.RoomBackupStore
import com.ventures814.brdcrmbs.reminders.*
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.json.JSONArray

data class PreparedBackup(val text: String, val filename: String)
data class RestoreResult(val remindersReconciled: Boolean)

interface BackupOperations {
    suspend fun create(): PreparedBackup
    suspend fun read(uri: Uri): BackupSnapshot
    suspend fun write(uri: Uri, backup: PreparedBackup)
    suspend fun restore(snapshot: BackupSnapshot): RestoreResult
}

/** Local SAF I/O and a recoverable Room/preferences commit; no alarms or permissions are portable. */
class BackupService(
    private val store: RoomBackupStore,
    private val settings: ReminderSettingsStore,
    private val resolver: ContentResolver,
    journalFile: File,
    private val reconcile: suspend () -> Unit,
    private val boundary: suspend (suspend () -> Unit) -> Unit = { it() },
    private val clock: Clock = Clock.systemDefaultZone(),
    private val restoreBoundary: suspend (suspend () -> Unit) -> Boolean = { action ->
        boundary { action() }
        runCatching { reconcile() }.isSuccess
    }
) : BackupOperations {
    private val mutex = Mutex()
    private val journal = AtomicFile(journalFile)
    private fun hasJournal() = journal.baseFile.exists() || File(journal.baseFile.path + ".bak").exists()

    override suspend fun create(): PreparedBackup = withContext(Dispatchers.IO) { mutex.withLock {
        var data: BackupSnapshot? = null
        boundary { recoverLocked(); data = store.snapshot(clock.instant()) }
        PreparedBackup(BackupCodec.encode(checkNotNull(data)), "brdcrmbs-backup-${LocalDate.now(clock)}.json")
    } }

    override suspend fun read(uri: Uri): BackupSnapshot = withContext(Dispatchers.IO) {
        val bytes = requireNotNull(resolver.openInputStream(uri)) { "Could not open backup." }.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= BackupCodec.MAX_BYTES) { "Backup exceeds the supported 32 MiB size." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        BackupCodec.decode(text).also(::rejectQa)
    }

    override suspend fun write(uri: Uri, backup: PreparedBackup) = withContext(Dispatchers.IO) {
        BackupCodec.decode(backup.text) // Never export an unvalidated/incomplete prepared payload.
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "Could not open destination." }.use {
            it.write(backup.text.toByteArray(Charsets.UTF_8)); it.flush()
        }
    }

    override suspend fun restore(snapshot: BackupSnapshot): RestoreResult = withContext(Dispatchers.IO) { mutex.withLock {
        snapshot.validate(); rejectQa(snapshot)
        // Once replacement starts, cancellation cannot interrupt preference compensation/commit cleanup.
        withContext(NonCancellable) {
            RestoreResult(restoreBoundary {
                recoverLocked()
                val before = settings.state.value
                val beforeApp = store.appPreferences?.state?.value ?: com.ventures814.brdcrmbs.preferences.AppPreferences()
                writeJournal(before, snapshot.reminders, digest(snapshot), beforeApp, snapshot.appPreferences)
                try { store.replace(snapshot) }
                catch (failure: Exception) {
                    // Room rolled back, including late insert/commit failures. Keep recovery evidence if this write fails.
                    settings.replaceForRestore(before)
                    store.appPreferences?.replace(beforeApp)
                    journal.delete()
                    throw failure
                }
                journal.delete()
            })
        }
    } }

    /** Called before application reminder observation/recovery starts, and before subsequent backups. */
    suspend fun recover() = withContext(Dispatchers.IO) { mutex.withLock { recoverLocked() } }

    private suspend fun recoverLocked() {
        if (!hasJournal()) return
        val saved = journal.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        fun preferences(key: String) = saved.getJSONArray(key).let { a ->
            ReminderPreferences((0 until a.length()).map { ReminderType.valueOf(a.getString(it)) }.toSet())
        }
        // SQLite rolls an interrupted transaction back. Only the exact committed target selects new choices.
        val current = store.snapshot(Instant.EPOCH, excludeQa = false)
        val side = if (digest(current) == saved.getString("target")) "after" else "before"
        settings.replaceForRestore(preferences(side))
        store.appPreferences?.replace(if (saved.has("${side}App")) BackupCodec.decodePreferences(saved.getJSONObject("${side}App"))
            else com.ventures814.brdcrmbs.preferences.AppPreferences())
        journal.delete()
    }

    private fun writeJournal(before: ReminderPreferences, after: ReminderPreferences, target: String,
        beforeApp: com.ventures814.brdcrmbs.preferences.AppPreferences, afterApp: com.ventures814.brdcrmbs.preferences.AppPreferences) {
        journal.baseFile.parentFile?.mkdirs()
        val text = JSONObject().put("target", target)
            .put("before", JSONArray(before.enabled.sortedBy { it.name }.map { it.name }))
            .put("after", JSONArray(after.enabled.sortedBy { it.name }.map { it.name }))
            .put("beforeApp", BackupCodec.encodePreferences(beforeApp)).put("afterApp", BackupCodec.encodePreferences(afterApp)).toString()
        val output = journal.startWrite()
        try { output.write(text.toByteArray(Charsets.UTF_8)); journal.finishWrite(output) }
        catch (failure: Exception) { journal.failWrite(output); throw failure }
    }

    private fun digest(snapshot: BackupSnapshot): String {
        val canonical = BackupCodec.encode(snapshot.copy(createdAt = Instant.EPOCH, reminders = ReminderPreferences(), appVersion = 1, appPreferences = com.ventures814.brdcrmbs.preferences.AppPreferences()))
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun rejectQa(s: BackupSnapshot) {
        val ids = s.plans.fasting.map { it.id } + s.plans.noFast.map { it.id } + s.plans.meals.map { it.id } + s.plans.activities.map { it.id }
        require(ids.none(::isBackupQaOwned)) { "This file contains developer test records and is not a user backup." }
    }
}
