package com.puddle.water

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val WBg = ColorProvider(day = Color(0xFFE3F5F2), night = Color(0xFF102C31))
private val WInk = ColorProvider(day = Color(0xFF123A3F), night = Color(0xFFE3F5F3))
private val WMuted = ColorProvider(day = Color(0xFF52767A), night = Color(0xFF90B4B3))
private val WTrack = ColorProvider(day = Color(0xFFCDE9E5), night = Color(0xFF1F4950))
private val WBlue = ColorProvider(day = Color(0xFF1F8FB3), night = Color(0xFF6CCFEA))
private val WOnBlue = ColorProvider(day = Color.White, night = Color(0xFF00313F))
private val WAlert = ColorProvider(day = Color(0xFFFFE3D6), night = Color(0xFF5A2E22))
private val WAlertInk = ColorProvider(day = Color(0xFF8A3418), night = Color(0xFFFFD2BF))

/** Short, cute status for the widget. */
private fun widgetMood(level: Float, name: String, pending: Boolean): String = when {
    level >= 1f -> "$name is floating!"
    pending -> "The llama is waiting!"
    level <= 0f -> "$name is thirsty"
    level < 0.5f -> "$name wants a swim"
    else -> "$name is paddling"
}

class PuddleWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }

    @Composable
    private fun WidgetContent() {
        val ctx = LocalContext.current
        val st by Store.get(ctx).state.collectAsState()
        val sum = Summary(st)
        val s = st.settings
        val level = (sum.todayEff / sum.goal).coerceIn(0.0, 1.0).toFloat()
        val critter = Critters.byId(s.activeCritter)
        val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // Round to 2% steps so the picture isn't redrawn for every tiny change.
        val step = (level * 50).toInt()
        val jar = remember(s.activeCritter, step, dark) { jarBitmap(s.activeCritter, step / 50f, dark) }
        val wide = LocalSize.current.width >= WIDE.width

        Box(
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(28.dp)
                .background(if (s.reminderPending && level < 1f) WAlert else WBg)
                .clickable(actionStartActivity<MainActivity>())
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (wide) {
                Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(jar),
                        contentDescription = "${critter.name}'s jar, ${(level * 100).toInt()}% full",
                        modifier = GlanceModifier.size(78.dp, 92.dp),
                    )
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(
                            widgetMood(level, critter.name, s.reminderPending),
                            style = TextStyle(
                                color = if (s.reminderPending && level < 1f) WAlertInk else WMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                fmtAmount(sum.todayEff, s.unit),
                                style = TextStyle(color = WInk, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                            )
                            Text(
                                " / ${fmtWithUnit(sum.goal, s.unit)}",
                                style = TextStyle(color = WMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                modifier = GlanceModifier.padding(bottom = 4.dp),
                            )
                        }
                        LinearProgressIndicator(
                            progress = level,
                            modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
                            color = WBlue,
                            backgroundColor = WTrack,
                        )
                        Spacer(GlanceModifier.height(8.dp))
                        AddPill(s, GlanceModifier)
                    }
                }
            } else {
                Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        provider = ImageProvider(jar),
                        contentDescription = "${critter.name}'s jar, ${(level * 100).toInt()}% full",
                        modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                    )
                    Text(
                        "${fmtAmount(sum.todayEff, s.unit)} / ${fmtWithUnit(sum.goal, s.unit)}",
                        style = TextStyle(color = WInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    AddPill(s, GlanceModifier.fillMaxWidth())
                }
            }
        }
    }

    @Composable
    private fun AddPill(s: Settings, modifier: GlanceModifier) {
        Box(
            modifier
                .cornerRadius(50.dp)
                .background(WBlue)
                .clickable(actionRunCallback<WidgetLogWater>())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+ ${fmtWithUnit(s.quickAddMl, s.unit)}",
                style = TextStyle(color = WOnBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}

/** The "+ 8 oz" button on the widget. Counts as drinking, so it also ends any open reminder. */
class WidgetLogWater : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val store = Store.get(context)
        store.reload()
        Hydration.log(context, store.state.value.settings.quickAddMl, "water")
        PuddleWidget().updateAll(context)
    }
}

class PuddleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PuddleWidget()
}

/** Refreshes the widget shortly after anything changes. */
object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Synchronized
    fun request(ctx: Context) {
        val app = ctx.applicationContext
        job?.cancel()
        job = scope.launch {
            delay(150)
            try {
                PuddleWidget().updateAll(app)
            } catch (_: Exception) {
            }
        }
    }
}
