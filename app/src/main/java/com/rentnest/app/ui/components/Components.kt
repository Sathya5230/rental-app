package com.rentnest.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.model.BookingStatus
import com.rentnest.app.domain.model.RatingSummary
import com.rentnest.app.domain.rules.PriceBreakdown
import com.rentnest.app.ui.model.ItemSummary
import java.util.Locale

@Composable
fun ItemCard(
    summary: ItemSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sharedKey: String? = null,
    onToggleFavourite: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box {
            ItemArt(
                summary.item.photos.firstOrNull().orEmpty(),
                Modifier.fillMaxWidth().aspectRatio(1.3f).sharedArt(sharedKey).clip(MaterialTheme.shapes.medium),
            )
            if (onToggleFavourite != null) {
                FavouriteButton(summary.isFavourite, onToggleFavourite, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(summary.item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary.providerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriceText(summary.item.dailyRate, Modifier.weight(1f))
                RatingBadge(summary.rating)
            }
        }
    }
}

@Composable
fun FavouriteButton(isFavourite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (isFavourite) 1.15f else 1f, spring(Spring.DampingRatioHighBouncy), label = "fav")
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), modifier = modifier.size(40.dp)) {
        IconButton(onClick = onToggle) {
            Icon(
                if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavourite) "Remove from saved" else "Save",
                tint = if (isFavourite) Color(0xFFE53950) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp).scale(scale),
            )
        }
    }
}

@Composable
fun PriceText(dailyRate: Long, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append(MoneyFormatter.format(dailyRate)) }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" /day") }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}

@Composable
fun RatingBadge(summary: RatingSummary?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Star, null, tint = Color(0xFFF5A623), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(2.dp))
        Text(
            if (summary == null) "New" else "${String.format(Locale.ENGLISH, "%.1f", summary.average)} (${summary.count})",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

data class StatusStyle(val label: String, val icon: ImageVector, val container: Color, val content: Color)

@Composable
fun statusStyle(status: BookingStatus): StatusStyle {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    fun c(light: Long, darkC: Long) = Color(if (dark) darkC else light)
    return when (status) {
        BookingStatus.REQUESTED -> StatusStyle("Pending", Icons.Rounded.HourglassTop, c(0xFFFFF0C2, 0xFF4A3B00), c(0xFF5C4400, 0xFFFFE08A))
        BookingStatus.ACCEPTED -> StatusStyle("Confirmed", Icons.Rounded.EventAvailable, c(0xFFDCE7FF, 0xFF0B2E66), c(0xFF0B3A82, 0xFFC4D6FF))
        BookingStatus.ACTIVE -> StatusStyle("Active", Icons.Rounded.PlayCircle, c(0xFFD6F5DD, 0xFF0F3D1D), c(0xFF0D5222, 0xFFA6EBB7))
        BookingStatus.RETURNED -> StatusStyle("Completed", Icons.Rounded.TaskAlt, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
        BookingStatus.DECLINED -> StatusStyle("Declined", Icons.Rounded.DoNotDisturbOn, c(0xFFFFDAD6, 0xFF5C1512), c(0xFF8C1D18, 0xFFFFB4AB))
        BookingStatus.CANCELLED -> StatusStyle("Cancelled", Icons.Rounded.Cancel, c(0xFFFFDAD6, 0xFF5C1512), c(0xFF8C1D18, 0xFFFFB4AB))
    }
}

@Composable
fun StatusChip(status: BookingStatus, modifier: Modifier = Modifier) {
    val s = statusStyle(status)
    Surface(color = s.container, contentColor = s.content, shape = CircleShape, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(s.icon, null, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(s.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Requested → Confirmed → Picked up → Returned, with an animated progress line. */
@Composable
fun StatusTimeline(status: BookingStatus, modifier: Modifier = Modifier) {
    if (status == BookingStatus.DECLINED || status == BookingStatus.CANCELLED) {
        val s = statusStyle(status)
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Icon(s.icon, null, tint = s.content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (status == BookingStatus.DECLINED) "Declined by provider" else "Cancelled", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val steps = listOf("Requested", "Confirmed", "Picked up", "Returned")
    val index = when (status) { BookingStatus.REQUESTED -> 0; BookingStatus.ACCEPTED -> 1; BookingStatus.ACTIVE -> 2; else -> 3 }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(if (started) index.toFloat() else 0f, tween(700, easing = FastOutSlowInEasing), label = "timeline")
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.semantics { contentDescription = "Status: ${steps[index]}" }) {
        Box(Modifier.fillMaxWidth().height(14.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val slot = size.width / steps.size
                val y = size.height / 2
                drawLine(inactive, Offset(slot / 2, y), Offset(slot * (steps.size - 0.5f), y), 4.dp.toPx(), StrokeCap.Round)
                drawLine(active, Offset(slot / 2, y), Offset(slot * (0.5f + progress), y), 4.dp.toPx(), StrokeCap.Round)
                steps.indices.forEach { i ->
                    drawCircle(if (i <= progress + 0.01f) active else inactive, radius = 6.dp.toPx(), center = Offset(slot * (i + 0.5f), y))
                }
            }
        }
        Row {
            steps.forEachIndexed { i, label ->
                Text(
                    label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                    color = if (i <= index) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onAction, shape = MaterialTheme.shapes.small) { Text(actionLabel) }
        }
    }
}

@Composable
private fun Modifier.composedShimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(0f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerLowest
    return drawBehind {
        drawRect(Brush.linearGradient(listOf(base, highlight, base), start = Offset(size.width * (x - 1), 0f), end = Offset(size.width * x, size.height)))
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier) {
    Box(modifier.clip(MaterialTheme.shapes.medium).composedShimmer())
}

@Composable
fun SkeletonList(rows: Int = 4, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(rows) { SkeletonBlock(Modifier.fillMaxWidth().height(96.dp)) }
    }
}

@Composable
fun KpiTile(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val content: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    if (onClick != null) Card(onClick, modifier, shape = MaterialTheme.shapes.medium, colors = colors, content = content)
    else Card(modifier, shape = MaterialTheme.shapes.medium, colors = colors, content = content)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.heightIn(min = 54.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        } else {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun PriceBreakdownCard(breakdown: PriceBreakdown, dailyRate: Long, weeklyRate: Long, modifier: Modifier = Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Price details", style = MaterialTheme.typography.titleMedium)
            if (breakdown.weeks > 0) {
                val label = "${breakdown.weeks} week${if (breakdown.weeks > 1) "s" else ""} × ${MoneyFormatter.format(weeklyRate)}"
                PriceLine(if (breakdown.bestPriceApplied) "$label (best price)" else label, breakdown.weeklyCharge)
            }
            if (breakdown.extraDays > 0) PriceLine("${breakdown.extraDays} day${if (breakdown.extraDays > 1) "s" else ""} × ${MoneyFormatter.format(dailyRate)}", breakdown.dailyCharge)
            PriceLine("Refundable deposit", breakdown.deposit)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row {
                Text("Total due now", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(MoneyFormatter.format(breakdown.totalDueNow), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Rental ${MoneyFormatter.format(breakdown.subtotal)} for ${breakdown.days} day${if (breakdown.days > 1) "s" else ""}. Deposit is returned after a successful return.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PriceLine(label: String, amount: Long) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(MoneyFormatter.format(amount), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Text(initials, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
