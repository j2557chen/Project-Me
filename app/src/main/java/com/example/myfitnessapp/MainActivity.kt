// A "package" groups related code together. It must match your project's package name.
package com.example.myfitnessapp

// "import" lets us use code that lives in other files/libraries by its short name.
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import com.example.myfitnessapp.ui.theme.Typography as AppTypography
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import android.app.UiModeManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import java.time.LocalDate
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.flowOf
import androidx.core.content.edit
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.material.icons.filled.LocationOn
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight

// An "enum class" defines a fixed set of options. Here, the app has four screens.
// Each option holds a text label and an icon.
enum class Screen(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Workout("Workout", Icons.Filled.PlayArrow),
    Record(label="Record", Icons.Filled.LocationOn),
    History("History", Icons.Filled.DateRange),
    // Settings opens from the gear on the Profile screen.
    Profile("Profile", Icons.Filled.Person)
}

// MainActivity is the entry point Android launches when your app opens.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A rest countdown and the timers set in a running workout come back after a restart.
        RestTimer.load(this)

        // SharedPreferences is a small file of key/value pairs stored on the device.
        // It survives the app being closed, which is how we make the setting persist.
        val prefs = getSharedPreferences(SETTINGS_FILE, MODE_PRIVATE)

        // UiModeManager lets the app choose its own night mode (API 31+, Android 12).
        // Android saves this choice, and uses it to pick the dark or light XML theme
        // (and splash screen) the next time the app starts.
        val uiModeManager = getSystemService(UiModeManager::class.java)

        // First launch only: nothing has been saved yet, so make dark mode the default.
        // prefs.contains(...) checks whether a value has been saved under that key.
        if (!prefs.contains("dark_mode")) {
            prefs.edit { putBoolean("dark_mode", true) }
            // A new install, so the welcome page is shown until it's been through.
            FirstRun.markNewInstall(this)
            uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
        }

        // Lets the app draw behind the system bars on every Android version. Android 15 and
        // newer do this regardless. Calling it makes Android 12 to 14 behave the same, so
        // the status bar block below looks identical everywhere.
        enableEdgeToEdge()

        setContent {
            // Load the saved value. The second argument (true) is the fallback
            // if nothing is saved, so the app defaults to dark.
            var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }
            // Read by every screen through LocalWeightUnit.
            var weightUnit by remember { mutableStateOf(WeightUnitPrefs.load(this)) }
            // Whether Home and Profile show the body weight log.
            remember { TrackWeight.load(this) }
            // The automatic backup folder, and the daily backup set up again.
            remember { AutoBackup.load(this) }
            // The welcome page, on a new install until "Get started" is tapped.
            var showWelcome by remember { mutableStateOf(FirstRun.welcomePending(this)) }
            // Health Connect's settings open the app this way to ask how health data is used.
            var showHealthDataUse by remember {
                mutableStateOf(
                    intent?.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
                            intent?.action == "android.intent.action.VIEW_PERMISSION_USAGE"
                )
            }

            MaterialTheme(
                colorScheme = if (darkMode) darkColorScheme() else lightColorScheme(),
                // Every text style in the app's font, Outfit (see ui/theme/Type.kt).
                typography = AppTypography
            ) {
                CompositionLocalProvider(LocalWeightUnit provides weightUnit) {
                    if (showWelcome) WelcomeScreen(
                        weightUnit = weightUnit,
                        onWeightUnitChange = { unit ->
                            weightUnit = unit
                            WeightUnitPrefs.save(this@MainActivity, unit)
                        },
                        onGetStarted = {
                            FirstRun.welcomeDone(this@MainActivity)
                            showWelcome = false
                        }
                    ) else FitnessApp(
                        darkMode = darkMode,
                        onDarkModeChange = { enabled ->
                            darkMode = enabled
                            prefs.edit { putBoolean("dark_mode", enabled) }
                            // Tell Android which theme to use for this app, including at startup.
                            uiModeManager.setApplicationNightMode(
                                if (enabled) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
                            )
                        },
                        onWeightUnitChange = { unit ->
                            weightUnit = unit
                            WeightUnitPrefs.save(this@MainActivity, unit)
                        }
                    )
                    if (showHealthDataUse) HealthDataUseDialog(onDismiss = { showHealthDataUse = false })
                }
            }
        }
    }
}

@Composable
fun FitnessApp(
    darkMode: Boolean,
    // "(Boolean) -> Unit" is a function type: takes a Boolean, returns nothing (Unit).
    onDarkModeChange: (Boolean) -> Unit,
    onWeightUnitChange: (WeightUnit) -> Unit
) {
    // Remembers which screen is selected. Changing it makes Compose redraw the UI.
    var currentScreen by rememberSaveable { mutableStateOf(Screen.Home) }
    // The id of the workout open in History's summary screen, or null for the list.
    var selectedWorkoutId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Kept here, so History's calendar keeps its month and selection while a workout is open.
    val calendarSelection = rememberSaveable(saver = CalendarSelection.Saver) { CalendarSelection() }
    // History's scroll position, kept here so coming back from a workout returns to the same place.
    val historyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    // Each time the app opens with no age, height or weight saved, a pop-up asks for them.
    val appContext = LocalContext.current
    var showAboutYouPrompt by rememberSaveable { mutableStateOf(isAboutYouEmpty(appContext)) }
    // The user's own exercises, kept in the library for every screen that lists exercises.
    LaunchedEffect(Unit) {
        WorkoutDatabase.getInstance(appContext).workoutDao().observeCustomExercises()
            .collect { ExerciseLibrary.custom = it }
    }
    // Each time the app opens, quietly bring in any new watch workouts if Health Connect is connected.
    LaunchedEffect(Unit) {
        try {
            if (healthConnectStatus(appContext) == HealthConnectStatus.CONNECTED) {
                syncWatchWorkouts(appContext, WorkoutDatabase.getInstance(appContext))
            }
        } catch (e: Exception) {
            // A failed background sync is tried again next time; Sync Now shows any error.
        }
    }
    // Leaving the app closes the keyboard, so it isn't open on coming back.
    ClearFocusWhenAppStops()
    // Works out the fastest times of recordings saved before they were stored, once, in the
    // background, so the calendar's medals and records don't have to.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { BestEffortCache.catchUp(WorkoutDatabase.getInstance(appContext).workoutDao()) }
    }
    // Whether Settings is open over the Profile screen.
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // The phone's back gesture closes Settings, back to Profile.
    BackHandler(enabled = currentScreen == Screen.Profile && showSettings) { showSettings = false }

    // The active workout is read here, because both the tab label and the Workout screen need it.
    val context = LocalContext.current
    val dao = remember { WorkoutDatabase.getInstance(context).workoutDao() }
    val activeFlow = remember { dao.observeActiveWorkout() }
    val activeWorkout by activeFlow.collectAsState(initial = null)

    Scaffold(
        topBar = {
            // A solid block exactly as tall as the status bar, in the app's own color.
            // It sits behind the clock and icons, so nothing on the screen can show through
            // the bar. Because it's part of the Scaffold, it applies to every screen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(headerBarColor())
            )
        },
        bottomBar = {
            NavigationBar(containerColor = headerBarColor()) {
                // Builds one tab for every entry in the Screen enum.
                Screen.entries.forEach { screen ->
                    // The Workout tab changes its name while a workout is running.
                    val tabLabel = if (screen == Screen.Workout && activeWorkout != null) {
                        "In Progress"
                    } else {
                        screen.label
                    }

                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = {
                            // Tapping any tab closes an open summary, so tapping History
                            // while viewing one takes you back to the list.
                            selectedWorkoutId = null
                            showSettings = false
                            currentScreen = screen
                        },
                        icon = { Icon(screen.icon, contentDescription = tabLabel) },
                        // One line, shrinking a little if it has to (such as at a large font
                        // size), so a long name is never cut off or wrapped.
                        label = {
                            val style = LocalTextStyle.current
                            BasicText(
                                text = tabLabel,
                                maxLines = 1,
                                style = style.copy(color = LocalContentColor.current, textAlign = TextAlign.Center),
                                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize)
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Apply the padding so content isn't hidden behind the bottom bar. The screen also
        // shrinks above the keyboard when it's open (the bottom bar's space isn't counted twice),
        // so lists can scroll what's being typed into above it.
        val contentModifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding()
            // Tapping empty space closes the keyboard.
            .clearFocusOnBackgroundTap()

        // "when" is Kotlin's version of a switch statement. It picks which screen
        // to show based on the value of currentScreen.
        // Because Screen is an enum, Kotlin makes sure we handle every entry.
        when (currentScreen) {
            // The Workout screen now has its own composable with buttons.
            Screen.Workout -> WorkoutScreen(activeWorkout = activeWorkout, modifier = contentModifier)
            // The other screens still just show their name.
            Screen.Home -> HomeScreen(
                onStartWorkout = { currentScreen = Screen.Workout },
                onRecord = { currentScreen = Screen.Record },
                modifier = contentModifier
            )
            // The list, or the workout opened from it, sliding between the two.
            Screen.History -> AnimatedContent(
                targetState = selectedWorkoutId,
                transitionSpec = { screenSlideTransition(forward = initialState == null) },
                label = "history",
                modifier = contentModifier
            ) { openId ->
                // A solid background, so the screen underneath never shows through mid-slide.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (openId == null) {
                        HistoryScreen(
                            calendarSelection = calendarSelection,
                            listState = historyListState,
                            onOpenWorkout = { selectedWorkoutId = it },
                            onStartWorkout = { currentScreen = Screen.Workout }
                        )
                    } else {
                        WorkoutDetailScreen(
                            workoutId = openId,
                            onBack = { selectedWorkoutId = null }
                        )
                    }
                }
            }
            // Profile, or Settings when its gear has been tapped.
            Screen.Profile -> if (showSettings) {
                SettingsScreen(
                    darkMode = darkMode,
                    onDarkModeChange = onDarkModeChange,
                    onWeightUnitChange = onWeightUnitChange,
                    onBack = { showSettings = false },
                    modifier = contentModifier
                )
            } else {
                ProfileScreen(onOpenSettings = { showSettings = true }, modifier = contentModifier)
            }
            Screen.Record -> RecordScreen(modifier = contentModifier)
        }

        if (showAboutYouPrompt) {
            // Remembered for this launch only, so it asks again next time if still empty.
            AboutYouPrompt(onDone = { showAboutYouPrompt = false })
        }
    }
}

