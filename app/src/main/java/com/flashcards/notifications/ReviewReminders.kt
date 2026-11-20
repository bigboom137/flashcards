package com.flashcards.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.flashcards.MainActivity
import com.flashcards.R
import com.flashcards.data.AppDatabase
import java.util.concurrent.TimeUnit

/**
 * "Time to review" notifications. A one-shot job is scheduled for the moment the next
 * card becomes due; a 12-hour periodic job is the safety net for cards that stay due.
 */
object ReviewReminders {
    private const val CHANNEL_ID = "due_reviews"
    private const val NOTIFICATION_ID = 1
    private const val NEXT_DUE_WORK = "next-due-review"
    private const val PERIODIC_WORK = "periodic-due-review"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Review reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when flashcards are due for review"
                }
            )
        }
    }

    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DueReviewWorker>(12, TimeUnit.HOURS).build(),
        )
    }

    /** (Re)schedules the one-shot reminder for the next card that becomes due. */
    suspend fun scheduleNext(context: Context) {
        val now = System.currentTimeMillis()
        val next = AppDatabase.get(context).cardDao().nextNotifiableDueAfter(now) ?: return
        WorkManager.getInstance(context).enqueueUniqueWork(
            NEXT_DUE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DueReviewWorker>()
                .setInitialDelay((next - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, lines: List<String>, totalDue: Int) {
        if (!canPost(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to review")
            .setContentText("$totalDue card${if (totalDue == 1) "" else "s"} due: ${lines.joinToString(", ")}")
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach { style.addLine(it) } })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

class DueReviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val due = db.deckDao().summaries(System.currentTimeMillis())
            .filter { it.due > 0 && it.deck.notify }
        if (due.isNotEmpty()) {
            ReviewReminders.post(
                applicationContext,
                lines = due.map { "${it.deck.name}: ${it.due} card${if (it.due == 1) "" else "s"}" },
                totalDue = due.sumOf { it.due },
            )
        }
        ReviewReminders.scheduleNext(applicationContext)
        com.flashcards.widget.DueWidget.update(applicationContext)
        return Result.success()
    }
}
