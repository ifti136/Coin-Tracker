package com.cointracker.mobile.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import java.time.*
import java.util.concurrent.TimeUnit

@HiltWorker
class DailyReminderWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        // Check if user already logged a transaction today (UTC date)
        val today = LocalDate.now(ZoneOffset.UTC).toString()          // "2025-06-15"
        val prefs: SharedPreferences = applicationContext
            .getSharedPreferences("cointracker_prefs", Context.MODE_PRIVATE)
        val currentProfile = prefs.getString("last_profile", "Default") ?: "Default"

        return try {
            val doc = FirebaseFirestore.getInstance()
                .collection("user_data").document(uid).get().await()
            val profiles = doc.data?.get("profiles") as? Map<*, *>
            val txns = (profiles?.get(currentProfile) as? Map<*, *>)
                ?.get("transactions") as? List<*>

            val loggedToday = txns?.any { tx ->
                val txMap = tx as? Map<*, *> ?: return@any false
                val date  = txMap["date"] as? String ?: return@any false
                date.startsWith(today)
            } ?: false

            if (!loggedToday) {
                NotificationHelper.notifyDailyReminder(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "daily_coin_reminder"

        /**
         * Schedule daily at 8pm BDT = 14:00 UTC.
         * Call from Application.onCreate() or after login.
         */
        fun schedule(ctx: Context) {
            val now     = ZonedDateTime.now(ZoneOffset.UTC)
            val target  = now.toLocalDate().atTime(14, 0).atZone(ZoneOffset.UTC) // 8pm BDT
            val initial = if (now.isBefore(target)) target else target.plusDays(1)
            val delay   = Duration.between(now, initial).toMinutes()

            val req = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,   // don't reset timer if already scheduled
                req
            )
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
        }
    }
}