private val START_BUTTON_WIDTH = 262.5.dp

// Buttons in the same colour as the cards on Home and Profile.
@Composable
private fun cardButtonColors() = ButtonDefaults.buttonColors(
    containerColor = CardDefaults.cardColors().containerColor,
    contentColor = MaterialTheme.colorScheme.onSurface
)

// The Workout screen has two looks: the start screen (title + three buttons, centered)
// and the in-progress screen (title at the top, then a timer, the exercise button,
// and the list of exercises). A single number, "progress", blends between them:
// 0 = start screen, 1 = in progress.
@Composable
fun WorkoutScreen(activeWorkout: Workout?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // We keep the database itself (db) as well as the DAO, because adding an exercise
    // and its first set needs a transaction, which is a feature of the database.
    val db = remember { WorkoutDatabase.getInstance(context) }
    val dao = remember { db.workoutDao() }
    val scope = rememberCoroutineScope()

    // animateFloatAsState glides smoothly from the current number to the target whenever
    // the target changes. When the screen first appears it starts AT the target, so
    // opening the tab during a workout doesn't replay the animation.
    val progress by animateFloatAsState(
        targetValue = if (activeWorkout != null) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "workoutProgress"
    )

    // Remembers the most recent workout, so the timer and exercises don't vanish
    // while the screen is fading back to the start screen after ending a workout.
    var shownWorkout by remember { mutableStateOf(activeWorkout) }
    // The "deleted" message with its Undo button, at the bottom.
    val undoHost = rememberUndoHost()
    if (activeWorkout != null) shownWorkout = activeWorkout
    val shown = shownWorkout

    // The exercises for the workout being shown. remember(shown?.id) rebuilds the
    // Flow only when the workout changes. With no workout, we use an empty list.
    val exercisesFlow = remember(shown?.id) {
        if (shown != null) dao.observeExercises(shown.id) else flowOf(emptyList())
    }
    val loadedExercises by exercisesFlow.collectAsState(initial = null)
    val exercises = loadedExercises.orEmpty()
    // False until the database answers, so a workout with exercises doesn't flash as empty.
    val exercisesLoaded = loadedExercises != null

    // Every set of every exercise in this workout, as one list.
    // Each exercise card picks out its own sets from it.
    val setsFlow = remember(shown?.id) {
        if (shown != null) dao.observeSetsForWorkout(shown.id) else flowOf(emptyList())
    }
    val sets by setsFlow.collectAsState(initial = emptyList())

    // Which pop-ups are open.
    var showAddDialog by remember { mutableStateOf(false) }
    var showReorderDialog by remember { mutableStateOf(false) }
    var showProgramPicker by remember { mutableStateOf(false) }
    var showDiscardWorkoutDialog by remember { mutableStateOf(false) }
    // After a save is stopped by incomplete sets: their empty boxes are outlined in red until
    // filled in, and the pop-up lists them with ways on (null once it's closed).
    var highlightIncomplete by remember(shown?.id) { mutableStateOf(false) }
    var unfinished by remember { mutableStateOf<IncompleteSets?>(null) }
    // The pop-up for records the finished workout beat (null when there's none to show).
    var celebration by remember { mutableStateOf<Celebration?>(null) }
    val weightUnit = LocalWeightUnit.current
    // The program editor, and the program it's editing (null for a new one).
    var editorOpen by remember { mutableStateOf(false) }
    var editorProgramId by remember { mutableStateOf<Long?>(null) }

    // The records screens, opened from View Exercises: the list of exercises, then one
    // exercise's records.
    var showExercises by rememberSaveable { mutableStateOf(false) }
    var openExercise by rememberSaveable { mutableStateOf<String?>(null) }
    openExercise?.let { name ->
        ExerciseRecordsScreen(
            name = name,
            onBack = { openExercise = null },
            // A custom exercise can be renamed from its screen, which then follows the new name.
            onRenamed = { openExercise = it },
            modifier = modifier
        )
        return
    }
    if (showExercises) {
        ExerciseListScreen(onOpen = { openExercise = it }, onBack = { showExercises = false }, modifier = modifier)
        return
    }

    // The program editor takes over the whole screen while it's open.
    if (editorOpen) {
        ProgramEditorScreen(
            programId = editorProgramId,
            onClose = { editorOpen = false },
            modifier = modifier
        )
        return
    }

    // Saves the workout, after ticking off or removing any sets given.
    fun saveWorkout(workout: Workout, tickSetIds: List<Long> = emptyList(), removeSets: List<ExerciseSet> = emptyList()) {
        // Nothing deleted can come back once the workout is saved, and the rest timers end with it.
        undoHost.dismiss()
        RestTimer.clearAll(context)
        scope.launch {
            finishWorkout(db, workout.id, tickSetIds, removeSets)
            // The new workout goes into the backup folder shortly.
            AutoBackup.backupSoon(context)
            // Only now, with the workout saved, is it checked for new records.
            val records = workoutRecords(context, workout.id, weightUnit)
            if (records.isNotEmpty()) {
                celebration = Celebration("From ${workout.displayName()}, just now", records)
            }
        }
    }

    // Used by both Finish and Finish Workout. An unchecked or incomplete set stops the save: what
    // needs filling in turns red, and a pop-up offers to tick or remove those sets instead.
    fun trySaveWorkout(workout: Workout) {
        val incomplete = incompleteSets(exercises, sets)
        if (incomplete.byExercise.isEmpty()) {
            saveWorkout(workout)
        } else {
            highlightIncomplete = true
            unfinished = incomplete
        }
    }

    // Box lets us place the Finish button in the top right corner without
    // affecting the centered layout of the Column.
    Box(modifier = modifier.fillMaxSize()) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Two weighted areas share the empty space above and below the content.
            // Equal weights centers it. Shrinking the top weight toward zero pushes the
            // content up to the top, and because the weight animates, the title slides.
            Spacer(modifier = Modifier.weight((1f - 0.999f * progress).coerceAtLeast(0.001f)))

            // "Workout" on the start screen; the workout's own name, which can be edited, once
            // one is running.
            // During a workout, the name sits level with the Finish button in the corner.
            val titleModifier = Modifier.padding(top = (14f * progress).dp)
            if (shown != null && progress > 0.5f) {
                // Kept clear of the Finish button in the corner. There's nothing on the left,
                // so a long name can use that side too.
                CenteredBetween(startRoom = 16.dp, endRoom = 88.dp, modifier = titleModifier.fillMaxWidth()) {
                    WorkoutNameTitle(workout = shown, fontSize = AppText.heading)
                }
            } else {
                Text(text = Screen.Workout.label, fontSize = AppText.pageTitle, modifier = titleModifier)
            }

            // The area under the title that holds both looks. Its height shrinks from
            // 280dp to 50dp as the workout starts, leaving just the timer, so the exercise
            // list starts close under it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((280f - 230f * progress).dp)
            ) {
                // START SCREEN: fades out as progress goes from 0 to 1.
                // It's removed from the screen once fully faded, so it can't be tapped.
                if (progress < 1f) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(1f - progress),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            colors = cardButtonColors(),
                            onClick = {
                                scope.launch {
                                    dao.insertWorkout(Workout(startTime = System.currentTimeMillis()))
                                }
                            },
                            enabled = activeWorkout == null,
                            modifier = Modifier.width(START_BUTTON_WIDTH)
                        ) {
                            Text("Start New Workout", fontSize = AppText.body)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            colors = cardButtonColors(),
                            onClick = { showProgramPicker = true },
                            enabled = activeWorkout == null,
                            // Narrow side padding so the longer label fits on one line.
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.width(START_BUTTON_WIDTH)
                        ) {
                            Text("Start Workout from Program", fontSize = AppText.body, maxLines = 1)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            colors = cardButtonColors(),
                            onClick = {
                                editorProgramId = null
                                editorOpen = true
                            },
                            modifier = Modifier.width(START_BUTTON_WIDTH)
                        ) {
                            Text("Create New Program", fontSize = AppText.body)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            colors = cardButtonColors(),
                            onClick = { showExercises = true },
                            modifier = Modifier.width(START_BUTTON_WIDTH)
                        ) {
                            Text("View Exercises", fontSize = AppText.body)
                        }
                    }
                }

                // IN-PROGRESS SCREEN: fades in as progress goes from 0 to 1.
                if (progress > 0f && shown != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(progress),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // The persistent timer, based on the workout's saved start time.
                        WorkoutTimer(startTime = shown.startTime)
                    }
                }
            }

            // This flexible area takes whatever space is left. On the start screen it's
            // empty, so it simply acts as the bottom spacer that keeps things centered.
            // During a workout it holds the scrolling list of exercises.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (progress > 0f && shown != null) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(progress),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
                        // Room at the end to scroll the last buttons clear of the rest countdown.
                        contentPadding = PaddingValues(bottom = if (RestTimer.running != null) 88.dp else 16.dp)
                    ) {
                        exerciseItems(
                            exercises, sets, db, scope,
                            onReorder = { showReorderDialog = true },
                            highlightIncomplete = highlightIncomplete,
                            undoHost = undoHost,
                            restTimer = true,
                            substitute = true
                        )
                        // A new workout with nothing in it yet says what to do next.
                        if (exercisesLoaded && exercises.isEmpty()) {
                            item(key = "firstExercise") {
                                FirstExerciseNote(
                                    modifier = Modifier
                                        .fillMaxWidth(CARD_WIDTH_FRACTION)
                                        .padding(top = 40.dp)
                                )
                            }
                        }
                        // Under the last exercise: Add Exercise, then Discard and Save Workout,
                        // all outlined alike. Add Exercise spans the width of the two below it.
                        item(key = "workoutActions") {
                            val buttonShape = RoundedCornerShape(12.dp)
                            val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            val purple = MaterialTheme.colorScheme.primary
                            val red = MaterialTheme.colorScheme.error

                            Column(
                                modifier = Modifier
                                    .widthIn(min = ADD_EXERCISE_BUTTON_WIDTH)
                                    .width(IntrinsicSize.Max),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Filled in purple, so it stands out from the two outlined below.
                                AddExerciseButton(
                                    onClick = { showAddDialog = true },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Both take half the row, so Save is as wide as Discard.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                                ) {
                                    // Red, so it isn't mistaken for the usual way out.
                                    OutlinedButton(
                                        onClick = { showDiscardWorkoutDialog = true },
                                        shape = buttonShape,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = red),
                                        border = BorderStroke(1.dp, red),
                                        contentPadding = buttonPadding,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                    ) {
                                        Text("Discard Workout", fontSize = AppText.small)
                                    }

                                    // Outlined like Discard, in purple.
                                    OutlinedButton(
                                        onClick = { trySaveWorkout(shown) },
                                        shape = buttonShape,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = purple),
                                        border = BorderStroke(1.dp, purple),
                                        contentPadding = buttonPadding,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                    ) {
                                        Text("Finish Workout", fontSize = AppText.small)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }   // <- closes the Column

        // A second way to finish, always in reach at the top, styled like Finish Workout.
        if (progress > 0f && shown != null) {
            val purple = MaterialTheme.colorScheme.primary
            OutlinedButton(
                onClick = { trySaveWorkout(shown) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = purple),
                border = BorderStroke(1.dp, purple),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 12.dp)
                    .height(36.dp)
                    .alpha(progress)
            ) {
                Text("Finish", fontSize = AppText.small)
            }
        }

        // At the bottom: the "deleted" message with its Undo button, above the rest countdown.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            SnackbarHost(undoHost.snackbar)
            if (shown != null && progress > 0f) RestTimerBar()
        }

        // The pop-up only exists while showAddDialog is true.
        if (showAddDialog && shown != null) {
            ExercisePickerDialog(
                // Exercises already in this workout show as unavailable in the list.
                alreadyAdded = exercises.map { it.name }.toSet(),
                onDismiss = { showAddDialog = false },
                onSelect = { picked ->
                    // One after another, so each goes to the end of the list in the order picked.
                    scope.launch { picked.forEach { addExerciseToWorkout(db, shown.id, it) } }
                    showAddDialog = false
                }
            )
        }

        celebration?.let { RecordCelebration(it, onDismiss = { celebration = null }) }

        // Why the workout wasn't saved, and the ways on.
        unfinished?.let { incomplete ->
            UnfinishedSetsDialog(
                incomplete = incomplete,
                onTickAll = {
                    unfinished = null
                    shown?.let { saveWorkout(it, tickSetIds = incomplete.setIds) }
                },
                onRemove = {
                    unfinished = null
                    shown?.let { saveWorkout(it, removeSets = incomplete.byExercise.flatMap { (_, sets) -> sets }) }
                },
                onGoBack = { unfinished = null }
            )
        }

        // Deletes the workout with its exercises, sets, and route pictures.
        if (showDiscardWorkoutDialog && shown != null) {
            ConfirmDestructiveDialog(
                title = "Discard workout?",
                message = "This can't be undone.",
                confirmLabel = "Discard",
                onConfirm = {
                    showDiscardWorkoutDialog = false
                    RestTimer.clearAll(context)
                    scope.launch { deleteWorkoutAndImages(dao, shown.id) }
                },
                onDismiss = { showDiscardWorkoutDialog = false }
            )
        }

        if (showProgramPicker) {
            ProgramPickerDialog(
                onDismiss = { showProgramPicker = false },
                onStart = { programId ->
                    showProgramPicker = false
                    scope.launch { startWorkoutFromProgram(db, programId) }
                },
                onEdit = { programId ->
                    showProgramPicker = false
                    editorProgramId = programId
                    editorOpen = true
                }
            )
        }

        if (showReorderDialog && shown != null) {
            ReorderExercisesDialog(
                exercises = exercises,
                onDismiss = { showReorderDialog = false },
                onReorder = { orderedIds -> scope.launch { reorderExercises(db, orderedIds) } }
            )
        }
    }   // <- closes the Box
}       // <- closes the WorkoutScreen function

