package com.rentnest.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rentnest.app.R
import com.rentnest.app.domain.model.AppMode
import com.rentnest.app.domain.model.ThemePref

private data class Accent(val primary: Color, val onPrimary: Color, val container: Color, val onContainer: Color, val secondaryContainer: Color, val onSecondaryContainer: Color)

private val CustomerLight = Accent(Color(0xFF00695C), Color.White, Color(0xFFB2F1E8), Color(0xFF00201C), Color(0xFFCCE8E2), Color(0xFF05201C))
private val CustomerDark = Accent(Color(0xFF53DBC9), Color(0xFF003731), Color(0xFF005048), Color(0xFF74F8E5), Color(0xFF334B47), Color(0xFFCCE8E2))
private val ProviderLight = Accent(Color(0xFF9A5B00), Color.White, Color(0xFFFFDDB8), Color(0xFF2F1500), Color(0xFFF8DFC6), Color(0xFF2A1708))
private val ProviderDark = Accent(Color(0xFFFFB866), Color(0xFF4A2800), Color(0xFF6A3C00), Color(0xFFFFDDB8), Color(0xFF574330), Color(0xFFF8DFC6))

private fun scheme(accent: Accent, dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = accent.primary, onPrimary = accent.onPrimary, primaryContainer = accent.container, onPrimaryContainer = accent.onContainer,
    secondaryContainer = accent.secondaryContainer, onSecondaryContainer = accent.onSecondaryContainer,
    tertiary = Color(0xFFB8C4FF), tertiaryContainer = Color(0xFF37437A), onTertiaryContainer = Color(0xFFDDE1FF),
    background = Color(0xFF0F1413), surface = Color(0xFF0F1413), onSurface = Color(0xFFDEE4E2), onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceContainerLowest = Color(0xFF0A0F0E), surfaceContainerLow = Color(0xFF171D1C), surfaceContainer = Color(0xFF1B2120),
    surfaceContainerHigh = Color(0xFF252B2A), surfaceContainerHighest = Color(0xFF303635),
    outline = Color(0xFF899390), outlineVariant = Color(0xFF3F4947),
) else lightColorScheme(
    primary = accent.primary, onPrimary = accent.onPrimary, primaryContainer = accent.container, onPrimaryContainer = accent.onContainer,
    secondaryContainer = accent.secondaryContainer, onSecondaryContainer = accent.onSecondaryContainer,
    tertiary = Color(0xFF4F5B92), tertiaryContainer = Color(0xFFDDE1FF), onTertiaryContainer = Color(0xFF07164B),
    background = Color(0xFFF7F9F8), surface = Color(0xFFF7F9F8), onSurface = Color(0xFF181C1B), onSurfaceVariant = Color(0xFF3F4947),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F4F3), surfaceContainer = Color(0xFFEBEFEE),
    surfaceContainerHigh = Color(0xFFE5E9E8), surfaceContainerHighest = Color(0xFFE0E3E2),
    outline = Color(0xFF6F7977), outlineVariant = Color(0xFFBEC9C6),
)

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

private val Jakarta = FontFamily(listOf(500, 600, 700, 800).map { variable(R.font.plus_jakarta_sans, it) })
private val Inter = FontFamily(listOf(400, 500, 600, 700).map { variable(R.font.inter, it) })

private val base = Typography()
val RentNestTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter),
    bodySmall = base.bodySmall.copy(fontFamily = Inter),
    labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
)

val RentNestShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ThemePref.isDark(): Boolean = when (this) {
    ThemePref.SYSTEM -> isSystemInDarkTheme()
    ThemePref.LIGHT -> false
    ThemePref.DARK -> true
}

/** Earnings-chart bar color, validated against chart surfaces in both themes. */
@Composable
fun chartBarColor(): Color = if (MaterialTheme.colorScheme.surface == Color(0xFF0F1413)) Color(0xFFC77C1E) else Color(0xFF9A5B00)

@Composable
fun RentNestTheme(mode: AppMode = AppMode.CUSTOMER, themePref: ThemePref = ThemePref.SYSTEM, content: @Composable () -> Unit) {
    val dark = themePref.isDark()
    val accent = when (mode) {
        AppMode.CUSTOMER -> if (dark) CustomerDark else CustomerLight
        AppMode.PROVIDER -> if (dark) ProviderDark else ProviderLight
    }
    val target = scheme(accent, dark)
    val spec = tween<Color>(450)
    val primary by animateColorAsState(target.primary, spec, label = "primary")
    val container by animateColorAsState(target.primaryContainer, spec, label = "container")
    val onContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "onContainer")
    val secondary by animateColorAsState(target.secondaryContainer, spec, label = "secondary")
    MaterialTheme(
        colorScheme = target.copy(primary = primary, primaryContainer = container, onPrimaryContainer = onContainer, secondaryContainer = secondary),
        typography = RentNestTypography,
        shapes = RentNestShapes,
        content = content,
    )
}
