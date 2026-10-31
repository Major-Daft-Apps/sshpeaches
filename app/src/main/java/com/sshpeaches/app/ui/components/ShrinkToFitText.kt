package com.majordaftapps.sshpeaches.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * One line of text that shrinks its font, a step at a time, until it fits the width it's given. Below
 * [minFontSize] it stops shrinking and ends in an ellipsis instead: a long host name in a top bar stays readable.
 */
@Composable
fun ShrinkToFitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    minFontSize: TextUnit = 15.sp,
    testTag: String? = null
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val baseSize = if (style.fontSize.isSpecified) style.fontSize.value else 22f
        val maxWidth = constraints.maxWidth
        val fontSize = remember(text, style, maxWidth, minFontSize) {
            if (maxWidth == Constraints.Infinity) {
                baseSize
            } else {
                shrinkToFitFontSize(baseSize, minFontSize.value, maxWidth) { size ->
                    measurer.measure(text, style.copy(fontSize = size.sp), maxLines = 1, softWrap = false).size.width
                }
            }
        }
        Text(
            text = text,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
            style = style.copy(fontSize = fontSize.sp),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The largest font size from [baseSize] down to [minSize], in steps of [step] sp, at which [widthAt] is within
 * [maxWidth]; [minSize] when even that is too wide (the text is then ellipsized).
 */
internal fun shrinkToFitFontSize(
    baseSize: Float,
    minSize: Float,
    maxWidth: Int,
    step: Float = 1f,
    widthAt: (Float) -> Int
): Float {
    var size = baseSize
    while (size > minSize && widthAt(size) > maxWidth) {
        size = maxOf(size - step, minSize)
    }
    return size
}