// Shows how long the workout has been running, as HH:MM:SS.
// The time is calculated from the workout's saved start time rather than counted
// up in memory, so it stays correct when you switch tabs or close and reopen the app.
@Composable
fun WorkoutTimer(startTime: Long) {
    // "now" is state, so changing it redraws the text.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // LaunchedEffect runs a coroutine while this composable is on screen.
    // It restarts if startTime changes, and stops automatically when the timer leaves the screen.
    LaunchedEffect(startTime) {
        while (true) {
            now = System.currentTimeMillis()
            // Waits until the next whole second of elapsed time, so the display
            // doesn't slowly drift or skip a second.
            delay((1000 - (now - startTime) % 1000).milliseconds)
        }
    }

    Text(text = formatElapsed(now - startTime), fontSize = AppText.heading)
}

// A pop-up with a search bar and a live list of matching exercises.
// Tapping a result reports it through onSelect; onDismiss is called if the user cancels.
// Exercises named in alreadyAdded are shown dimmed and can't be chosen.
@Composable
fun ExercisePickerDialog(
    alreadyAdded: Set<String>,
    onDismiss: () -> Unit,
    // The exercises chosen, in the order they were tapped.
    onSelect: (List<LibraryExercise>) -> Unit
) {
    // What the user has typed so far.
    var query by remember { mutableStateOf("") }
    // The exercises tapped so far. Tapping one again takes it back out, so a mistaken tap is
    // easy to undo; nothing is added until the Add button is pressed.
    var selected by remember { mutableStateOf(listOf<LibraryExercise>()) }
    fun toggle(exercise: LibraryExercise) {
        selected = if (selected.any { it.name == exercise.name }) selected.filter { it.name != exercise.name }
        else selected + exercise
    }

    // The exercises the user has done, for the Recent and Frequent sections.
    val context = LocalContext.current
    val historyFlow = remember { WorkoutDatabase.getInstance(context).workoutDao().observeExerciseSummaries() }
    // Null until it loads. The list waits for it, so it doesn't open on All Exercises and then
    // keep its place as Recent and Frequent arrive above it.
    val history by historyFlow.collectAsState(initial = null)

    // Recalculated as the user types, when their history loads, or when they make an exercise.
    val custom = ExerciseLibrary.custom
    val sections = remember(query, history, custom, alreadyAdded) {
        history?.let { ExerciseLibrary.pickerSections(query, it, alreadyAdded) }
    }
    val noResults = sections?.isEmpty() == true
    // The pop-up for making a new exercise, started with what was typed.
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        CustomExerciseDialog(
            existing = null,
            initialName = query,
            onDismiss = { creating = false },
            // The new exercise joins the selection, ready to be added with the others.
            onSaved = { saved ->
                creating = false
                query = ""
                selected = selected + saved.toLibraryExercise()
            }
        )
    }

    val focusManager = LocalFocusManager.current

    // Used to put the cursor in the search bar as soon as the pop-up opens.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // usePlatformDefaultWidth = false lets the pop-up be wider than Android's default.
    // decorFitsSystemWindows = false lets it see the keyboard, so it can shrink above it.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
      // The space above the keyboard. The list shrinks to fit it, so the Add button is always
      // in reach without closing the keyboard. Tapping outside the card still closes the pop-up.
      // The pop-up has its own focus, so it closes its keyboard too when the app is left.
      ClearFocusWhenAppStops()
      Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
              .fillMaxSize()
              .clickable(interactionSource = null, indication = null, onClick = onDismiss)
              .systemBarsPadding()
              .imePadding()
              .padding(vertical = 16.dp)
      ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth(POPUP_WIDTH_FRACTION)
                // Caps the height so the pop-up doesn't fill the whole screen.
                .heightIn(max = 749.dp)
                // A tap on the card's empty space closes the keyboard, not the pop-up.
                .clickable(interactionSource = null, indication = null) { focusManager.clearFocus() }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Add Exercise", fontSize = AppText.heading)

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search exercises") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (noResults) {
                    Text(
                        text = "No exercises found",
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    // weight(1f, fill = false) lets the list take the remaining space,
                    // but shrink to fit when there are only a few results.
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                      sections.orEmpty().forEach { (title, results) ->
                        if (title != null) item(key = "title:$title") {
                            Text(
                                text = title,
                                fontSize = AppText.small,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
                            )
                        }
                        // An exercise can be in more than one section, so keys include the section.
                        items(results, key = { "$title:${it.name}" }) { exercise ->
                            // True if this exercise is already part of the workout.
                            val added = exercise.name in alreadyAdded
                            // Picked to be added: outlined in a purple box.
                            val isSelected = selected.any { it.name == exercise.name }
                            val rowShape = RoundedCornerShape(8.dp)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(rowShape)
                                    .then(
                                        if (isSelected) Modifier
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                            .border(2.dp, MaterialTheme.colorScheme.primary, rowShape)
                                        else Modifier
                                    )
                                    // enabled = !added turns the tap off for added exercises.
                                    .clickable(enabled = !added) { toggle(exercise) }
                                    // Dims added exercises to 40% so they look unavailable.
                                    .alpha(if (added) 0.4f else 1f)
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text(text = exercise.name, fontSize = AppText.body)
                                // Added ones sit under their own "Already added" heading, so they
                                // keep the usual details, faded.
                                Text(
                                    text = "${exercise.group} · ${exercise.type.displayName()}" +
                                            if (exercise.custom) " · Custom" else "",
                                    fontSize = AppText.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                      }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Adds everything selected at once. Greyed out until something is picked.
                val count = selected.size
                Button(
                    onClick = { onSelect(selected) },
                    enabled = count > 0,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        when (count) {
                            0 -> "Select Exercises to Add"
                            1 -> "Add 1 Exercise"
                            else -> "Add $count Exercises"
                        },
                        fontSize = AppText.body
                    )
                }

                // Cancel on the right. With no results, a button to create one sits level with it on
                // the left: a text button like Cancel, so it has the same color and tap ripple.
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (noResults) {
                        TextButton(
                            onClick = { creating = true },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
                        ) {
                            Text("+ Create New Exercise", fontSize = AppText.body)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
      }
    }
}

// One exercise in the workout's list. Strength exercises show a row per set;
// the other types show their details (distance, duration, notes).
@Composable
fun ExerciseCard(
    exercise: Exercise,
    sets: List<ExerciseSet>,
    onAddSet: () -> Unit,
    onUpdateSet: (ExerciseSet) -> Unit,
    onDeleteSet: (ExerciseSet) -> Unit,
    onUpdateExercise: (Exercise) -> Unit,
    onDelete: () -> Unit,
    // Null makes the card not tappable.
    onClick: (() -> Unit)? = null,
    // Null hides the rearrange button.
    onReorder: (() -> Unit)? = null,
    onToggleEffort: () -> Unit = {},
    // The last time this exercise was done, shown faintly in empty boxes. Null if it never
    // was, or hasn't loaded yet.
    previous: PreviousExercise? = null,
    // The records this exercise set in its workout.
    prBadges: PrBadges = PrBadges(),
    // How much of the screen's width the card takes.
    widthFraction: Float = EXERCISE_CARD_WIDTH,
    // Outlines in red the empty boxes that stop the workout from being saved.
    highlightIncomplete: Boolean = false,
    // The colour of the bar down the left edge of an exercise in a superset; null for one on its own.
    supersetColor: Color? = null,
    // Whether this exercise is linked with the one below it into a superset.
    linkedBelow: Boolean = false,
    // Links or unlinks it with the one below. Null when there's nothing below.
    onToggleLink: (() -> Unit)? = null,
    // For the bottom exercise of a superset: unlinks it from the one above.
    onUnlinkAbove: (() -> Unit)? = null,
    // This exercise's rest length in seconds, or null for no rest timer.
    restSeconds: Int? = null,
    // Sets or clears the rest length. Null hides the rest timer button.
    onPickRest: ((Int?) -> Unit)? = null,
    // Opens the Substitute list. Null leaves Substitute out of the ⋮ menu.
    onSubstitute: (() -> Unit)? = null
) {
    val cardShape = RoundedCornerShape(12.dp)

    Card(
        shape = cardShape,
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            // clip makes the ripple follow the rounded corners.
            .then(if (onClick != null) Modifier.clip(cardShape).clickable(onClick = onClick) else Modifier)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // The name has the whole line, apart from the ⋮ menu in the corner.
                ExerciseTitle(
                    name = exercise.name,
                    modifier = Modifier.padding(end = CORNER_BUTTON_SPACE)
                )

                if (prBadges.labels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    PrBadgeRow(labels = prBadges.labels)
                }

                // A free-form note, like "elbow pain" or "triceps tired", with the rest timer
                // beside it during a workout.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onPickRest != null) {
                        RestTimerButton(
                            seconds = restSeconds,
                            onPick = onPickRest,
                            modifier = Modifier.padding(top = 4.dp, end = 10.dp)
                        )
                    }
                    ExerciseNote(
                        exercise = exercise,
                        onUpdateExercise = onUpdateExercise,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                if (exercise.type == ActivityType.STRENGTH) {
                    // The same sets from last time, by set number. Last time's effort only
                    // counts if it was the same kind (an RPE of 8 isn't an RIR of 8).
                    // A heavier weight to try, when last time's heaviest set was easy for its reps.
                    val unit = LocalWeightUnit.current
                    val increase = remember(previous, unit) {
                        previous?.let { suggestedIncrease(it.sets, it.exercise.effortType, unit) }
                    }
                    val previousSets = remember(previous, exercise.effortType) {
                        val sameEffortType = previous?.exercise?.effortType == exercise.effortType
                        previous?.sets.orEmpty().associate { set ->
                            set.setNumber to if (sameEffortType) set else set.copy(effort = null)
                        }
                    }

                    // The set rows touch, with the 4dp gap between them inside each row (2dp above
                    // and below), so the purple of finished sets runs on without breaks.
                    Column {
                        SetTableHeader(
                            effortType = exercise.effortType,
                            onToggleEffort = onToggleEffort,
                            showWeight = TrackedField.WEIGHT in exercise.type.tracks
                        )
                        Spacer(modifier = Modifier.height(2.dp))

                        // Normal sets are numbered among themselves; the others show their letter.
                        val labels = setLabels(sets.map { it.setType })
                        // key ties each row to its set, so deleting one shifts the rest up correctly.
                        sets.forEachIndexed { i, exerciseSet ->
                            key(exerciseSet.id) {
                                // Swiping a set to the left deletes it.
                                SwipeToDeleteSet(
                                    setName = if (exerciseSet.setType == SetType.NORMAL) "Set ${labels[i]}"
                                    else "${exerciseSet.setType.label} Set",
                                    onDelete = { onDeleteSet(exerciseSet) }
                                ) {
                                    SetRow(
                                        exerciseSet = exerciseSet,
                                        label = labels[i],
                                        effortType = exercise.effortType,
                                        previousSet = previousSets[exerciseSet.setNumber],
                                        // Sets done at last time's heaviest weight are suggested heavier.
                                        suggestedWeightKg = increase?.takeIf {
                                            previousSets[exerciseSet.setNumber]?.weight == it.fromKg
                                        }?.toKg,
                                        isPr = exerciseSet.id in prBadges.setIds,
                                        missing = if (highlightIncomplete) missingFields(exercise, exerciseSet)
                                        else MissingFields.NONE,
                                        // A run of finished sets is one purple block, rounded only
                                        // at its top and bottom.
                                        joinsAbove = sets.getOrNull(i - 1)?.done == true,
                                        joinsBelow = sets.getOrNull(i + 1)?.done == true,
                                        onUpdate = onUpdateSet
                                    )
                                }
                            }
                        }

                        // With the last row's 2dp, the same 8dp gap above it as before.
                        Box(modifier = Modifier.padding(top = 6.dp)) {
                            AddSetButton(onClick = onAddSet)
                        }
                    }
                } else {
                    ExerciseDetails(
                        exercise = exercise,
                        onUpdate = onUpdateExercise,
                        previous = previous?.exercise
                    )

                    exercise.routeImagePath?.let { path ->
                        Spacer(modifier = Modifier.height(12.dp))
                        RouteImage(path = path)
                    }
                }
            }

            // Everything else to do with the exercise is in the ⋮ menu in the corner.
            ExerciseMenu(
                linkedBelow = linkedBelow,
                onToggleLink = onToggleLink,
                onUnlinkAbove = onUnlinkAbove,
                onReorder = onReorder,
                onDelete = onDelete,
                onSubstitute = onSubstitute,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            // A superset's exercises share a coloured bar down their left edge.
            if (supersetColor != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(end = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(6.dp)
                            .background(supersetColor)
                    )
                }
            }
        }
    }
}

