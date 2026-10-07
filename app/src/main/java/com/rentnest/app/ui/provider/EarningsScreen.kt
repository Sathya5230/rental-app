package com.rentnest.app.ui.provider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.model.Booking
import com.rentnest.app.domain.model.BookingStatus
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.SkeletonList
import com.rentnest.app.ui.theme.chartBarColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class EarningsPeriod(val label: String, val current: String) { WEEKLY("Weekly", "This week"), MONTHLY("Monthly", "This month") }
data class EarningsBar(val label: String, val amount: Long, val start: LocalDate, val end: LocalDate)
data class TopItem(val title: String, val amount: Long, val rentals: Int)
data class Payout(val label: String, val amount: Long, val pending: Boolean)
data class EarningsSummary(val bars: List<EarningsBar>, val total: Long, val changePercent: Int?, val topItems: List<TopItem>, val payouts: List<Payout>)

object EarningsCalculator {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val month = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private fun amount(b: Booking) = b.subtotal + b.damageFee

    fun compute(period: EarningsPeriod, today: LocalDate, bookings: List<Booking>, titles: Map<Long, String>): EarningsSummary {
        val returned = bookings.filter { it.status == BookingStatus.RETURNED }
        val monday = today.with(DayOfWeek.MONDAY)
        val weeks = (7 downTo 0).map { monday.minusWeeks(it.toLong()).let { s -> s to s.plusDays(6) } }
        val buckets = when (period) {
            EarningsPeriod.WEEKLY -> weeks
            EarningsPeriod.MONTHLY -> (5 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()).let { m -> m.atDay(1) to m.atEndOfMonth() } }
        }
        fun sum(s: LocalDate, e: LocalDate) = returned.filter { it.endDate in s..e }.sumOf(::amount)
        val bars = buckets.map { (s, e) -> EarningsBar(s.format(if (period == EarningsPeriod.WEEKLY) dayMonth else month), sum(s, e), s, e) }
        val total = bars.last().amount
        val prev = bars[bars.size - 2].amount
        val windowStart = bars.first().start
        val top = returned.filter { !it.endDate.isBefore(windowStart) }.groupBy { it.itemId }
            .map { (id, list) -> TopItem(titles[id] ?: "Item", list.sumOf(::amount), list.size) }
            .sortedByDescending { it.amount }.take(5)
        val payouts = weeks.takeLast(4).reversed().mapNotNull { (s, e) ->
            val a = sum(s, e)
            if (a == 0L) null else Payout("Week of ${s.format(dayMonth)}", a, pending = s == monday)
        }
        return EarningsSummary(bars, total, if (prev == 0L) null else ((total - prev) * 100 / prev).toInt(), top, payouts)
    }
}

data class EarningsUiState(val loading: Boolean = true, val period: EarningsPeriod = EarningsPeriod.WEEKLY, val summary: EarningsSummary? = null)

