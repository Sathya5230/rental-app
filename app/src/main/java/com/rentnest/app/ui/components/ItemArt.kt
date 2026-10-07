package com.rentnest.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object CategoryVisuals {
    private val palettes = mapOf(
        "cameras" to listOf(Color(0xFF355C7D), Color(0xFF6C5B7B)),
        "tools" to listOf(Color(0xFFD35400), Color(0xFFF39C12)),
        "camping" to listOf(Color(0xFF134E5E), Color(0xFF4F9A6E)),
        "party" to listOf(Color(0xFFC0392B), Color(0xFF8E2D6B)),
        "sports" to listOf(Color(0xFF117A65), Color(0xFF48C9B0)),
        "electronics" to listOf(Color(0xFF2B5876), Color(0xFF4E4376)),
        "vehicles" to listOf(Color(0xFF1C2833), Color(0xFF2E6177)),
        "music" to listOf(Color(0xFF6C3483), Color(0xFF3F2DB5)),
    )
    private val icons = mapOf(
        "cameras" to Icons.Rounded.PhotoCamera, "tools" to Icons.Rounded.Construction, "camping" to Icons.Rounded.Forest,
        "party" to Icons.Rounded.Celebration, "sports" to Icons.Rounded.SportsTennis, "electronics" to Icons.Rounded.Devices,
        "vehicles" to Icons.Rounded.TwoWheeler, "music" to Icons.Rounded.MusicNote,
    )

    fun icon(key: String): ImageVector = icons[key] ?: Icons.Rounded.Category
    fun gradient(key: String): List<Color> = palettes[key] ?: listOf(Color(0xFF00695C), Color(0xFF26A69A))
}

object PhotoKeys {
    fun parse(key: String): Pair<String, Int> {
        val parts = key.split(":")
        return parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
    fun variants(categoryKey: String): List<String> = (0 until 4).map { "$categoryKey:$it" }
}

/** Offline "photo": a category gradient with the category glyph. Variant changes direction and composition. */
@Composable
fun ItemArt(photoKey: String, modifier: Modifier = Modifier, iconSize: Dp = 52.dp) {
    val (category, variant) = PhotoKeys.parse(photoKey)
    val colors = CategoryVisuals.gradient(category).let { if (variant % 2 == 0) it else it.reversed() }
    val icon = CategoryVisuals.icon(category)
    Box(modifier.clipToBounds().background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        val (align, offset) = when (variant % 4) {
            0 -> Alignment.BottomEnd to 28.dp
            1 -> Alignment.TopStart to (-28).dp
            2 -> Alignment.TopEnd to 24.dp
            else -> Alignment.BottomStart to (-24).dp
        }
        Icon(icon, null, tint = Color.White.copy(alpha = 0.13f), modifier = Modifier.align(align).offset(offset, offset).size(iconSize * 2.8f))
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}
