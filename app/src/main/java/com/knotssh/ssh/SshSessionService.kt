package com.knotssh.ssh

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.knotssh.MainActivity
import com.knotssh.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps background SSH sessions alive and shows one notification per session, each offering
 * "riapri" and "termina".
 */
@AndroidEntryPoint
class SshSessionService : Service() {

    @Inject
    lateinit var registry: SessionRegistry

    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var observer: Job? = null
    private var shownIds = emptySet<Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Claim foreground status immediately: Android kills services that take too long.
        startForegroundCompat(summaryNotification(0))
        observer = scope.launch {
            registry.active.collectLatest { sessions ->
                if (sessions.isEmpty()) {
                    clearAll()
                    stopSelf()
                } else {
                    render(sessions)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TERMINATE -> {
                val serverId = intent.getLongExtra(EXTRA_SERVER_ID, -1L)
                if (serverId > 0) registry.close(serverId)
            }
        }
        if (registry.active.value.isEmpty()) stopSelf()
        return START_STICKY
    }

    // POST_NOTIFICATIONS may be denied; the runCatching below is the handling lint asks for.
    @SuppressLint("MissingPermission")
    private fun render(sessions: List<SessionSummary>) {
        val manager = NotificationManagerCompat.from(this)
        startForegroundCompat(summaryNotification(sessions.size))

        sessions.forEach { session ->
            runCatching {
                manager.notify(notificationId(session.serverId), sessionNotification(session))
            }
        }

        // Drop notifications for sessions that have since ended.
        (shownIds - sessions.map { it.serverId }.toSet()).forEach {
            manager.cancel(notificationId(it))
        }
        shownIds = sessions.map { it.serverId }.toSet()
    }

    private fun clearAll() {
        val manager = NotificationManagerCompat.from(this)
        shownIds.forEach { manager.cancel(notificationId(it)) }
        shownIds = emptySet()
    }

    private fun sessionNotification(session: SessionSummary): Notification {
        val open = PendingIntent.getActivity(
            this,
            session.serverId.toInt(),
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TERMINAL_SERVER_ID, session.serverId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val terminate = PendingIntent.getService(
            this,
            // Distinct request code so it does not collide with the "open" intent above.
            -session.serverId.toInt(),
            Intent(this, SshSessionService::class.java).apply {
                action = ACTION_TERMINATE
                putExtra(EXTRA_SERVER_ID, session.serverId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(session.label.ifBlank { session.title })
            .setContentText(getString(R.string.notification_session_active))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_reopen), open)
            .addAction(0, getString(R.string.notification_terminate), terminate)
            .setOngoing(true)
            .setSilent(true)
            .setGroup(GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun summaryNotification(count: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                when (count) {
                    0, 1 -> getString(R.string.notification_session_active)
                    else -> getString(R.string.notification_sessions_active, count)
                }
            )
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setOngoing(true)
            .setSilent(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
            startForeground(
                SUMMARY_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        observer?.cancel()
        scope.cancel()
        clearAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "knotssh_session_channel"
        const val ACTION_TERMINATE = "com.knotssh.ACTION_TERMINATE_SESSION"
        const val EXTRA_SERVER_ID = "extra_server_id"

        private const val GROUP_KEY = "knotssh_sessions"
        private const val SUMMARY_NOTIFICATION_ID = 1000
        private const val NOTIFICATION_ID_BASE = 2000

        private fun notificationId(serverId: Long) =
            NOTIFICATION_ID_BASE + (serverId % 1000).toInt()

        /** Starts or refreshes the service so notifications match the live sessions. */
        fun sync(context: Context) {
            val intent = Intent(context, SshSessionService::class.java)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SshSessionService::class.java))
        }
    }
}
