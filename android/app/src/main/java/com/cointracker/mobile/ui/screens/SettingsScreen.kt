package com.cointracker.mobile.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cointracker.mobile.data.*
import com.cointracker.mobile.ui.components.GlassCard
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SettingsScreen(
    envelope: ProfileEnvelope?,
    profiles: List<String>,
    onUpdateSettings: (Settings) -> Unit,
    onAddQuickAction: (QuickAction) -> Unit,
    onUpdateQuickAction: (Int, QuickAction) -> Unit,
    onDeleteQuickAction: (Int) -> Unit,
    onCreateProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDeleteAllData: () -> Unit,
    onImportJson: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    context: Context
) {
    val currentSettings = envelope?.settings

    var goalInput by remember { mutableStateOf(currentSettings?.goal?.toString() ?: "13500") }
    LaunchedEffect(currentSettings?.goal) { goalInput = currentSettings?.goal?.toString() ?: "13500" }

    var actionText       by remember { mutableStateOf("") }
    var actionAmount     by remember { mutableStateOf("") }
    var actionIsPositive by remember { mutableStateOf(true) }
    var editActionIndex  by remember { mutableIntStateOf(-1) }
    var newProfileName   by remember { mutableStateOf("") }
    var newIncomeCategory   by remember { mutableStateOf("") }
    var newExpenseCategory  by remember { mutableStateOf("") }
    val textColor = MaterialTheme.colorScheme.onSurface

    // ── Dialogs ───────────────────────────────────────────────────────────────
    var showDeleteProfileDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog     by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteAccountPassword   by remember { mutableStateOf("") }

    // ── File launchers ────────────────────────────────────────────────────────

    // Export
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val arr = JSONArray()
                envelope?.transactions?.forEach { tx ->
                    arr.put(JSONObject().apply {
                        put("id", tx.id); put("date", tx.date)
                        put("amount", tx.amount); put("source", tx.source)
                        put("previous_balance", tx.previousBalance)
                    })
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(arr.toString().toByteArray()) }
                Toast.makeText(context, "Backup saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Import
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: throw Exception("Could not read file")
                // Quick validation: must start with '['
                if (!jsonString.trim().startsWith("["))
                    throw Exception("File does not look like a Coin Tracker backup")
                onImportJson(jsonString)
                Toast.makeText(context, "Backup imported — check your transactions!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Confirmation dialogs ─────────────────────────────────────────────────

    if (showDeleteProfileDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteProfileDialog = false },
            title = { Text("Delete Profile?") },
            text  = { Text("This will delete '${envelope?.profile}' and switch to Default.") },
            confirmButton = {
                Button(onClick = { onDeleteProfile(envelope?.profile ?: ""); showDeleteProfileDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteProfileDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete ALL Data?") },
            text  = { Text("This wipes ALL profiles and transactions permanently. Cannot be undone.") },
            confirmButton = {
                Button(onClick = { onDeleteAllData(); showDeleteAllDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("WIPE EVERYTHING") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false; deleteAccountPassword = "" },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Delete Account?") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This permanently deletes your account AND all data. This cannot be undone.")
                    Text("Enter your password to confirm:", style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f))
                    OutlinedTextField(
                        value = deleteAccountPassword,
                        onValueChange = { deleteAccountPassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deleteAccountPassword.isNotBlank()) {
                            onDeleteAccount(deleteAccountPassword)
                            showDeleteAccountDialog = false
                            deleteAccountPassword = ""
                        }
                    },
                    enabled = deleteAccountPassword.isNotBlank(),
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete My Account") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false; deleteAccountPassword = "" }) { Text("Cancel") }
            }
        )
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = textColor)
        Spacer(Modifier.height(16.dp))

        // ── Data Management ───────────────────────────────────────────────────
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text("Data Management", style = MaterialTheme.typography.titleMedium, color = Color(0xFF3B82F6))
                Spacer(Modifier.height(8.dp))

                // Export
                Button(
                    onClick = { exportLauncher.launch("cointracker_backup_${System.currentTimeMillis()}.json") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("⬇  Download JSON Backup") }

                Spacer(Modifier.height(8.dp))

                // Import
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) { Text("⬆  Restore from JSON Backup") }

                Spacer(Modifier.height(8.dp))

                Text(
                    "Import replaces current profile's transactions with those from the backup file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.55f)
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick  = { showDeleteAllDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All Data") }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── Goal ──────────────────────────────────────────────────────────────
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text("Goal Setting", style = MaterialTheme.typography.titleMedium, color = textColor)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = goalInput, onValueChange = { goalInput = it },
                    label = { Text("Coin Goal") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val ng = goalInput.toIntOrNull()
                    if (ng != null && ng > 0) onUpdateSettings((currentSettings ?: Settings()).copy(goal = ng))
                }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Update Goal") }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── Quick Actions ─────────────────────────────────────────────────────
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text(if (editActionIndex >= 0) "Edit Action" else "Add Quick Action",
                    style = MaterialTheme.typography.titleMedium, color = textColor)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = actionText, onValueChange = { actionText = it },
                    label = { Text("Label") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = actionAmount, onValueChange = { actionAmount = it },
                        label = { Text("Amount") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { actionIsPositive = !actionIsPositive },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (actionIsPositive) Color(0xFF10B981) else Color(0xFFEF4444))) {
                        Text(if (actionIsPositive) "+" else "-")
                    }
                }
                Button(onClick = {
                    val amt = actionAmount.toIntOrNull()
                    if (amt != null && amt > 0 && actionText.isNotBlank()) {
                        val qa = QuickAction(actionText.trim(), amt, actionIsPositive)
                        if (editActionIndex >= 0) { onUpdateQuickAction(editActionIndex, qa); editActionIndex = -1 }
                        else onAddQuickAction(qa)
                        actionText = ""; actionAmount = ""; actionIsPositive = true
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(if (editActionIndex >= 0) "Save Changes" else "Add Action")
                }
                if (editActionIndex >= 0) {
                    TextButton(onClick = { editActionIndex = -1; actionText = ""; actionAmount = "" },
                        modifier = Modifier.fillMaxWidth()) { Text("Cancel Edit") }
                }
                if (envelope?.settings?.quickActions?.isNotEmpty() == true) {
                    Divider(Modifier.padding(vertical = 8.dp))
                    envelope.settings.quickActions.forEachIndexed { index, action ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { editActionIndex = index; actionText = action.text; actionAmount = action.value.toString(); actionIsPositive = action.isPositive },
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${action.text} (${if (action.isPositive) "+" else "-"}${action.value})", color = textColor)
                            IconButton(onClick = { onDeleteQuickAction(index) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── Income Categories ─────────────────────────────────────────────────
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text("Income Categories", style = MaterialTheme.typography.titleMedium, color = Color(0xFF10B981))
                Text("Customise the source list shown when adding coins. Leave empty to use the default list.",
                    style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                val effectiveIncome  = currentSettings?.effectiveIncomeCategories() ?: DEFAULT_INCOME_CATEGORIES
                val isCustomIncome   = currentSettings?.incomeCategories?.isNotEmpty() == true
                effectiveIncome.forEachIndexed { index, cat ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("• $cat", color = textColor)
                        if (isCustomIncome) {
                            IconButton(onClick = {
                                val updated = effectiveIncome.toMutableList().also { it.removeAt(index) }
                                onUpdateSettings((currentSettings ?: Settings()).copy(incomeCategories = updated))
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newIncomeCategory, onValueChange = { newIncomeCategory = it },
                        label = { Text("New category") }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val t = newIncomeCategory.trim()
                        if (t.isNotBlank()) {
                            val base = if (isCustomIncome) effectiveIncome else emptyList()
                            if (!base.contains(t)) onUpdateSettings((currentSettings ?: Settings()).copy(incomeCategories = base + t))
                            newIncomeCategory = ""
                        }
                    }) { Text("Add") }
                }
                if (isCustomIncome) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onUpdateSettings((currentSettings ?: Settings()).copy(incomeCategories = emptyList())) },
                        modifier = Modifier.fillMaxWidth()) { Text("Reset to defaults", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── Expense Categories ────────────────────────────────────────────────
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text("Expense Categories", style = MaterialTheme.typography.titleMedium, color = Color(0xFFEF4444))
                Text("Customise the category list shown when spending coins. Leave empty to use the default list.",
                    style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                val effectiveExpense = currentSettings?.effectiveExpenseCategories() ?: DEFAULT_EXPENSE_CATEGORIES
                val isCustomExpense  = currentSettings?.expenseCategories?.isNotEmpty() == true
                effectiveExpense.forEachIndexed { index, cat ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("• $cat", color = textColor)
                        if (isCustomExpense) {
                            IconButton(onClick = {
                                val updated = effectiveExpense.toMutableList().also { it.removeAt(index) }
                                onUpdateSettings((currentSettings ?: Settings()).copy(expenseCategories = updated))
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newExpenseCategory, onValueChange = { newExpenseCategory = it },
                        label = { Text("New category") }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val t = newExpenseCategory.trim()
                        if (t.isNotBlank()) {
                            val base = if (isCustomExpense) effectiveExpense else emptyList()
                            if (!base.contains(t)) onUpdateSettings((currentSettings ?: Settings()).copy(expenseCategories = base + t))
                            newExpenseCategory = ""
                        }
                    }) { Text("Add") }
                }
                if (isCustomExpense) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onUpdateSettings((currentSettings ?: Settings()).copy(expenseCategories = emptyList())) },
                        modifier = Modifier.fillMaxWidth()) { Text("Reset to defaults", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── Profiles ──────────────────────────────────────────────────────────
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text("Manage Profiles", style = MaterialTheme.typography.titleMedium, color = textColor)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = newProfileName, onValueChange = { newProfileName = it },
                    label = { Text("New Profile Name") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { if (newProfileName.isNotBlank()) { onCreateProfile(newProfileName.trim()); newProfileName = "" } },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Create Profile") }
                if (envelope?.profile != null && envelope.profile != "Default") {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showDeleteProfileDialog = true }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Delete Current Profile (${envelope.profile})")
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── Danger Zone ───────────────────────────────────────────────────────
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text("Danger Zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text("Permanently delete your account and all associated data. You will be logged out immediately and this cannot be undone.",
                    style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) { Text("Delete My Account") }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}