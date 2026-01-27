package com.sshpeaches.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector

data class DrawerDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val drawerDestinations = listOf(
    DrawerDestination(Routes.FAVORITES, "Favorites", Icons.Default.FavoriteBorder),
    DrawerDestination(Routes.HOSTS, "Hosts", Icons.Default.Storage),
    DrawerDestination(Routes.IDENTITIES, "Identities", Icons.Default.Key),
    DrawerDestination(Routes.FORWARDS, "Port Forwards", Icons.Default.Bolt),
    DrawerDestination(Routes.SNIPPETS, "Snippets", Icons.Default.Code),
    DrawerDestination(Routes.SETTINGS, "Settings", Icons.Default.Settings),
    DrawerDestination(Routes.HELP, "Help", Icons.Default.Help),
    DrawerDestination(Routes.ABOUT, "About", Icons.Default.Info)
)
