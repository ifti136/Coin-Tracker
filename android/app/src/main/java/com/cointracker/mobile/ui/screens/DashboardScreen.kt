package com.cointracker.mobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointracker.mobile.data.ProfileEnvelope
import com.cointracker.mobile.data.UserSession
import com.cointracker.mobile.ui.components.GlassCard
import com.cointracker.mobile.ui.theme.WebSuccess
import com.cointracker.mobile.ui.theme.WebDanger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    envelope: ProfileEnvelope?,
    session: UserSession?,
    loading: Boolean,
    onAddIncome: (Int, String, String?) -> Unit,
    onAddExpense: (Int, String, String?) -> Unit,
    onNavigate: (String) -> Unit
) {
    var addAmount by remember { mutableStateOf("") }
    var addSource by remember { mutableStateOf("Other") }
    var spendAmount by remember { mutableStateOf("") }
    var spendCategory by remember { mutableStateOf("Other") }

    // Bottom Sheet State
    var showSourceSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Predefined Lists
    val incomeSources = listOf("Event Reward", "Login", "Daily Games", "Campaign Reward", "Ads", "Achievements", "Other")
    val expenseCategories = listOf("Box Draw", "Manager Purchase", "Pack Purchase", "Store Purchase", "Other")

    // Support Dialog
    var showSupportDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant, // Darker background
            title = { Text("Buy me a Coffee ☕", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("If you find this app useful, consider supporting the dev!")
                    DonationOption("bKash", "01678713786", Color(0xFFE2136E), clipboardManager, context)
                    DonationOption("Nagad", "01678713786", Color(0xFFF6921E), clipboardManager, context)
                    DonationOption("Rocket", "01678713786", Color(0xFF8C3494), clipboardManager, context)
                    Text("Tap a card to copy number", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            },
            confirmButton = {
                TextButton(onClick = { showSupportDialog = false }) { Text("Close") }
            }
        )
    }

    // Source Selection Sheet
    if (showSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showSourceSheet = false }, sheetState = sheetState) {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(incomeSources) { src ->
                    ListItem(
                        headlineContent = { Text(src) },
                        modifier = Modifier.clickable { addSource = src; showSourceSheet = false }
                    )
                }
            }
        }
    }

    // Category Selection Sheet
    if (showCategorySheet) {
        ModalBottomSheet(onDismissRequest = { showCategorySheet = false }, sheetState = sheetState) {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(expenseCategories) { cat ->
                    ListItem(
                        headlineContent = { Text(cat) },
                        modifier = Modifier.clickable { spendCategory = cat; showCategorySheet = false }
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // Balance Card
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Current Balance", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text("${envelope?.balance ?: 0} coins", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(
                    progress = { (envelope?.progress ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Text("Goal: ${envelope?.goal ?: 0} • ${envelope?.progress ?: 0}%", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Stats Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today" to envelope?.dashboardStats?.today, "Week" to envelope?.dashboardStats?.week, "Month" to envelope?.dashboardStats?.month).forEach { (label, value) ->
                GlassCard(modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.7f))
                        Text("${value ?: 0}", fontWeight = FontWeight.Bold, color = WebSuccess)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Quick Actions (unchanged logic)
        Text("Quick Actions", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        val actions = envelope?.settings?.quickActions ?: emptyList()
        if (actions.isNotEmpty()) {
            val chunked = actions.chunked(2)
            chunked.forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { action ->
                        Button(
                            onClick = {
                                if (action.isPositive) onAddIncome(action.value, action.text, null)
                                else onAddExpense(action.value, action.text, null)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(action.text, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    if (action.isPositive) "+${action.value}" else "-${action.value}",
                                    fontSize = 12.sp,
                                    color = if (action.isPositive) WebSuccess else WebDanger
                                )
                            }
                        }
                    }
                    if (rowItems.size < 2) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        // Add / Spend Forms
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // ADD CARD
            GlassCard(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Add Coins", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = addAmount, onValueChange = { addAmount = it },
                        placeholder = { Text("Amt") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = addSource,
                        onValueChange = {},
                        label = { Text("Source") },
                        modifier = Modifier.fillMaxWidth().clickable { showSourceSheet = true },
                        enabled = false, // Disable typing, enable click
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            addAmount.toIntOrNull()?.let { onAddIncome(it, addSource, null); addAmount = "" }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WebSuccess),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add") }
                }
            }

            // SPEND CARD
            GlassCard(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Spend Coins", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = spendAmount, onValueChange = { spendAmount = it },
                        placeholder = { Text("Amt") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = spendCategory,
                        onValueChange = {},
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth().clickable { showCategorySheet = true },
                        enabled = false,
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            spendAmount.toIntOrNull()?.let { onAddExpense(it, spendCategory, null); spendAmount = "" }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WebDanger),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Spend") }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Buy me a cha Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showSupportDialog = true }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("☕", fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Buy me a cha", fontWeight = FontWeight.Bold)
                    Text("Support the developer", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun DonationOption(
    name: String,
    number: String,
    color: Color,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                clipboardManager.setText(AnnotatedString(number))
                Toast.makeText(context, "$name number copied!", Toast.LENGTH_SHORT).show()
            }
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                Text("Personal • Send Money", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            }
            Text(number, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
        }
    }
}