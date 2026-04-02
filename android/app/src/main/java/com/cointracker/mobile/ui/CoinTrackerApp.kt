package com.cointracker.mobile.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cointracker.mobile.R
import com.cointracker.mobile.data.defaultSettings
import com.cointracker.mobile.ui.components.GlassCard
import com.cointracker.mobile.ui.screens.*
import com.cointracker.mobile.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AnimatedGradientBackground(isDark: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val c1Start = if (isDark) GradientDark1 else GradientLight1
    val c1End   = if (isDark) GradientDark2 else GradientLight2
    val c2Start = if (isDark) GradientDark3 else GradientLight3
    val c2End   = if (isDark) GradientDark4 else GradientLight4
    val c1 by infiniteTransition.animateColor(initialValue = c1Start, targetValue = c1End,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse), label = "c1")
    val c2 by infiniteTransition.animateColor(initialValue = c2Start, targetValue = c2End,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse), label = "c2")
    Box(modifier = modifier.background(Brush.linearGradient(listOf(c1, c2)))) { content() }
}

@Composable
fun LoadingOverlay() {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            tonalElevation = 8.dp, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinTrackerApp(viewModel: CoinTrackerViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.error) {
        val msg = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        viewModel.clearError()
    }

    CoinTrackerTheme(darkTheme = isDark) {
        AnimatedGradientBackground(isDark = isDark, modifier = Modifier.fillMaxSize()) {
            if (uiState.session == null) {
                LoginScreen(loading = uiState.loading, onLogin = { u, p -> viewModel.login(u, p) },
                    onRegister = { u, p -> viewModel.register(u, p) }, onToggleTheme = { viewModel.toggleTheme() },
                    isDark = isDark, loggedIn = uiState.session != null, onSuccess = {}, error = uiState.error)
                if (uiState.loading) LoadingOverlay()
                Box(modifier = Modifier.fillMaxSize()) {
                    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        topBar = {
                            Column(modifier = Modifier.statusBarsPadding()) {
                                GlassCard(modifier = Modifier.padding(16.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(painter = painterResource(id = R.drawable.coin), contentDescription = "Logo",
                                                tint = Color.Unspecified, modifier = Modifier.size(28.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Coin Tracker", style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { viewModel.toggleTheme() }) {
                                                Text(text = if (isDark) "☀️" else "🌙", style = MaterialTheme.typography.titleMedium)
                                            }
                                            Box {
                                                var showProfileMenu by remember { mutableStateOf(false) }
                                                IconButton(onClick = { showProfileMenu = true }) {
                                                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurface)
                                                }
                                                MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = MaterialTheme.shapes.medium)) {
                                                    DropdownMenu(expanded = showProfileMenu, onDismissRequest = { showProfileMenu = false },
                                                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                                                        Text("Profiles", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        val activeProfile = uiState.session?.currentProfile
                                                        uiState.profiles.forEach { profile ->
                                                            val isActive = profile == activeProfile
                                                            DropdownMenuItem(
                                                                text = {
                                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                                        Text(text = profile,
                                                                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                                                                        if (isActive) {
                                                                            Spacer(Modifier.weight(1f))
                                                                            Icon(imageVector = Icons.Default.Check, contentDescription = "Active",
                                                                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                                        }
                                                                    }
                                                                },
                                                                onClick = { if (!isActive) viewModel.switchProfile(profile); showProfileMenu = false }
                                                            )
                                                        }
                                                        Divider()
                                                        DropdownMenuItem(text = { Text("+ Add Profile") },
                                                            onClick = { navController.navigate("settings"); showProfileMenu = false })
                                                        if (uiState.session?.role == "admin") {
                                                            DropdownMenuItem(
                                                                text = { Text("👑 Admin Panel", color = MaterialTheme.colorScheme.primary) },
                                                                onClick = { navController.navigate("admin"); showProfileMenu = false })
                                                        }
                                                        DropdownMenuItem(
                                                            text = { Text("Log Out", color = MaterialTheme.colorScheme.error) },
                                                            onClick = { viewModel.logout(); showProfileMenu = false })
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        bottomBar = {
                            NavigationBar(containerColor = if (isDark) Color(0xFF1A1D23).copy(alpha = 0.9f) else Color.White,
                                contentColor = MaterialTheme.colorScheme.primary, tonalElevation = 8.dp) {
                                val currentBackStack by navController.currentBackStackEntryAsState()
                                val currentRoute = currentBackStack?.destination?.route ?: "dashboard"
                                listOf("dashboard" to Icons.Default.Home, "analytics" to Icons.Default.DateRange,
                                    "history" to Icons.Default.List, "settings" to Icons.Default.Settings
                                ).forEach { (route, icon) ->
                                    NavigationBarItem(icon = { Icon(icon, contentDescription = route) },
                                        label = { Text(route.replaceFirstChar { it.uppercase() }) },
                                        selected = currentRoute == route,
                                        onClick = {
                                            if (currentRoute != route) {
                                                navController.navigate(route) {
                                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                    launchSingleTop = true; restoreState = true
                                                }
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)))
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            NavHost(navController = navController, startDestination = "dashboard") {
                                composable("dashboard") {
                                    DashboardScreen(envelope = uiState.profileEnvelope, session = uiState.session,
                                        loading = uiState.loading,
                                        onAddIncome = { amt, src, date -> viewModel.addTransaction(amt, src, date) },
                                        onAddExpense = { amt, src, date -> viewModel.addTransaction(-amt, src, date) },
                                        onNavigate = { route -> navController.navigate(route) },
                                        onShowSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } })
                                }
                                composable("analytics") { AnalyticsScreen(envelope = uiState.profileEnvelope) }
                                composable("history") {
                                    HistoryScreen(envelope = uiState.profileEnvelope,
                                        onDelete = { txId ->
                                            val tx = uiState.profileEnvelope?.transactions?.find { it.id == txId }
                                            if (tx != null) {
                                                viewModel.deleteTransaction(txId)
                                                scope.launch {
                                                    val result = snackbarHostState.showSnackbar("Transaction deleted", "UNDO", duration = SnackbarDuration.Short)
                                                    if (result == SnackbarResult.ActionPerformed)
                                                        viewModel.addTransaction(if (tx.amount < 0) -tx.amount else tx.amount, tx.source, tx.date)
                                                }
                                            }
                                        },
                                        onEdit = { id, amt, src, date -> viewModel.updateTransaction(id, amt, src, date) })
                                }
                                composable("settings") {
                                    SettingsScreen(envelope = uiState.profileEnvelope, profiles = uiState.profiles,
                                        onUpdateSettings = { viewModel.updateSettings(it) },
                                        onAddQuickAction = { viewModel.addQuickAction(it) },
                                        onUpdateQuickAction = { idx, action -> viewModel.updateQuickAction(idx, action) },
                                        onDeleteQuickAction = { viewModel.deleteQuickAction(it) },
                                        onCreateProfile = { viewModel.createProfile(it) },
                                        onDeleteProfile = { viewModel.deleteProfile(it) },
                                        onDeleteAllData = { viewModel.deleteAllData() },
                                        onImportData = {}, onExportData = {}, context = context)
                                }
                                composable("admin") {
                                    LaunchedEffect(Unit) { viewModel.loadAdmin() }
                                    AdminScreen(session = uiState.session, stats = uiState.adminStats,
                                        users = uiState.adminUsers, loading = uiState.loading,
                                        onRefresh = { viewModel.loadAdmin() },
                                        onDeleteUser = { viewModel.deleteUser(it) },
                                        onBack = { navController.popBackStack() })
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
