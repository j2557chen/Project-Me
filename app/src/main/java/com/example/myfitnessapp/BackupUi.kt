package com.example.myfitnessapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Where a backup, restore or export is up to.
private sealed interface BackupState {
    data object Idle : BackupState
    data class Working(val message: String) : BackupState
    data class ConfirmRestore(val backup: UnpackedBackup) : BackupState
    data class Done(val title: String, val message: String) : BackupState
    data class Failed(val title: String, val message: String) : BackupState
}

// The Backup & Export card in Settings: Back Up Now, Restore from Backup.
@Composable
fun BackupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<BackupState>(BackupState.Idle) }
    // Restore from Backup, the same as on the welcome screen.
    val startRestore = rememberRestoreBackup()

    // Back Up Now: the phone's save picker, so it can go to Drive, Downloads or anywhere else.
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        state = BackupState.Working("Backing up…")
        scope.launch {
            state = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { writeBackup(context, it) }
                        ?: throw BackupException("Couldn't save to that place.")
                }
                BackupState.Done("Backed up", "Everything was saved. Keep the file somewhere safe, such as Google Drive.")
            } catch (e: BackupException) {
                BackupState.Failed("Couldn't back up", e.message ?: "")
            } catch (e: Exception) {
                BackupState.Failed("Couldn't back up", "Something went wrong saving the backup.")
            }
        }
    }

    // Export: every finished workout as a CSV file, wherever the save picker reaches.
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        state = BackupState.Working("Exporting…")
        scope.launch {
            state = try {
                val count = withContext(Dispatchers.IO) {
                    val dao = WorkoutDatabase.getInstance(context).workoutDao()
                    val workouts = dao.getFinishedWorkouts()
                    val csv = workoutsToCsv(workouts, dao.getAllExercises(), dao.getAllSets())
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                        ?: throw BackupException("Couldn't save to that place.")
                    workouts.size
                }
                BackupState.Done(
                    "Exported",
                    "$count workouts were saved as a CSV file. It opens in Excel or Google Sheets, " +
                        "and can be brought back in with Import Workouts."
                )
            } catch (e: Exception) {
                BackupState.Failed("Couldn't export", e.message ?: "Something went wrong saving the file.")
            }
        }
    }

    // Automatic backups: the phone's folder picker. The app is allowed to keep using the folder.
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) AutoBackup.setFolder(context, uri)
    }
    var showAutoOptions by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Backup & Export",
                fontSize = AppText.heading,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            )
            Text(
                text = "Your workouts are only stored on this phone. A backup saved outside the app keeps them safe if the app is removed.",
                fontSize = AppText.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.padding(top = 8.dp))
            val folder = AutoBackup.folderName
            BackupRow(
                title = "Automatic Backups",
                subtitle = if (folder == null) "Off. Choose a folder to back up to every day"
                else "Daily to \"$folder\" · " + (AutoBackup.lastBackup?.let { "last ${lastBackupText(it)}" } ?: "not backed up yet"),
                error = AutoBackup.lastError
            ) {
                if (folder == null) chooseFolder.launch(null) else showAutoOptions = true
            }
            HorizontalDivider()
            BackupRow("Back Up Now", "Save a backup file anywhere, such as Google Drive") {
                saveBackup.launch(backupFileName())
            }
            HorizontalDivider()
            BackupRow("Restore from Backup", "Replace all data with a backup file") {
                startRestore()
            }
            HorizontalDivider()
            BackupRow("Export Workouts (CSV)", "Every set in a spreadsheet file, laid out like Hevy's export") {
                saveCsv.launch("ProjectMe-workouts-${java.time.LocalDate.now()}.csv")
            }
        }
    }

    if (showAutoOptions) {
        AlertDialog(
            onDismissRequest = { showAutoOptions = false },
            title = { Text("Automatic Backups") },
            text = {
                Text(
                    "Every day, and shortly after each workout, a backup is saved to \"${AutoBackup.folderName}\". " +
                        "The newest $BACKUPS_KEPT are kept. To restore one, use Restore from Backup and pick it from that folder."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAutoOptions = false
                    chooseFolder.launch(null)
                }) { Text("Change Folder") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAutoOptions = false
                    AutoBackup.turnOff(context)
                }) { Text("Turn Off", color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    BackupStateDialog(state = state, onStateChange = { state = it })
}

// One tappable row in the Backup & Export card.
@Composable
private fun BackupRow(title: String, subtitle: String, error: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontSize = AppText.body)
            Text(text = subtitle, fontSize = AppText.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error != null) Text(text = error, fontSize = AppText.small, color = MaterialTheme.colorScheme.error)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

// When the last automatic backup was made, such as "today at 9:15 AM" or "Nov 8".
private fun lastBackupText(millis: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val time = java.time.Instant.ofEpochMilli(millis).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val clock = java.time.format.DateTimeFormatter.ofPattern("h:mm a").format(time)
    return when (time.toLocalDate()) {
        today -> "today at $clock"
        today.minusDays(1) -> "yesterday at $clock"
        else -> java.time.format.DateTimeFormatter.ofPattern("MMM d").format(time)
    }
}

// The pop-ups for whatever a backup, restore or export is doing: a spinner while it works, the
// question before a restore replaces anything, and how it went.
@Composable
private fun BackupStateDialog(state: BackupState, onStateChange: (BackupState) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    when (val s = state) {
        BackupState.Idle -> {}
        is BackupState.Working -> AlertDialog(
            onDismissRequest = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(s.message)
                }
            },
            confirmButton = {}
        )
        is BackupState.ConfirmRestore -> ConfirmDestructiveDialog(
            title = "Restore this backup?",
            message = "It's from ${s.backup.createdText}, with ${s.backup.workoutCount} workouts. " +
                "All the data in the app now will be replaced. A copy of it is saved first, so this can be undone.",
            confirmLabel = "Restore",
            onConfirm = {
                onStateChange(BackupState.Working("Restoring…"))
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            // The data as it is now, kept in case the restore was a mistake.
                            safetyBackupFile(context).outputStream().use { writeBackup(context, it) }
                            restoreBackup(context, s.backup)
                        }
                    } catch (e: Exception) {
                        onStateChange(BackupState.Failed("Couldn't restore", "Something went wrong, so nothing was changed."))
                    }
                }
            },
            onDismiss = {
                s.backup.folder.deleteRecursively()
                onStateChange(BackupState.Idle)
            }
        )
        is BackupState.Done -> AlertDialog(
            onDismissRequest = { onStateChange(BackupState.Idle) },
            title = { Text(s.title) },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { onStateChange(BackupState.Idle) }) { Text("OK") } }
        )
        is BackupState.Failed -> AlertDialog(
            onDismissRequest = { onStateChange(BackupState.Idle) },
            title = { Text(s.title) },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { onStateChange(BackupState.Idle) }) { Text("OK") } }
        )
    }
}

// Restoring a backup: picks a file, checks it, asks before replacing anything, then restores
// and restarts the app. Returns the function that starts it. Used by Settings and the welcome
// screen.
@Composable
fun rememberRestoreBackup(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<BackupState>(BackupState.Idle) }

    // Restore: pick a backup file, check it, then ask before replacing anything.
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        state = BackupState.Working("Reading the backup…")
        scope.launch {
            state = try {
                val backup = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { unpackBackup(context, it) }
                        ?: throw BackupException("Couldn't open the file.")
                }
                BackupState.ConfirmRestore(backup)
            } catch (e: BackupException) {
                BackupState.Failed("Couldn't restore", e.message ?: "")
            } catch (e: Exception) {
                BackupState.Failed("Couldn't restore", "This file couldn't be read as a backup.")
            }
        }
    }

    BackupStateDialog(state = state, onStateChange = { state = it })
    return { openBackup.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }
}
