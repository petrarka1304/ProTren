@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.protren.ui.trainer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.protren.data.UserPreferences

@Composable
fun TrainerHomeScreen(
    nav: NavHostController,      // wewnętrzna nawigacja trenera (trainerNav)
    appNav: NavHostController     // ✅ główna nawigacja aplikacji (navController)
) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }

    var askLogout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel trenera") },
                actions = {
                    IconButton(onClick = { askLogout = true }) {
                        Icon(Icons.Filled.Logout, contentDescription = "Wyloguj się")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Panel trenera", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Zarządzaj podopiecznymi i ofertą – wszystko w jednym miejscu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Szybkie akcje", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Divider(modifier = Modifier.weight(1f))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TrainerTile(
                    title = "Plan okresowy",
                    subtitle = "Miesiąc i więcej",
                    icon = Icons.Filled.CalendarMonth,
                    onClick = { nav.navigate("trainerPeriodPlan") },
                    modifier = Modifier.weight(1f)
                )
                TrainerTile(
                    title = "Podopieczni",
                    subtitle = "Lista i notatki",
                    icon = Icons.Filled.People,
                    onClick = { nav.navigate("trainerTrainees") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TrainerTile(
                    title = "Moje plany",
                    subtitle = "Szablony i edycja",
                    icon = Icons.Filled.ListAlt,
                    onClick = { nav.navigate("trainerPlans") },
                    modifier = Modifier.weight(1f)
                )
                TrainerTile(
                    title = "Moja oferta",
                    subtitle = "Opis, specjalizacje",
                    icon = Icons.Filled.Edit,
                    onClick = { nav.navigate("trainerOffer") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (askLogout) {
        AlertDialog(
            onDismissRequest = { askLogout = false },
            title = { Text("Wylogować się?") },
            text = { Text("Zostaniesz wylogowany i wrócisz do ekranu logowania.") },
            confirmButton = {
                TextButton(onClick = {
                    askLogout = false
                    prefs.clearTokens()

                    // ✅ NAJWAŻNIEJSZE: nawiguj po appNav, nie po trainerNav
                    appNav.navigate("login") {
                        popUpTo(appNav.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }) { Text("Wyloguj") }
            },
            dismissButton = {
                TextButton(onClick = { askLogout = false }) { Text("Anuluj") }
            }
        )
    }
}

@Composable
private fun TrainerTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.height(120.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).padding(10.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
