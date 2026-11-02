@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.puddle.water

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(store: Store, tick: Int, requestNotif: () -> Unit) {
    val ctx = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    var unit by rememberSaveable { mutableStateOf("oz") }
    var weight by rememberSaveable { mutableStateOf("150") }
    var activity by rememberSaveable { mutableIntStateOf(1) }
    var wakeStart by rememberSaveable { mutableIntStateOf(8 * 60) }
    var wakeEnd by rememberSaveable { mutableIntStateOf(21 * 60) }
    var interval by rememberSaveable { mutableIntStateOf(90) }
    var asked by rememberSaveable { mutableStateOf(false) }

    val entered = weight.toIntOrNull() ?: 0
    val lbs = if (unit == "oz") entered else (entered * 2.2046).toInt()
    val goal = suggestedGoalMl(if (lbs > 40) lbs else 150, activity)

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (page == 0) {
            Canvas(Modifier.size(150.dp)) {
                drawCritter("frog", Offset(size.width / 2, size.height / 2 + 12f), size.minDimension * 0.62f, mood = 1f)
            }
            Text("Meet Puddle", fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text(
                "Puddle lives in a jar. Every drink you log fills it up, and when you hit your goal Puddle floats. Keep it up to unlock new critters.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Units", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(selected = unit == "oz", onClick = {
                    if (unit != "oz") weight = "${((weight.toIntOrNull() ?: 68) * 2.2046).toInt()}"
                    unit = "oz"
                }, label = { Text("Ounces, pounds") })
                FilterChip(selected = unit == "ml", onClick = {
                    if (unit != "ml") weight = "${((weight.toIntOrNull() ?: 150) / 2.2046).toInt()}"
                    unit = "ml"
                }, label = { Text("Milliliters, kg") })
            }

            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it.filter(Char::isDigit).take(3) },
                label = { Text(if (unit == "oz") "Your weight (lbs)" else "Your weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("How active are you?", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth()) { ActivityChips(activity) { activity = it } }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Your daily goal", fontWeight = FontWeight.Bold)
                    Text(fmtWithUnit(goal, unit), fontSize = 36.sp, fontWeight = FontWeight.Black)
                    Text("You can change this any time in Settings.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(MEDICAL_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

            Button(onClick = { page = 1 }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Next: reminders", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text("Reminders", fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text(
                "During your waking hours a llama will check in when it's time to drink. If you don't answer, it keeps politely pestering you until you tap \"I drank\" or log a drink.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Waking hours", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            TimeStepper("Start", wakeStart) { wakeStart = it }
            TimeStepper("End", wakeEnd) { wakeEnd = it }

            Text("Check in at least every", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(45, 60, 90, 120).forEach { m ->
                    FilterChip(selected = interval == m, onClick = { interval = m }, label = { Text(durationLabel(m)) })
                }
            }

            val allowed = remember(tick) { Notifier.canNotify(ctx) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Allow notifications", fontWeight = FontWeight.Black)
                    Text(
                        "Puddle needs permission to send notifications, or reminders can only show inside the app. " +
                            "Your phone stays in charge: Do Not Disturb, notification settings and battery saving still apply, " +
                            "and Puddle can't get around them. You can fine-tune this later in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    when {
                        allowed -> Text("✓ Notifications are on", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        !asked -> Button(onClick = { asked = true; requestNotif() }) { Text("Allow notifications") }
                        else -> {
                            Text("Notifications are still off.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { openAppNotifications(ctx) }) { Text("Open notification settings") }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    store.settings {
                        it.copy(
                            onboarded = true,
                            unit = unit,
                            goalMl = goal,
                            weightLbs = if (lbs > 40) lbs else 150,
                            activity = activity,
                            quickAddMl = quickAddChoicesMl(unit)[0],
                            wakeStartMin = wakeStart,
                            wakeEndMin = wakeEnd,
                            intervalMin = interval,
                            remindersOn = true,
                        )
                    }
                    Scheduler.settingsChanged(ctx)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Let's go", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            TextButton(onClick = { page = 0 }) { Text("Back") }
        }
    }
}
