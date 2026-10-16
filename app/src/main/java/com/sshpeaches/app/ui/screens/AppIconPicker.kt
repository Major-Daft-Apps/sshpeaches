package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.majordaftapps.sshpeaches.app.R
import com.majordaftapps.sshpeaches.app.data.settings.AppIconOption
import com.majordaftapps.sshpeaches.app.ui.testing.UiTestTags

internal data class AppIconVariant(
    val option: AppIconOption,
    val name: String,
    @ColorRes val background: Int,
    @DrawableRes val art: Int,
    /** Recolours the white artwork; null draws it as is. */
    @ColorRes val foreground: Int?
)

private val WHITE_PEACH_ART = R.drawable.ic_launcher_peach_dark_foreground_art

/** Same colours as the launcher icons in mipmap-anydpi-v26, in grid order. */
internal val appIconVariants = listOf(
    AppIconVariant(AppIconOption.ORANGE_ON_CREAM, "Orange on cream", R.color.app_icon_orange_on_cream_bg, WHITE_PEACH_ART, R.color.app_icon_orange_on_cream_fg),
    AppIconVariant(AppIconOption.PEACH_LIGHT, "Orange Peach", R.color.color_vanilla, R.drawable.ic_launcher_peach_light_foreground_art, null),
    AppIconVariant(AppIconOption.PEACH_DARK, "White Peach", R.color.color_hard_black, WHITE_PEACH_ART, null),
    AppIconVariant(AppIconOption.WHITE_ON_ORANGE, "White on orange", R.color.app_icon_white_on_orange_bg, WHITE_PEACH_ART, R.color.app_icon_white_on_orange_fg),
    AppIconVariant(AppIconOption.CORAL_ON_BLUSH, "Coral on blush", R.color.app_icon_coral_on_blush_bg, WHITE_PEACH_ART, R.color.app_icon_coral_on_blush_fg),
    AppIconVariant(AppIconOption.PEACH_ON_PLUM, "Peach on plum", R.color.app_icon_peach_on_plum_bg, WHITE_PEACH_ART, R.color.app_icon_peach_on_plum_fg),
    AppIconVariant(AppIconOption.BLACK_ON_PEACH, "Black on peach", R.color.app_icon_black_on_peach_bg, WHITE_PEACH_ART, R.color.app_icon_black_on_peach_fg),
    AppIconVariant(AppIconOption.MINT_ON_CHARCOAL, "Mint on charcoal", R.color.app_icon_mint_on_charcoal_bg, WHITE_PEACH_ART, R.color.app_icon_mint_on_charcoal_fg),
    AppIconVariant(AppIconOption.ORANGE_ON_BLACK, "Orange on black", R.color.app_icon_orange_on_black_bg, WHITE_PEACH_ART, R.color.app_icon_orange_on_black_fg),
    AppIconVariant(AppIconOption.TERMINAL_GREEN, "Terminal green", R.color.app_icon_terminal_green_bg, WHITE_PEACH_ART, R.color.app_icon_terminal_green_fg)
)

private val SELECTION_COLOR = Color(0xFFF28C28)
private val RING_WIDTH = 3.dp
private val RING_GAP = 2.dp
private val CELL_SPACING = 4.dp
private val MIN_CELL = 56.dp
private val MAX_CELL = 96.dp
private const val TILE_CORNER_FRACTION = 0.25f

/** An adaptive icon's 108dp layers show their middle 72dp. */
private const val ADAPTIVE_ICON_ZOOM = 108f / 72f

@Composable
internal fun AppIconPicker(
    selected: AppIconOption,
    onSelect: (AppIconOption) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        fun cellFor(columns: Int): Dp = (maxWidth - CELL_SPACING * (columns - 1)) / columns
        val columns = if (cellFor(5) >= MIN_CELL) 5 else 4
        val cell = min(cellFor(columns), MAX_CELL)
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(CELL_SPACING)
        ) {
            appIconVariants.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(CELL_SPACING)) {
                    row.forEach { variant ->
                        AppIconTile(
                            variant = variant,
                            selected = variant.option == selected,
                            onClick = { onSelect(variant.option) },
                            size = cell
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconTile(variant: AppIconVariant, selected: Boolean, onClick: () -> Unit, size: Dp) {
    val inset = RING_WIDTH + RING_GAP
    val tile = size - inset * 2
    val tileShape = RoundedCornerShape(tile * TILE_CORNER_FRACTION)
    Box(
        modifier = Modifier
            .size(size)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = variant.name }
            .testTag(UiTestTags.settingsAppIconOption(variant.name))
            .then(
                if (selected) {
                    Modifier.border(RING_WIDTH, SELECTION_COLOR, RoundedCornerShape(tile * TILE_CORNER_FRACTION + inset))
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .padding(inset)
                .fillMaxSize()
                .clip(tileShape)
                .background(colorResource(variant.background))
        ) {
            Image(
                painter = painterResource(variant.art),
                contentDescription = null,
                colorFilter = variant.foreground?.let { ColorFilter.tint(colorResource(it)) },
                modifier = Modifier
                    .fillMaxSize()
                    .scale(ADAPTIVE_ICON_ZOOM)
            )
        }
        if (selected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp)
                    .background(SELECTION_COLOR, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
