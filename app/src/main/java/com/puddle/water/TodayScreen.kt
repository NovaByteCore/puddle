@file:OptIn(ExperimentalMaterial3Api::class)

package com.puddle.water

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private val dayFmt = DateTimeFormatter.ofPattern("EEEE, MMM d")
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

fun moodLine(level: Float, name: String): String = when {
    level <= 0f -> "$name is thirsty. Time for a first sip?"
    level < 0.25f -> "Nice start! Keep the sips coming."
    level < 0.5f -> "$name can almost swim."
    level < 0.75f -> "Over halfway. Splash!"
    level < 1f -> "So close. One or two more drinks."
    else -> "Goal reached! $name is happily floating."
}

fun drinkNote(d: DrinkType): String? = when {
    d.factor < 0 -> "${d.name} dehydrates you. It takes away ${abs(d.factor * 100).roundToInt()}% of what you drink."
    d.factor < 1 -> "${d.name} counts as ${(d.factor * 100).roundToInt()}% water."
    else -> null
}

@Composable
fun TodayScreen(store: Store, sum: Summary) {
    val ctx = LocalContext.current
    val s = sum.settings
    var drinkId by rememberSaveable { mutableStateOf(s.lastDrinkId) }
    var showCustom by remember { mutableStateOf(false) }
    val drink = Drinks.byId(drinkId)
    val critter = Critters.byId(s.activeCritter)
    val level = (sum.todayEff / sum.goal).coerceIn(0.0, 1.0).toFloat()

    fun add(ml: Double) {
        store.addDrink(ml, drink.id)
        Toast.makeText(ctx, "Added ${fmtWithUnit(ml, s.unit)} of ${drink.name.lowercase()}", Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sum.today.format(dayFmt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${critter.name}'s jar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
            ScoreRing(sum.todayScore.total)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Jar(critter.id, level, Modifier.size(width = 210.dp, height = 240.dp))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmtAmount(sum.todayEff, s.unit), fontSize = 40.sp, fontWeight = FontWeight.Black)
                    Text(
                        "  / ${fmtWithUnit(sum.goal, s.unit)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(moodLine(level, critter.name), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("${(sum.todayEff / sum.goal * 100).roundToInt()}% of goal")
                    Pill("${sum.streak}-day streak")
                }
            }
        }

        SectionLabel("What are you drinking?")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Drinks.all, key = { it.id }) { d ->
                FilterChip(
                    selected = d.id == drinkId,
                    onClick = { drinkId = d.id },
                    label = { Text(d.name) },
                    leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(d.color))) },
                )
            }
        }
        drinkNote(drink)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (ml in cupSizesMl(s.unit)) {
                Button(
                    onClick = { add(ml) },
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmtAmount(ml, s.unit), fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(s.unit, fontSize = 11.sp)
                    }
                }
            }
            OutlinedButton(
                onClick = { showCustom = true },
                modifier = Modifier.weight(1f).height(64.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text("Other", fontWeight = FontWeight.Bold) }
        }

        ScoreCard(sum.todayScore)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                SectionLabel("Today's drinks")
                if (sum.todayList.isEmpty()) {
                    Text("Nothing logged yet. Pick a drink and tap a cup size.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                sum.todayList.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(e.drink.color)))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            Instant.ofEpochMilli(e.time).atZone(ZoneId.systemDefault()).format(timeFmt),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(64.dp),
                        )
                        Text(e.drink.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(fmtWithUnit(e.ml, s.unit), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { store.removeEntry(e) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showCustom) {
        val isOz = s.unit == "oz"
        var value by remember { mutableFloatStateOf(if (isOz) 10f else 300f) }
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("How much ${drink.name.lowercase()}?") },
            text = {
                Column {
                    Text("${value.roundToInt()} ${s.unit}", fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = if (isOz) 1f..64f else 50f..2000f,
                        steps = if (isOz) 62 else 38,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    add(if (isOz) value.roundToInt() * ML_PER_OZ else value.roundToInt().toDouble())
                    showCustom = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
fun Pill(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
fun ScoreRing(score: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
        CircularProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 6.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text("score", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ScoreCard(score: Score) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Hydration score")
                Spacer(Modifier.weight(1f))
                Text("${score.total} / 100", fontWeight = FontWeight.Black)
            }
            ScoreRow("Amount", "How close you are to your goal", score.intake, 70)
            ScoreRow("Drink quality", "More water, less soda and alcohol", score.quality, 15)
            ScoreRow("Pacing", "Drinking through the day, not all at once", score.pacing, 15)
        }
    }
}

@Composable
private fun ScoreRow(title: String, hint: String, value: Int, max: Int) {
    Column {
        Row {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("$value / $max", style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(
            progress = { value / max.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(8.dp).clip(RoundedCornerShape(50)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Jar(critterId: String, level: Float, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(level, animationSpec = spring(dampingRatio = 0.6f, stiffness = 40f), label = "level")
    val t = rememberInfiniteTransition(label = "jar")
    val phase by t.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "wave",
    )
    val bob by t.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    val glass = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.onSurface
    val water = Color(0xFF4FC3F7)
    val lid = Color(0xFF3F8F45)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val left = w * 0.1f
        val right = w * 0.9f
        val top = h * 0.12f
        val bottom = h * 0.98f
        val jar = Path().apply { addRoundRect(RoundRect(Rect(left, top, right, bottom), CornerRadius(w * 0.16f))) }
        val waterTop = bottom - (bottom - top) * anim
        val cs = w * 0.42f
        val rest = bottom - cs * 0.62f
        val floating = waterTop + cs * 0.1f + bob * 3.dp.toPx()
        val cy = minOf(rest, maxOf(floating, top + cs * 0.75f))
        val amp = if (anim > 0.001f && anim < 0.999f) 5.dp.toPx() else 0f

        clipPath(jar) {
            drawRect(glass)
            drawRect(water.copy(alpha = 0.3f), Offset(left, waterTop), Size(right - left, bottom - waterTop))
            drawCritter(critterId, Offset(w / 2, cy), cs, mood = if (level >= 1f) 1f else level)
            if (anim > 0.001f) {
                val p = Path()
                p.moveTo(left, bottom)
                var x = left
                while (x <= right + 4f) {
                    p.lineTo(x, waterTop + sin(((x - left) / w) * 2 * PI.toFloat() * 1.5f + phase) * amp)
                    x += 4f
                }
                p.lineTo(right, bottom)
                p.close()
                drawPath(p, water.copy(alpha = 0.5f))
            }
        }
        drawPath(jar, outline, style = Stroke(3.dp.toPx()))
        drawLine(
            Color.White.copy(alpha = 0.55f),
            Offset(left + w * 0.08f, top + h * 0.1f),
            Offset(left + w * 0.08f, top + h * 0.32f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val lidTop = Offset(left - w * 0.03f, top - h * 0.08f)
        val lidSize = Size(right - left + w * 0.06f, h * 0.085f)
        drawRoundRect(lid, lidTop, lidSize, CornerRadius(8.dp.toPx()))
        drawRoundRect(outline, lidTop, lidSize, CornerRadius(8.dp.toPx()), style = Stroke(3.dp.toPx()))
    }
}
