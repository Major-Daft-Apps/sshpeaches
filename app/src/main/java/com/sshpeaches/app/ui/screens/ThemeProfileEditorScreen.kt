package com.majordaftapps.sshpeaches.app.ui.screens

import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.majordaftapps.sshpeaches.app.data.model.TerminalCursorStyle
import com.majordaftapps.sshpeaches.app.data.model.TerminalFont
import com.majordaftapps.sshpeaches.app.data.model.TerminalProfile
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags
import com.majordaftapps.sshpeaches.app.ui.terminal.resolveTerminalTypeface
import com.majordaftapps.sshpeaches.app.ui.terminal.resolveTerminalTypefaceResult
import java.util.Locale
import java.util.UUID

private enum class ThemeColorField {
    FOREGROUND,
    BACKGROUND,
    CURSOR
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ThemeProfileEditorScreen(
    initialProfile: TerminalProfile,
    existingProfiles: List<TerminalProfile> = emptyList(),
    isEditingExisting: Boolean,
    onSaveTheme: (TerminalProfile) -> Unit,
    onNavigateBack: () -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var name by remember(initialProfile.id) { mutableStateOf(initialProfile.name) }
    var font by remember(initialProfile.id) { mutableStateOf(initialProfile.font) }
    var fontSizePt by remember(initialProfile.id) { mutableFloatStateOf(initialProfile.fontSizeSp.toFloat()) }
    var foregroundHex by remember(initialProfile.id) { mutableStateOf(initialProfile.foregroundHex) }
    var backgroundHex by remember(initialProfile.id) { mutableStateOf(initialProfile.backgroundHex) }
    var cursorHex by remember(initialProfile.id) { mutableStateOf(initialProfile.cursorHex) }
    var cursorStyle by remember(initialProfile.id) { mutableStateOf(initialProfile.cursorStyle) }
    var cursorBlink by remember(initialProfile.id) { mutableStateOf(initialProfile.cursorBlink) }

    var showNameDialog by remember { mutableStateOf(false) }
    var showFontFamilyDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showCursorStyleDialog by remember { mutableStateOf(false) }
    var colorFieldDialog by remember { mutableStateOf<ThemeColorField?>(null) }
    var editorError by remember { mutableStateOf<String?>(null) }

    val title = if (isEditingExisting) "Edit Terminal Theme" else "New Terminal Theme"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(UiTestTags.SCREEN_THEME_PROFILE_EDITOR)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)

        Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditorRow(
                    label = "Theme Name",
                    value = name,
                    buttonText = "Rename",
                    onClick = { showNameDialog = true }
                )
                EditorRow(
                    label = "Font",
                    value = font.label,
                    buttonText = "Select",
                    onClick = { showFontFamilyDialog = true },
                    modifier = Modifier.testTag(UiTestTags.THEME_PROFILE_FONT_BUTTON)
                )
                EditorRow(
                    label = "Font Size",
                    value = "${fontSizePt.toInt()} pt",
                    buttonText = "Adjust",
                    onClick = { showFontSizeDialog = true }
                )
                ThemePreviewSample(
                    context = context,
                    terminalFont = font,
                    fontSizePt = fontSizePt,
                    foregroundHex = foregroundHex,
                    backgroundHex = backgroundHex
                )
                ColorRow(
                    label = "Foreground",
                    hexValue = foregroundHex,
                    onPick = { colorFieldDialog = ThemeColorField.FOREGROUND }
                )
                ColorRow(
                    label = "Background",
                    hexValue = backgroundHex,
                    onPick = { colorFieldDialog = ThemeColorField.BACKGROUND }
                )
                ColorRow(
                    label = "Cursor",
                    hexValue = cursorHex,
                    onPick = { colorFieldDialog = ThemeColorField.CURSOR }
                )
                EditorRow(
                    label = "Cursor Style",
                    value = cursorStyle.label,
                    buttonText = "Select",
                    onClick = { showCursorStyleDialog = true }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cursor Blink")
                    Switch(checked = cursorBlink, onCheckedChange = { cursorBlink = it })
                }
            }
        }

        editorError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(UiTestTags.THEME_PROFILE_ERROR)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    when {
                        name.isBlank() -> {
                            editorError = "Theme name is required."
                            return@Button
                        }
                        !isValidHexColor(foregroundHex) -> {
                            editorError = "Foreground color must be #RRGGBB."
                            return@Button
                        }
                        !isValidHexColor(backgroundHex) -> {
                            editorError = "Background color must be #RRGGBB."
                            return@Button
                        }
                        !isValidHexColor(cursorHex) -> {
                            editorError = "Cursor color must be #RRGGBB."
                            return@Button
                        }
                    }
                    val normalizedName = name.trim()
                    val duplicateName = existingProfiles.any {
                        it.id != initialProfile.id && it.name.equals(normalizedName, ignoreCase = true)
                    }
                    if (duplicateName) {
                        editorError = "Theme name already exists."
                        return@Button
                    }
                    val profile = TerminalProfile(
                        id = initialProfile.id.ifBlank { "custom-${UUID.randomUUID()}" },
                        name = normalizedName,
                        font = font,
                        fontSizeSp = fontSizePt.toInt().coerceIn(6, 28),
                        foregroundHex = foregroundHex.trim().uppercase(Locale.US),
                        backgroundHex = backgroundHex.trim().uppercase(Locale.US),
                        cursorHex = cursorHex.trim().uppercase(Locale.US),
                        cursorStyle = cursorStyle,
                        cursorBlink = cursorBlink
                    )
                    onSaveTheme(profile)
                    onShowMessage("Terminal theme saved.")
                    onNavigateBack()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(UiTestTags.THEME_PROFILE_SAVE_BUTTON)
            ) {
                Text("Save")
            }
            Button(
                onClick = onNavigateBack,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UiTestTags.THEME_PROFILE_CANCEL_BUTTON)
            ) {
                Text("Cancel")
            }
        }
    }

    if (showNameDialog) {
        var draftName by remember(name) { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Theme Name") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it.take(48) },
                    singleLine = true,
                    label = { Text("Name") },
                    keyboardOptions = KeyboardOptions(
                        autoCorrect = false,
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text
                    ),
                    modifier = Modifier.testTag(UiTestTags.THEME_PROFILE_NAME_INPUT)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    name = draftName.trim()
                    showNameDialog = false
                    editorError = null
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showFontFamilyDialog) {
        var draftFont by remember(font) { mutableStateOf(font) }
        var fontExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showFontFamilyDialog = false },
            title = { Text("Font") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = fontExpanded,
                        onExpandedChange = { fontExpanded = !fontExpanded }
                    ) {
                        OutlinedTextField(
                            value = draftFont.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Terminal font") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag(UiTestTags.THEME_PROFILE_FONT_FIELD)
                        )
                        ExposedDropdownMenu(
                            expanded = fontExpanded,
                            onDismissRequest = { fontExpanded = false }
                        ) {
                            TerminalFont.selectableValues().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        draftFont = option
                                        fontExpanded = false
                                    },
                                    modifier = Modifier.testTag(UiTestTags.themeProfileFontOption(option.label))
                                )
                            }
                        }
                    }
                    ThemePreviewSample(
                        context = context,
                        terminalFont = draftFont,
                        fontSizePt = fontSizePt,
                        foregroundHex = foregroundHex,
                        backgroundHex = backgroundHex
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    font = draftFont
                    showFontFamilyDialog = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showFontFamilyDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showFontSizeDialog) {
        var draftSize by remember(fontSizePt) { mutableFloatStateOf(fontSizePt) }
        AlertDialog(
            onDismissRequest = { showFontSizeDialog = false },
            title = { Text("Font Size") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${draftSize.toInt()} pt")
                    Slider(
                        value = draftSize,
                        onValueChange = { draftSize = it },
                        valueRange = 6f..28f
                    )
                    FontPreviewText(
                        context = context,
                        text = "AaBb 0Oo1Il",
                        terminalFont = font,
                        fontSizePt = draftSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    fontSizePt = draftSize
                    showFontSizeDialog = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showFontSizeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCursorStyleDialog) {
        var draftCursorStyle by remember(cursorStyle) { mutableStateOf(cursorStyle) }
        var cursorStyleExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCursorStyleDialog = false },
            title = { Text("Cursor Style") },
            text = {
                ExposedDropdownMenuBox(
                    expanded = cursorStyleExpanded,
                    onExpandedChange = { cursorStyleExpanded = !cursorStyleExpanded }
                ) {
                    OutlinedTextField(
                        value = draftCursorStyle.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cursor style") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = cursorStyleExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = cursorStyleExpanded,
                        onDismissRequest = { cursorStyleExpanded = false }
                    ) {
                        TerminalCursorStyle.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    draftCursorStyle = option
                                    cursorStyleExpanded = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    cursorStyle = draftCursorStyle
                    showCursorStyleDialog = false
                }) { Text("OK") }
            }
        )
    }

    colorFieldDialog?.let { field ->
        val initialHex = when (field) {
            ThemeColorField.FOREGROUND -> foregroundHex
            ThemeColorField.BACKGROUND -> backgroundHex
            ThemeColorField.CURSOR -> cursorHex
        }
        ColorPickerDialog(
            title = when (field) {
                ThemeColorField.FOREGROUND -> "Foreground Color"
                ThemeColorField.BACKGROUND -> "Background Color"
                ThemeColorField.CURSOR -> "Cursor Color"
            },
            initialHex = initialHex,
            onDismiss = { colorFieldDialog = null },
            onApply = { selectedHex ->
                when (field) {
                    ThemeColorField.FOREGROUND -> foregroundHex = selectedHex
                    ThemeColorField.BACKGROUND -> backgroundHex = selectedHex
                    ThemeColorField.CURSOR -> cursorHex = selectedHex
                }
                editorError = null
                colorFieldDialog = null
            }
        )
    }
}

