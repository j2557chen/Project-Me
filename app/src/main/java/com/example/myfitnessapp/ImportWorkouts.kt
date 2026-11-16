package com.example.myfitnessapp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The apps workouts can be imported from. Only Hevy so far.
enum class ImportSource(val label: String, val instructions: String) {
    HEVY("Hevy", ".csv file exported from Hevy")
}

// Where an import is up to.
private sealed interface ImportState {
    data object Idle : ImportState
    data object ChoosingSource : ImportState
    data object Working : ImportState
    data class Done(val result: ImportResult) : ImportState
    data class Failed(val message: String) : ImportState
}

// The "Import Workouts" bar in Settings. Tapping it asks where from, then opens the phone's file
// picker for that app's export, then saves what's in it.
@Composable
fun ImportWorkoutsBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { WorkoutDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ImportState>(ImportState.Idle) }

    // The file picker. Some file apps call a .csv "text/comma-separated-values", or a plain file.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) { state = ImportState.Idle; return@rememberLauncherForActivityResult }
        state = ImportState.Working
        scope.launch {
            state = try {
                val workouts = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw ImportException("Couldn't open the file.")
                    parseHevyExport(text)
                }
                ImportState.Done(withContext(Dispatchers.IO) { saveImportedWorkouts(db, workouts) })
            } catch (e: ImportException) {
                ImportState.Failed(e.message ?: "Couldn't read the file.")
            } catch (e: Exception) {
                ImportState.Failed("Something went wrong reading the file, so nothing was imported.")
            }
        }
    }

    val shape = RoundedCornerShape(12.dp)
    Card(
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { state = ImportState.ChoosingSource }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Import Workouts", fontSize = AppText.heading, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }

    when (val s = state) {
        ImportState.Idle -> {}
        ImportState.ChoosingSource -> AlertDialog(
            onDismissRequest = { state = ImportState.Idle },
            // Wider than a standard dialog, so the options have room.
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(POPUP_WIDTH_FRACTION),
            title = { Text("Import From:") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Each source is a bordered, tinted button with an arrow, so it reads as tappable.
                    ImportSource.entries.forEach { source ->
                        val optionShape = RoundedCornerShape(12.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(optionShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.primary, optionShape)
                                .clickable {
                                    picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/*", "application/octet-stream"))
                                }
                                .padding(vertical = 14.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source.label,
                                    fontSize = AppText.heading,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = source.instructions,
                                    fontSize = AppText.small,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { state = ImportState.Idle }) { Text("Cancel") } }
        )
        ImportState.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Importing") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Reading your workouts…")
                }
            },
            confirmButton = {}
        )
        is ImportState.Done -> AlertDialog(
            onDismissRequest = { state = ImportState.Idle },
            title = { Text("Import finished") },
            text = {
                val (imported, already) = s.result
                Text(
                    (if (imported == 1) "Imported 1 workout." else "Imported $imported workouts.") +
                            if (already > 0) " $already already in the app were left as they were." else ""
                )
            },
            confirmButton = { TextButton(onClick = { state = ImportState.Idle }) { Text("OK") } }
        )
        is ImportState.Failed -> AlertDialog(
            onDismissRequest = { state = ImportState.Idle },
            title = { Text("Couldn't import") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { state = ImportState.Idle }) { Text("OK") } }
        )
    }
}
