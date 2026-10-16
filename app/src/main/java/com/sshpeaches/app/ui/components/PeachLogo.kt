package com.majordaftapps.sshpeaches.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import com.majordaftapps.sshpeaches.app.R

/** The flat peach in the default app icon's orange (Orange on cream), without its tile. */
@Composable
fun PeachLogo(modifier: Modifier = Modifier, contentDescription: String? = "SSHPeaches logo") {
    Image(
        painter = painterResource(R.drawable.ic_launcher_peach_dark_foreground_art),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(colorResource(R.color.app_icon_orange_on_cream_fg)),
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
