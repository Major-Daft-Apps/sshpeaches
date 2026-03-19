package com.majordaftapps.sshpeaches.app.ui.terminal

import android.graphics.Paint
import android.graphics.Typeface
import com.majordaftapps.sshpeaches.app.data.model.TerminalFont
import kotlin.math.abs

fun resolveTerminalTypeface(font: TerminalFont): Typeface {
    if (font == TerminalFont.SYSTEM_MONOSPACE) return Typeface.MONOSPACE
    return font.typefaceFamilies
        .asSequence()
        .mapNotNull { family ->
            runCatching { Typeface.create(family, Typeface.NORMAL) }.getOrNull()
        }
        .firstOrNull { typeface ->
            runCatching { isMonospacedTypeface(typeface) }.getOrDefault(false)
        }
        ?: Typeface.MONOSPACE
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
