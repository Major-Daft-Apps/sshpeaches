package com.sshpeaches.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.Identity
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.Snippet
import com.sshpeaches.app.ui.components.AppDrawer
import com.sshpeaches.app.ui.components.HostCard
import com.sshpeaches.app.ui.navigation.Routes
import com.sshpeaches.app.ui.navigation.drawerDestinations
import com.sshpeaches.app.ui.screens.FavoritesScreen
import com.sshpeaches.app.ui.screens.HostsScreen
import com.sshpeaches.app.ui.screens.IdentitiesScreen
import com.sshpeaches.app.ui.screens.PortForwardScreen
import com.sshpeaches.app.ui.screens.SnippetManagerScreen
import com.sshpeaches.app.ui.state.AppUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SSHPeachesRoot(
    uiState: AppUiState,
    onToggleSortMode: () -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val showQuickConnect = rememberSaveable { mutableStateOf(false) }
    val showAbout = rememberSaveable { mutableStateOf(false) }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        ?: Routes.FAVORITES

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                destinations = drawerDestinations,
                currentRoute = currentRoute,
                onDestinationSelected = { destination ->
                    scope.launch { drawerState.close() }
                    when (destination.route) {
                        Routes.HELP -> { /* TODO open CustomTab */ }
                        Routes.ABOUT -> showAbout.value = true
                        else -> {
                            if (destination.route != currentRoute) {
                                navController.navigate(destination.route) {
                                    popUpTo(Routes.FAVORITES)
                                }
                            }
                        }
                    }
                },
                onQuickConnect = { showQuickConnect.value = true }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "SSHPeaches") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showQuickConnect.value = true }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Quick Connect")
                        }
                        IconButton(onClick = { /* search placeholder */ }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onToggleSortMode) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Sort")
                        }
                    }
                )
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.FAVORITES,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                composable(Routes.FAVORITES) {
                    FavoritesScreen(section = uiState.favorites)
                }
                composable(Routes.HOSTS) {
                    HostsScreen(hosts = uiState.hosts)
                }
                composable(Routes.IDENTITIES) {
                    IdentitiesScreen(items = uiState.identities)
                }
                composable(Routes.FORWARDS) {
                    PortForwardScreen(items = uiState.portForwards)
                }
                composable(Routes.SNIPPETS) {
                    SnippetManagerScreen(snippets = uiState.snippets)
                }
                composable(Routes.SETTINGS) {
                    PlaceholderScreen(title = "Settings", description = "Coming soon")
                }
            }
        }
    }

    if (showQuickConnect.value) {
        QuickConnectSheet(onDismiss = { showQuickConnect.value = false })
    }

    if (showAbout.value) {
        AboutDialog(onDismiss = { showAbout.value = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickConnectSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Quick Connect", style = MaterialTheme.typography.titleLarge)
            PlaceholderField(label = "Host / IP")
            PlaceholderField(label = "Port", value = "22")
            PlaceholderField(label = "Username")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {}) { Text("Password") }
                FilledTonalButton(onClick = {}) { Text("Identity") }
                FilledTonalButton(onClick = {}) { Text("Both") }
            }
            FilledTonalButton(onClick = {}) {
                Icon(Icons.Default.QrCode, contentDescription = null)
                Text("Scan QR")
            }
            FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun PlaceholderField(label: String, value: String = "") {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = if (value.isEmpty()) "Tap to configure" else value,
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("About SSHPeaches") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version 0.1.0 (draft)")
                Text("Carbon Black + Blazing Flame theme")
                Text("Built with Jetpack Compose")
            }
        }
    )
}

@Composable
private fun PlaceholderScreen(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Info, contentDescription = null)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}
