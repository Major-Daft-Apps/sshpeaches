package com.sshpeaches.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun rememberDialogBodyMaxHeight(
    verticalMargin: Dp = 24.dp,
    reservedDialogChrome: Dp = 180.dp,
    minBodyHeight: Dp = 160.dp
): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    return (screenHeight - verticalMargin - reservedDialogChrome).coerceAtLeast(minBodyHeight)
}

@Composable
fun rememberBottomSheetMaxHeight(
    verticalMargin: Dp = 24.dp,
    minSheetHeight: Dp = 240.dp
): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    return (screenHeight - verticalMargin).coerceAtLeast(minSheetHeight)
}