// One set: its number, boxes for effort (RPE or RIR), weight, and reps, and an X to delete it.
// Changes are saved as the user types.
@Composable
fun SetRow(
    exerciseSet: ExerciseSet,
    // The set's number, or W, D or F for the other kinds.
    label: String,
    effortType: EffortType,
    // The same set from last time, shown faintly in empty boxes.
    previousSet: ExerciseSet?,
    // A heavier weight to try than last time's, shown in place of it with an arrow.
    suggestedWeightKg: Double? = null,
    // Whether this set beat the heaviest weight or the best set volume.
    isPr: Boolean = false,
    // Boxes to outline in red because they must be filled in before saving.
    missing: MissingFields = MissingFields.NONE,
    // Whether the sets just above and below are also finished, so their shading joins this one's.
    joinsAbove: Boolean = false,
    joinsBelow: Boolean = false,
    onUpdate: (ExerciseSet) -> Unit
) {
    val missingOutline = Modifier.border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
    // Weights are saved in kilograms and shown in this unit.
    val unit = LocalWeightUnit.current

    // Each box keeps its text, so the user can type freely ("12." on the way to "12.5").
    // Keyed on the unit, so switching it shows the weight again in the new unit.
    var weightText by remember(exerciseSet.id, unit) {
        mutableStateOf(exerciseSet.weight?.let { formatNumber(unit.fromKg(it)) } ?: "")
    }
    // The exact saved weight, so editing another box doesn't save the rounded one on screen.
    var weightKg by remember(exerciseSet.id) { mutableStateOf(exerciseSet.weight) }
    var repsText by remember(exerciseSet.id) { mutableStateOf(exerciseSet.reps?.let(::formatNumber) ?: "") }
    var effortText by remember(exerciseSet.id) {
        mutableStateOf(exerciseSet.effort?.let(::formatNumber) ?: "")
    }
    // Empties the effort box as soon as the type is toggled, before the database clears it.
    var lastEffortType by remember(exerciseSet.id) { mutableStateOf(effortType) }
    if (lastEffortType != effortType) {
        lastEffortType = effortType
        effortText = ""
    }

    // Whether it's ticked off. Kept here too, so ticking and typing save together.
    var done by remember(exerciseSet.id) { mutableStateOf(exerciseSet.done) }
    // Normal, warm-up, drop or failure, picked from the set number's menu.
    var setType by remember(exerciseSet.id) { mutableStateOf(exerciseSet.setType) }

    // Every box is saved together, so one can never overwrite another with an old value.
    fun save() {
        onUpdate(
            exerciseSet.copy(
                weight = weightKg,
                reps = repsText.toDoubleOrNull(),
                effort = effortText.toDoubleOrNull(),
                done = done,
                setType = setType
            )
        )
    }

    // Ticking a set fills any empty box with last time's value (the faint number in it).
    fun setDone(checked: Boolean) {
        if (checked && previousSet != null) {
            if (effortText.isEmpty()) previousSet.effort?.let { effortText = formatNumber(it) }
            if (weightText.isEmpty()) (suggestedWeightKg ?: previousSet.weight)?.let {
                weightKg = it
                weightText = formatNumber(unit.fromKg(it))
            }
            if (repsText.isEmpty()) previousSet.reps?.let { repsText = formatNumber(it) }
        }
        done = checked
        save()
    }

    // When a box is tapped, the whole row is scrolled into view once the keyboard has opened,
    // so the keyboard doesn't cover the set being filled in.
    val rowInView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val keepInView = Modifier.onFocusEvent { focus ->
        if (focus.isFocused) scope.launch {
            delay(350)
            rowInView.bringIntoView()
        }
    }

    // A ticked set is shaded purple across the row's full height, so neighbouring finished sets
    // join into one block. Corners are only rounded where the block starts and ends.
    val corner = 8.dp
    val shape = RoundedCornerShape(
        topStart = if (joinsAbove) 0.dp else corner,
        topEnd = if (joinsAbove) 0.dp else corner,
        bottomStart = if (joinsBelow) 0.dp else corner,
        bottomEnd = if (joinsBelow) 0.dp else corner
    )
    Box(
        modifier = Modifier
            .bringIntoViewRequester(rowInView)
            .background(
                // The same purple as the Add Exercise button.
                if (done) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape
            )
            .padding(vertical = 2.dp)
    ) {
    SetTableRow {
        SetNumberCell(
            label = label,
            type = setType,
            isPr = isPr,
            onTypeChange = {
                setType = it
                save()
            }
        )

        SetCell {
            CompactNumberField(
                value = effortText,
                onValueChange = { input ->
                    if (isValidEffortInput(effortType, input)) {
                        effortText = input
                        save()
                    }
                },
                keyboardType = effortType.keyboardType,
                placeholder = previousSet?.effort?.let(::formatNumber),
                modifier = SetBoxModifier.then(keepInView)
            )
        }

        WeightColumn {
            val weightPlaceholder = (suggestedWeightKg ?: previousSet?.weight)?.let { formatNumber(unit.fromKg(it)) }
            val weightAccent = if (suggestedWeightKg != null) "↑" else null
            CompactNumberField(
                value = weightText,
                onValueChange = { input ->
                    if (isWeightInput(input)) {
                        weightText = input
                        weightKg = input.toDoubleOrNull()?.let(unit::toKg)
                        save()
                    }
                },
                keyboardType = KeyboardType.Decimal,
                placeholder = weightPlaceholder,
                placeholderAccent = weightAccent,
                modifier = Modifier
                    .fillMaxHeight()
                    // From the 4th digit, the box widens to the left to fit the weight. The
                    // faint suggestion counts too while the box is empty.
                    .growLeftToFit(
                        text = weightText.ifEmpty { (weightAccent ?: "") + (weightPlaceholder ?: "") },
                        baseWidth = WEIGHT_BOX_WIDTH
                    )
                    .then(if (missing.weight) missingOutline else Modifier)
                    .then(keepInView)
            )
        }

        RepsCell {
            CompactNumberField(
                value = repsText,
                onValueChange = { input ->
                    if (isRepsInput(input)) {
                        repsText = input
                        save()
                    }
                },
                keyboardType = KeyboardType.Decimal,
                placeholder = previousSet?.reps?.let(::formatNumber),
                modifier = SetBoxModifier.then(if (missing.reps) missingOutline else Modifier).then(keepInView)
            )
        }

        SetDoneCheckbox(checked = done, onCheckedChange = ::setDone, highlight = missing.notDone)
    }
    }
}