@Composable
private fun EditorRow(
    label: String,
    value: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onClick, modifier = modifier) { Text(buttonText) }
    }
}

@Composable
private fun ThemePreviewSample(
    context: android.content.Context,
    terminalFont: TerminalFont,
    fontSizePt: Float,
    foregroundHex: String,
    backgroundHex: String
) {
    val fg = parseHexColorOrDefault(foregroundHex, Color(0xFFE6E6E6))
    val bg = parseHexColorOrDefault(backgroundHex, Color(0xFF101010))
    val resolvedFont = remember(context, terminalFont) { resolveTerminalTypefaceResult(context, terminalFont) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FontPreviewText(
                context = context,
                text = "AaBb 0Oo1Il [] {} () /\\\\",
                terminalFont = terminalFont,
                fontSizePt = fontSizePt,
                color = fg,
                modifier = Modifier.fillMaxWidth()
            )
            val previewStatus = if (resolvedFont.fellBackToSystemMonospace) {
                "${terminalFont.label} is unavailable on this device. Previewing system monospace."
            } else {
                "Previewing ${resolvedFont.resolvedFamily ?: terminalFont.label}."
            }
            Text(
                text = previewStatus,
                color = fg.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FontPreviewText(
    context: android.content.Context,
    text: String,
    terminalFont: TerminalFont,
    fontSizePt: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val typeface = remember(context, terminalFont) { resolveTerminalTypeface(context, terminalFont) }
    key(terminalFont) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                TextView(context).apply {
                    setTypeface(typeface)
                }
            },
            update = { view ->
                view.text = text
                view.typeface = typeface
                view.textSize = fontSizePt
                view.setTextColor(color.toArgb())
            }
        )
    }
}

