package com.rentnest.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AvailabilityCalendar(unavailable: Set<LocalDate>, today: LocalDate, modifier: Modifier = Modifier, monthsAhead: Long = 2) {
    val first = YearMonth.from(today)
    val last = first.plusMonths(monthsAhead)
    var month by remember { mutableStateOf(first) }
    val title = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }, enabled = month > first) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month")
            }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < last) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month")
            }
        }
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val days = month.lengthOfMonth()
        val rows = (offset + days + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (c in 0 until 7) {
                    val day = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..days) DayCell(month.atDay(day), today, month.atDay(day) in unavailable)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(MaterialTheme.colorScheme.primaryContainer, "Available")
            Legend(MaterialTheme.colorScheme.surfaceContainerHighest, "Booked")
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, today: LocalDate, booked: Boolean) {
    val past = date.isBefore(today)
    val scheme = MaterialTheme.colorScheme
    val bg = when { past -> Color.Transparent; booked -> scheme.surfaceContainerHighest; else -> scheme.primaryContainer }
    val fg = when { past -> scheme.onSurface.copy(alpha = 0.35f); booked -> scheme.onSurface.copy(alpha = 0.45f); else -> scheme.onPrimaryContainer }
    val label = "${date.dayOfMonth} ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }}, ${if (booked) "booked" else if (past) "past" else "available"}"
    Box(
        Modifier.fillMaxSize().clip(CircleShape).background(bg)
            .then(if (date == today) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(), color = fg, style = MaterialTheme.typography.labelMedium,
            textDecoration = if (booked && !past) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