// A set that can be swiped to the left to delete it, showing red with a bin as it goes.
// The swipe asks first. The set slides back into place as the pop-up opens, so what's in it can
// be seen while deciding.
@Composable
private fun SwipeToDeleteSet(setName: String, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        ConfirmDestructiveDialog(
            title = "Delete $setName?",
            message = "You can undo this for a few seconds afterwards.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                confirmDelete = true
                scope.launch { state.reset() }
            }
        },
        backgroundContent = {
            // An arrow before the bin, so it's clear to keep swiping all the way left.
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                    .padding(end = 16.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Filled.Delete, contentDescription = "Delete set", tint = MaterialTheme.colorScheme.onError)
            }
        }
    ) {
        // The card's colour behind the row, so the red only shows as it slides away.
        Box(modifier = Modifier.background(CardDefaults.cardColors().containerColor)) { content() }
    }
}

// The note under an exercise's name. Empty, it shows a faint "Leave a note...". It's saved as
// it's typed, and scrolls itself above the keyboard.
// The ⋮ button in an exercise card's corner, and its menu: substituting another exercise for it,
// linking it into a superset or out of one, moving it, and deleting it. Only what applies is listed: there's nothing to
// link with below the last exercise, and Move is left out where exercises can't be rearranged.
// Used on workouts and in the program editor, where deleting is called removing.
@Composable
fun ExerciseMenu(
    linkedBelow: Boolean,
    onToggleLink: (() -> Unit)?,
    onUnlinkAbove: (() -> Unit)?,
    onReorder: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    deleteLabel: String = "Delete exercise",
    // Opens the list of exercises that can take its place. Null leaves it out of the menu.
    onSubstitute: (() -> Unit)? = null
) {
    var open by remember { mutableStateOf(false) }
    // Runs a menu choice, closing the menu first.
    fun choose(action: () -> Unit) {
        open = false
        action()
    }
    Box(modifier = modifier) {
        CornerIconButton(
            onClick = { open = true },
            icon = Icons.Filled.MoreVert,
            contentDescription = "Exercise options"
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onSubstitute != null) {
                DropdownMenuItem(
                    text = { Text("Substitute") },
                    leadingIcon = { SwapArrowsIcon(modifier = Modifier.size(18.dp)) },
                    onClick = { choose(onSubstitute) }
                )
            }
            if (onToggleLink != null) {
                DropdownMenuItem(
                    text = { Text(if (linkedBelow) "Unlink from exercise below" else "Superset with exercise below") },
                    leadingIcon = { ChainLinkIcon(modifier = Modifier.size(18.dp)) },
                    onClick = { choose(onToggleLink) }
                )
            }
            if (onUnlinkAbove != null) {
                DropdownMenuItem(
                    text = { Text("Unlink from exercise above") },
                    leadingIcon = { ChainLinkIcon(modifier = Modifier.size(18.dp)) },
                    onClick = { choose(onUnlinkAbove) }
                )
            }
            if (onReorder != null) {
                DropdownMenuItem(
                    text = { Text("Move") },
                    leadingIcon = { UpDownArrowsIcon(modifier = Modifier.size(18.dp)) },
                    onClick = { choose(onReorder) }
                )
            }
            // Asks first (see onDelete).
            DropdownMenuItem(
                text = { Text(deleteLabel, color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = { choose(onDelete) }
            )
        }
    }
}

@Composable
private fun ExerciseNote(exercise: Exercise, onUpdateExercise: (Exercise) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(exercise.id) { mutableStateOf(exercise.notes.orEmpty()) }
    val inView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            onUpdateExercise(exercise.copy(notes = it.ifBlank { null }))
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier
            .padding(top = 4.dp, end = 8.dp)
            .bringIntoViewRequester(inView)
            .onFocusEvent { focus ->
                if (focus.isFocused) scope.launch {
                    delay(350)
                    inView.bringIntoView()
                }
            },
        decorationBox = { field ->
            Box {
                if (text.isEmpty()) {
                    Text("Leave a note...", style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.5f))
                }
                field()
            }
        }
    )
}

