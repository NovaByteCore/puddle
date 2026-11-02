package com.puddle.water

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/* ------------------------------------------------------------------------------------------
 * How reminders work
 *
 * Only one alarm is ever scheduled. Each time it fires (or the app opens, the phone restarts,
 * or the clock changes) Scheduler.tick() looks at the saved state and decides what to do now,
 * then schedules the single next alarm. Because there is never more than one alarm, a phone
 * that was off for hours gets at most one catch-up reminder, never a backlog.
 *
 * A reminder "cycle" starts when a check-in is due. It stays open, sending follow-ups every
 * followUpMin minutes, until you tap "I drank" or log a drink. Opening or swiping away the
 * notification does not end it. Snoozing pauses follow-ups until the snooze ends.
 * At the end of your waking hours an open cycle is closed quietly and starts fresh tomorrow.
 * ---------------------------------------------------------------------------------------- */

object Window {
    private fun minuteOfDay(t: Long): Int {
        val z = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
        return z.hour * 60 + z.minute
    }

    /** True if minute m falls in [start, end), allowing the range to wrap past midnight. */
    fun inRange(m: Int, start: Int, end: Int, sameMeansAllDay: Boolean = false): Boolean = when {
        start == end -> sameMeansAllDay
        start < end -> m in start until end
        else -> m >= start || m < end
    }

    fun inWaking(s: Settings, t: Long) = inRange(minuteOfDay(t), s.wakeStartMin, s.wakeEndMin, sameMeansAllDay = true)

    fun inQuiet(s: Settings, t: Long) = s.quietOn && inRange(minuteOfDay(t), s.quietStartMin, s.quietEndMin)

    fun allowed(s: Settings, t: Long) = inWaking(s, t) && !inQuiet(s, t)

    /** The first moment at or after t when reminders are allowed, or null if never. */
    fun nextAllowed(s: Settings, t: Long): Long? {
        if (allowed(s, t)) return t
        var x = (t / 60_000L + 1) * 60_000L
        repeat(2 * 24 * 60) {
            if (allowed(s, x)) return x
            x += 60_000L
        }
        return null
    }

    /** Minutes of reminder-allowed time left in the current stretch, starting at t. */
    fun minutesLeft(s: Settings, t: Long): Int {
        var x = t
        var n = 0
        while (n < 24 * 60 && allowed(s, x)) {
            n++
            x += 60_000L
        }
        return n
    }

    fun startOfTomorrow(now: Long): Long =
        LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

object Scheduler {
    const val ACTION_ALARM = "com.puddle.water.ALARM"
    const val ACTION_DRANK = "com.puddle.water.DRANK"
    const val ACTION_SNOOZE = "com.puddle.water.SNOOZE"
    private const val SLACK_MS = 30_000L
    private const val MIN = 60_000L

    /** Decide what should happen right now, then schedule the one next alarm. Safe to call any time. */
    @Synchronized
    fun tick(ctx: Context) {
        val store = Store.get(ctx)
        val st = store.state.value
        val s = st.settings
        val now = System.currentTimeMillis()

        if (!s.onboarded || !s.remindersOn) {
            closeCycle(store)
            Notifier.cancelNag(ctx)
            cancelAlarm(ctx)
            return
        }

        if (s.pausedUntil > now) {
            closeCycle(store)
            Notifier.cancelNag(ctx)
            setAlarm(ctx, s.pausedUntil)
            return
        }

        if (!Window.allowed(s, now)) {
            val reopen = Window.nextAllowed(s, now)
            if (!Window.inWaking(s, now)) {
                // Overnight: end any open reminder quietly. Tomorrow starts fresh.
                closeCycle(store)
                Notifier.cancelNag(ctx)
                if (reopen != null) store.settings { it.copy(nextAt = reopen) }
            }
            // During quiet hours an open reminder simply waits; follow-ups resume afterwards.
            if (reopen != null) setAlarm(ctx, reopen) else cancelAlarm(ctx)
            return
        }

        val sum = Summary(st)
        if (sum.todayEff >= s.goalMl) {
            closeCycle(store)
            Notifier.cancelNag(ctx)
            val tomorrow = Window.nextAllowed(s, Window.startOfTomorrow(now))
            store.settings { it.copy(nextAt = tomorrow ?: 0L) }
            if (tomorrow != null) setAlarm(ctx, tomorrow) else cancelAlarm(ctx)
            return
        }

        if (s.reminderPending) {
            // If reminders were blocked at some point since the last nudge (night, phone off
            // overnight), start a fresh cycle instead of picking up mid-nag.
            val gap = s.lastNagAt > 0 && s.lastNagAt + (Window.minutesLeft(s, s.lastNagAt) + 1) * MIN < now
            if (gap) {
                startCycle(ctx, store, now)
                return
            }
            val due = if (s.snoozedUntil > 0) s.snoozedUntil else s.lastNagAt + s.followUpMin * MIN
            if (now + SLACK_MS >= due) {
                val n = s.nagCount + 1
                store.settings { it.copy(nagCount = n, lastNagAt = now, snoozedUntil = 0L) }
                Notifier.showNag(ctx, n)
                setAlarm(ctx, now + s.followUpMin * MIN)
            } else {
                setAlarm(ctx, due)
            }
            return
        }

        var next = s.nextAt
        if (next <= 0L) {
            next = computeNext(st, now)
            store.settings { it.copy(nextAt = next) }
        }
        if (now + SLACK_MS >= next) startCycle(ctx, store, now) else setAlarm(ctx, next)
    }

