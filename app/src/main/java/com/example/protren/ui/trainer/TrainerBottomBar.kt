package com.example.protren.ui.trainer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RuleFolder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun TrainerBottomBar(nav: NavHostController) {
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    NavigationBar {

        NavigationBarItem(
            selected = route == "trainerHome",
            onClick = {
                nav.navigate("trainerHome") {
                    launchSingleTop = true
                }
            },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Start") },
            label = { Text("Start") }
        )

        NavigationBarItem(
            selected = route == "trainerTrainees",
            onClick = {
                nav.navigate("trainerTrainees") {
                    launchSingleTop = true
                }
            },
            icon = { Icon(Icons.Filled.People, contentDescription = "Podopieczni") },
            label = { Text("Podopieczni") }
        )

        NavigationBarItem(
            selected = route == "trainerChats",
            onClick = {
                nav.navigate("trainerChats") {
                    launchSingleTop = true
                }
            },
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Czaty"
                )
            },
            label = { Text("Czaty") }
        )

        NavigationBarItem(
            selected = route == "trainerProfile",
            onClick = {
                nav.navigate("trainerProfile") {
                    launchSingleTop = true
                }
            },
            icon = { Icon(Icons.Filled.Person, contentDescription = "Profil") },
            label = { Text("Profil") }
        )
    }
}
