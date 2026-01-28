package com.sshpeaches.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sshpeaches.app.ui.components.AppDrawer
import com.sshpeaches.app.ui.navigation.Routes
import com.sshpeaches.app.ui.navigation.drawerDestinations
import com.sshpeaches.app.ui.screens.FavoritesScreen
import com.sshpeaches.app.ui.screens.HostsScreen
import com.sshpeaches.app.ui.screens.IdentitiesScreen
import com.sshpeaches.app.ui.screens.KeyboardEditorScreen
import com.sshpeaches.app.ui.screens.PortForwardScreen
import com.sshpeaches.app.ui.screens.SettingsScreen
import com.sshpeaches.app.ui.screens.SnippetManagerScreen
import com.sshpeaches.app.ui.state.AppUiState
import com.sshpeaches.app.ui.state.SortMode
import com.sshpeaches.app.ui.theme.CarbonBlack
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SSHPeachesRoot(
    uiState: AppUiState,
    onSortModeChange: (SortMode) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit
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
            ModalDrawerSheet(
                drawerContainerColor = CarbonBlack,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                AppDrawer(
                    destinations = drawerDestinations,
                    currentRoute = currentRoute,
                    onDestinationSelected = { destination ->
                        scope.launch { drawerState.close() }
                        when (destination.route) {
                            Routes.HELP -> { /* TODO open support site */ }
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
        },
        scrimColor = Color.Black.copy(alpha = 0.4f)
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("SSHPeaches") },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showQuickConnect.value = true }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Quick Connect")
                            }
                            IconButton(onClick = { /* TODO search */ }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
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
                        .background(MaterialTheme.colorScheme.background)
                        .padding(padding)
                ) {
                    composable(Routes.FAVORITES) {
                        FavoritesScreen(section = uiState.favorites)
                    }
                    composable(Routes.HOSTS) {
                        HostsScreen(
                            hosts = uiState.hosts,
                            sortMode = uiState.sortMode,
                            onSortModeChange = onSortModeChange
                        )
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
                    composable(Routes.KEYBOARD) {
                        KeyboardEditorScreen()
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            currentTheme = uiState.themeMode,
                            onThemeChange = onThemeModeChange
                        )
                    }
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
                Button(onClick = {}) { Text("Password") }
                Button(onClick = {}) { Text("Identity") }
                Button(onClick = {}) { Text("Both") }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
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
                Text("Version 0.1.0")
                Text("Carbon Black + Blazing Flame theme")
                Text("Built with Jetpack Compose")
            }
        }
    )
}
