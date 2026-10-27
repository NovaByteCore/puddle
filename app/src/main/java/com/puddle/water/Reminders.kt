package com.puddle.water

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Reminders {
    private const val CHANNEL = "reminders"
    private const val NOTIF_ID = 42

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(CHANNEL, "Drink reminders", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "Nudges to drink during the day"
        nm.createNotificationChannel(ch)
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("puddle-reminders", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun nudge(ctx: Context) {
        val sum = Summary(Store.get(ctx).state.value)
        val name = Critters.byId(sum.settings.activeCritter).name
        val title = listOf(
            "$name is getting thirsty",
            "Time for a sip!",
            "$name's jar is running low",
            "Water break?",
        ).random()
        val text = "You're at ${fmtAmount(sum.todayEff, sum.unit)} of ${fmtWithUnit(sum.goal, sum.unit)} today. " +
            "Tap a button below to log water."
        show(ctx, title, text, withActions = true)
    }

    fun logged(ctx: Context, ml: Double) {
        val sum = Summary(Store.get(ctx).state.value)
        val name = Critters.byId(sum.settings.activeCritter).name
        val done = sum.todayEff >= sum.goal
        val text = if (done) "Goal reached! $name is happily floating."
        else "You're at ${fmtAmount(sum.todayEff, sum.unit)} of ${fmtWithUnit(sum.goal, sum.unit)}. $name says thanks!"
        show(ctx, "Logged ${fmtWithUnit(ml, sum.unit)} of water", text, withActions = false, timeoutMs = 6000L)
    }

    @SuppressLint("MissingPermission")
    private fun show(ctx: Context, title: String, text: String, withActions: Boolean, timeoutMs: Long = 0L) {
        if (!canNotify(ctx)) return
        val settings = Store.get(ctx).state.value.settings
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(0xFF2BA3C9.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(!withActions)
        if (timeoutMs > 0) b.setTimeoutAfter(timeoutMs)
        if (withActions) {
            listOf(settings.quickAddMl, settings.quickAddMl * 2).forEachIndexed { i, ml ->
                val pi = PendingIntent.getBroadcast(
                    ctx, 100 + i,
                    Intent(ctx, QuickAddReceiver::class.java).putExtra("ml", ml),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                b.addAction(0, "+ ${fmtWithUnit(ml, settings.unit)}", pi)
            }
        }
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, b.build())
    }
}

/** Runs about every 15 minutes and decides whether a nudge is due. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val store = Store.get(applicationContext)
        store.reload()
        val st = store.state.value
        val s = st.settings
        if (!s.onboarded || !s.remindersOn) return Result.success()

        val now = LocalDateTime.now()
        val h = now.hour + now.minute / 60.0
        if (h < s.startHour || h >= s.endHour) return Result.success()

        val sum = Summary(st)
        if (sum.todayEff >= s.goalMl) return Result.success()

        val nowMs = System.currentTimeMillis()
        val lastDrink = sum.todayList.maxOfOrNull { it.time } ?: 0L
        val last = maxOf(lastDrink, s.lastReminderAt)
        if (nowMs - last < s.intervalMin * 60_000L) return Result.success()

        if (s.smartSkip) {
            val span = maxOf(1, s.endHour - s.startHour).toDouble()
            val expected = s.goalMl * ((h - s.startHour) / span).coerceIn(0.0, 1.0)
            if (sum.todayEff > 0 && sum.todayEff >= expected) return Result.success()
        }

        Reminders.nudge(applicationContext)
        store.settings { it.copy(lastReminderAt = nowMs) }
        return Result.success()
    }
}

/** Handles the "+8 oz" buttons on a reminder. */
class QuickAddReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ml = intent.getDoubleExtra("ml", 8 * ML_PER_OZ)
        val store = Store.get(context)
        store.reload()
        store.addDrink(ml, "water")
        Reminders.logged(context, ml)
    }
}
