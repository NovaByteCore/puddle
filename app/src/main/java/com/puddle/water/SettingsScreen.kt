@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.puddle.water

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val clockFmt = DateTimeFormatter.ofPattern("h:mm a")
fun minuteLabel(m: Int): String = LocalTime.of((m / 60) % 24, m % 60).format(clockFmt)
private fun wrapDay(m: Int) = ((m % 1440) + 1440) % 1440

@Composable
fun SettingsScreen(store: Store, sum: Summary, tick: Int, requestNotif: () -> Unit) {
    val ctx = LocalContext.current
    val s = sum.settings
    val unit = s.unit
    var confirmClear by remember { mutableStateOf(false) }
    var weightText by remember(s.weightLbs, unit) {
        mutableStateOf(if (unit == "oz") "${s.weightLbs}" else "${(s.weightLbs / 2.2046).toInt()}")
    }
    val step = if (unit == "oz") 4 * ML_PER_OZ else 100.0

    /** Change a setting that affects reminder timing, then re-plan. */
    fun remind(change: (Settings) -> Settings) {
        store.settings(change)
        Scheduler.settingsChanged(ctx)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

        SettingsCard("Daily goal") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { remind { it.copy(goalMl = maxOf(step * 2, it.goalMl - step)) } }) { Text("−", fontSize = 20.sp) }
                Text(fmtWithUnit(s.goalMl, unit), fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                FilledTonalButton(onClick = { remind { it.copy(goalMl = it.goalMl + step) } }) { Text("+", fontSize = 20.sp) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Work out a goal for me", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it.filter(Char::isDigit).take(3) },
                label = { Text(if (unit == "oz") "Weight (lbs)" else "Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ActivityChips(s.activity) { a -> store.settings { it.copy(activity = a) } }
            val lbs = weightText.toIntOrNull()?.let { if (unit == "oz") it else (it * 2.2046).toInt() }
            if (lbs != null && lbs > 40) {
                val suggested = suggestedGoalMl(lbs, s.activity)
                OutlinedButton(onClick = {
                    remind { it.copy(goalMl = suggested, weightLbs = lbs) }
                    Toast.makeText(ctx, "Goal set to ${fmtWithUnit(suggested, unit)}", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Use suggested goal: ${fmtWithUnit(suggested, unit)}") }
            }
            Text(MEDICAL_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsCard("Reminders") {
            ToggleRow("Remind me to drink", s.remindersOn) { on ->
                remind { it.copy(remindersOn = on, pausedUntil = 0L) }
                if (on) requestNotif()
            }
            Text(
                Scheduler.status(s, System.currentTimeMillis()),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )

            if (s.remindersOn) {
                // Pause
                if (s.pausedUntil > System.currentTimeMillis()) {
                    Button(onClick = { Scheduler.resume(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Resume reminders now") }
                } else {
                    Text("Take a break", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { Scheduler.pause(ctx, System.currentTimeMillis() + 60 * 60_000L) }, label = { Text("Pause 1 hr") })
                        AssistChip(onClick = { Scheduler.pause(ctx, System.currentTimeMillis() + 3 * 60 * 60_000L) }, label = { Text("Pause 3 hr") })
                        AssistChip(onClick = {
                            val now = System.currentTimeMillis()
                            Scheduler.pause(ctx, Window.nextAllowed(s, Window.startOfTomorrow(now)) ?: Window.startOfTomorrow(now))
                        }, label = { Text("Until tomorrow") })
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Waking hours", fontWeight = FontWeight.Bold)
                Hint("Reminders only come between these times. Nothing overnight.")
                TimeStepper("Start", s.wakeStartMin) { m -> remind { it.copy(wakeStartMin = m) } }
                TimeStepper("End", s.wakeEndMin) { m -> remind { it.copy(wakeEndMin = m) } }

                ToggleRow("Quiet hours", s.quietOn) { on -> remind { it.copy(quietOn = on) } }
                if (s.quietOn) {
                    Hint("A break inside your waking hours, like lunch or a meeting. Follow-ups wait until it ends.")
                    TimeStepper("From", s.quietStartMin) { m -> remind { it.copy(quietStartMin = m) } }
                    TimeStepper("Until", s.quietEndMin) { m -> remind { it.copy(quietEndMin = m) } }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Check in at least every", fontWeight = FontWeight.Bold)
                ChoiceChips(listOf(30, 45, 60, 90, 120, 180), s.intervalMin, ::durationLabel) { m -> remind { it.copy(intervalMin = m) } }
                Hint("When you're behind on your goal, check-ins can come up to twice as often (never closer than 20 min).")

                Text("If I don't answer, follow up every", fontWeight = FontWeight.Bold)
                ChoiceChips(listOf(3, 5, 10, 15, 30), s.followUpMin, ::durationLabel) { m -> remind { it.copy(followUpMin = m) } }
                if (s.followUpMin < 10 && !Scheduler.canExact(ctx)) {
                    Hint("Short follow-ups need \"Alarms & reminders\" turned on below, or Android may space them out to about 10 minutes.")
                }

                Text("\"Remind me later\" snoozes for", fontWeight = FontWeight.Bold)
                ChoiceChips(listOf(5, 10, 15, 30), s.snoozeMin, ::durationLabel) { m -> store.settings { it.copy(snoozeMin = m) } }

                Text("\"I drank\" logs", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickAddChoicesMl(unit).forEach { ml ->
                        FilterChip(
                            selected = fmtAmount(s.quickAddMl, unit) == fmtAmount(ml, unit),
                            onClick = { remind { it.copy(quickAddMl = ml) } },
                            label = { Text(fmtWithUnit(ml, unit)) },
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Notification tone", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("sound" to "Sound", "vibrate" to "Vibrate only", "silent" to "Silent").forEach { (id, label) ->
                        FilterChip(selected = s.tone == id, onClick = { store.settings { it.copy(tone = id) } }, label = { Text(label) })
                    }
                }
                if (s.tone == "sound") {
                    TextButton(onClick = { openChannel(ctx, Notifier.CH_SOUND) }) { Text("Choose the reminder sound") }
                }

                OutlinedButton(onClick = {
                    if (!Notifier.canNotify(ctx)) requestNotif()
                    Scheduler.testNow(ctx)
                    Toast.makeText(ctx, "Test reminder sent. Try the buttons on it.", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Send a test reminder now") }
            }
        }

        ReliabilityCard(tick, requestNotif)

        SettingsCard("Units") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = unit == "oz", onClick = {
                    remind { it.copy(unit = "oz", quickAddMl = quickAddChoicesMl("oz")[0]) }
                }, label = { Text("Ounces (oz)") })
                FilterChip(selected = unit == "ml", onClick = {
                    remind { it.copy(unit = "ml", quickAddMl = quickAddChoicesMl("ml")[0]) }
                }, label = { Text("Milliliters (ml)") })
            }
        }

        SettingsCard("Your data") {
            Text("Everything you log is saved inside Puddle on this phone. Nothing is sent anywhere.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { confirmClear = true }) { Text("Clear today's drinks") }
        }
        Text("Puddle ${appVersion(ctx)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear today's drinks?") },
            text = { Text("This removes the ${sum.todayList.size} drinks you logged today. Earlier days stay.") },
            confirmButton = {
                TextButton(onClick = {
                    store.clearDay { Stats.dayOf(it.time) != sum.today }
                    Scheduler.settingsChanged(ctx)
                    confirmClear = false
                }) { Text("Clear today") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep them") } },
        )
    }
}

/** Shows each phone setting that affects reminders, with a button to fix it. */
@Composable
fun ReliabilityCard(tick: Int, requestNotif: () -> Unit) {
    val ctx = LocalContext.current
    val notif = remember(tick) { Notifier.canNotify(ctx) }
    val exact = remember(tick) { Scheduler.canExact(ctx) }
    val battery = remember(tick) { Reliability.batteryUnrestricted(ctx) }
    val dnd = remember(tick) { Reliability.doNotDisturbOn(ctx) }

    SettingsCard("Make reminders reliable") {
        Hint(
            "Puddle schedules reminders with Android's own alarm system. Your phone stays in charge: " +
                "Do Not Disturb, notification settings and battery saving all apply, and Puddle can't override them.",
        )
        StatusRow(
            ok = notif,
            title = "Notifications",
            okText = "Allowed",
            badText = "Off. Reminders only show inside the app.",
            fix = "Turn on",
        ) {
            requestNotif()
            openAppNotifications(ctx)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            StatusRow(
                ok = exact,
                title = "On-time reminders",
                okText = "Allowed",
                badText = "Off. Reminders may arrive a few minutes late.",
                fix = "Allow",
            ) {
                tryStart(ctx, Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
            }
        }
        StatusRow(
            ok = battery,
            title = "Battery",
            okText = "Unrestricted",
            badText = "Optimized. Samsung may delay or stop reminders.",
            fix = "Fix",
        ) {
            tryStart(ctx, Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
        }
        if (!battery) Hint("In App info, tap Battery and choose Unrestricted.")
        if (dnd) {
            Text(
                "Do Not Disturb is on right now, so reminders will arrive silently until it's off.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Hint(
            "On newer Android versions you can still swipe a reminder away. That doesn't count as drinking, " +
                "so the next follow-up still comes. If you force stop Puddle, reminders stop until you open it again.",
        )
    }
}

@Composable
private fun StatusRow(ok: Boolean, title: String, okText: String, badText: String, fix: String, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ok) "✓" else "!",
            fontWeight = FontWeight.Black,
            color = if (ok) Color(0xFF3F8F45) else MaterialTheme.colorScheme.error,
            modifier = Modifier.width(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(if (ok) okText else badText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ok) TextButton(onClick = onFix) { Text(fix) }
    }
}

fun durationLabel(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} hr"
    else -> "${m / 60}½ hr"
}

@Composable
private fun ChoiceChips(options: List<Int>, selected: Int, label: (Int) -> String, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = selected == o, onClick = { onPick(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun TimeStepper(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = { onChange(wrapDay(minutes - 30)) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("−") }
        Text(minuteLabel(minutes), fontWeight = FontWeight.Black, modifier = Modifier.width(92.dp), textAlign = TextAlign.Center)
        FilledTonalButton(onClick = { onChange(wrapDay(minutes + 30)) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("+") }
    }
}

private fun tryStart(ctx: Context, i: Intent) {
    try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            ctx.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }
}

fun openAppNotifications(ctx: Context) {
    tryStart(ctx, Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
}

private fun openChannel(ctx: Context, channel: String) {
    tryStart(
        ctx,
        Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
            .putExtra(AndroidSettings.EXTRA_CHANNEL_ID, channel),
    )
}

private fun appVersion(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
} catch (_: Exception) {
    ""
}

@Composable
fun ActivityChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Light", "Moderate", "Very active").forEachIndexed { i, label ->
            FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(label) })
        }
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(title)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
