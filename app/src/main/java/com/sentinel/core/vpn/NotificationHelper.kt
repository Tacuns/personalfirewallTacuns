package com.sentinel.core.vpn

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tacu.nsfwzerotrust.R

object NotificationHelper {
    private const val CHANNEL_ID    = "sentinel_vpn_channel"
    const val         NOTIFICATION_ID = 1337

    fun createNotification(context: Context, blockedToday: Int = 0): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TacU-NS Firewall Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows protection status while firewall is active"
                setShowBadge(false)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, com.sentinel.ui.SentinelActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            context, 1,
            Intent(context, SentinelVpnService::class.java).apply {
                action = SentinelVpnService.ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (blockedToday > 0) "Blocked $blockedToday threats today" else "Monitoring DNS traffic"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("TacU-NS Firewall • Active")
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_shield, "Stop Firewall", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun updateNotification(context: Context, blockedToday: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, createNotification(context, blockedToday))
    }

    fun getNotificationId() = NOTIFICATION_ID
}
