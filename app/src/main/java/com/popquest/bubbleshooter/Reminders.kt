package com.popquest.bubbleshooter

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** Local reminders: full lives, daily gift, event ending and a come-back nudge. No server needed. */
object Reminders {
    const val CHANNEL = "reminders"
    private const val TAG = "pq_reminder"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, ctx.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun schedule(ctx: Context, json: String) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelAllWorkByTag(TAG)
        val list = runCatching { JSONArray(json) }.getOrNull() ?: return
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val delay = o.optLong("delay", 0)
            if (delay < 60_000) continue
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("title" to o.optString("title"), "body" to o.optString("body"), "nid" to (100 + i)))
                .addTag(TAG)
                .build()
            wm.enqueueUniqueWork("pq_" + o.optString("id", "r$i"), ExistingWorkPolicy.REPLACE, req)
        }
    }

    fun cancelAll(ctx: Context) {
        WorkManager.getInstance(ctx).cancelAllWorkByTag(TAG)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    @SuppressLint("MissingPermission")
    override fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        Reminders.ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF5D8F.toInt())
            .setContentTitle(inputData.getString("title") ?: ctx.getString(R.string.app_name))
            .setContentText(inputData.getString("body") ?: "")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(ctx).notify(inputData.getInt("nid", 100), n)
        return Result.success()
    }
}