@Composable
private fun ColorRow(
    label: String,
    hexValue: String,
    onPick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(parseHexColorOrDefault(hexValue, Color.Black), RoundedCornerShape(4.dp))
            )
            Column {
                Text(label)
                Text(hexValue.uppercase(Locale.US), style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = onPick) { Text("Pick") }
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    initialHex: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val initial = parseHexToRgb(initialHex)
    val initialHsv = rgbToHsv(initial.red, initial.green, initial.blue)
    var hue by remember(initialHex) { mutableFloatStateOf(initialHsv.hue) }
    var saturation by remember(initialHex) { mutableFloatStateOf(initialHsv.saturation) }
    var brightness by remember(initialHex) { mutableFloatStateOf(initialHsv.value) }
    var hexDraft by remember(initialHex) { mutableStateOf(rgbToHex(initial.red, initial.green, initial.blue)) }
    val rgb = hsvToRgb(hue = hue, saturation = saturation, value = brightness)
    val hex = rgbToHex(rgb.red, rgb.green, rgb.blue)
    val preview = Color(rgb.red, rgb.green, rgb.blue)

    LaunchedEffect(hue, saturation, brightness) {
        hexDraft = hex
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(preview, RoundedCornerShape(8.dp))
                )
                OutlinedTextField(
                    value = hexDraft,
                    onValueChange = { next ->
                        val normalized = next.take(7).uppercase(Locale.US)
                        hexDraft = normalized
                        if (isValidHexColor(normalized)) {
                            val parsed = parseHexToRgb(normalized)
                            val nextHsv = rgbToHsv(parsed.red, parsed.green, parsed.blue)
                            hue = nextHsv.hue
                            saturation = nextHsv.saturation
                            brightness = nextHsv.value
                        }
                    },
                    label = { Text("Hex (#RRGGBB)") },
                    singleLine = true
                )
                Text("Hue ${hue.toInt()} deg")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Red,
                                    Color.Yellow,
                                    Color.Green,
                                    Color.Cyan,
                                    Color.Blue,
                                    Color.Magenta,
                                    Color.Red
                                )
                            ),
                            shape = RoundedCornerShape(6.dp)
                        )
                )
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f
                )
                Text("Saturation ${(saturation * 100f).toInt()}%")
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0f..1f
                )
                Text("Brightness ${(brightness * 100f).toInt()}%")
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = 0f..1f
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValidHexColor(hexDraft),
                onClick = { onApply(hexDraft) }
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private data class Rgb(val red: Int, val green: Int, val blue: Int)
private data class Hsv(val hue: Float, val saturation: Float, val value: Float)

