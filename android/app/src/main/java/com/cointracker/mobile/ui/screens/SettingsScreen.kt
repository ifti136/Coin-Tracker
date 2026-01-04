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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointracker.mobile.data.ProfileEnvelope
import com.cointracker.mobile.data.QuickAction
import com.cointracker.mobile.ui.components.GlassCard
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SettingsScreen(
    envelope: ProfileEnvelope?,
    profiles: List<String>,
    onUpdateGoal: (Int) -> Unit,
    onAddQuickAction: (QuickAction) -> Unit,
    onUpdateQuickAction: (Int, QuickAction) -> Unit,
    onDeleteQuickAction: (Int) -> Unit,
    onCreateProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDeleteAllData: () -> Unit,
    onImportData: (String) -> Unit,
    onExportData: () -> Unit,
    context: Context
) {
    var goalInput by remember { mutableStateOf(envelope?.settings?.goal?.toString() ?: "13500") }
    var actionText by remember { mutableStateOf("") }
    var actionAmount by remember { mutableStateOf("") }
    var actionIsPositive by remember { mutableStateOf(true) }
    var editActionIndex by remember { mutableIntStateOf(-1) }

    var newProfileName by remember { mutableStateOf("") }
    val textColor = MaterialTheme.colorScheme.onSurface

    // Export Launcher (Saves as JSON file)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                // Manually build JSON array to avoid Gson dependency issues
                val transactionsArray = JSONArray()
                envelope?.transactions?.forEach { tx ->
                    val txJson = JSONObject().apply {
                        put("id", tx.id)
                        put("date", tx.date)
                        put("amount", tx.amount)
                        put("source", tx.source)
                        put("previous_balance", tx.previousBalance)
                    }
                    transactionsArray.put(txJson)
                }

                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(transactionsArray.toString().toByteArray())
                }
                Toast.makeText(context, "Backup saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Delete Confirmation Dialogs
    var showDeleteProfileDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    if (showDeleteProfileDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteProfileDialog = false },
            title = { Text("Delete Profile?") },
            text = { Text("This will delete the current profile '${envelope?.profile}' and switch to Default.") },
            confirmButton = {
                Button(
                    onClick = { onDeleteProfile(envelope?.profile ?: ""); showDeleteProfileDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteProfileDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete ALL DATA?") },
            text = { Text("This will wipe ALL profiles and transactions permanently. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { onDeleteAllData(); showDeleteAllDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("WIPE EVERYTHING") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = textColor)
        Spacer(Modifier.height(16.dp))

        // Data Management
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text("Data Management", style = MaterialTheme.typography.titleMedium, color = Color(0xFF3B82F6))
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { exportLauncher.launch("cointracker_backup_${System.currentTimeMillis()}.json") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Download JSON Backup") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showDeleteAllDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All Data") }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Goal
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text("Goal Setting", style = MaterialTheme.typography.titleMedium, color = textColor)
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    label = { Text("Coin Goal") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { goalInput.toIntOrNull()?.let(onUpdateGoal) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Update Goal") }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Quick Actions
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text(if(editActionIndex >= 0) "Edit Action" else "Add Quick Action", style = MaterialTheme.typography.titleMedium, color = textColor)
                OutlinedTextField(
                    value = actionText,
                    onValueChange = { actionText = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = actionAmount,
                        onValueChange = { actionAmount = it },
                        label = { Text("Amount") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { actionIsPositive = !actionIsPositive },
                        colors = ButtonDefaults.buttonColors(containerColor = if(actionIsPositive) Color(0xFF10B981) else Color(0xFFEF4444))
                    ) {
                        Text(if(actionIsPositive) "+" else "-")
                    }
                }
                Button(
                    onClick = {
                        val amt = actionAmount.toIntOrNull()
                        if (amt != null && actionText.isNotBlank()) {
                            val qa = QuickAction(actionText, amt, actionIsPositive)
                            if (editActionIndex >= 0) {
                                onUpdateQuickAction(editActionIndex, qa)
                                editActionIndex = -1
                            } else {
                                onAddQuickAction(qa)
                            }
                            actionText = ""; actionAmount = ""; actionIsPositive = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text(if(editActionIndex >= 0) "Save Changes" else "Add Action") }

                if (editActionIndex >= 0) {
                    TextButton(
                        onClick = { editActionIndex = -1; actionText = ""; actionAmount = "" },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancel Edit") }
                }

                Divider(Modifier.padding(vertical = 8.dp))

                envelope?.settings?.quickActions?.forEachIndexed { index, action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                editActionIndex = index
                                actionText = action.text
                                actionAmount = action.value.toString()
                                actionIsPositive = action.isPositive
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${action.text} (${if(action.isPositive)+action.value else -action.value})", color = textColor)
                        IconButton(onClick = { onDeleteQuickAction(index) }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Profiles
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text("Manage Profiles", style = MaterialTheme.typography.titleMedium, color = textColor)
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("New Profile Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { if (newProfileName.isNotBlank()) onCreateProfile(newProfileName); newProfileName = "" },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Create Profile") }

                if (envelope?.profile != "Default") {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showDeleteProfileDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Current Profile (${envelope?.profile})")
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}