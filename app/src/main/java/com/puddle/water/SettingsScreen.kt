@file:OptIn(ExperimentalMaterial3Api::class)

package com.puddle.water

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val hourFmt = DateTimeFormatter.ofPattern("h a")
fun hourLabel(h: Int): String = LocalTime.of(h.coerceIn(0, 23), 0).format(hourFmt)

@Composable
fun SettingsScreen(store: Store, sum: Summary, tick: Int, requestNotif: () -> Unit) {
    val ctx = LocalContext.current
    val s = sum.settings
    val unit = s.unit
    val canNotify = remember(tick, s.remindersOn) { Reminders.canNotify(ctx) }
    var confirmClear by remember { mutableStateOf(false) }
    var weightText by remember(s.weightLbs, unit) {
        mutableStateOf(if (unit == "oz") "${s.weightLbs}" else "${(s.weightLbs / 2.2046).toInt()}")
    }
    val step = if (unit == "oz") 4 * ML_PER_OZ else 100.0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

        SettingsCard("Daily goal") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { store.settings { it.copy(goalMl = maxOf(step * 2, it.goalMl - step)) } }) { Text("−", fontSize = 20.sp) }
                Text(
                    fmtWithUnit(s.goalMl, unit),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                FilledTonalButton(onClick = { store.settings { it.copy(goalMl = it.goalMl + step) } }) { Text("+", fontSize = 20.sp) }
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
                    store.settings { it.copy(goalMl = suggested, weightLbs = lbs) }
                    Toast.makeText(ctx, "Goal set to ${fmtWithUnit(suggested, unit)}", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Use suggested goal: ${fmtWithUnit(suggested, unit)}")
                }
            }
        }

        SettingsCard("Units") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = unit == "oz", onClick = {
                    store.settings { it.copy(unit = "oz", quickAddMl = quickAddChoicesMl("oz")[0]) }
                }, label = { Text("Ounces (oz)") })
                FilterChip(selected = unit == "ml", onClick = {
                    store.settings { it.copy(unit = "ml", quickAddMl = quickAddChoicesMl("ml")[0]) }
                }, label = { Text("Milliliters (ml)") })
            }
        }

        SettingsCard("Reminders") {
            ToggleRow("Remind me to drink", s.remindersOn) { on ->
                store.settings { it.copy(remindersOn = on) }
                if (on) requestNotif()
            }
            if (s.remindersOn && !canNotify) {
                Text(
                    "Notifications are turned off for Puddle, so reminders can't show up.",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(onClick = {
                    requestNotif()
                    val i = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { ctx.startActivity(i) } catch (_: Exception) {}
                }) { Text("Turn on notifications") }
            }
            if (s.remindersOn) {
                StepperRow("From", hourLabel(s.startHour),
                    onMinus = { store.settings { it.copy(startHour = maxOf(4, it.startHour - 1)) } },
                    onPlus = { store.settings { it.copy(startHour = minOf(it.endHour - 2, it.startHour + 1)) } })
                StepperRow("Until", hourLabel(s.endHour),
                    onMinus = { store.settings { it.copy(endHour = maxOf(it.startHour + 2, it.endHour - 1)) } },
                    onPlus = { store.settings { it.copy(endHour = minOf(23, it.endHour + 1)) } })
                Text("Remind me every", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60 to "1 hr", 90 to "1½ hr", 120 to "2 hr", 180 to "3 hr").forEach { (m, label) ->
                        FilterChip(selected = s.intervalMin == m, onClick = { store.settings { it.copy(intervalMin = m) } }, label = { Text(label) })
                    }
                }
                ToggleRow("Skip reminders when I'm on track", s.smartSkip) { on -> store.settings { it.copy(smartSkip = on) } }
                Text("Quick-add button on reminders", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickAddChoicesMl(unit).forEach { ml ->
                        FilterChip(
                            selected = fmtAmount(s.quickAddMl, unit) == fmtAmount(ml, unit),
                            onClick = { store.settings { it.copy(quickAddMl = ml) } },
                            label = { Text(fmtWithUnit(ml, unit)) },
                        )
                    }
                }
                OutlinedButton(onClick = {
                    if (!Reminders.canNotify(ctx)) {
                        requestNotif()
                    } else {
                        Reminders.nudge(ctx)
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Send me a test reminder") }
                Text(
                    "Samsung phones sometimes pause apps to save battery. If reminders stop, open App info > Battery and choose Unrestricted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    val i = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { ctx.startActivity(i) } catch (_: Exception) {}
                }) { Text("Open App info") }
            }
        }

        SettingsCard("Your data") {
            Text(
                "Everything you log is saved inside Puddle on this phone. Nothing is sent anywhere.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { confirmClear = true }) { Text("Clear today's drinks") }
        }
        Text(
            "Puddle ${appVersion(ctx)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear today's drinks?") },
            text = { Text("This removes the ${sum.todayList.size} drinks you logged today. Earlier days stay.") },
            confirmButton = {
                TextButton(onClick = {
                    store.clearDay { Stats.dayOf(it.time) != sum.today }
                    confirmClear = false
                }) { Text("Clear today") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep them") } },
        )
    }
}

private fun appVersion(ctx: android.content.Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
} catch (_: Exception) {
    ""
}

@Composable
fun ActivityChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Light", "Moderate", "Very active").forEachIndexed { i, label ->
            FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = onMinus, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("−") }
        Text(value, fontWeight = FontWeight.Black, modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FilledTonalButton(onClick = onPlus, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("+") }
    }
}
