package com.launcher.androidmediabar.ui.xmb

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Geometry of the cross media bar, in dp on a 960x540dp TV canvas (overscan-safe). */
internal object XmbDimens {
    val AnchorX = 200.dp
    val CategoryY = 150.dp
    val CategorySpacing = 150.dp
    val CategoryIconSize = 60.dp
    val CategoryColumnWidth = 160.dp

    val ThumbWidth = 128.dp
    val ThumbHeight = 72.dp
    val SelectedItemY = 255.dp
    val FirstBelowGap = 100.dp
    val ItemSpacing = 80.dp
    val FirstAboveY = 60.dp
    val ListShift = 48.dp

    const val UnselectedItemScale = 0.72f
    const val ItemsWindowAbove = 4
    const val ItemsWindowBelow = 7

    val OverscanHorizontal = 48.dp
    val OverscanVertical = 27.dp

    /** Side panel geometry: it covers the right part of the 960dp canvas. */
    val PanelWidth = 420.dp
    const val PanelVisibleRows = 7

    /** How far the bar slides left while a side panel is open. */
    val PanelContentShift = 40.dp
}

private val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 2f), blurRadius = 8f)

internal object XmbTextStyles {
    val Category = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, shadow = TextShadow)
    val ItemTitle = TextStyle(color = Color.White, fontSize = 24.sp, shadow = TextShadow)
    val ItemSubtitle = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, shadow = TextShadow)
    val Clock = TextStyle(color = Color.White, fontSize = 18.sp, shadow = TextShadow)
    val PanelTitle = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp, shadow = TextShadow)
    val PanelOption = TextStyle(color = Color.White, fontSize = 20.sp, shadow = TextShadow)
}

/** 1 when [distance] is 0, fading linearly to 0 at distance 1. */
internal fun selectedness(distance: Float): Float = (1f - abs(distance)).coerceIn(0f, 1f)

internal fun lerpF(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

/**
 * Vertical center of an item, given its distance from the selected one.
 * The selected item sits right below the category row; previous items jump above it.
 */
internal fun itemCenterY(rel: Float): Dp = with(XmbDimens) {
    when {
        rel >= 1f -> SelectedItemY + FirstBelowGap + ItemSpacing * (rel - 1f)
        rel >= 0f -> SelectedItemY + FirstBelowGap * rel
        rel >= -1f -> SelectedItemY + (FirstAboveY - SelectedItemY) * -rel
        else -> FirstAboveY + ItemSpacing * (rel + 1f)
    }
}
