package com.cointracker.mobile.ui

import android.widget.Toast
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cointracker.mobile.data.defaultSettings
import com.cointracker.mobile.ui.screens.*
import com.cointracker.mobile.ui.theme.CoinTrackerTheme

// Web Gradient Colors
val Gradient1 = Color(0xFFC3AED6)
val Gradient2 = Color(0xFFF0ABFC)
val Gradient3 = Color(0xFFA1C4FD)
val Gradient4 = Color(0xFFFDF8C8)
// ----------------------------------------------------------------
// THEME COLORS
// ----------------------------------------------------------------
val WebPrimary = Color(0xFF3B82F6)
val WebSuccess = Color(0xFF10B981)
val WebDanger = Color(0xFFEF4444)

@Composable
fun AnimatedGradientBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val c1 by infiniteTransition.animateColor(
        initialValue = Gradient1, targetValue = Gradient2,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse), label = "c1"
    )
    val c2 by infiniteTransition.animateColor(
        initialValue = Gradient3, targetValue = Gradient4,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse), label = "c2"
    )

    Box(modifier = modifier.background(Brush.linearGradient(listOf(c1, c2)))) {
        content()
    }
}

@Composable
fun GlassTheme(isDark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if(isDark) darkColorScheme(
            primary = WebPrimary,
            secondary = WebSuccess,
            error = WebDanger,
            background = Color.Transparent,
            surface = Color.Black.copy(alpha = 0.3f),
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        ) else lightColorScheme(
            primary = WebPrimary,
            secondary = WebSuccess,
            error = WebDanger,
            background = Color.Transparent,
            surface = Color.White.copy(alpha = 0.1f),
            onPrimary = Color.White,
            onBackground = Color.Black, // Text color in light mode
            onSurface = Color.Black
        ),
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
        content = content
    )
}

