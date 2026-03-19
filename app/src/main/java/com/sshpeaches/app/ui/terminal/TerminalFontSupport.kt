package com.majordaftapps.sshpeaches.app.ui.terminal

import android.graphics.Paint
import android.graphics.Typeface
import com.majordaftapps.sshpeaches.app.data.model.TerminalFont
import kotlin.math.abs

data class ResolvedTerminalTypeface(
    val typeface: Typeface,
    val requestedFont: TerminalFont,
    val resolvedFamily: String?,
    val fellBackToSystemMonospace: Boolean
)

fun resolveTerminalTypeface(font: TerminalFont): Typeface {
    return resolveTerminalTypefaceResult(font).typeface
}

fun resolveTerminalTypefaceResult(font: TerminalFont): ResolvedTerminalTypeface {
    if (font == TerminalFont.SYSTEM_MONOSPACE) {
        return ResolvedTerminalTypeface(
            typeface = Typeface.MONOSPACE,
            requestedFont = font,
            resolvedFamily = null,
            fellBackToSystemMonospace = false
        )
    }
    val resolvedFamily = font.typefaceFamilies
        .asSequence()
        .mapNotNull { family ->
            val typeface = runCatching { Typeface.create(family, Typeface.NORMAL) }.getOrNull()
            if (typeface != null && runCatching { isMonospacedTypeface(typeface) }.getOrDefault(false)) {
                family
            } else {
                null
            }
        }
        .firstOrNull()
    val resolvedTypeface = resolvedFamily?.let { family ->
        runCatching { Typeface.create(family, Typeface.NORMAL) }.getOrNull()
    } ?: Typeface.MONOSPACE
    return ResolvedTerminalTypeface(
        typeface = resolvedTypeface,
        requestedFont = font,
        resolvedFamily = resolvedFamily,
        fellBackToSystemMonospace = resolvedFamily == null
    )
}

private fun isMonospacedTypeface(typeface: Typeface): Boolean {
    val paint = Paint().apply {
        textSize = 32f
        this.typeface = typeface
    }
    val narrow = paint.measureText("iiii")
    val wide = paint.measureText("WWWW")
    return abs(narrow - wide) < 0.5f
}
