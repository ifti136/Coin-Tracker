package com.cointracker.mobile.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointracker.mobile.data.ProfileEnvelope
import com.cointracker.mobile.data.UserSession
import com.cointracker.mobile.ui.components.GlassCard

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

    // Support Dialog State
    var showSupportDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            title = { Text("Buy me a Coffee ☕") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "If you find this app useful, consider supporting the dev!",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    DonationOption(
                        name = "bKash",
                        number = "01678713786",
                        color = Color(0xFFE2136E),
                        clipboardManager = clipboardManager,
                        context = context
                    )
                    DonationOption(
                        name = "Nagad",
                        number = "01678713786",
                        color = Color(0xFFF6921E),
                        clipboardManager = clipboardManager,
                        context = context
                    )
                    DonationOption(
                        name = "Rocket",
                        number = "01678713786",
                        color = Color(0xFF8C3494),
                        clipboardManager = clipboardManager,
                        context = context
                    )

                    Text(
                        "Tap a card to copy number",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSupportDialog = false }) { Text("Close") }
            }
        )
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
                Text("Goal: ${envelope?.goal ?: 0} • ${envelope?.progress ?: 0}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Stats Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today" to envelope?.dashboardStats?.today, "Week" to envelope?.dashboardStats?.week, "Month" to envelope?.dashboardStats?.month).forEach { (label, value) ->
                GlassCard(modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.7f))
                        Text("${value ?: 0}", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Quick Actions
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(action.text, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    if (action.isPositive) "+${action.value}" else "-${action.value}",
                                    fontSize = 12.sp,
                                    color = if (action.isPositive) Color(0xFF10B981) else Color(0xFFEF4444)
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
            GlassCard(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Add Coins", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(
                        value = addAmount, onValueChange = { addAmount = it },
                        placeholder = { Text("Amt") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = addSource, onValueChange = { addSource = it }, label = { Text("Source") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            addAmount.toIntOrNull()?.let { onAddIncome(it, addSource, null); addAmount = "" }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add") }
                }
            }

            GlassCard(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Spend Coins", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                    OutlinedTextField(
                        value = spendAmount, onValueChange = { spendAmount = it },
                        placeholder = { Text("Amt") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = spendCategory, onValueChange = { spendCategory = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            spendAmount.toIntOrNull()?.let { onAddExpense(it, spendCategory, null); spendAmount = "" }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
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
                    Text("Buy me a cha", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Support the developer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.7f))
                }
            }
        }

        if (session?.role == "admin") {
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onNavigate("admin") }, modifier = Modifier.fillMaxWidth()) {
                Text("Access Admin Panel")
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
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
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