// A compact number box that works at small heights, unlike OutlinedTextField,
// which needs at least 56dp. BasicTextField is the bare text input, and we draw
// the rounded background behind it ourselves.
@Composable
fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    // Faint text shown while the box is empty.
    placeholder: String? = null,
    // A mark shown before the placeholder in the accent colour, like the ↑ of a suggested weight.
    placeholderAccent: String? = null
) {
    // BasicTextField doesn't pick a text color by itself, so we use the surrounding
    // content color. Without this, the text could be invisible in dark mode.
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = LocalContentColor.current,
        textAlign = TextAlign.Center
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier,
        // decorationBox is where we draw the box around the text.
        // innerTextField() marks the spot where the typed text appears.
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // A tint of the text color shows up on any background, light or dark.
                    .background(
                        color = LocalContentColor.current.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = buildAnnotatedString {
                            if (placeholderAccent != null) {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                                    append(placeholderAccent)
                                }
                            }
                            append(placeholder)
                        },
                        style = textStyle,
                        color = textStyle.color.copy(alpha = 0.4f),
                        maxLines = 1
                    )
                }
                innerTextField()
            }
        }
    )
}

// Shows 50.0 as "50" and 22.5 as "22.5".
fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

// An "extension function": it adds a new function to an existing type (here, ActivityType)
// without changing the type itself. It turns the stored name "STRENGTH" into "Strength".
fun ActivityType.displayName(): String {
    return name.lowercase().replaceFirstChar { it.uppercase() }
}

