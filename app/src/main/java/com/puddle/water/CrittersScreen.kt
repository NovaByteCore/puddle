@file:OptIn(ExperimentalMaterial3Api::class)

package com.puddle.water

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun CrittersScreen(store: Store, sum: Summary) {
    val unlockedCount = Critters.all.count { sum.isUnlocked(it.id) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column {
                Text("Critters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "$unlockedCount of ${Critters.all.size} unlocked. Complete challenges to earn new buddies, then tap one to put it in your jar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(Critters.all, key = { it.id }) { c ->
            val unlocked = sum.isUnlocked(c.id)
            val active = sum.settings.activeCritter == c.id
            val p = sum.progress(c.id)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
                border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(enabled = unlocked && !active) {
                        store.settings { it.copy(activeCritter = c.id) }
                    },
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Canvas(Modifier.size(96.dp)) {
                        drawCritter(
                            c.id,
                            Offset(size.width / 2, size.height / 2 + size.height * 0.06f),
                            size.minDimension * 0.6f,
                            locked = !unlocked,
                            mood = 0.6f,
                        )
                    }
                    Text(if (unlocked) c.name else "???", fontWeight = FontWeight.Black)
                    Text(
                        if (unlocked) "the ${c.species}" else "Locked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    when {
                        active -> Pill("In your jar")
                        unlocked -> Text("Tap to choose", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        else -> {
                            Text(p.goalText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                            LinearProgressIndicator(
                                progress = { p.fraction },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(50)),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round,
                            )
                            Text(p.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