@HiltViewModel
class EarningsViewModel @Inject constructor(observeShop: ObserveShop, time: TimeProvider) : ViewModel() {
    private val period = MutableStateFlow(EarningsPeriod.WEEKLY)
    val state = combine(observeShop(), period) { shop, p ->
        EarningsUiState(false, p, shop?.let { EarningsCalculator.compute(p, time.today(), it.bookings, it.items.associate { i -> i.id to i.title }) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EarningsUiState())
    fun setPeriod(p: EarningsPeriod) { period.value = p }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsScreen(viewModel: EarningsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var asTable by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Earnings") }) }) { padding ->
        val s = state.summary
        if (s == null) { SkeletonList(modifier = Modifier.padding(padding)); return@Scaffold }
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                EarningsPeriod.entries.forEachIndexed { i, p ->
                    SegmentedButton(state.period == p, { viewModel.setPeriod(p) }, SegmentedButtonDefaults.itemShape(i, EarningsPeriod.entries.size)) { Text(p.label) }
                }
            }
            Column {
                Text(state.period.current, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(MoneyFormatter.format(s.total), style = MaterialTheme.typography.displaySmall)
                    s.changePercent?.let { c ->
                        Spacer(Modifier.width(10.dp))
                        Icon(if (c >= 0) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${if (c >= 0) "+" else ""}$c% vs previous", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Completed rentals", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { asTable = !asTable }) { Text(if (asTable) "Show chart" else "View as table") }
                    }
                    if (asTable) s.bars.reversed().forEach { b ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Text(b.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(MoneyFormatter.format(b.amount), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else EarningsChart(s.bars)
                }
            }
            Text("Top items", style = MaterialTheme.typography.titleLarge)
            if (s.topItems.isEmpty()) Text("No completed rentals in this period yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.topItems.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                        Text("${t.rentals} rental${if (t.rentals > 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(MoneyFormatter.format(t.amount), style = MaterialTheme.typography.titleSmall)
                }
            }
            Text("Payouts", style = MaterialTheme.typography.titleLarge)
            if (s.payouts.isEmpty()) Text("Payouts appear after completed rentals.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.payouts.forEach { p ->
                ListItem(
                    headlineContent = { Text(p.label) },
                    supportingContent = { Text(if (p.pending) "Pending · pays out Monday" else "Paid to HDFC •••• 2207") },
                    leadingContent = { Icon(if (p.pending) Icons.Rounded.Schedule else Icons.Rounded.CheckCircle, if (p.pending) "Pending" else "Paid") },
                    trailingContent = { Text(MoneyFormatter.format(p.amount), style = MaterialTheme.typography.titleSmall) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Single-series bar chart: one color, 4dp rounded tops, recessive grid, tap a bar for its value. */
@Composable
private fun EarningsChart(bars: List<EarningsBar>) {
    val color = chartBarColor()
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()
    var selected by remember(bars) { mutableIntStateOf(bars.lastIndex) }
    val grow = remember(bars) { Animatable(0f) }
    LaunchedEffect(bars) { grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val max = (bars.maxOfOrNull { it.amount } ?: 0L).coerceAtLeast(1L)
    val summary = bars.joinToString { "${it.label} ${MoneyFormatter.format(it.amount)}" }
    Canvas(
        Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = "Earnings chart: $summary" }
            .pointerInput(bars) {
                detectTapGestures { pos ->
                    val axis = 44.dp.toPx()
                    val slot = (size.width - axis) / bars.size
                    selected = ((pos.x - axis) / slot).toInt().coerceIn(0, bars.lastIndex)
                }
            },
    ) {
        val axis = 44.dp.toPx()
        val top = 28.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val plotH = bottom - top
        (0..2).forEach { i ->
            val y = bottom - plotH * i / 2
            drawLine(grid, Offset(axis, y), Offset(size.width, y), 1.dp.toPx())
            val t = measurer.measure(MoneyFormatter.compact(max * i / 2), labelStyle)
            drawText(t, topLeft = Offset(0f, y - t.size.height / 2))
        }
        val slot = (size.width - axis) / bars.size
        val barW = slot * 0.56f
        val r = 4.dp.toPx()
        bars.forEachIndexed { i, b ->
            val h = plotH * (b.amount.toFloat() / max) * grow.value
            val x = axis + slot * i + (slot - barW) / 2
            if (h > 0f) {
                drawRoundRect(color, Offset(x, bottom - h), Size(barW, h), CornerRadius(r, r))
                if (h > r) drawRect(color, Offset(x, bottom - minOf(h, r)), Size(barW, minOf(h, r)))
            }
            val label = measurer.measure(b.label, labelStyle)
            drawText(label, topLeft = Offset(axis + slot * i + (slot - label.size.width) / 2, bottom + 4.dp.toPx()))
            if (i == selected) {
                val v = measurer.measure(MoneyFormatter.compact(b.amount), valueStyle)
                val vx = (axis + slot * i + (slot - v.size.width) / 2).coerceIn(axis, size.width - v.size.width)
                drawText(v, topLeft = Offset(vx, bottom - h - v.size.height - 4.dp.toPx()))
            }
        }
    }
}
