@file:OptIn(ExperimentalMaterial3Api::class)

package com.puddle.water

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(sum: Summary) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val unit = sum.unit
    val last7 = (6 downTo 0).map { sum.today.minusDays(it.toLong()) }
    val avg = last7.sumOf { sum.dayEff(it) } / 7.0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Current streak", "${sum.streak}", "days", Modifier.weight(1f))
            StatTile("Best streak", "${sum.best}", "days", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("7-day average", fmtAmount(avg, unit), "$unit a day", Modifier.weight(1f))
            StatTile("Goals reached", "${sum.goalsMet}", "all time", Modifier.weight(1f))
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                SectionLabel("Last 7 days")
                WeekChart(sum, last7)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                MonthCalendar(sum, month, onPrev = { month = month.minusMonths(1) }, onNext = { month = month.plusMonths(1) })
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("What you drank this week")
                val week = last7.flatMap { sum.days[it] ?: emptyList() }
                val total = week.sumOf { it.ml }
                if (week.isEmpty()) {
                    Text("No drinks logged this week yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                week.groupBy { it.drinkId }
                    .map { (id, list) -> Drinks.byId(id) to list.sumOf { it.ml } }
                    .sortedByDescending { it.second }
                    .forEach { (d, ml) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(d.color)))
                            Spacer(Modifier.width(8.dp))
                            Text(d.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp))
                            LinearProgressIndicator(
                                progress = { (ml / total).toFloat() },
                                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)),
                                color = Color(d.color),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(fmtWithUnit(ml, unit), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                        }
                    }
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekChart(sum: Summary, days: List<LocalDate>) {
    val values = days.map { sum.dayEff(it) }
    val max = maxOf(sum.goal * 1.15, values.maxOrNull() ?: 0.0)
    val hit = MaterialTheme.colorScheme.primary
    val miss = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val goalLine = MaterialTheme.colorScheme.secondary

    Row {
        days.forEachIndexed { i, _ ->
            Text(
                fmtAmount(values[i], sum.unit),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Canvas(Modifier.fillMaxWidth().height(140.dp).padding(vertical = 4.dp)) {
        val slot = size.width / days.size
        val barW = slot * 0.55f
        values.forEachIndexed { i, v ->
            val bh = (v / max).toFloat() * size.height
            val x = i * slot + (slot - barW) / 2
            drawRoundRect(
                if (v >= sum.goal) hit else miss,
                Offset(x, size.height - maxOf(bh, 4f)),
                Size(barW, maxOf(bh, 4f)),
                CornerRadius(8.dp.toPx()),
            )
        }
        val gy = size.height - (sum.goal / max).toFloat() * size.height
        drawLine(
            goalLine, Offset(0f, gy), Offset(size.width, gy),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
        )
    }
    Row {
        days.forEach { d ->
            Text(
                if (d == sum.today) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (d == sum.today) FontWeight.Black else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Text(
        "Dashed line is your goal of ${fmtWithUnit(sum.goal, sum.unit)}.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun MonthCalendar(sum: Summary, month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev) { Text("‹", fontSize = 24.sp) }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onNext, enabled = month < YearMonth.now()) { Text("›", fontSize = 24.sp) }
    }
    Row {
        listOf("S", "M", "T", "W", "T", "F", "S").forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
    }
    val offset = month.atDay(1).dayOfWeek.value % 7
    val count = month.lengthOfMonth()
    val cells = offset + count
    val rows = (cells + 6) / 7
    for (r in 0 until rows) {
        Row {
            for (c in 0 until 7) {
                val idx = r * 7 + c
                val dayNum = idx - offset + 1
                Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                    if (dayNum in 1..count) {
                        val date = month.atDay(dayNum)
                        val frac = (sum.dayEff(date) / sum.goal).coerceIn(0.0, 1.0).toFloat()
                        val future = date.isAfter(sum.today)
                        Box(
                            Modifier.fillMaxSize().clip(CircleShape)
                                .background(
                                    when {
                                        future -> Color.Transparent
                                        frac >= 1f -> primary
                                        frac > 0f -> primary.copy(alpha = 0.15f + frac * 0.45f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$dayNum",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (date == sum.today) FontWeight.Black else FontWeight.Normal,
                                color = if (frac >= 1f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
    Text(
        "Darker days got closer to your goal. Solid days hit it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}
