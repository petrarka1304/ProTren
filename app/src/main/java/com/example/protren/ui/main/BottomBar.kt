package com.example.protren.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomBar(navController: NavController) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()

    Surface(
        color = Color(0xFF0E0F12).copy(alpha = 0.85f),
        contentColor = Color.White,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarItem(
                label = "Start",
                selected = route.startsWith("home"),
                icon = Icons.Filled.Home
            ) { navController.navigate("home") { launchSingleTop = true } }

            BarItem(
                label = "Suplementy",
                selected = route.startsWith("supplements"),
                icon = Icons.Filled.Medication
            ) { navController.navigate("supplements/today") { launchSingleTop = true } }

            // 🟢 NOWE: przycisk do listy czatów użytkownika
            BarItem(
                label = "Czaty",
                selected = route.startsWith("chats") || route.startsWith("chatThread"),
                icon = Icons.Filled.Chat
            ) { navController.navigate("chats") { launchSingleTop = true } }

            BarItem(
                label = "Analityka",
                selected = route.startsWith("analytics"),
                icon = Icons.Filled.BarChart
            ) { navController.navigate("analytics") { launchSingleTop = true } }

            BarItem(
                label = "Profil",
                selected = route.startsWith("profile"),
                icon = Icons.Filled.Person
            ) { navController.navigate("profile") { launchSingleTop = true } }
        }
    }
}

@Composable
private fun RowScope.BarItem(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val color = if (selected) Color.White else Color.White.copy(alpha = 0.75f)
    val textStyle = if (selected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color)
        Spacer(Modifier.height(4.dp))
        Text(text = label, color = color, style = textStyle, maxLines = 1)
    }
}
