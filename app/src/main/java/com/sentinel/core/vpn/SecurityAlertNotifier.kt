package com.sentinel.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sentinel.core.rules.LookalikeDomainDetector
import com.tacu.nsfwzerotrust.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fires the two new user-facing notifications for the Look-Alike Domain Shield —
 * a separate file from [NotificationHelper] so the existing persistent VPN-status
 * notification and its channel are never touched.
 *
 * Throttling (per explicit product requirement — never more than a handful of
 * interruptions a day):
 *  1. Per-domain dedupe: the same lookalike domain only ever alerts once.
 *  2. Daily cap: at most [MAX_ALERTS_PER_DAY] instant alerts fire per calendar day;
 *     anything beyond that is still logged and still counted in the Daily Summary,
 *     it just doesn't interrupt with another heads-up notification.
 *
 * On Android 13+ nothing appears unless the user allowed notifications; the app asks
 * once when protection is first switched on (SentinelActivity).
 */
object SecurityAlertNotifier {

    private const val ALERT_CHANNEL_ID   = "sentinel_security_alerts"
    private const val SUMMARY_CHANNEL_ID = "sentinel_daily_summary"
    private const val SUMMARY_NOTIFICATION_ID = 4200
    private const val PRIVATE_DNS_NOTIFICATION_ID = 4201
    private const val MAX_ALERTS_PER_DAY = 5

    /** On the activity intent from a look-alike notification: open the Alerts list. */
    const val EXTRA_OPEN_ALERTS = "open_alerts"

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Volatile private var trackedDay: String = dayFormat.format(Date())
    private val alertedDomainsToday = mutableSetOf<String>()
    private var alertsFiredToday = 0
    private val throttleLock = Any()

    private fun resetIfNewDay() {
        val today = dayFormat.format(Date())
        if (today != trackedDay) {
            trackedDay = today
            alertedDomainsToday.clear()
            alertsFiredToday = 0
        }
    }

    /**
     * [blocked] is true only when the firewall really stopped the site ("Block look-alike
     * sites" on, not in watch-only mode). Otherwise the alert is a warning, and says so.
     * Returns true if a notification was actually shown (used only for tests/verification).
     */
    fun notifyLookalike(context: Context, match: LookalikeDomainDetector.LookalikeMatch, blocked: Boolean): Boolean {
        synchronized(throttleLock) {
            resetIfNewDay()
            if (match.queriedDomain in alertedDomainsToday) return false
            if (alertsFiredToday >= MAX_ALERTS_PER_DAY) return false
            alertedDomainsToday.add(match.queriedDomain)
            alertsFiredToday++
        }

        ensureChannels(context)
        // Its own request code, so this intent (with the extra) is not merged with the
        // daily summary's plain "open the app" intent.
        val openIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, com.sentinel.ui.SentinelActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_ALERTS, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (blocked) context.getString(R.string.notif_scam_blocked_title)
                    else "⚠️ " + context.getString(R.string.notif_scam_warning_title)
        val body = context.getString(R.string.notif_scam_body, match.queriedDomain, match.matchedTrustedDomain)

        val notification: Notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                body + " " + context.getString(R.string.notif_scam_tap)
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(match.queriedDomain.hashCode(), notification)
        return true
    }

    fun notifyDailySummary(context: Context, blockedToday: Int) {
        ensureChannels(context)
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, com.sentinel.ui.SentinelActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (blockedToday > 0)
            context.getString(R.string.notif_summary_blocked_title, blockedToday.toString())
        else
            context.getString(R.string.notif_summary_clear_title)

        val body = if (blockedToday > 0)
            context.getString(R.string.notif_summary_blocked_body)
        else
            context.getString(R.string.notif_summary_clear_body)

        val notification: Notification = NotificationCompat.Builder(context, SUMMARY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SUMMARY_NOTIFICATION_ID, notification)
    }

    /** Protection was not started, or was stopped, because Private DNS is in strict mode. */
    fun notifyPrivateDnsPaused(context: Context, provider: String) {
        ensureChannels(context)
        val openIntent = PendingIntent.getActivity(
            context, 2,
            Intent(context, com.sentinel.ui.SentinelActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_ALERTS, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val body = context.getString(R.string.private_dns_body, provider)
        val notification: Notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(R.string.private_dns_paused_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(PRIVATE_DNS_NOTIFICATION_ID, notification)
    }

    // Created every time: re-creating an existing channel only updates its name and
    // description, so they follow the app language. Importance set by the user is kept.
    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                context.getString(R.string.notif_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.notif_channel_alerts_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                SUMMARY_CHANNEL_ID,
                context.getString(R.string.notif_channel_summary),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notif_channel_summary_desc) }
        )
    }
}
