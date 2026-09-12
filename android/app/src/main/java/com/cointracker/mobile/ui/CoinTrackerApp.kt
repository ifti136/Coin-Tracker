package com.cointracker.mobile.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
fun SyncBanner(syncState: SyncState, onDismiss: () -> Unit) {
    val (text, icon, color, iconDesc) = when (syncState) {
        SyncState.Offline -> Triple("Offline — showing cached data", Icons.Default.WifiOff, MaterialTheme.colorScheme.tertiary, "Offline indicator")
        SyncState.RestoredFromCache -> Triple("DB was empty — restored from local safety cache", Icons.Default.Restore, MaterialTheme.colorScheme.secondary, "Restored from cache")
        SyncState.PushedCacheToDb -> Triple("Offline changes synced to database", Icons.Default.CloudUpload, MaterialTheme.colorScheme.primary, "Synced to database")
        else -> return
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            Arrangement.spacedBy(10.dp),
            Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = iconDesc, tint = color, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss sync banner", tint = color, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ConflictDialog(syncState: SyncState.Conflict, onUseCache: () -> Unit, onUseDatabase: () -> Unit, onDismiss: () -> Unit) {
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        icon = { Icon(Icons.Default.Warning, contentDescription = "Warning: data conflict detected", modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.error) },
        title = { Text("Data Conflict Detected", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your local safety cache and the database have different data. Choose which to keep.",
                    style = MaterialTheme.typography.bodyMedium)
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Local Cache (saved ${fmt.format(Date(syncState.cached.savedAt))})",
                            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text("Balance: ${syncState.cached.balance} coins  •  ${syncState.cached.transactionCount} txns",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Database (online)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text("Balance: ${syncState.db.balance} coins  •  ${syncState.db.transactions.size} txns",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onUseCache, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text("Use Local Cache")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onUseDatabase) { Text("Use Database") }
        },
        secondaryDismissButton = {
            TextButton(onClick = onDismiss) { Text("Decide Later") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CoinTrackerApp(viewModel: CoinTrackerViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val syncState = uiState.syncState

    // Window size class for adaptive layouts
    val windowSizeClass = calculateWindowSizeClass(activity)
    val isTablet = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.Expanded
    val maxContentWidth = if (isTablet) 600.dp else Dp.Unspecified

    LaunchedEffect(uiState.error) {
        val msg = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Short,
            actionLabel = null
        )
        viewModel.clearError()
    }

    if (syncState is SyncState.Conflict) {
        ConflictDialog(syncState,
            onUseCache = { viewModel.resolveConflictUseCache() },
            onUseDatabase = { viewModel.resolveConflictUseDatabase() },
            onDismiss = { viewModel.dismissConflictDialog() }
        )
    }

    CoinTrackerTheme(darkTheme = isDark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (uiState.session == null) {
                LoginScreen(
                    isActionLoading = { action -> viewModel.isActionLoading(action) },
                    onLogin = { u, p -> viewModel.login(u, p) },
                    onRegister = { u, p -> viewModel.register(u, p) },
                    onToggleTheme = { viewModel.toggleTheme() },
                    isDark = isDark,
                    loggedIn = uiState.session != null,
                    onSuccess = {},
                    error = uiState.error
                )
                Box(Modifier.fillMaxSize()) {
                    SnackbarHost(
                        snackbarHostState,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            } else {
                val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    // Permission result handled
                }
                // Request notification permission contextually after first achievement
                LaunchedEffect(uiState.profileEnvelope?.achievements?.isNotEmpty() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val prefs = context.getSharedPreferences("cointracker_prefs", Context.MODE_PRIVATE)
                        val permRequested = prefs.getBoolean("notification_perm_requested", false)
                        if (!permRequested && uiState.profileEnvelope?.achievements?.isNotEmpty() == true) {
                            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            prefs.edit().putBoolean("notification_perm_requested", true).apply()
                        }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
                        topBar = {
                            Column(Modifier.statusBarsPadding()) {
                                GlassCard(Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .widthIn(max = maxContentWidth)
                                    .align(Alignment.CenterHorizontally)
                                ) {
                                    Row(Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(painterResource(R.drawable.coin), contentDescription = "Logo", tint = Color.Unspecified, modifier = Modifier.size(28.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Coin Tracker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val cbse by navController.currentBackStackEntryAsState()
                                            val cr = cbse?.destination?.route
                                            Box {
                                                IconButton(onClick = { if (cr != "notifications") navController.navigate("notifications") }) {
                                                    Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = if (cr == "notifications") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                                }
                                                if (uiState.unreadNotifCount > 0) {
                                                    Badge(Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp), containerColor = MaterialTheme.colorScheme.tertiary) {
                                                        Text(if (uiState.unreadNotifCount > 9) "9+" else uiState.unreadNotifCount.toString(), style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                            IconButton(onClick = { viewModel.toggleTheme() }) {
                                                Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = if (isDark) "Light mode" else "Dark mode", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                                            }
                                            Box {
                                                var showMenu by remember { mutableStateOf(false) }
                                                IconButton(onClick = { showMenu = true }) {
                                                    Icon(Icons.Default.Person, contentDescription = "Profile menu", tint = MaterialTheme.colorScheme.onSurface)
                                                }
                                                MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = MaterialTheme.shapes.medium)) {
                                                    DropdownMenu(showMenu, { showMenu = false }, Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                                                        Text("Profiles", Modifier.padding(12.dp, 8.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        val ap = uiState.session?.currentProfile
                                                        uiState.profiles.forEach { p ->
                                                            val ia = p == ap
                                                            DropdownMenuItem(text = {
                                                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                                    Text(p, color = if (ia) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (ia) FontWeight.Bold else FontWeight.Normal)
                                                                    if (ia) { Spacer(Modifier.weight(1f)); Icon(Icons.Default.Check, contentDescription = "Current profile", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                                                                }
                                                            }, onClick = { if (!ia) viewModel.switchProfile(p); showMenu = false })
                                                        }
                                                        Divider()
                                                        DropdownMenuItem({ Text("+ Add Profile") }, { navController.navigate("settings"); showMenu = false })
                                                        if (uiState.session?.role == "admin") DropdownMenuItem({ Text("Admin Panel", color = MaterialTheme.colorScheme.primary) }, { navController.navigate("admin"); showMenu = false })
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
                            if (!isTablet) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    tonalElevation = 8.dp
                                ) {
                                    val cbse by navController.currentBackStackEntryAsState()
                                    val cr = cbse?.destination?.route ?: "dashboard"
                                    listOf(
                                        "dashboard" to Icons.Default.Home,
                                        "analytics" to Icons.Default.Analytics,
                                        "history" to Icons.Default.History,
                                        "settings" to Icons.Default.Settings
                                    ).forEach { (route, icon) ->
                                        NavigationBarItem(
                                            icon = { Icon(icon, contentDescription = route) },
                                            label = { Text(route.replaceFirstChar { it.uppercase() }) },
                                            selected = cr == route,
                                            onClick = { navController.navigate(route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true; inclusive = false }; launchSingleTop = true; restoreState = true } },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            )
                                        )
                                    }
                                }
                            } else {
                                NavigationRail(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(72.dp),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    itemColors = NavigationRailDefaults.itemColors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    )
                                ) {
                                    val cbse by navController.currentBackStackEntryAsState()
                                    val cr = cbse?.destination?.route ?: "dashboard"
                                    listOf(
                                        "dashboard" to Icons.Default.Home,
                                        "analytics" to Icons.Default.Analytics,
                                        "history" to Icons.Default.History,
                                        "settings" to Icons.Default.Settings
                                    ).forEach { (route, icon) ->
                                        NavigationRailItem(
                                            icon = { Icon(icon, contentDescription = route) },
                                            label = { Text(route.replaceFirstChar { it.uppercase() }) },
                                            selected = cr == route,
                                            onClick = { navController.navigate(route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true; inclusive = false }; launchSingleTop = true; restoreState = true } }
                                        )
                                    }
                                }
                            }
                        }
                    ) { ip ->
                        Box(
                            Modifier
                                .padding(ip)
                                .widthIn(max = maxContentWidth)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            NavHost(navController, "dashboard") {
                                composable("dashboard") {
                                    DashboardScreen(
                                        envelope = uiState.profileEnvelope,
                                        session = uiState.session,
                                        isActionLoading = { action -> viewModel.isActionLoading(action) },
                                        onAddIncome = { a, s, d -> viewModel.addTransaction(a, s, d) },
                                        onAddExpense = { a, s, d -> viewModel.addTransaction(-a, s, d) },
                                        onNavigate = { r -> navController.navigate(r) },
                                        onShowSnackbar = { m -> scope.launch { snackbarHostState.showSnackbar(m) } }
                                    )
                                }
                                composable("analytics") { AnalyticsScreen(uiState.profileEnvelope) }
                                composable("history") {
                                    HistoryScreen(
                                        envelope = uiState.profileEnvelope,
                                        isActionLoading = { action -> viewModel.isActionLoading(action) },
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
                                        onEdit = { id, a, s, d -> viewModel.updateTransaction(id, a, s, d) }
                                    )
                                }
                                composable("settings") {
                                    SettingsScreen(
                                        envelope = uiState.profileEnvelope,
                                        profiles = uiState.profiles,
                                        isActionLoading = { action -> viewModel.isActionLoading(action) },
                                        onUpdateSettings = { viewModel.updateSettings(it) },
                                        onAddQuickAction = { viewModel.addQuickAction(it) },
                                        onUpdateQuickAction = { i, a -> viewModel.updateQuickAction(i, a) },
                                        onDeleteQuickAction = { viewModel.deleteQuickAction(it) },
                                        onCreateProfile = { viewModel.createProfile(it) },
                                        onDeleteProfile = { viewModel.deleteProfile(it) },
                                        onDeleteAllData = { viewModel.deleteAllData() },
                                        onImportJson = { viewModel.importFromJson(it) },
                                        onDeleteAccount = { p -> viewModel.deleteAccount(p) },
                                        context = context
                                    )
                                }
                                composable("notifications") {
                                    NotificationsScreen(
                                        envelope = uiState.profileEnvelope,
                                        isActionLoading = { action -> viewModel.isActionLoading(action) },
                                        onReadAll = { viewModel.markNotificationsSeen() },
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                                composable("admin") {
                                    LaunchedEffect(Unit) { viewModel.loadAdmin() }
                                    AdminScreen(
                                        session = uiState.session,
                                        stats = uiState.adminStats,
                                        users = uiState.adminUsers,
                                        isActionLoading = { action -> viewModel.isActionLoading(action) },
                                        onRefresh = { viewModel.loadAdmin() },
                                        onDeleteUser = { viewModel.deleteUser(it) },
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}