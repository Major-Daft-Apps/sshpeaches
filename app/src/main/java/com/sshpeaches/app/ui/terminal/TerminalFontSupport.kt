package com.majordaftapps.sshpeaches.app.ui.terminal

import android.content.Context
import android.graphics.Typeface
import com.majordaftapps.sshpeaches.app.data.model.TerminalFont

data class ResolvedTerminalTypeface(
    val typeface: Typeface,
    val requestedFont: TerminalFont,
    val resolvedFamily: String?,
    val fellBackToSystemMonospace: Boolean
)

private val typefaceCache = mutableMapOf<String, Typeface>()

fun resolveTerminalTypeface(context: Context, font: TerminalFont): Typeface {
    return resolveTerminalTypefaceResult(context, font).typeface
}

fun resolveTerminalTypefaceResult(context: Context, font: TerminalFont): ResolvedTerminalTypeface {
    val assetPath = font.assetPath
    if (assetPath == null) {
        return ResolvedTerminalTypeface(
            typeface = Typeface.MONOSPACE,
            requestedFont = font,
            resolvedFamily = null,
            fellBackToSystemMonospace = false
        )
    }
    val resolvedTypeface = synchronized(typefaceCache) {
        typefaceCache[assetPath] ?: runCatching {
            Typeface.createFromAsset(context.assets, assetPath)
        }.getOrNull()?.also { typefaceCache[assetPath] = it }
    }
    return ResolvedTerminalTypeface(
        typeface = resolvedTypeface ?: Typeface.MONOSPACE,
        requestedFont = font,
        resolvedFamily = assetPath.substringAfterLast('/'),
        fellBackToSystemMonospace = resolvedTypeface == null
    )
}