    private fun startCycle(ctx: Context, store: Store, now: Long) {
        val s = store.state.value.settings
        store.settings {
            it.copy(pendingSince = now, nagCount = 0, lastNagAt = now, snoozedUntil = 0L, nextAt = 0L)
        }
        Notifier.showNag(ctx, 0)
        setAlarm(ctx, now + s.followUpMin * MIN)
    }

    private fun closeCycle(store: Store) {
        val s = store.state.value.settings
        if (s.pendingSince != 0L || s.snoozedUntil != 0L || s.nagCount != 0) {
            store.settings { it.copy(pendingSince = 0L, nagCount = 0, lastNagAt = 0L, snoozedUntil = 0L) }
        }
    }

    /**
     * When should the next check-in be? Spread what's left of today's goal across the waking
     * time that's left, one "I drank" serving at a time. Never wait longer than your check-in
     * frequency, and never come back sooner than half of it (or 20 minutes).
     */
    fun computeNext(st: AppState, now: Long): Long {
        val s = st.settings
        val remaining = s.goalMl - Summary(st).todayEff
        if (remaining <= 0) {
            return Window.nextAllowed(s, Window.startOfTomorrow(now)) ?: (now + s.intervalMin * MIN)
        }
        val left = Window.minutesLeft(s, now)
        if (left <= 0) return Window.nextAllowed(s, now) ?: (now + s.intervalMin * MIN)
        val servings = ceil(remaining / maxOf(s.quickAddMl, 30.0)).coerceAtLeast(1.0)
        val pace = left / servings
        val floor = maxOf(20.0, s.intervalMin / 2.0)
        val gapMin = pace.coerceIn(minOf(floor, s.intervalMin.toDouble()), s.intervalMin.toDouble())
        val target = now + (gapMin * MIN).toLong()
        return Window.nextAllowed(s, target) ?: target
    }

    /** A drink was logged: close the reminder and pace the next one from now. */
    @Synchronized
    fun onDrank(ctx: Context) {
        val store = Store.get(ctx)
        val now = System.currentTimeMillis()
        store.settings { it.copy(pendingSince = 0L, nagCount = 0, lastNagAt = 0L, snoozedUntil = 0L) }
        val next = computeNext(store.state.value, now)
        store.settings { it.copy(nextAt = next) }
        Notifier.cancelNag(ctx)
        tick(ctx)
    }

    @Synchronized
    fun snooze(ctx: Context) {
        val store = Store.get(ctx)
        val now = System.currentTimeMillis()
        val s = store.state.value.settings
        val until = now + s.snoozeMin * MIN
        store.settings {
            it.copy(
                pendingSince = if (it.pendingSince > 0) it.pendingSince else now,
                lastNagAt = if (it.lastNagAt > 0) it.lastNagAt else now,
                snoozedUntil = until,
            )
        }
        Notifier.cancelNag(ctx)
        setAlarm(ctx, until)
    }

    /** Settings changed: re-pace the next check-in from now (unless a reminder is already open). */
    @Synchronized
    fun settingsChanged(ctx: Context) {
        val store = Store.get(ctx)
        val now = System.currentTimeMillis()
        if (!store.state.value.settings.reminderPending) {
            val next = computeNext(store.state.value, now)
            store.settings { it.copy(nextAt = next) }
        }
        tick(ctx)
    }

    @Synchronized
    fun pause(ctx: Context, until: Long) {
        Store.get(ctx).settings { it.copy(pausedUntil = until) }
        tick(ctx)
    }