// Turns a number of milliseconds into text like "01:05:09".
// "%02d" means a whole number padded with zeros to at least 2 digits.
fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

@Composable
fun HistoryScreen(
    calendarSelection: CalendarSelection,
    listState: LazyListState,
    onOpenWorkout: (Long) -> Unit,
    // Goes to the Workout tab, from the message shown while History is empty.
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dao = remember { WorkoutDatabase.getInstance(context).workoutDao() }
    val scope = rememberCoroutineScope()

    // Listen to every saved workout, newest first. The list updates by itself
    // whenever a workout is added or deleted. It's null until the first answer, and the list
    // waits for it, so a kept scroll position lands on the real workouts.
    val workoutsFlow = remember { dao.observeAllWorkouts() }
    val loadedWorkouts by workoutsFlow.collectAsState(initial = null)
    // A workout still running isn't history yet: it's under the In Progress tab until it's finished.
    val workouts = remember(loadedWorkouts) { loadedWorkouts.orEmpty().filter { it.workout.endTime != null } }
    // The workout whose bin was tapped, waiting for the user to confirm.
    var workoutToDelete by remember { mutableStateOf<Workout?>(null) }

    val zone = ZoneId.systemDefault()
    val workoutDates = remember(workouts) {
        workouts.map { Instant.ofEpochMilli(it.workout.startTime).atZone(zone).toLocalDate() }.toSet()
    }
    // The kinds of activity done on each day, for the coloured dots on the calendar.
    val dayKinds = remember(workouts) {
        workouts.groupBy { Instant.ofEpochMilli(it.workout.startTime).atZone(zone).toLocalDate() }
            .mapValues { (_, onDay) -> kindsOf(onDay.flatMap { it.exercises }) }
    }

    // How many records each workout set, for the medals on the calendar and the list. Worked
    // out in the background from every strength set and activity, and again when they change.
    val strengthFlow = remember { dao.observeAllStrengthSets() }
    val strengthSets by strengthFlow.collectAsState(initial = emptyList())
    val activityFlow = remember { dao.observeAllActivityRows() }
    val activityRows by activityFlow.collectAsState(initial = emptyList())
    val prCounts by produceState(emptyMap<Long, Int>(), strengthSets, activityRows) {
        value = withContext(Dispatchers.IO) {
            val strength = strengthSets.groupBy({ it.name }, { it.toStrengthSet() })
            val activities = activityRows.groupBy { it.name }.mapValues { (name, rows) ->
                val type = rows.first().type
                type to rows.map { BestEffortCache.session(dao, it.toActivityRow(), tracksFastestTimes(name, type)) }
            }
            prCountsByWorkout(strength, activities)
        }
    }
    // Days with a workout that set a record get a medal on the calendar.
    val prDates = remember(workouts, prCounts) {
        workouts.filter { it.workout.id in prCounts }
            .map { Instant.ofEpochMilli(it.workout.startTime).atZone(zone).toLocalDate() }
            .toSet()
    }
    // With days selected, only workouts on those days are listed.
    val selectedDays = calendarSelection.selectedDays
    val shownWorkouts = remember(workouts, selectedDays) {
        if (selectedDays.isEmpty()) workouts
        else workouts.filter { Instant.ofEpochMilli(it.workout.startTime).atZone(zone).toLocalDate() in selectedDays }
    }

    // Column stacks its children vertically. Without a verticalArrangement,
    // the children start at the top, which is what we want here.
    Column(
        modifier = modifier.fillMaxSize(),
        // Still centers the title left-to-right.
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader(title = Screen.History.label)
        // The same space under the bar as on Home: one gap between cards.
        Spacer(modifier = Modifier.height(CARD_GAP))

        // The calendar scrolls with the list, so the workouts aren't squeezed below it.
        if (loadedWorkouts != null) LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(CARD_WIDTH_FRACTION),
            verticalArrangement = Arrangement.spacedBy(CARD_GAP)
        ) {
            item(key = "calendar") {
                // On a card, like the boxes on Home and Profile.
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                        MonthCalendar(
                            selection = calendarSelection,
                            workoutDates = workoutDates,
                            prDates = prDates,
                            dayKinds = dayKinds
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = calendarSelection::clear,
                                enabled = selectedDays.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Clear Selection", fontSize = AppText.caption)
                            }
                        }
                    }
                }
            }

            // "All Workouts" with nothing picked, "Workouts" for the picked dates.
            item(key = "listHeader") {
                Text(
                    text = if (selectedDays.isEmpty()) "All Workouts" else "Workouts",
                    fontSize = AppText.heading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 4.dp)
                )
            }

            // Nothing in History at all yet: what will appear here, and a way to start.
            if (workouts.isEmpty()) {
                item(key = "noWorkouts") {
                    NoWorkoutsCard(onStartWorkout = onStartWorkout, modifier = Modifier.fillMaxWidth())
                }
            }

            if (selectedDays.isNotEmpty() && shownWorkouts.isEmpty() && workouts.isNotEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No workouts",
                        fontSize = AppText.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp)
                    )
                }
            }

            items(shownWorkouts, key = { it.workout.id }) { item ->
                WorkoutCard(
                    workout = item.workout,
                    kinds = kindsOf(item.exercises),
                    prCount = prCounts[item.workout.id] ?: 0,
                    onClick = { onOpenWorkout(item.workout.id) },
                    // The bin asks first, so a workout isn't deleted by a stray tap.
                    onDelete = { workoutToDelete = item.workout }
                )
            }
        }
    }

    workoutToDelete?.let { workout ->
        ConfirmDestructiveDialog(
            title = "Delete ${workout.displayName()}?",
            message = "Its exercises and sets will be deleted too. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                workoutToDelete = null
                scope.launch { deleteWorkoutAndImages(dao, workout.id) }
            },
            onDismiss = { workoutToDelete = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
// One entry in the history list. Tapping it opens the workout summary. On the left, a coloured
// icon for each kind of activity in it; under the name, the date and a gold tag for any records.
@Composable
fun WorkoutCard(
    workout: Workout,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    prCount: Int = 0,
    kinds: List<ActivityKind> = emptyList()
) {
    // The same rounded corners as the other cards.
    val shape = RoundedCornerShape(12.dp)
    val cardColor = CardDefaults.cardColors().containerColor

    Card(
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            // clip makes the tap ripple follow the rounded corners.
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        // Box lets us position children independently, such as the delete button in a corner.
        // Every card is at least 72dp tall; one with a long name grows to fit it on two lines.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    // Clear of the bin in the corner on the right.
                    .padding(start = 14.dp, end = 44.dp, top = 8.dp, bottom = 8.dp)
            ) {
                if (kinds.isNotEmpty()) {
                    ActivityIcons(kinds = kinds, ringColor = cardColor)
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column {
                    Text(
                        text = workout.displayName(),
                        fontSize = AppText.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // The records tag sits beside the date, or under it if there isn't room.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = formatDay(workout.startTime), fontSize = AppText.small)
                        if (prCount > 0) RecordsTag(count = prCount)
                    }
                }
            }

            // A small delete button in the top right corner.
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete workout",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}


// Turns a stored time (milliseconds since 1970) into text like "Mon, Oct 5, 2026 - 6:41 PM",
// using the phone's time zone.
fun formatDate(millis: Long): String {
    return DateTimeFormatter.ofPattern("EEE, MMM d, yyyy - h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}

// Builds the duration text from the start and end times.
// A workout with no end time is still running.
fun durationLabel(startTime: Long, endTime: Long?): String {
    if (endTime == null) return "In progress"

    val totalSeconds = (endTime - startTime) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    // "if" is an expression in Kotlin, so we can return its result directly.
    return when {
        hours > 0 -> "Duration: ${hours}h ${minutes}m"
        minutes > 0 -> "Duration: ${minutes}m ${seconds}s"
        else -> "Duration: ${seconds}s"
    }
}

@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onWeightUnitChange: (WeightUnit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weightUnit = LocalWeightUnit.current

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader(
            title = "Settings",
            start = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Profile")
                }
            }
        )

        // The header stays put while the settings scroll under it.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = CARD_GAP),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Row lays its children out horizontally, left to right.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                // SpaceBetween pushes the first child to the left edge and the last to the right.
                horizontalArrangement = Arrangement.SpaceBetween,
                // Lines up the label and switch on the same vertical center.
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Light mode", fontSize = AppText.heading)

                // The app stores "dark mode", but the switch is labeled "Light mode",
                // so the two are opposites. "!" means "not" and flips a Boolean.
                // The switch is on when dark mode is off, and tapping it passes the
                // flipped value back: turning light mode ON turns dark mode OFF.
                Switch(
                    checked = !darkMode,
                    onCheckedChange = { lightModeOn -> onDarkModeChange(!lightModeOn) }
                )
            }

            SettingsDivider()

            // Track Weight: a body weight card on Home and a chart of it on Profile. Off by default.
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Track Weight", fontSize = AppText.heading)
                    Text(
                        text = "Log your body weight on Home and see it over time on Profile",
                        fontSize = AppText.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = TrackWeight.enabled,
                    onCheckedChange = { on ->
                        TrackWeight.set(context, on)
                        if (on) scope.launch { startBodyWeightLog(context) }
                    }
                )
            }

            SettingsDivider()

            // The unit weights are shown and typed in. Two radio buttons, only one selected.
            // selectableGroup tells screen readers that these rows are one group of choices.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .selectableGroup()
            ) {
                Text(text = "Weight unit", fontSize = AppText.heading)

                Spacer(modifier = Modifier.height(8.dp))

                // One row per unit. The whole row can be tapped.
                listOf(
                    WeightUnit.LBS to "Pounds (lbs)",
                    WeightUnit.KG to "Kilograms (kg)"
                ).forEach { (unit, text) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = weightUnit == unit,
                                onClick = { onWeightUnitChange(unit) },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The row handles the tap, so the button itself doesn't need to.
                        RadioButton(selected = weightUnit == unit, onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = text)
                    }
                }
            }

            SettingsDivider()

            SplitSettingsSection()

            SettingsDivider()

            // Watch workouts and heart rate, through Health Connect.
            HealthConnectBar(modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION))

            Spacer(modifier = Modifier.height(CARD_GAP))

            // A bar for bringing in workouts from another app.
            ImportWorkoutsBar(modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION))

            Spacer(modifier = Modifier.height(CARD_GAP))

            // Backing up everything, restoring a backup, and exporting workouts.
            BackupCard(modifier = Modifier.fillMaxWidth(CARD_WIDTH_FRACTION))

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// The theme's lighter surface with most of its purple tint taken out, for the header bar and
// the status bar block above it.
@Composable
fun headerBarColor(): Color {
    val c = MaterialTheme.colorScheme.surfaceContainer
    val gray = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    return lerp(c, Color(gray, gray, gray), 0.7f)
}

