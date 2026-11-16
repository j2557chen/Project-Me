package com.example.myfitnessapp

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.BorderStroke
import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// The rest timer. Each exercise in the running workout can be given a rest length from the
// stopwatch button on its card. Ticking off one of its sets then counts that rest down, in a bar
// at the bottom of the screen and on the lock screen, and the phone buzzes when it's over.
// The lengths are for this workout only: every exercise starts with no timer in the next one.
// ---------------------------------------------------------------------------

// The rest lengths to pick from, in seconds.
val REST_LENGTHS = listOf(30, 60, 90, 120, 180, 300)

// "90s" up to 90 seconds, then "2m", "3m"...
fun restLabel(seconds: Int): String = if (seconds < 120) "${seconds}s" else "${seconds / 60}m"

object RestTimer {
    // Its own settings file, kept out of backups: nothing here outlives the workout.
    private const val FILE = "rest_timer"
    private const val LENGTH_PREFIX = "length_"
    private const val KEY_NAME = "name"
    private const val KEY_END = "endAt"

    private const val COUNTDOWN_CHANNEL = "rest_countdown"
    private const val OVER_CHANNEL = "rest_over"
    private const val NOTIFICATION_ID = 2

    // A countdown in progress: which exercise it's for, and when it ends.
    data class Countdown(val exerciseName: String, val endAtMillis: Long)

    // The rest length given to each exercise of the running workout, by exercise id. Exercises
    // without one have no timer. A state map, so the cards redraw when it changes.
    val lengths = mutableStateMapOf<Long, Int>()

    // The countdown running now, or null.
    var running by mutableStateOf<Countdown?>(null)
        private set

