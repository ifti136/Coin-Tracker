package com.cointracker.mobile.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cointracker.mobile.MainActivity
import com.cointracker.mobile.R

object NotificationHelper {

    private const val CH_MILESTONES = "ch_milestones"
    private const val CH_REMINDER   = "ch_reminder"

    // Call once at app start (idempotent)
    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_MILESTONES, "Achievements", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "New achievement unlocked" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, "Daily Reminder", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Remind to log today's coins" }
        )
    }

    // ── Milestone / achievement unlock ────────────────────────────────────────

    fun notifyMilestone(ctx: Context, icon: String, name: String, desc: String) {
        val intent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(ctx, CH_MILESTONES)
            .setSmallIcon(R.drawable.coin)
            .setContentTitle("$icon Achievement Unlocked!")
            .setContentText("$name — $desc")
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        // Use hash of name as ID so same achievement doesn't double-fire
        NotificationManagerCompat.from(ctx).notify(name.hashCode(), notif)
    }

    // ── Daily reminder (called by Worker) ─────────────────────────────────────

    fun notifyDailyReminder(ctx: Context) {
        val intent = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(ctx, CH_REMINDER)
            .setSmallIcon(R.drawable.coin)
            .setContentTitle("Don't forget your coins! 🪙")
            .setContentText("You haven't logged today's eFootball coins yet.")
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()

        NotificationManagerCompat.from(ctx).notify(9999, notif)
    }
}
