package com.example.myfitnessapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Shown when Finish finds sets that aren't finished. Rather than only saying so, it offers ways
// on: tick them all (when each already has its numbers), remove them, or go back and fill them in.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnfinishedSetsDialog(
    incomplete: IncompleteSets,
    onTickAll: () -> Unit,
    onRemove: () -> Unit,
    onGoBack: () -> Unit
) {
    val canTickAll = incomplete.onlyUnticked
    BasicAlertDialog(onDismissRequest = onGoBack) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 10.dp)) {
                Text("Some sets aren't finished", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (canTickAll) {
                        "These sets aren't ticked off:"
                    } else {
                        "These sets need ticking off, and some are missing their reps or weight " +
                                "(outlined in red):"
                    } + "\n\n" + incomplete.byExercise.joinToString("\n") { (exercise, sets) ->
                        "• ${exercise.name} — ${sets.size} ${if (sets.size == 1) "set" else "sets"}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                // The choices are long, so they're stacked, lined up on the right.
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (canTickAll) {
                        TextButton(onClick = onTickAll) { Text("Tick all and finish") }
                    }
                    TextButton(onClick = onRemove) {
                        Text("Remove unfinished sets and finish", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onGoBack) { Text("Go back") }
                }
            }
        }
    }
}
