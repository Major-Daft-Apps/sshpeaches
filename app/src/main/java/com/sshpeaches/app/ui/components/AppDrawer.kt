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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.majordaftapps.sshpeaches.app.ui.adaptive.desktopHoverable
import com.majordaftapps.sshpeaches.app.ui.adaptive.rememberDesktopHoverState
import com.majordaftapps.sshpeaches.app.ui.navigation.DrawerDestination
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

@Composable
fun AppDrawer(
    destinations: List<DrawerDestination>,
    currentRoute: String,
    onDestinationSelected: (DrawerDestination) -> Unit
) {
    val drawerScroll = rememberScrollState()
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val activityBarLogo = com.majordaftapps.sshpeaches.app.R.drawable.sshpeaches_activitybar
    val logoTint = if (isDarkSurface) null else Color(0xFFFA992A)
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(drawerScroll)
            .testTag(UiTestTags.DRAWER_SCROLL_CONTAINER)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = activityBarLogo),
                contentDescription = "SSHPeaches logo",
                colorFilter = logoTint?.let { ColorFilter.tint(it) },
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
        destinations.forEach { dest ->
            val (interactionSource, hovered) = rememberDesktopHoverState(enabled = true)
            val selected = currentRoute == dest.route
            val background = when {
                selected -> Color(0xFFFA992A).copy(alpha = 0.18f)
                hovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
                else -> Color.Transparent
            }
            val foreground = if (selected) Color(0xFFFA992A) else MaterialTheme.colorScheme.onSurface
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .testTag(UiTestTags.drawerItem(dest.route))
                    .desktopHoverable(
                        enabled = true,
                        interactionSource = interactionSource
                    )
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
