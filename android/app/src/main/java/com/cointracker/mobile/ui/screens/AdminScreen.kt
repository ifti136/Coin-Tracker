package com.cointracker.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cointracker.mobile.data.AdminStats
import com.cointracker.mobile.data.AdminUserRow
import com.cointracker.mobile.data.UserSession
import com.cointracker.mobile.ui.components.GlassCard
import com.cointracker.mobile.ui.theme.brand
import com.cointracker.mobile.ui.theme.expense
import com.cointracker.mobile.ui.theme.income

@Composable
fun AdminScreen(
    session: UserSession?,
    stats: AdminStats?,
    users: List<AdminUserRow>,
    isActionLoading: (String) -> Boolean,
    onRefresh: () -> Unit,
    onDeleteUser: (String) -> Unit,
    onBack: () -> Unit
) {
    if (session?.role != "admin") {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var userToDelete by remember { mutableStateOf<AdminUserRow?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) users
        else users.filter { it.username.lowercase().contains(searchQuery.lowercase()) }
    }

    if (userToDelete != null) {
        val expenseColor = MaterialTheme.colorScheme.expense
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    "Delete User?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "Permanently delete \"${userToDelete!!.username}\" and all their data. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUser(userToDelete!!.userId)
                        userToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = expenseColor),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete User", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Admin Panel",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val isRefreshing = isActionLoading("loadAdmin")
                FilledTonalButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh admin data",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh")
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search users") },
                placeholder = { Text("Search by username...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions.Default
            )
        }

        // Overview stats cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "System Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.brand
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminStatCard(Modifier.weight(1f), "Total Users", "${stats?.totalUsers ?: 0}")
                    AdminStatCard(Modifier.weight(1f), "Coins Tracked", (stats?.totalCoins ?: 0).formatCoins())
                    AdminStatCard(Modifier.weight(1f), "Transactions", "${stats?.totalTransactions ?: 0}")
                }
            }
        }

        // New Users progress breakdown
        if (stats != null && stats.labels.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "New Users — Last 7 Days",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    GlassCard(shape = RoundedCornerShape(14.dp)) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val maxVal = (stats.newUsersData.maxOrNull() ?: 1).coerceAtLeast(1)
                            stats.labels.zip(stats.newUsersData).forEach { (label, count) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        modifier = Modifier.width(42.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    LinearProgressIndicator(
                                        progress = { count / maxVal.toFloat() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "$count",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Users header
        item {
            Text(
                "Registered Users (${filteredUsers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (filteredUsers.isEmpty() && !isActionLoading("loadAdmin")) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isNotBlank()) "No users match '$searchQuery'" else "No users found. Tap Refresh.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Users list
        items(filteredUsers, key = { it.userId }) { user ->
            GlassCard(shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = user.username,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (user.userId == session.userId) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "You",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val balanceColor = if (user.balance >= 0) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.expense
                            Text(
                                text = "Balance: ${user.balance.formatCoins()}",
                                fontSize = 12.sp,
                                color = balanceColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Txns: ${user.txnCount}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Joined: ${user.createdAt.take(10)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (user.lastUpdated != "N/A") {
                            Text(
                                text = "Last active: ${user.lastUpdated.take(10)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (user.userId != session.userId) {
                        val isDeleting = isActionLoading("deleteUser")
                        val expenseColor = MaterialTheme.colorScheme.expense
                        FilledTonalIconButton(
                            onClick = { userToDelete = user },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = expenseColor.copy(alpha = 0.12f),
                                contentColor = expenseColor
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isDeleting
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = expenseColor,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete user ${user.username}",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun AdminStatCard(modifier: Modifier, label: String, value: String) {
    GlassCard(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.brand
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Int.formatCoins(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
