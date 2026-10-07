package com.rentnest.app.ui.components

import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Horizontal carousels reach the screen edges, where Android's back gesture lives.
 * Excluding a band through the middle of the row lets edge swipes scroll the row instead of
 * leaving the app. Android honours at most 200dp of exclusion per edge, so keep the band small.
 */
@Composable
fun Modifier.carouselGestureExclusion(band: Dp = 64.dp): Modifier {
    val bandPx = with(LocalDensity.current) { band.toPx() }
    return systemGestureExclusion { coords ->
        val w = coords.size.width.toFloat()
        val h = coords.size.height.toFloat()
        val b = minOf(bandPx, h)
        Rect(0f, (h - b) / 2, w, (h + b) / 2)
    }
}
