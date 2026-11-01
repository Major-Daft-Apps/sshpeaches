package com.majordaftapps.sshpeaches.app.ui.components

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.LruCache
import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.model.OsFamily
import com.majordaftapps.sshpeaches.app.data.model.OsMetadata

/**
 * A host's OS logo in [tint], or a desktop icon when the OS isn't known. The bundled SVGs are drawn here, at once,
 * instead of through Coil: an async load that failed during a cold start left the circle empty until the host
 * changed (connecting to it updated it and loaded the logo again).
 */
@Composable
fun OsLogo(
    osMetadata: OsMetadata,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = Color.White
) {
    val iconRes = osMetadata.logoResOrNull()
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val resources = LocalContext.current.resources
    val logo = remember(iconRes, sizePx) { iconRes?.let { OsLogoCache.get(resources, it, sizePx) } }
    if (logo != null) {
        Image(
            bitmap = logo,
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint),
            modifier = modifier.size(size)
        )
    } else {
        Icon(
            imageVector = Icons.Default.DesktopWindows,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.size(size)
        )
    }
}

internal fun OsMetadata.logoResOrNull(): Int? = when (this) {
    is OsMetadata.Known -> when (family) {
        OsFamily.UBUNTU -> R.raw.ubuntu
        OsFamily.DEBIAN -> R.raw.debian
        OsFamily.FEDORA -> R.raw.fedora
        OsFamily.MINT -> R.raw.linux_mint
        OsFamily.ARCH -> R.raw.arch
        OsFamily.SUSE -> R.raw.suse
        OsFamily.REDHAT -> R.raw.redhat
        OsFamily.MAC -> R.raw.apple
        OsFamily.WINDOWS -> R.raw.windows
        OsFamily.BSD -> R.raw.bsd
        else -> R.raw.linux
    }
    else -> null
}

/** OS logos rendered from their SVGs, kept per size for the life of the process. */
internal object OsLogoCache {
    private val logos = LruCache<String, ImageBitmap>(32)

    /** The logo [sizePx] square, or null if its SVG couldn't be drawn. */
    fun get(resources: Resources, @RawRes iconRes: Int, sizePx: Int): ImageBitmap? {
        if (sizePx <= 0) return null
        val key = "$iconRes@$sizePx"
        logos.get(key)?.let { return it }
        val logo = runCatching { render(resources, iconRes, sizePx) }.getOrNull() ?: return null
        logos.put(key, logo)
        return logo
    }

    private fun render(resources: Resources, @RawRes iconRes: Int, sizePx: Int): ImageBitmap {
        val svg = SVG.getFromResource(resources, iconRes)
        // Some of the logos only give a width and height; without a viewBox they wouldn't scale to the square.
        if (svg.documentViewBox == null && svg.documentWidth > 0f && svg.documentHeight > 0f) {
            svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
        }
        // A fixed width and height (ubuntu.svg says 800px) would be drawn at that size and cropped to the square.
        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bitmap), RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()))
        return bitmap.asImageBitmap()
    }
}