    @Synchronized
    fun resume(ctx: Context) {
        Store.get(ctx).settings { it.copy(pausedUntil = 0L, nextAt = 0L) }
        tick(ctx)
    }

    /** Fire a real reminder now so the buttons can be tried out. */
    @Synchronized
    fun testNow(ctx: Context) {
        val store = Store.get(ctx)
        startCycle(ctx, store, System.currentTimeMillis())
    }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    private fun alarmIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 1,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun setAlarm(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(ctx)
        val time = maxOf(at, System.currentTimeMillis() + 5_000L)
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    private fun cancelAlarm(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(ctx))
    }

    /** Backup: WorkManager runs tick() about every 15 minutes in case an alarm was lost. */
    fun ensureSafetyNet(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("puddle-reminders", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

    fun fmtTime(t: Long): String = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(timeFmt)

    fun fmtWhen(t: Long): String {
        val z: ZonedDateTime = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
        val day = when (z.toLocalDate()) {
            LocalDate.now() -> "today"
            LocalDate.now().plusDays(1) -> "tomorrow"
            else -> z.format(DateTimeFormatter.ofPattern("EEE"))
        }
        return "$day at ${z.format(timeFmt)}"
    }

    /** One line for the settings screen about what happens next. */
    fun status(s: Settings, now: Long): String = when {
        !s.remindersOn -> "Reminders are off."
        s.pausedUntil > now -> "Paused until ${fmtWhen(s.pausedUntil)}."
        s.reminderPending && s.snoozedUntil > now -> "Snoozed until ${fmtTime(s.snoozedUntil)}."
        s.reminderPending -> "A reminder is waiting for you to drink."
        s.nextAt > now -> "Next check-in ${fmtWhen(s.nextAt)}."
        else -> "Next check-in is coming up."
    }
}

/** Logging a drink from anywhere goes through here so reminders always hear about it. */
object Hydration {
    fun log(ctx: Context, ml: Double, drinkId: String) {
        val store = Store.get(ctx)
        store.addDrink(ml, drinkId)
        if (Drinks.byId(drinkId).factor > 0) Scheduler.onDrank(ctx)
    }
}

/** What the llama says. Playful and persistent, never guilt-trippy. */
object LlamaLines {
    private val openers = listOf(
        "Water check. The llama is waiting." to "Grab a glass when you get a second.",
        "Sip o'clock!" to "The llama has checked the schedule. It's time.",
        "The llama would like a word" to "The word is water.",
        "Hydration station is open" to "No line, no wait. Just you and a glass of water.",
    )

    private val followUps = listOf(
        "Still no sip? I'll be back." to "The llama is patient. Mostly.",
        "The llama has not forgotten" to "One sip and I'll stop. For a little while.",
        "Knock knock" to "Who's there? Water. Water who? Water you waiting for?",
        "The llama is staring at your glass" to "Not blinking. Very lovingly. Very intensely.",
        "Reminder #%d. The llama is counting." to "I have all day. You have water. Let's make a deal.",
        "Formal llama request submitted" to "Subject: sips. Status: pending. Urgency: extremely fluffy.",
        "*dramatic llama sigh*" to "A single sip would cure me. Probably.",
        "New tactic: asking nicely" to "Pretty please? With a lemon slice on top?",
        "The llama wrote you a song" to "It has one word. The word is \"drink.\" It's on repeat.",
        "Still here. Still fond of you." to "Still waiting on that sip, though.",
        "Llamas are famously stubborn" to "I'm told it's my best quality.",
        "Plot twist: it's still about water" to "You knew it would be. One sip, then freedom.",
    )

    /** n = 0 is the first nudge, 1+ are follow-ups. */
    fun line(n: Int): Pair<String, String> {
        val pair = if (n <= 0) {
            openers[(System.currentTimeMillis() / 60_000L % openers.size).toInt()]
        } else if (n <= followUps.size) {
            followUps[n - 1]
        } else {
            // After the full set, keep cycling through the sillier half.
            val loop = followUps.drop(3)
            loop[(n - followUps.size - 1) % loop.size]
        }
        return pair.first.replace("%d", (n + 1).toString()) to pair.second
    }
}

object Notifier {
    private const val NAG_ID = 42
    private const val LOGGED_ID = 43
    const val CH_SOUND = "nag_sound"
    const val CH_VIBRATE = "nag_vibrate"
    const val CH_SILENT = "nag_silent"
    private const val CH_LOGGED = "logged"

    fun channelFor(tone: String) = when (tone) {
        "vibrate" -> CH_VIBRATE
        "silent" -> CH_SILENT
        else -> CH_SOUND
    }

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.deleteNotificationChannel("reminders") // from the first version
        val sound = NotificationChannel(CH_SOUND, "Reminders: sound", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Drink reminders with your notification sound"
            enableVibration(true)
        }
        val vib = NotificationChannel(CH_VIBRATE, "Reminders: vibrate only", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Drink reminders that vibrate without sound"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
        }
        val silent = NotificationChannel(CH_SILENT, "Reminders: silent", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Drink reminders that appear quietly"
        }
        val logged = NotificationChannel(CH_LOGGED, "Drink logged", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Quick confirmation after you tap I drank"
        }
        nm.createNotificationChannels(listOf(sound, vib, silent, logged))
    }

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun progress(ctx: Context): String {
        val sum = Summary(Store.get(ctx).state.value)
        return "You're at ${fmtAmount(sum.todayEff, sum.unit)} of ${fmtWithUnit(sum.goal, sum.unit)} today."
    }

    @SuppressLint("MissingPermission")
    fun showNag(ctx: Context, n: Int) {
        if (!canNotify(ctx)) return
        val s = Store.get(ctx).state.value.settings
        val (title, body) = LlamaLines.line(n)
        val full = "$body\n${progress(ctx)}"

        val open = PendingIntent.getActivity(
            ctx, 10,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val drank = PendingIntent.getBroadcast(
            ctx, 11,
            Intent(ctx, ReminderReceiver::class.java).setAction(Scheduler.ACTION_DRANK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val logIt = PendingIntent.getActivity(
            ctx, 12,
            Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_LOG, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snooze = PendingIntent.getBroadcast(
            ctx, 13,
            Intent(ctx, ReminderReceiver::class.java).setAction(Scheduler.ACTION_SNOOZE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val quiet = s.tone == "silent"
        val b = NotificationCompat.Builder(ctx, channelFor(s.tone))
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(0xFF2BA3C9.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(full))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setOngoing(true) // stays put; swiping it away (where Android allows) doesn't count as drinking
            .setAutoCancel(false)
            .setOnlyAlertOnce(false) // every follow-up buzzes again
            .setNumber(n + 1)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .addAction(0, "I drank ${fmtWithUnit(s.quickAddMl, s.unit)}", drank)
            .addAction(0, "Log water", logIt)
            .addAction(0, "Remind me in ${s.snoozeMin} min", snooze)
        NotificationManagerCompat.from(ctx).notify(NAG_ID, b.build())
    }

    fun cancelNag(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(NAG_ID)
    }

    @SuppressLint("MissingPermission")
    fun showLogged(ctx: Context, ml: Double) {
        if (!canNotify(ctx)) return
        val sum = Summary(Store.get(ctx).state.value)
        val done = sum.todayEff >= sum.goal
        val text = if (done) "Goal reached! The llama is doing a happy dance."
        else "${progress(ctx)} The llama will check back later."
        val b = NotificationCompat.Builder(ctx, CH_LOGGED)
            .setSmallIcon(R.drawable.ic_stat_drop)
            .setColor(0xFF2BA3C9.toInt())
            .setContentTitle("Logged ${fmtWithUnit(ml, sum.unit)} of water")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setTimeoutAfter(6000L)
            .setAutoCancel(true)
        NotificationManagerCompat.from(ctx).notify(LOGGED_ID, b.build())
    }
}

/** Phone and app settings that affect how reliable reminders are. */
object Reliability {
    fun batteryUnrestricted(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun doNotDisturbOn(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        return nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
    }
}

/** Alarm firing plus the "I drank" and "Remind me" buttons. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store.get(context)
        store.reload()
        when (intent.action) {
            Scheduler.ACTION_DRANK -> {
                val ml = store.state.value.settings.quickAddMl
                Hydration.log(context, ml, "water")
                Notifier.showLogged(context, ml)
            }
            Scheduler.ACTION_SNOOZE -> Scheduler.snooze(context)
            else -> Scheduler.tick(context)
        }
    }
}

/** Restart, app update, clock or time zone change, or exact-alarm permission change. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.get(context).reload()
        Notifier.ensureChannels(context)
        Scheduler.tick(context)
    }
}

/** Safety net that re-checks about every 15 minutes. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Store.get(applicationContext).reload()
        Scheduler.tick(applicationContext)
        return Result.success()
    }
}

/** Kept so buttons on notifications from the first version still work after updating. */
class QuickAddReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ml = intent.getDoubleExtra("ml", 8 * ML_PER_OZ)
        Store.get(context).reload()
        Hydration.log(context, ml, "water")
        Notifier.showLogged(context, ml)
    }
}
