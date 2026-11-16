package com.example.myfitnessapp

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit

// ---------------------------------------------------------------------------
// What a new user sees: a welcome page on the very first launch, friendly messages where a
// screen has nothing in it yet, and a word on what the Record screen's permissions are for.
// ---------------------------------------------------------------------------

object FirstRun {
    // Set on the very first launch, and cleared once the welcome page is done with. Phones that
    // had the app before this was added never have it, so they never see the welcome page.
    private const val KEY_WELCOME = "welcome_pending"
    private const val KEY_PERMISSIONS = "record_permissions_explained"

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    fun markNewInstall(context: Context) = prefs(context).edit { putBoolean(KEY_WELCOME, true) }
    fun welcomePending(context: Context) = prefs(context).getBoolean(KEY_WELCOME, false)
    fun welcomeDone(context: Context) = prefs(context).edit { remove(KEY_WELCOME) }

    fun permissionsExplained(context: Context) = prefs(context).getBoolean(KEY_PERMISSIONS, false)
    fun markPermissionsExplained(context: Context) = prefs(context).edit { putBoolean(KEY_PERMISSIONS, true) }
}

// ----- The welcome page -----

@Composable
fun WelcomeScreen(
    weightUnit: WeightUnit,
    onWeightUnitChange: (WeightUnit) -> Unit,
    onGetStarted: () -> Unit
) {
    // Restoring replaces everything and restarts the app, after which the welcome page is gone.
    val startRestore = rememberRestoreBackup()

    // A Surface gives the page the app's background and its usual text colour; the welcome page
    // comes before the rest of the app, which normally sets those.
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        AppLogo()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Project Me", fontSize = AppText.pageTitle, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Your workouts, runs and rides in one place",
            fontSize = AppText.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
            WelcomeFeature(ActivityKind.STRENGTH.icon, ActivityKind.STRENGTH.color(), "Log your lifts",
                "Sets, weights and reps, with last time's numbers ready")
            WelcomeFeature(ActivityKind.RUN.icon, ActivityKind.RUN.color(), "Record runs, walks and rides",
                "Your route, distance and pace, with GPS")
            WelcomeFeature(TrophyIcon, RecordGold.solid(), "Watch your progress",
                "Records, streaks and charts over time")
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Weights in",
            fontSize = AppText.small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            WeightUnit.entries.forEachIndexed { index, unit ->
                SegmentedButton(
                    selected = unit == weightUnit,
                    onClick = { onWeightUnitChange(unit) },
                    shape = SegmentedButtonDefaults.itemShape(index, WeightUnit.entries.size)
                ) {
                    Text(if (unit == WeightUnit.LBS) "Pounds (lbs)" else "Kilograms (kg)")
                }
            }
        }

        // Pushes the buttons to the bottom on a tall screen.
        Spacer(modifier = Modifier
            .weight(1f)
            .height(32.dp))
        Button(
            onClick = onGetStarted,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Get started", fontSize = AppText.body, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = startRestore, modifier = Modifier.padding(top = 4.dp)) {
            Text("Restore from a backup")
        }
    }
    }
}

// The app icon, as it looks on the home screen: the stopwatch on its dark circle.
@Composable
private fun AppLogo() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(Color(0xFF1D1B26))
    ) {
        // The icon's drawing is made for a 108-unit canvas, of which the middle 72 show on the
        // home screen, so it's drawn half as big again as the circle and the edges are cut off.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Project Me",
            modifier = Modifier.requiredSize(132.dp)
        )
    }
}

@Composable
private fun WelcomeFeature(icon: ImageVector, color: Color, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(color, CircleShape)
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF1B1A1F), modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = title, fontSize = AppText.body, fontWeight = FontWeight.SemiBold)
            Text(text = detail, fontSize = AppText.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ----- Empty screens -----

// A friendly message in a card where a list would be: a few activity icons, a heading, a
// sentence, and buttons to get going.
@Composable
fun EmptyStateCard(
    kinds: List<ActivityKind>,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    buttons: @Composable () -> Unit = {}
) {
    Card(modifier = modifier) {
        EmptyStateContent(kinds, title, message, ringColor = CardDefaults.cardColors().containerColor, buttons = buttons)
    }
}

@Composable
private fun EmptyStateContent(
    kinds: List<ActivityKind>,
    title: String,
    message: String,
    // The colour behind the icons, which shows as a thin ring that keeps them apart.
    ringColor: Color,
    buttons: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        ActivityIcons(kinds = kinds, ringColor = ringColor, size = 40.dp)
        Text(text = title, fontSize = AppText.subheading, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            text = message,
            fontSize = AppText.small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            buttons()
        }
    }
}

// The filled and outlined buttons used in the empty-state cards.
@Composable
fun EmptyStateButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    if (filled) {
        Button(onClick = onClick, shape = shape) { Text(text, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(onClick = onClick, shape = shape) { Text(text) }
    }
}

// Home, before the first workout: the two ways to begin.
@Composable
fun FirstWorkoutCard(onStartWorkout: () -> Unit, onRecord: () -> Unit, modifier: Modifier = Modifier) {
    EmptyStateCard(
        kinds = listOf(ActivityKind.STRENGTH, ActivityKind.RUN, ActivityKind.RIDE),
        title = "Let's get your first workout in",
        message = "Log a gym session, or record a run, walk or ride. Your streak, stats and muscle map fill in from here.",
        modifier = modifier
    ) {
        EmptyStateButton("Start a workout", filled = true, onClick = onStartWorkout)
        EmptyStateButton("Record", filled = false, onClick = onRecord)
    }
}

// History, with nothing in it yet.
@Composable
fun NoWorkoutsCard(onStartWorkout: () -> Unit, modifier: Modifier = Modifier) {
    EmptyStateCard(
        kinds = listOf(ActivityKind.WALK),
        title = "No workouts yet",
        message = "Finished workouts and recorded activities appear here, with a dot on the calendar for each day you train.",
        modifier = modifier
    ) {
        EmptyStateButton("Start a workout", filled = true, onClick = onStartWorkout)
    }
}

// A running workout with no exercises in it yet. No card: it sits on the screen above the
// Add Exercise button.
@Composable
fun FirstExerciseNote(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        EmptyStateContent(
            kinds = listOf(ActivityKind.STRENGTH),
            title = "Add your first exercise",
            message = "Pick from over 400 exercises, or make your own. Last time's numbers fill in for you.",
            // Not on a card, so the ring is the screen's own colour.
            ringColor = MaterialTheme.colorScheme.background,
            buttons = {}
        )
    }
}

// ----- Before the Record screen's permission prompts -----

// Says what each permission Android is about to ask for is used for. Shown the first time a
// recording is started without them. Continue goes on to Android's own prompts.
@Composable
fun RecordPermissionsDialog(askActivity: Boolean, onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before you record") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Android will ask for a few permissions next. Here's what each one is for.")
                PermissionLine(
                    title = "Location",
                    needed = true,
                    detail = "Maps your route and measures distance and pace, including with the screen off."
                )
                PermissionLine(
                    title = "Notifications",
                    needed = false,
                    detail = "Shows the recording and its timer on your lock screen."
                )
                if (askActivity) {
                    PermissionLine(
                        title = "Physical activity",
                        needed = false,
                        detail = "Feels your steps on walks and runs, to pause when you stop."
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

@Composable
private fun PermissionLine(title: String, needed: Boolean, detail: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            if (needed) {
                Text(
                    text = "NEEDED",
                    fontSize = AppText.caption,
                    fontWeight = FontWeight.Bold,
                    color = RecordGold.color(),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        Text(text = detail, fontSize = AppText.small)
    }
}
