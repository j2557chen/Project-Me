package com.example.myfitnessapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// A foreground service: a background task that Android is told is doing something the
// user knows about, which it shows as a notification. Android lets it keep running, and
// keeps delivering GPS points, while the screen is locked or the app isn't open.
//
// The actual recording is done by RecordingTracker. This service just keeps the app
// "in the foreground" while that happens, and shows the notification.
class RecordingService : Service() {

    // A place to run background work that belongs to this service. It's cancelled when
    // the service is destroyed, so nothing keeps running afterwards.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The task that keeps the notification up to date (see onStartCommand).
    private var notificationJob: Job? = null

    // Services can also be "bound" to by other code. We don't use that, so we return null.
    override fun onBind(intent: Intent?): IBinder? = null

    // Called each time something starts this service with an Intent.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The Stop button in the notification sends this action. It freezes the recording,
        // exactly like the stop button in the app, so the user can still save it.
        if (intent?.action == ACTION_STOP) {
            RecordingTracker.stop()
            return START_NOT_STICKY
        }

        createNotificationChannel()

        // Turns this service into a foreground service. Android requires this to happen
        // within a few seconds of the service being started. The type tells Android
        // the service uses location, which is what allows GPS in the background.
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        // If recording was stopped before we got going, there's nothing to keep alive.
        if (!RecordingTracker.state.value.isRecording) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Watch for pausing, resuming and stopping, and rebuild the notification each time,
        // so its timer stops while paused and carries on afterwards. map picks out the
        // values we care about, and distinctUntilChanged ignores updates where none of them
        // changed (such as every new GPS point).
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            RecordingTracker.state
                .map { Triple(it.pausedAtMillis, it.pausedTotalMillis, it.isStopped) }
                .distinctUntilChanged()
                .collect {
                    // Posting a notification with the same id replaces the old one.
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification())
                }
        }

        // START_NOT_STICKY means: if Android has to kill this service, don't restart it
        // automatically. A recording that lost its data shouldn't silently carry on.
        return START_NOT_STICKY
    }

    // Called when the service is shut down for any reason. Make sure recording stops too.
    override fun onDestroy() {
        serviceScope.cancel()
        RecordingTracker.finish(this)
        super.onDestroy()
    }

    // Notifications must belong to a "channel", which is what appears in the phone's
    // notification settings. IMPORTANCE_LOW means no sound or pop-up.
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recording",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val state = RecordingTracker.state.value
        val activityName = state.activity.label.lowercase()

        // Tapping the notification opens the app.
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // The Stop button sends this service the stop action (handled at the top of
        // onStartCommand). FLAG_IMMUTABLE is required on newer Android versions.
        val stopIntent = Intent(this, RecordingService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Stop",
            stopPending
        ).build()

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openPending)
            // Can't be swiped away while recording.
            .setOngoing(true)
            // Show the notification right away instead of after a short delay.
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)

        if (state.isStopped) {
            // Stopped, but not saved yet. Tapping the notification opens the app, where
            // the activity can be saved or carried on.
            builder
                .setContentTitle("Stopped $activityName")
                .setContentText("Open the app to save it")
        } else if (state.isPaused) {
            builder.addAction(stopAction)
            // While paused, show the frozen time as plain text instead of a live timer.
            builder
                .setContentTitle("Paused $activityName")
                .setContentText("Paused at " + formatElapsed(state.elapsedMillis(System.currentTimeMillis())))
        } else {
            builder.addAction(stopAction)
            builder
                .setContentTitle("Recording $activityName")
                .setContentText("Tracking your location")
                // These two make the notification show a live timer counting up. Android
                // updates it by itself. The start time is moved forward by the time spent
                // paused, so paused time isn't counted.
                .setWhen(state.startTimeMillis + state.pausedTotalMillis)
                .setUsesChronometer(true)
        }

        return builder.build()
    }

    // Fixed values used by the service.
    companion object {
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.example.myfitnessapp.ACTION_STOP"
    }
}