// ----------------------------------------------------------------
// 1. MAIN NAVIGATION HOST
// ----------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinTrackerApp(viewModel: CoinTrackerViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    CoinTrackerTheme {
        GlassTheme(isDark = isDark) {
            AnimatedGradientBackground(modifier = Modifier.fillMaxSize()) {
                if (uiState.session == null) {
                    LoginNavigation(viewModel)
                } else {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets.statusBars,
                        topBar = {
                            GlassCard(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // LEFT SIDE: Theme | Profile
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.toggleTheme() }) {
                                            Icon(
                                                if (isDark) Icons.Default.ThumbUp else Icons.Default.Menu, // Using Menu/ThumbUp as placeholder
                                                contentDescription = "Theme",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Box {
                                            var showProfileMenu by remember { mutableStateOf(false) }
                                            IconButton(onClick = { showProfileMenu = true }) {
                                                Icon(Icons.Default.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurface)
                                            }
                                            DropdownMenu(expanded = showProfileMenu, onDismissRequest = { showProfileMenu = false }) {
                                                Text("Profiles", modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                                                uiState.profiles.forEach { profile ->
                                                    DropdownMenuItem(
                                                        text = { Text(profile) },
                                                        onClick = { viewModel.switchProfile(profile); showProfileMenu = false }
                                                    )
                                                }
                                                Divider()
                                                DropdownMenuItem(text = { Text("+ Add Profile") }, onClick = {
                                                    navController.navigate("settings")
                                                    showProfileMenu = false
                                                })
                                                DropdownMenuItem(text = { Text("Log Out", color = WebDanger) }, onClick = {
                                                    viewModel.logout()
                                                    showProfileMenu = false
                                                })
                                            }
                                        }
                                    }

                                    // RIGHT SIDE: Coin Tracker Logo/Text
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Coin Tracker",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Default.Star, contentDescription = "Logo", tint = Color(0xFFF59E0B)) // Gold Coin placeholder
                                    }
                                }
                            }
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = Color(0xFF1A1D23).copy(alpha = 0.9f),
                                contentColor = WebPrimary
                            ) {
                                val currentBackStack by navController.currentBackStackEntryAsState()
                                val currentRoute = currentBackStack?.destination?.route ?: "dashboard"
                                val items = listOf(
                                    "dashboard" to Icons.Default.Home,
                                    "analytics" to Icons.Default.DateRange,
                                    "history" to Icons.Default.List,
                                    "settings" to Icons.Default.Settings
                                )
                                items.forEach { (route, icon) ->
                                    NavigationBarItem(
                                        icon = { Icon(icon, contentDescription = route) },
                                        label = { Text(route.replaceFirstChar { it.uppercase() }) },
                                        selected = currentRoute == route,
                                        onClick = {
                                            if (currentRoute != route) {
                                                navController.navigate(route) {
                                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = WebPrimary,
                                            indicatorColor = WebPrimary.copy(alpha = 0.15f)
                                        )
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            NavHost(navController = navController, startDestination = "dashboard") {
                                composable("dashboard") {
                                    DashboardScreen(
                                        envelope = uiState.profileEnvelope,
                                        session = uiState.session,
                                        loading = uiState.loading,
                                        onAddIncome = { amt, src, date -> viewModel.addTransaction(amt, src, date) },
                                        onAddExpense = { amt, src, date -> viewModel.addTransaction(-amt, src, date) },
                                        onNavigate = { route -> navController.navigate(route) }
                                    )
                                }
                                composable("analytics") {
                                    AnalyticsScreen(
                                        envelope = uiState.profileEnvelope
                                    )
                                }
                                composable("history") {
                                    HistoryScreen(
                                        envelope = uiState.profileEnvelope,
                                        onDelete = { viewModel.deleteTransaction(it) },
                                        onEdit = { id, amt, src, date -> viewModel.updateTransaction(id, amt, src, date) }
                                    )
                                }
                                composable("settings") {
                                    SettingsScreen(
                                        envelope = uiState.profileEnvelope,
                                        profiles = uiState.profiles,
                                        onUpdateGoal = { viewModel.updateSettings((uiState.profileEnvelope?.settings ?: defaultSettings()).copy(goal = it)) },
                                        onAddQuickAction = { viewModel.addQuickAction(it) },
                                        onUpdateQuickAction = { idx, action -> viewModel.updateQuickAction(idx, action) },
                                        onDeleteQuickAction = { viewModel.deleteQuickAction(it) },
                                        onCreateProfile = { viewModel.createProfile(it) },
                                        onDeleteProfile = { viewModel.deleteProfile(it) },
                                        onDeleteAllData = { viewModel.deleteAllData() },
                                        onImportData = {
                                            // Import logic if needed
                                        },
                                        onExportData = {
                                            // Handled in screen
                                        },
                                        context = context
                                    )
                                }
                                composable("admin") {
                                    LaunchedEffect(Unit) { viewModel.loadAdmin() }
                                    AdminScreen(
                                        session = uiState.session,
                                        stats = uiState.adminStats,
                                        users = uiState.adminUsers,
                                        loading = uiState.loading,
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

// ----------------------------------------------------------------
// 6. LOGIN / REGISTER SCREEN
// ----------------------------------------------------------------
@Composable
fun LoginNavigation(viewModel: CoinTrackerViewModel) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard(modifier = Modifier.padding(32.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isRegisterMode) "Create Account" else "Welcome Back",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uiState.error ?: "", color = WebDanger, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { if (isRegisterMode) viewModel.register(username, password) else viewModel.login(username, password) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WebPrimary),
                    enabled = !uiState.loading
                ) {
                    if (uiState.loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    else Text(if (isRegisterMode) "Register" else "Login")
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { isRegisterMode = !isRegisterMode }) {
                    Text(
                        text = if (isRegisterMode) "Already have an account? Login" else "Don't have an account? Register",
                        color = WebPrimary
                    )
                }
            }
        }
    }
}