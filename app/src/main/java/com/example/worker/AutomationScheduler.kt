package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object AutomationScheduler {
    private const val DAILY_WORK_NAME = "dusklore_daily_7pm_shorts_automation"

    /**
     * Schedules the daily background generator 30 minutes before the chosen 7:00 publish slot
     * so the 15s Short is rendered and uploaded to YouTube with `publishAt` locked to exact 7:00.
     */
    fun syncDailySchedule(
        context: Context,
        enabled: Boolean,
        publishHour: Int,
        publishMinute: Int
    ) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(DAILY_WORK_NAME)
            return
        }

        val initialDelayMs = computeDelayUntilPrepWindowMs(publishHour, publishMinute)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<DailyShortWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun computeDelayUntilPrepWindowMs(publishHour: Int, publishMinute: Int): Long {
        val now = Calendar.getInstance()
        val prep = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, publishHour.coerceIn(0, 23))
            set(Calendar.MINUTE, publishMinute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -30) // Generate & upload 30 mins ahead of 7:00 YouTube publishAt
        }
        if (prep.timeInMillis <= now.timeInMillis + 60_000L) {
            prep.add(Calendar.DAY_OF_YEAR, 1)
        }
        return (prep.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
    }

    fun formatCountdownToNextPublish(publishHour: Int, publishMinute: Int): String {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, publishHour.coerceIn(0, 23))
            set(Calendar.MINUTE, publishMinute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now.timeInMillis) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }
        val diffMs = (next.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs) % 60
        return "%02d soat %02d daq".format(hours, minutes)
    }
}