private fun parseHexToRgb(hex: String): Rgb {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6) return Rgb(230, 230, 230)
    return runCatching {
        Rgb(
            red = cleaned.substring(0, 2).toInt(16),
            green = cleaned.substring(2, 4).toInt(16),
            blue = cleaned.substring(4, 6).toInt(16)
        )
    }.getOrDefault(Rgb(230, 230, 230))
}

private fun rgbToHex(red: Int, green: Int, blue: Int): String =
    String.format(Locale.US, "#%02X%02X%02X", red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))

private fun rgbToHsv(red: Int, green: Int, blue: Int): Hsv {
    val r = red.coerceIn(0, 255) / 255f
    val g = green.coerceIn(0, 255) / 255f
    val b = blue.coerceIn(0, 255) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    val saturation = if (max == 0f) 0f else delta / max
    return Hsv(hue = hue, saturation = saturation, value = max)
}

private fun hsvToRgb(hue: Float, saturation: Float, value: Float): Rgb {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val chroma = v * s
    val x = chroma * (1 - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = v - chroma

    val (r1, g1, b1) = when {
        h < 60f -> Triple(chroma, x, 0f)
        h < 120f -> Triple(x, chroma, 0f)
        h < 180f -> Triple(0f, chroma, x)
        h < 240f -> Triple(0f, x, chroma)
        h < 300f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    return Rgb(
        red = ((r1 + m) * 255f).toInt().coerceIn(0, 255),
        green = ((g1 + m) * 255f).toInt().coerceIn(0, 255),
        blue = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
    )
}

private fun parseHexColorOrDefault(hex: String, fallback: Color): Color =
    runCatching {
        val rgb = parseHexToRgb(hex)
        Color(rgb.red, rgb.green, rgb.blue)
    }.getOrDefault(fallback)

private fun isValidHexColor(value: String): Boolean =
    Regex("^#[0-9A-Fa-f]{6}$").matches(value.trim())
