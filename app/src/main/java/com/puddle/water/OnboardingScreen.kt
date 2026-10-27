@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(onDone: (unit: String, goalMl: Double, weightLbs: Int, activity: Int) -> Unit) {
    var unit by rememberSaveable { mutableStateOf("oz") }
    var weight by rememberSaveable { mutableStateOf("150") }
    var activity by rememberSaveable { mutableIntStateOf(1) }
    val entered = weight.toIntOrNull() ?: 0
    val lbs = if (unit == "oz") entered else (entered * 2.2046).toInt()
    val goal = suggestedGoalMl(if (lbs > 40) lbs else 150, activity)

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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

        Button(
            onClick = { onDone(unit, goal, if (lbs > 40) lbs else 150, activity) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Let's go", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}
