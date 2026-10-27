@file:OptIn(ExperimentalMaterial3Api::class)

package com.puddle.water

import android.Manifest
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick.intValue++ }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(this)) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reminders.ensureChannel(this)
        Reminders.schedule(this)
        val store = Store.get(this)
        setContent {
            PuddleTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PuddleApp(store, resumeTick.intValue) { requestNotifications() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Store.get(this).reload()
        resumeTick.intValue++
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
fun PuddleApp(store: Store, tick: Int, requestNotif: () -> Unit) {
    val st by store.state.collectAsState()
    val sum = remember(st, tick) { Summary(st) }

    if (!st.settings.onboarded) {
        OnboardingScreen { unit, goal, weight, activity ->
            store.settings {
                it.copy(onboarded = true, unit = unit, goalMl = goal, weightLbs = weight, activity = activity,
                    quickAddMl = quickAddChoicesMl(unit)[0])
            }
            requestNotif()
        }
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
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
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> TodayScreen(store, sum)
                1 -> HistoryScreen(sum)
                2 -> CrittersScreen(store, sum)
                else -> SettingsScreen(store, sum, tick, requestNotif)
            }
        }
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
