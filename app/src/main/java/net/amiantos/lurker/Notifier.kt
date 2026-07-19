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
    // Bumped from "connection" (was IMPORTANCE_LOW) so existing installs pick up
    // the quieter IMPORTANCE_MIN — Android locks a channel's importance after
    // creation, so lowering it requires a new channel id. The old one is deleted
    // in ensureChannels.
    const val CHANNEL_SERVICE = "connection_quiet"
    private const val CHANNEL_SERVICE_OLD = "connection"
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
        // Silent, minimal ongoing notification for the opt-in background service:
        // IMPORTANCE_MIN keeps it out of the status bar and collapsed at the bottom
        // of the shade — a quiet "permanent" indicator rather than a recurring alert
        // (freaky: it kept reappearing and demanding a swipe).
        mgr.deleteNotificationChannel(CHANNEL_SERVICE_OLD)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Background connection", NotificationManager.IMPORTANCE_MIN).apply {
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
        // Strip mIRC bold/color/etc. control codes — the notification is plain text,
        // so raw codes would show as literal "3,01" garbage.
        val body = Mirc.strip(e.text)
        val notif = NotificationCompat.Builder(context, if (e.isDm) CHANNEL_DMS else CHANNEL_MENTIONS)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(400)))
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
