package com.cointracker.mobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cointracker.mobile.R
import com.cointracker.mobile.ui.components.GlassCard
import com.cointracker.mobile.ui.screens.*
import com.cointracker.mobile.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnimatedGradientBackground(isDark: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val c1 by t.animateColor(if (isDark) GradientDark1 else GradientLight1, if (isDark) GradientDark2 else GradientLight2,
        infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse), label = "c1")
    val c2 by t.animateColor(if (isDark) GradientDark3 else GradientLight3, if (isDark) GradientDark4 else GradientLight4,
        infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse), label = "c2")
    Box(modifier = modifier.background(Brush.linearGradient(listOf(c1, c2)))) { content() }
}

@Composable
fun PulseDots() {
    val t = rememberInfiniteTransition(label = "pulse")
    val scales = listOf(0, 200, 400).map { d -> t.animateFloat(0.6f, 1.2f, infiniteRepeatable(tween(600, delayMillis = d, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s$d") }
    val alphas = listOf(0, 200, 400).map { d -> t.animateFloat(0.4f, 1.0f, infiniteRepeatable(tween(600, delayMillis = d, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a$d") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        scales.zip(alphas).forEach { (s, a) ->
            Box(Modifier.size((10 * s.value).dp).background(MaterialTheme.colorScheme.primary.copy(alpha = a.value), CircleShape))
        }
    }
}

@Composable
fun LoadingOverlay() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            tonalElevation = 8.dp, modifier = Modifier.size(100.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { PulseDots() }
        }
    }
}

@Composable
fun SyncBanner(syncState: SyncState, onDismiss: () -> Unit) {
    val (text, icon, color) = when (syncState) {
        SyncState.Offline            -> Triple("📴 Offline — showing cached data", Icons.Default.WifiOff, Color(0xFFF59E0B))
        SyncState.RestoredFromCache  -> Triple("🛡 DB was empty — restored from local safety cache", Icons.Default.Restore, Color(0xFF10B981))
        SyncState.PushedCacheToDb    -> Triple("⬆ Offline changes synced to database", Icons.Default.CloudUpload, Color(0xFF3B82F6))
        else -> return
    }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),Arrangement.spacedBy(10.dp), Alignment.CenterVertically,) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, "Dismiss", tint = color, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ConflictDialog(syncState: SyncState.Conflict, onUseCache: () -> Unit, onUseDatabase: () -> Unit) {
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    AlertDialog(
        onDismissRequest = {},
        containerColor   = MaterialTheme.colorScheme.surfaceVariant,
        icon             = { Text("⚠️", fontSize = 28.sp) },
        title            = { Text("Data Conflict Detected", fontWeight = FontWeight.Bold) },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your local safety cache and the database have different data. Choose which to keep.",
                    style = MaterialTheme.typography.bodyMedium)
                Surface(color = Color(0xFF10B981).copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("📱 Local Cache  (saved ${fmt.format(Date(syncState.cached.savedAt))})",
                            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text("Balance: ${syncState.cached.balance} coins  •  ${syncState.cached.transactionCount} txns",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
                Surface(color = Color(0xFF3B82F6).copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("☁️ Database (online)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text("Balance: ${syncState.db.balance} coins  •  ${syncState.db.transactions.size} txns",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onUseCache, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                Text("Use Local Cache")
            }
        },
        dismissButton = { OutlinedButton(onClick = onUseDatabase) { Text("Use Database") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinTrackerApp(viewModel: CoinTrackerViewModel = hiltViewModel()) {
    val navController     = rememberNavController()
    val uiState           by viewModel.uiState.collectAsState()
    val isDark            by viewModel.isDarkMode.collectAsState()
    val context           = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()
    val syncState         = uiState.syncState

    LaunchedEffect(uiState.error) {
        val msg = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        viewModel.clearError()
    }

    if (syncState is SyncState.Conflict) {
        ConflictDialog(syncState, onUseCache = { viewModel.resolveConflictUseCache() }, onUseDatabase = { viewModel.resolveConflictUseDatabase() })
    }

    CoinTrackerTheme(darkTheme = isDark) {
        AnimatedGradientBackground(isDark, Modifier.fillMaxSize()) {
            if (uiState.session == null) {
                LoginScreen(uiState.loading, { u, p -> viewModel.login(u, p) }, { u, p -> viewModel.register(u, p) },
                    { viewModel.toggleTheme() }, isDark, uiState.session != null, {}, uiState.error)
                if (uiState.loading) LoadingOverlay()
                Box(Modifier.fillMaxSize()) { SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter)) }
            } else {
                val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }

                Box(Modifier.fillMaxSize()) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            Column(Modifier.statusBarsPadding()) {
                                GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(painterResource(R.drawable.coin), "Logo", tint = Color.Unspecified, modifier = Modifier.size(28.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Coin Tracker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val cbse by navController.currentBackStackEntryAsState()
                                            val cr = cbse?.destination?.route
                                            Box {
                                                IconButton(onClick = { if (cr != "notifications") navController.navigate("notifications") }) {
                                                    Icon(Icons.Default.Notifications, "Notifications", tint = if (cr == "notifications") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                                }
                                                if (uiState.unreadNotifCount > 0) {
                                                    Badge(Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp), containerColor = Color(0xFFF59E0B)) {
                                                        Text(if (uiState.unreadNotifCount > 9) "9+" else uiState.unreadNotifCount.toString(), style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                            IconButton({ viewModel.toggleTheme() }) { Text(if (isDark) "☀️" else "🌙", style = MaterialTheme.typography.titleMedium) }
                                            Box {
                                                var showMenu by remember { mutableStateOf(false) }
                                                IconButton({ showMenu = true }) { Icon(Icons.Default.Person, "Profile", tint = MaterialTheme.colorScheme.onSurface) }
                                                MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = MaterialTheme.shapes.medium)) {
                                                    DropdownMenu(showMenu, { showMenu = false }, Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                                                        Text("Profiles", Modifier.padding(12.dp, 8.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        val ap = uiState.session?.currentProfile
                                                        uiState.profiles.forEach { p ->
                                                            val ia = p == ap
                                                            DropdownMenuItem(text = {
                                                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                                    Text(p, color = if (ia) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (ia) FontWeight.Bold else FontWeight.Normal)
                                                                    if (ia) { Spacer(Modifier.weight(1f)); Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                                                                }
                                                            }, onClick = { if (!ia) viewModel.switchProfile(p); showMenu = false })
                                                        }
                                                        Divider()
                                                        DropdownMenuItem({ Text("+ Add Profile") }, { navController.navigate("settings"); showMenu = false })
                                                        if (uiState.session?.role == "admin") DropdownMenuItem({ Text("👑 Admin Panel", color = MaterialTheme.colorScheme.primary) }, { navController.navigate("admin"); showMenu = false })
                                                        DropdownMenuItem({ Text("Log Out", color = MaterialTheme.colorScheme.error) }, { viewModel.logout(); showMenu = false })
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (syncState is SyncState.Offline || syncState is SyncState.RestoredFromCache || syncState is SyncState.PushedCacheToDb) {
                                    SyncBanner(syncState) { viewModel.dismissSyncBanner() }
                                }
                            }
                        },
                        bottomBar = {
                            NavigationBar(containerColor = if (isDark) Color(0xFF1A1D23).copy(alpha = 0.9f) else Color.White, contentColor = MaterialTheme.colorScheme.primary, tonalElevation = 8.dp) {
                                val cbse by navController.currentBackStackEntryAsState()
                                val cr = cbse?.destination?.route ?: "dashboard"
                                listOf("dashboard" to Icons.Default.Home, "analytics" to Icons.Default.DateRange, "history" to Icons.Default.List, "settings" to Icons.Default.Settings).forEach { (route, icon) ->
                                    NavigationBarItem(icon = { Icon(icon, route) }, label = { Text(route.replaceFirstChar { it.uppercase() }) }, selected = cr == route,
                                        onClick = { navController.navigate(route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true; inclusive = false }; launchSingleTop = true; restoreState = true } },
                                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)))
                                }
                            }
                        }
                    ) { ip ->
                        Box(Modifier.padding(ip)) {
                            NavHost(navController, "dashboard") {
                                composable("dashboard") {
                                    DashboardScreen(uiState.profileEnvelope, uiState.session, uiState.loading,
                                        { a, s, d -> viewModel.addTransaction(a, s, d) }, { a, s, d -> viewModel.addTransaction(-a, s, d) },
                                        { r -> navController.navigate(r) }, { m -> scope.launch { snackbarHostState.showSnackbar(m) } })
                                }
                                composable("analytics") { AnalyticsScreen(uiState.profileEnvelope) }
                                composable("history") {
                                    HistoryScreen(uiState.profileEnvelope,
                                        onDelete = { txId ->
                                            val tx = uiState.profileEnvelope?.transactions?.find { it.id == txId }
                                            if (tx != null) {
                                                viewModel.deleteTransaction(txId)
                                                scope.launch {
                                                    val r = snackbarHostState.showSnackbar("Transaction deleted", "UNDO", duration = SnackbarDuration.Short)
                                                    if (r == SnackbarResult.ActionPerformed) viewModel.addTransaction(if (tx.amount < 0) -tx.amount else tx.amount, tx.source, tx.date)
                                                }
                                            }
                                        },
                                        onEdit = { id, a, s, d -> viewModel.updateTransaction(id, a, s, d) })
                                }
                                composable("settings") {
                                    SettingsScreen(uiState.profileEnvelope, uiState.profiles,
                                        { viewModel.updateSettings(it) }, { viewModel.addQuickAction(it) },
                                        { i, a -> viewModel.updateQuickAction(i, a) }, { viewModel.deleteQuickAction(it) },
                                        { viewModel.createProfile(it) }, { viewModel.deleteProfile(it) },
                                        { viewModel.deleteAllData() }, { viewModel.importFromJson(it) },
                                        { p -> viewModel.deleteAccount(p) }, context)
                                }
                                composable("notifications") {
                                    NotificationsScreen(uiState.profileEnvelope, { viewModel.markNotificationsSeen() }, { navController.popBackStack() })
                                }
                                composable("admin") {
                                    LaunchedEffect(Unit) { viewModel.loadAdmin() }
                                    AdminScreen(uiState.session, uiState.adminStats, uiState.adminUsers, uiState.loading,
                                        { viewModel.loadAdmin() }, { viewModel.deleteUser(it) }, { navController.popBackStack() })
                                }
                            }
                        }
                    }
                    if (uiState.loading) LoadingOverlay()
                }
            }
        }
    }
}
