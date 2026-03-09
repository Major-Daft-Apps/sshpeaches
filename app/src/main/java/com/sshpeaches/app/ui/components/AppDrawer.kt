package com.majordaftapps.sshpeaches.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.ui.navigation.DrawerDestination
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

@Composable
fun AppDrawer(
    destinations: List<DrawerDestination>,
    currentRoute: String,
    onDestinationSelected: (DrawerDestination) -> Unit,
    onQuickConnect: () -> Unit
) {
    val drawerScroll = rememberScrollState()
    val isDarkDrawer = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activityBarLogo = if (isDarkDrawer) {
        com.majordaftapps.sshpeaches.app.R.drawable.sshpeaches_activitybar
    } else {
        com.majordaftapps.sshpeaches.app.R.drawable.sshpeaches_activitybar_black
    }
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(drawerScroll)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = activityBarLogo),
                contentDescription = "SSHPeaches logo",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Transparent),
                contentScale = ContentScale.Crop
            )
            Text(
                "SSHPeaches",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .testTag(UiTestTags.DRAWER_QUICK_CONNECT)
                .clickable { onQuickConnect() },
            color = MaterialTheme.colorScheme.primary
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Text("Quick Connect", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        destinations.forEach { dest ->
            val selected = currentRoute == dest.route
            val background = if (selected) Color(0xFFFA992A).copy(alpha = 0.18f) else Color.Transparent
            val foreground = if (selected) Color(0xFFFA992A) else MaterialTheme.colorScheme.onSurface
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .testTag(UiTestTags.drawerItem(dest.route))
                    .clickable { onDestinationSelected(dest) }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.Icon(
                    dest.icon,
                    contentDescription = null,
                    tint = foreground
                )
                Text(
                    dest.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = foreground
                )
            }
        }
    }
}
