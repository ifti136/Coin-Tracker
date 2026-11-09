package com.cointracker.mobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointracker.mobile.data.DEFAULT_EXPENSE_CATEGORIES
import com.cointracker.mobile.data.DEFAULT_INCOME_CATEGORIES
import com.cointracker.mobile.data.ProfileEnvelope
import com.cointracker.mobile.data.UserSession
import com.cointracker.mobile.data.effectiveExpenseCategories
import com.cointracker.mobile.data.effectiveIncomeCategories
import com.cointracker.mobile.ui.components.GlassCard
import com.cointracker.mobile.ui.theme.expense
import com.cointracker.mobile.ui.theme.expenseContainer
import com.cointracker.mobile.ui.theme.income
import com.cointracker.mobile.ui.theme.incomeContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    envelope: ProfileEnvelope?,
    session: UserSession?,
    isActionLoading: (String) -> Boolean,
    onAddIncome: (Int, String, String?) -> Unit,
    onAddExpense: (Int, String, String?) -> Unit,
    onNavigate: (String) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var addAmount by remember { mutableStateOf("") }
    var addSource by remember { mutableStateOf("Other") }
    var spendAmount by remember { mutableStateOf("") }
    var spendCategory by remember { mutableStateOf("Other") }
    var showSupportDialog by remember { mutableStateOf(false) }
    val incomeSources = envelope?.settings?.effectiveIncomeCategories() ?: DEFAULT_INCOME_CATEGORIES
    val expenseCategories = envelope?.settings?.effectiveExpenseCategories() ?: DEFAULT_EXPENSE_CATEGORIES
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Support dialog
    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Buy me a Coffee", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("If you find this app useful, consider supporting the dev!")
                    DonationOption("bKash", "01678713786", Color(0xFFE2136E), clipboardManager, context)
                    DonationOption("Nagad", "01678713786", Color(0xFFF6921E), clipboardManager, context)
                    DonationOption("Rocket", "01678713786", Color(0xFF8C3494), clipboardManager, context)
                    Text(
                        "Tap a card to copy number",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showSupportDialog = false }) { Text("Close") } }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        // ── 1. Focal Balance & Goal Panel (Gold Accent) ───────────────────────
        item {
            val bal = envelope?.balance ?: 0
            val goal = envelope?.goal ?: 0
            val remaining = goal - bal
            val prog = (envelope?.progress ?: 0).coerceIn(0, 100)

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header row: Label & Remaining Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = CircleShape,
                            color = when {
                                goal > 0 && remaining <= 0 -> MaterialTheme.colorScheme.incomeContainer
                                goal > 0 -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = when {
                                    goal > 0 && remaining <= 0 -> "🎉 Goal Achieved!"
                                    goal > 0 -> "⚡ ${remaining.formatCoins()} to goal"
                                    else -> "No goal set"
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    goal > 0 && remaining <= 0 -> MaterialTheme.colorScheme.income
                                    goal > 0 -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Balance Display
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = bal.formatCoins(),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "coins",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Progress Track with 25%, 50%, 75% Milestone Markers
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { prog / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        // Milestone tick marks
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.weight(0.25f))
                            Box(
                                Modifier
                                    .width(2.dp)
                                    .height(6.dp)
                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                            )
                            Spacer(Modifier.weight(0.25f))
                            Box(
                                Modifier
                                    .width(2.dp)
                                    .height(6.dp)
                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                            )
                            Spacer(Modifier.weight(0.25f))
                            Box(
                                Modifier
                                    .width(2.dp)
                                    .height(6.dp)
                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                            )
                            Spacer(Modifier.weight(0.25f))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Goal & ETA Footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (goal > 0) "Goal: ${goal.formatCoins()} • $prog%" else "No goal set",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        envelope?.estimatedDays?.let { days ->
                            Text(
                                text = when (days) {
                                    0 -> "Goal reached!"
                                    1 -> "~1 day to goal"
                                    else -> "~$days days to goal"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    days == 0 -> MaterialTheme.colorScheme.income
                                    days <= 7 -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Integrated Share Progress Card Action
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (envelope != null) {
                                scope.launch(Dispatchers.IO) {
                                    ProgressCardGenerator.shareProgressCard(context, envelope)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share progress card", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
        }

        // ── 2. Summary Metrics (Today, This Week, This Month) ───────────────────
        item {
            val stats = envelope?.dashboardStats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Today" to (stats?.today ?: 0),
                    "This week" to (stats?.week ?: 0),
                    "This month" to (stats?.month ?: 0)
                ).forEach { (label, value) ->
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "+${value.formatCoins()}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.income
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── 3. Quick Actions Grid ─────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Quick actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Repeat common entries in one tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onNavigate("settings") }) {
                    Text("Manage", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        val actions = envelope?.settings?.quickActions ?: emptyList()
        val totalActionItems = actions.size + 1
        val numRows = (totalActionItems + 1) / 2

        for (rowIndex in 0 until numRows) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val firstIndex = rowIndex * 2
                    val secondIndex = firstIndex + 1

                    // First tile
                    if (firstIndex < actions.size) {
                        val action = actions[firstIndex]
                        val isLoading = isActionLoading("addTransaction")
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = !isLoading) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (action.isPositive) {
                                        onAddIncome(action.value, action.text, null)
                                        onShowSnackbar("+${action.value} coins from ${action.text}")
                                    } else {
                                        onAddExpense(action.value, action.text, null)
                                        onShowSnackbar("-${action.value} coins for ${action.text}")
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = action.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (action.isPositive) "+${action.value}" else "-${action.value}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (action.isPositive) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.expense
                                )
                            }
                        }
                    } else if (firstIndex == actions.size) {
                        OutlinedButton(
                            onClick = { onNavigate("settings") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "New action", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text("New Action", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    // Second tile
                    if (secondIndex < actions.size) {
                        val action = actions[secondIndex]
                        val isLoading = isActionLoading("addTransaction")
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = !isLoading) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (action.isPositive) {
                                        onAddIncome(action.value, action.text, null)
                                        onShowSnackbar("+${action.value} coins from ${action.text}")
                                    } else {
                                        onAddExpense(action.value, action.text, null)
                                        onShowSnackbar("-${action.value} coins for ${action.text}")
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = action.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (action.isPositive) "+${action.value}" else "-${action.value}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (action.isPositive) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.expense
                                )
                            }
                        }
                    } else if (secondIndex == actions.size) {
                        OutlinedButton(
                            onClick = { onNavigate("settings") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "New action", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text("New Action", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        item { Spacer(Modifier.height(10.dp)) }

        // ── 4. Add or Spend Coins Entry Forms ─────────────────────────────────
        item {
            Text(
                text = "Add or spend coins",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Add coins card (Income)
                GlassCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.income.copy(alpha = 0.35f))
                ) {
                    Column(Modifier.padding(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Add coins", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.incomeContainer
                            ) {
                                Text(
                                    "Income +",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.income,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = addAmount,
                            onValueChange = { addAmount = it },
                            placeholder = { Text("Amount", fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        Spacer(Modifier.height(6.dp))

                        var sourceExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = sourceExpanded,
                            onExpandedChange = { sourceExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextField(
                                value = addSource,
                                onValueChange = { addSource = it },
                                label = { Text("Source", fontSize = 11.sp) },
                                readOnly = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded)
                                }
                            )
                            ExposedDropdownMenu(
                                expanded = sourceExpanded,
                                onDismissRequest = { sourceExpanded = false }
                            ) {
                                incomeSources.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = { addSource = option; sourceExpanded = false }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        val isAddLoading = isActionLoading("addTransaction")
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val amt = addAmount.toIntOrNull()
                                when {
                                    amt == null || amt <= 0 -> onShowSnackbar("Enter a valid positive amount")
                                    amt > 999_999 -> onShowSnackbar("Amount too large (max 999,999)")
                                    else -> {
                                        onAddIncome(amt, addSource, null)
                                        onShowSnackbar("+$amt coins from $addSource")
                                        addAmount = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.income,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isAddLoading
                        ) {
                            if (isAddLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Add coins", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Spend coins card (Expense)
                GlassCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.expense.copy(alpha = 0.35f))
                ) {
                    Column(Modifier.padding(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Spend coins", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.expenseContainer
                            ) {
                                Text(
                                    "Expense −",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.expense,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = spendAmount,
                            onValueChange = { spendAmount = it },
                            placeholder = { Text("Amount", fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        Spacer(Modifier.height(6.dp))

                        var categoryExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextField(
                                value = spendCategory,
                                onValueChange = { spendCategory = it },
                                label = { Text("Category", fontSize = 11.sp) },
                                readOnly = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                                }
                            )
                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                expenseCategories.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = { spendCategory = option; categoryExpanded = false }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        val isSpendLoading = isActionLoading("addTransaction")
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val amt = spendAmount.toIntOrNull()
                                when {
                                    amt == null || amt <= 0 -> onShowSnackbar("Enter a valid positive amount")
                                    amt > 999_999 -> onShowSnackbar("Amount too large (max 999,999)")
                                    else -> {
                                        onAddExpense(amt, spendCategory, null)
                                        onShowSnackbar("-$amt coins for $spendCategory")
                                        spendAmount = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.expense,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSpendLoading
                        ) {
                            if (isSpendLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Spend coins", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // ── 5. Support / Buy me a Coffee ───────────────────────────────────────
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showSupportDialog = true
                }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Coffee,
                        contentDescription = "Coffee",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Support the project • By Ifti",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Buy me a coffee if you find Coin Tracker useful",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

private fun Int.formatCoins(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()

// Donation card composable
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
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                clipboardManager.setText(AnnotatedString(number))
                Toast.makeText(context, "$name number copied!", Toast.LENGTH_SHORT).show()
            }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
                Text(
                    "Personal • Send Money",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Text(number, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
        }
    }
}
