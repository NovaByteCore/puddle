@file:OptIn(ExperimentalMaterial3Api::class)

package com.puddle.water

import android.Manifest
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)
    private val openLog = mutableStateOf(false)

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            resumeTick.intValue++
            Scheduler.tick(this)
        }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(this)) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannels(this)
        Scheduler.ensureSafetyNet(this)
        handleIntent(intent)
        val store = Store.get(this)
        setContent {
            PuddleTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PuddleApp(
                        store = store,
                        tick = resumeTick.intValue,
                        openLog = openLog.value,
                        onLogClosed = { openLog.value = false },
                        requestNotif = { requestNotifications() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i != null && i.getBooleanExtra(EXTRA_LOG, false)) {
            openLog.value = true
            i.removeExtra(EXTRA_LOG)
        }
    }

    override fun onResume() {
        super.onResume()
        Store.get(this).reload()
        // Opening the app never counts as drinking. This only catches up on anything that's due.
        Scheduler.tick(this)
        resumeTick.intValue++
    }

    companion object {
        const val EXTRA_LOG = "log_water"
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F8FB3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFF8),
    onPrimaryContainer = Color(0xFF0B3C4C),
    secondary = Color(0xFF3F8F45),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F0D2),
    onSecondaryContainer = Color(0xFF173D18),
    background = Color(0xFFEAF6F4),
    onBackground = Color(0xFF123A3F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF123A3F),
    surfaceVariant = Color(0xFFDFF1EE),
    onSurfaceVariant = Color(0xFF52767A),
    surfaceContainer = Color(0xFFF4FBFA),
    outlineVariant = Color(0xFFCFE6E3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6CCFEA),
    onPrimary = Color(0xFF00313F),
    primaryContainer = Color(0xFF1B5568),
    onPrimaryContainer = Color(0xFFD3F3FB),
    secondary = Color(0xFF7FD27C),
    onSecondary = Color(0xFF0B3A10),
    secondaryContainer = Color(0xFF235A25),
    onSecondaryContainer = Color(0xFFD7F0D2),
    background = Color(0xFF0C2226),
    onBackground = Color(0xFFE3F5F3),
    surface = Color(0xFF13333A),
    onSurface = Color(0xFFE3F5F3),
    surfaceVariant = Color(0xFF1A4148),
    onSurfaceVariant = Color(0xFF90B4B3),
    surfaceContainer = Color(0xFF102C31),
    outlineVariant = Color(0xFF1F4950),
)

@Composable
fun PuddleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

@Composable
fun PuddleApp(store: Store, tick: Int, openLog: Boolean, onLogClosed: () -> Unit, requestNotif: () -> Unit) {
    val ctx = LocalContext.current
    val st by store.state.collectAsState()
    val sum = remember(st, tick) { Summary(st) }

    if (!st.settings.onboarded) {
        OnboardingScreen(store, tick, requestNotif)
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var logDialog by remember { mutableStateOf(false) }
    LaunchedEffect(openLog) {
        if (openLog) {
            tab = 0
            logDialog = true
            onLogClosed()
        }
    }

    val pending = st.settings.reminderPending
    val tabs = listOf(
        "Today" to Icons.Filled.Home,
        "History" to Icons.Filled.DateRange,
        "Critters" to Icons.Filled.Favorite,
        "Settings" to Icons.Filled.Settings,
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            if (i == 0 && pending) {
                                BadgedBox(badge = { Badge { Text("!") } }) { Icon(icon, contentDescription = "$label, reminder waiting") }
                            } else {
                                Icon(icon, contentDescription = label)
                            }
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (pending) {
                ReminderBanner(
                    s = st.settings,
                    onDrank = {
                        val ml = st.settings.quickAddMl
                        Hydration.log(ctx, ml, "water")
                        Toast.makeText(ctx, "Logged ${fmtWithUnit(ml, st.settings.unit)}. The llama is pleased.", Toast.LENGTH_SHORT).show()
                    },
                    onLog = { tab = 0; logDialog = true },
                    onSnooze = { Scheduler.snooze(ctx) },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> TodayScreen(store, sum)
                    1 -> HistoryScreen(sum)
                    2 -> CrittersScreen(store, sum)
                    else -> SettingsScreen(store, sum, tick, requestNotif)
                }
            }
        }
    }

    if (logDialog) {
        LogDrinkDialog(
            unit = st.settings.unit,
            startDrinkId = st.settings.lastDrinkId,
            onDismiss = { logDialog = false },
            onLog = { ml, id ->
                Hydration.log(ctx, ml, id)
                logDialog = false
                Toast.makeText(ctx, "Added ${fmtWithUnit(ml, st.settings.unit)} of ${Drinks.byId(id).name.lowercase()}", Toast.LENGTH_SHORT).show()
            },
        )
    }

    val fresh = Critters.all.firstOrNull { it.id in sum.unlocked && it.id !in st.settings.seen }
    if (fresh != null) {
        val dismiss = { store.settings { it.copy(seen = it.seen + fresh.id) } }
        AlertDialog(
            onDismissRequest = { dismiss() },
            confirmButton = {
                TextButton(onClick = {
                    store.settings { it.copy(seen = it.seen + fresh.id, activeCritter = fresh.id) }
                }) { Text("Make ${fresh.name} my buddy") }
            },
            dismissButton = { TextButton(onClick = { dismiss() }) { Text("Later") } },
            title = { Text("New critter unlocked!", fontWeight = FontWeight.Black) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Canvas(Modifier.size(120.dp)) {
                        drawCritter(fresh.id, Offset(size.width / 2, size.height / 2 + 6f), size.minDimension * 0.62f, mood = 1f)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${fresh.name} the ${fresh.species} wants to hang out. You earned them: ${sum.progress(fresh.id).goalText.lowercase()}.",
                        textAlign = TextAlign.Center,
                    )
                }
            },
        )
    }
}
