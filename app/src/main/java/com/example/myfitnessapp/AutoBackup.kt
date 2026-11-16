package com.example.myfitnessapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// Automatic backups. Once the user picks a folder on the phone, a backup is written there every
// day, and shortly after each workout is saved. The newest few are kept. Files in that folder
// belong to the user, not the app, so they're still there if the app is removed.
// ---------------------------------------------------------------------------

const val BACKUPS_KEPT = 7

// Which backup files to delete so only the newest `keep` remain. Files that aren't backups are
// never touched.
fun backupsToDelete(names: List<String>, keep: Int = BACKUPS_KEPT): List<String> =
    names.mapNotNull { name -> backupDateOf(name)?.let { name to it } }
        .sortedByDescending { it.second }
        .drop(keep)
        .map { it.first }

object AutoBackup {
    private const val KEY_FOLDER = "backup_folder"
    private const val KEY_LAST = "last_backup"
    private const val KEY_ERROR = "last_backup_error"
    private const val DAILY_WORK = "auto-backup-daily"
    private const val SOON_WORK = "auto-backup-soon"

    // What Settings shows: the folder's name, the last backup's time, and any problem.
    var folderName by mutableStateOf<String?>(null)
        private set
    var lastBackup by mutableStateOf<Long?>(null)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    private fun folderUri(context: Context): Uri? = prefs(context).getString(KEY_FOLDER, null)?.let(Uri::parse)

    // Reads what's saved, for Settings. Called when the app starts.
    fun load(context: Context) {
        val prefs = prefs(context)
        folderName = folderUri(context)?.let { DocumentFile.fromTreeUri(context, it)?.name ?: "Chosen folder" }
        lastBackup = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 }
        lastError = prefs.getString(KEY_ERROR, null)
        // The daily backup is set up again, in case the phone was restarted or the app updated.
        if (folderName != null) scheduleDaily(context)
    }

    // Starts backing up into this folder: daily, plus one right away.
    fun setFolder(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs(context).edit { putString(KEY_FOLDER, uri.toString()); remove(KEY_ERROR) }
        load(context)
        backupSoon(context, delayMinutes = 0)
    }

    // Stops automatic backups. The backups already in the folder stay there.
    fun turnOff(context: Context) {
        folderUri(context)?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        prefs(context).edit { remove(KEY_FOLDER); remove(KEY_ERROR) }
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(SOON_WORK)
        load(context)
    }

    private fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    // A backup in a minute or so, such as after a workout is saved. Several in a row become one.
    fun backupSoon(context: Context, delayMinutes: Long = 1) {
        if (folderUri(context) == null) return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SOON_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    // Writes today's backup into the folder (replacing an earlier one from today), then removes
    // all but the newest few. Returns false, and records why, if it couldn't.
    suspend fun run(context: Context): Boolean = withContext(Dispatchers.IO) {
        val uri = folderUri(context) ?: return@withContext true
        val error = try {
            val folder = DocumentFile.fromTreeUri(context, uri)
            if (folder == null || !folder.canWrite()) {
                "The backup folder can't be reached. Choose it again in Settings."
            } else {
                val name = backupFileName()
                folder.findFile(name)?.delete()
                val file = folder.createFile("application/zip", name)
                    ?: throw BackupException("Couldn't create a file in the backup folder.")
                context.contentResolver.openOutputStream(file.uri)?.use { writeBackup(context, it) }
                    ?: throw BackupException("Couldn't write to the backup folder.")

                val files = folder.listFiles().filter { it.name != null }
                val old = backupsToDelete(files.map { it.name!! }).toSet()
                files.filter { it.name in old }.forEach { it.delete() }
                null
            }
        } catch (e: Exception) {
            e.message ?: "The backup couldn't be saved."
        }

        val now = System.currentTimeMillis()
        prefs(context).edit {
            if (error == null) { putLong(KEY_LAST, now); remove(KEY_ERROR) } else putString(KEY_ERROR, error)
        }
        withContext(Dispatchers.Main) {
            if (error == null) lastBackup = now
            lastError = error
        }
        error == null
    }
}

// Runs a backup in the background, when WorkManager decides it's time.
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (AutoBackup.run(applicationContext)) Result.success() else Result.retry()
}
