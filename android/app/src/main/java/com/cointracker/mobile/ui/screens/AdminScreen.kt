package com.cointracker.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointracker.mobile.data.AdminStats
import com.cointracker.mobile.data.AdminUserRow
import com.cointracker.mobile.data.UserSession
import com.cointracker.mobile.ui.components.GlassCard
import com.cointracker.mobile.ui.theme.WebDanger
import com.cointracker.mobile.ui.theme.WebSuccess

@Composable
fun AdminScreen(session: UserSession?, stats: AdminStats?, users: List<AdminUserRow>,
    loading: Boolean, onRefresh: () -> Unit, onDeleteUser: (String) -> Unit, onBack: () -> Unit) {
    if (session?.role != "admin") { LaunchedEffect(Unit) { onBack() }; return }
    var userToDelete by remember { mutableStateOf<AdminUserRow?>(null) }
    if (userToDelete != null) {
        AlertDialog(onDismissRequest = { userToDelete = null }, containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Delete User?") },
            text = { Text("Permanently delete \"${userToDelete!!.username}\" and all their data. Cannot be undone.") },
            confirmButton = { Button(onClick = { onDeleteUser(userToDelete!!.userId); userToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = WebDanger)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { userToDelete = null }) { Text("Cancel") } })
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Admin Panel", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Row { TextButton(onClick = onBack) { Text("Back") }; Spacer(Modifier.width(4.dp))
                    FilledTonalButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Refresh") } }
            }
            Spacer(Modifier.height(16.dp))
        }
        item {
            Text("Overview", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminStatCard(Modifier.weight(1f), "Users", "${stats?.totalUsers ?: 0}")
                AdminStatCard(Modifier.weight(1f), "Coins", "${stats?.totalCoins ?: 0}")
                AdminStatCard(Modifier.weight(1f), "Txns", "${stats?.totalTransactions ?: 0}")
            }
            Spacer(Modifier.height(16.dp))
        }
        if (stats != null && stats.labels.isNotEmpty()) {
            item {
                Text("New Users — Last 7 Days", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp))
                GlassCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val maxVal = (stats.newUsersData.maxOrNull() ?: 1).coerceAtLeast(1)
                        stats.labels.zip(stats.newUsersData).forEach { (label, count) ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, fontSize = 11.sp, modifier = Modifier.width(40.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                Spacer(Modifier.width(8.dp))
                                LinearProgressIndicator(progress = { count / maxVal.toFloat() }, modifier = Modifier.weight(1f).height(10.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        item { Text("Users (${users.size})", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)) }
        if (users.isEmpty() && !loading) {
            item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { Text("No users found. Tap Refresh.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) } }
        }
        items(users) { user ->
            GlassCard {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Balance: ${user.balance}", fontSize = 12.sp, color = if (user.balance >= 0) WebSuccess else WebDanger, fontWeight = FontWeight.SemiBold)
                            Text("Txns: ${user.txnCount}", fontSize = 12.sp)
                        }
                        Text("Joined: ${user.createdAt.take(10)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        if (user.lastUpdated != "N/A") Text("Last active: ${user.lastUpdated.take(10)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    if (user.userId != session.userId) {
                        FilledTonalIconButton(onClick = { userToDelete = user }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = WebDanger.copy(alpha = 0.1f), contentColor = WebDanger)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete user")
                        }
                    } else {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                            Text("You", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun AdminStatCard(modifier: Modifier, label: String, value: String) {
    GlassCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}