// The bar across the top of a screen, with its title in the middle and room for a button on
// each side. Every screen with a header uses this one, so they all look alike.
@Composable
fun ScreenHeader(
    title: String,
    // The bar behind the title, across the whole screen.
    filled: Boolean = true,
    // How much room is kept clear for the buttons on each side of the title.
    titleStartRoom: Dp = HEADER_BUTTON_ROOM,
    titleEndRoom: Dp = HEADER_BUTTON_ROOM,
    start: @Composable () -> Unit = {},
    end: @Composable () -> Unit = {}
) {
    ScreenHeader(
        filled = filled,
        titleStartRoom = titleStartRoom,
        titleEndRoom = titleEndRoom,
        start = start,
        end = end
    ) {
        HeaderTitle(title)
    }
}

// The same bar, for a title that's more than plain text (a name with a rename pencil, say).
@Composable
fun ScreenHeader(
    filled: Boolean = true,
    titleStartRoom: Dp = HEADER_BUTTON_ROOM,
    titleEndRoom: Dp = HEADER_BUTTON_ROOM,
    start: @Composable () -> Unit = {},
    end: @Composable () -> Unit = {},
    title: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (filled) Modifier.background(headerBarColor()) else Modifier)
            .padding(top = 24.dp, bottom = 16.dp)
    ) {
        // An unseen line of title-sized text sets the header's height. The title and the
        // buttons are then laid over it and may hang over its edges, so a tall button or a
        // pencil never makes one screen's header taller than another's.
        // Screen readers skip it, as there's nothing to read.
        Text(text = " ", fontSize = HEADER_TITLE_SIZE, modifier = Modifier.clearAndSetSemantics {})
        CenteredBetween(
            startRoom = titleStartRoom,
            endRoom = titleEndRoom,
            modifier = Modifier
                .matchParentSize()
                .wrapContentHeight(unbounded = true),
            content = title
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 8.dp)
        ) {
            Box(modifier = Modifier.align(Alignment.CenterStart).wrapContentHeight(unbounded = true)) { start() }
            Box(modifier = Modifier.align(Alignment.CenterEnd).wrapContentHeight(unbounded = true)) { end() }
        }
    }
}

// Places a title in the middle of the whole width when it fits there. A longer one also uses
// the spare room on the side with the smaller button, so the buttons on each side can be
// different widths without making a long name any shorter. startRoom and endRoom are kept
// clear for the buttons.
@Composable
fun CenteredBetween(startRoom: Dp, endRoom: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Layout lets us measure the title and then decide exactly where it goes.
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val start = startRoom.roundToPx()
        val end = endRoom.roundToPx()
        val width = constraints.maxWidth
        val room = (width - start - end).coerceAtLeast(0)
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, maxWidth = room))
        // In the middle of the whole width, unless that would run into a button.
        val middle = (width - placeable.width) / 2
        val x = middle.coerceIn(start, (width - end - placeable.width).coerceAtLeast(start))
        layout(width, placeable.height) { placeable.place(x, 0) }
    }
}

// The size of every header's title, and the room an icon button beside it needs.
val HEADER_TITLE_SIZE = AppText.screenTitle
val HEADER_BUTTON_ROOM = 56.dp

// A header's title, in the middle.
@Composable
fun HeaderTitle(text: String, modifier: Modifier = Modifier) {
    FittedTitle(text = text, maxSize = HEADER_TITLE_SIZE, modifier = modifier)
}

// A name shown as a title, which is never cut short while there's any way to fit it. A name too
// long for the room it has is first shown a little smaller on its one line, down to minSize. If
// it's still too long, it goes onto two lines at that size, and only a name too long even for
// that is cut short with "...".
@Composable
fun FittedTitle(
    text: String,
    maxSize: TextUnit,
    modifier: Modifier = Modifier,
    minSize: TextUnit = AppText.body,
    textAlign: TextAlign = TextAlign.Center
) {
    // Whether the one line, at its smallest, was still too short for this text.
    var twoLines by remember(text, maxSize) { mutableStateOf(false) }
    val style = LocalTextStyle.current.copy(color = LocalContentColor.current, textAlign = textAlign)
    if (twoLines) {
        BasicText(
            text = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = style.copy(fontSize = minSize, lineHeight = minSize * 1.2f),
            modifier = modifier
        )
    } else {
        BasicText(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = style,
            autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 1.sp),
            // Android reports here how the text was laid out, including whether it was cut short.
            onTextLayout = { if (it.hasVisualOverflow) twoLines = true },
            modifier = modifier
        )
    }
}

// A thin line between groups of settings, with space above and below.
@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

