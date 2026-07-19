// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Opt-in liveness anchor. It carries no socket of its own — the socket lives on
 * the process-scoped [LurkerApp.client]. Being a foreground service keeps the
 * OS from freezing/killing the process while the app is backgrounded, so the
 * existing WebSocket stays connected and highlight/DM notifications keep firing
 * indefinitely (not just for the minutes the process happens to survive).
 *
 * Gated on [Prefs.backgroundConnect] (default off) and always started/stopped
 * from the foreground, sidestepping the Android 12+ background-FGS-start rules.
 */
class LurkerConnectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs(this).backgroundConnect = false
            stopSelf()
            return START_NOT_STICKY
        }
        Notifier.ensureChannels(this)
        val open = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = android.app.PendingIntent.getService(
            this, 1,
            Intent(this, LurkerConnectionService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, Notifier.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Lurker is staying connected")
            .setContentText("Highlights and DMs will notify you in the background.")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        // If the OS kills us for memory, come back when it can — the app being
        // installed with backgroundConnect on means the user wants us alive.
        return START_STICKY
    }

    companion object {
        private const val NOTIF_ID = 42
        private const val ACTION_STOP = "net.amiantos.lurker.STOP_CONNECTION"

        /** Start the anchor. Call only from the foreground (e.g. app launch or
         *  the settings toggle) so the background-start restriction never applies. */
        fun start(context: Context) {
            val i = Intent(context, LurkerConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LurkerConnectionService::class.java))
        }
    }
}