    private var loaded = false

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // Reads back what was saved, so the timers survive the app being closed mid-workout.
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = prefs(context)
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(LENGTH_PREFIX) && value is Int) {
                key.removePrefix(LENGTH_PREFIX).toLongOrNull()?.let { lengths[it] = value }
            }
        }
        val end = prefs.getLong(KEY_END, 0L)
        val name = prefs.getString(KEY_NAME, null)
        if (name != null && end > System.currentTimeMillis()) running = Countdown(name, end)
    }

    // Gives an exercise a rest length, or takes its timer away (null).
    fun setLength(context: Context, exerciseId: Long, seconds: Int?) {
        if (seconds == null) lengths.remove(exerciseId) else lengths[exerciseId] = seconds
        prefs(context).edit {
            if (seconds == null) remove(LENGTH_PREFIX + exerciseId) else putInt(LENGTH_PREFIX + exerciseId, seconds)
        }
    }

    // Starts (or restarts) the countdown after a set of this exercise is ticked off.
    fun start(context: Context, exerciseName: String, seconds: Int) {
        setCountdown(context, Countdown(exerciseName, System.currentTimeMillis() + seconds * 1000L))
    }

    // Adds or takes off time. Taking it down to nothing ends the rest.
    fun adjust(context: Context, deltaSeconds: Int) {
        val current = running ?: return
        val end = current.endAtMillis + deltaSeconds * 1000L
        if (end <= System.currentTimeMillis()) skip(context) else setCountdown(context, current.copy(endAtMillis = end))
    }

    // Ends the rest early, without a buzz.
    fun skip(context: Context) {
        cancelAlarm(context)
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        clearCountdown(context)
    }

    // When the workout is saved or discarded: no timers carry over to the next one.
    fun clearAll(context: Context) {
        skip(context)
        lengths.clear()
        prefs(context).edit { clear() }
    }

    private fun setCountdown(context: Context, countdown: Countdown) {
        running = countdown
        prefs(context).edit {
            putString(KEY_NAME, countdown.exerciseName)
            putLong(KEY_END, countdown.endAtMillis)
        }
        scheduleAlarm(context, countdown.endAtMillis)
        showCountdownNotification(context, countdown)
    }

    private fun clearCountdown(context: Context) {
        running = null
        prefs(context).edit {
            remove(KEY_NAME)
            remove(KEY_END)
        }
    }

    // ----- Waking up when the rest is over, even with the app closed or the screen off -----

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, RestTimerReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun scheduleAlarm(context: Context, atMillis: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        // An exact alarm, so the buzz comes on time. Without permission for one, Android may
        // run it a little late.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, alarmIntent(context))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, alarmIntent(context))
        }
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }

    // Called by RestTimerReceiver when the alarm goes off: buzz, and say the rest is over.
    fun onTimeUp(context: Context) {
        load(context)
        val name = running?.exerciseName ?: prefs(context).getString(KEY_NAME, null)
        clearCountdown(context)

        context.getSystemService(VibratorManager::class.java).defaultVibrator
            .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))

        if (!canNotify(context)) return
        createChannels(context)
        val notification = Notification.Builder(context, OVER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rest over")
            .setContentText(if (name != null) "Time for your next set of $name" else "Time for your next set")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            // Gone by itself after a couple of minutes, since it's only useful right away.
            .setTimeoutAfter(2 * 60_000L)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    // ----- Notifications -----

    fun canNotify(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // The countdown: silent, just there to glance at on the lock screen.
        manager.createNotificationChannel(
            NotificationChannel(COUNTDOWN_CHANNEL, "Rest timer", NotificationManager.IMPORTANCE_LOW)
        )
        // Rest over: pops up. The app buzzes the phone itself, so the channel doesn't as well.
        manager.createNotificationChannel(
            NotificationChannel(OVER_CHANNEL, "Rest over", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE
    )

    // A notification counting down by itself (Android ticks the clock), so nothing needs to run.
    private fun showCountdownNotification(context: Context, countdown: Countdown) {
        if (!canNotify(context)) return
        createChannels(context)
        val notification = Notification.Builder(context, COUNTDOWN_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Resting")
            .setContentText(countdown.exerciseName)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(countdown.endAtMillis)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }
}

// Woken by the alarm when a rest is over.
class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RestTimer.onTimeUp(context)
    }
}

// ---------------------------------------------------------------------------
// On screen
// ---------------------------------------------------------------------------

// The rest timer button, to the left of an exercise's note: a small rounded button with a
// stopwatch, reading "Rest: Off", or filled in purple with the length ("Rest: 90s") once one is
// picked. Tapping it opens the list of lengths.
@Composable
fun RestTimerButton(seconds: Int?, onPick: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Asks to show notifications the first time a timer is set, if the app can't yet. The timer
    // works either way; without them there's no countdown on the lock screen or "Rest over".
    val askToNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Box(modifier = modifier) {
        val on = seconds != null
        val colors = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(50)
        Surface(
            onClick = { menuOpen = true },
            shape = shape,
            // Off: an outline, like the app's other buttons. On: filled, so a timer that's set
            // stands out.
            color = if (on) colors.primaryContainer else Color.Transparent,
            contentColor = if (on) colors.onPrimaryContainer else colors.primary,
            border = if (on) null else BorderStroke(1.dp, colors.primary),
            modifier = Modifier.semantics {
                contentDescription = if (on) "Rest timer, ${restLabel(seconds!!)}. Change" else "Rest timer, off. Set one"
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
            ) {
                StopwatchIcon(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (on) "Rest: ${restLabel(seconds!!)}" else "Rest: Off",
                    fontSize = AppText.caption,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("No rest timer") },
                onClick = {
                    menuOpen = false
                    onPick(null)
                }
            )
            REST_LENGTHS.forEach { length ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (length < 120) "$length seconds" else "${length / 60} minutes",
                            color = if (length == seconds) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            fontWeight = if (length == seconds) FontWeight.Bold else null
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onPick(length)
                        if (!RestTimer.canNotify(context)) askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }
        }
    }
}

// A small stopwatch outline, drawn in the current content colour.
@Composable
private fun StopwatchIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        val radius = size.minDimension * 0.36f
        val center = Offset(size.width / 2, size.height * 0.57f)
        drawCircle(color, radius, center, style = Stroke(stroke))
        // The crown on top, and the hand.
        val top = center.y - radius
        drawLine(color, Offset(center.x, top), Offset(center.x, top - size.height * 0.1f), stroke, StrokeCap.Round)
        drawLine(
            color, Offset(center.x - size.width * 0.1f, top - size.height * 0.12f),
            Offset(center.x + size.width * 0.1f, top - size.height * 0.12f), stroke, StrokeCap.Round
        )
        drawLine(color, center, Offset(center.x, center.y - radius * 0.6f), stroke, StrokeCap.Round)
    }
}

// The countdown bar at the bottom of the workout screen, shown while a rest is running.
@Composable
fun RestTimerBar(modifier: Modifier = Modifier) {
    val countdown = RestTimer.running ?: return
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Ticks the clock along. The alarm takes care of the end, so this only redraws.
    LaunchedEffect(countdown) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= countdown.endAtMillis) break
            delay(250)
        }
    }
    val remaining = ((countdown.endAtMillis - now + 999) / 1000).coerceAtLeast(0)
    if (remaining <= 0) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth(CARD_WIDTH_FRACTION)
            .padding(bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "%d:%02d".format(remaining / 60, remaining % 60),
                    fontSize = AppText.heading,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Rest · ${countdown.exerciseName}",
                    fontSize = AppText.caption,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val padding = PaddingValues(horizontal = 8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(onClick = { RestTimer.adjust(context, -15) }, contentPadding = padding) { Text("−15s") }
                TextButton(onClick = { RestTimer.adjust(context, 15) }, contentPadding = padding) { Text("+15s") }
                TextButton(onClick = { RestTimer.skip(context) }, contentPadding = padding) { Text("Skip") }
            }
        }
    }
}
