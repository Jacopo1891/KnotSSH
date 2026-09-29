package com.knotssh.ssh

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
import com.knotssh.MainActivity
import com.knotssh.R

/**
 * Keeps an interactive SSH session alive while the app is backgrounded.
 *
 * The service holds no session state of its own; it only surfaces status, so stopping it can
 * never leave a connection dangling.
 */
class SshSessionService : Service() {

    private var serverId: Long = -1
    private var serverAlias: String = "Sessione SSH"
    private var connected: Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.hasExtra(EXTRA_SERVER_ID)) serverId = it.getLongExtra(EXTRA_SERVER_ID, -1)
            if (it.hasExtra(EXTRA_SERVER_ALIAS)) {
                serverAlias = it.getStringExtra(EXTRA_SERVER_ALIAS) ?: serverAlias
            }
            if (it.hasExtra(EXTRA_CONNECTED)) {
                connected = it.getBooleanExtra(EXTRA_CONNECTED, true)
            }
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        // The session belongs to the UI layer, so a restarted service would have nothing to attach to.
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_TERMINAL_SERVER_ID, serverId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            serverId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KnotSSH — $serverAlias")
            .setContentText(if (connected) "Sessione SSH attiva" else "Connessione caduta")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(connected)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sessione SSH attiva",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene la sessione SSH viva mentre l'app è in background"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "knotssh_session_channel"
        const val NOTIFICATION_ID = 1001
        private const val EXTRA_SERVER_ID = "extra_server_id"
        private const val EXTRA_SERVER_ALIAS = "extra_server_alias"
        private const val EXTRA_CONNECTED = "extra_connected"

        fun startService(context: Context, serverId: Long, serverAlias: String) {
            runCatching {
                context.startForegroundService(
                    Intent(context, SshSessionService::class.java).apply {
                        putExtra(EXTRA_SERVER_ID, serverId)
                        putExtra(EXTRA_SERVER_ALIAS, serverAlias)
                        putExtra(EXTRA_CONNECTED, true)
                    }
                )
            }
        }

        fun updateStatus(context: Context, isConnected: Boolean) {
            runCatching {
                context.startService(
                    Intent(context, SshSessionService::class.java)
                        .putExtra(EXTRA_CONNECTED, isConnected)
                )
            }
        }

        fun stopService(context: Context) {
            runCatching { context.stopService(Intent(context, SshSessionService::class.java)) }
        }
    }
}
