// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Posts local notifications for notify-worthy messages that arrive while the app
 * is backgrounded. Rendering is decoupled from delivery so a future FCM path can
 * feed the same [post] with a plain [NotifiableEvent] — no server push exists yet
 * (native FCM is roadmapped), so today this only covers the window the socket
 * stays alive after backgrounding.
 */
object Notifier {
    private const val CHANNEL_MENTIONS = "mentions"
    private const val CHANNEL_DMS = "dms"
    const val CHANNEL_SERVICE = "connection"
    const val EXTRA_NETWORK_ID = "lurker.networkId"
    const val EXTRA_TARGET = "lurker.target"

    fun ensureChannels(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_MENTIONS, "Mentions & highlights", NotificationManager.IMPORTANCE_HIGH),
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_DMS, "Direct messages", NotificationManager.IMPORTANCE_HIGH),
        )
        // Silent, low-key ongoing notification for the opt-in background service.
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Background connection", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    fun post(context: Context, e: NotifiableEvent) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        ensureChannels(context)
        val tap = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            e.networkId?.let { putExtra(EXTRA_NETWORK_ID, it) }
            putExtra(EXTRA_TARGET, e.target)
        }
        val pi = PendingIntent.getActivity(
            context,
            "${e.networkId}::${e.target}".hashCode(),
            tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (e.isDm) e.nick else "${e.nick} · ${e.target}"
        val notif = NotificationCompat.Builder(context, if (e.isDm) CHANNEL_DMS else CHANNEL_MENTIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(e.text.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(e.text.take(400)))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            // One notification per buffer — a burst collapses onto the same id.
            nm.notify("${e.networkId}::${e.target}".hashCode(), notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted; nothing to show.
        }
    }
}
