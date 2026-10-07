package com.rentnest.app.ui.provider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.message
import com.rentnest.app.domain.model.AuditRecord
import com.rentnest.app.domain.model.Ownership
import com.rentnest.app.domain.repository.AuditEntry
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.repository.InventoryRepository
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.ItemArt
import com.rentnest.app.ui.components.PrimaryButton
import com.rentnest.app.ui.components.SkeletonList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

enum class AuditScope(val label: String) { ALL("All items"), OWNED("Owned"), BORROWED("Borrowed") }

data class AuditLine(val row: InventoryRow, val counted: Int, val notes: String) {
    /** Units that should be on the shelves: held units minus those out with customers. */
    val expected: Int get() = row.stock.inStore
    val matches: Boolean get() = counted == expected
}

/** One past audit session: every record saved with the same timestamp. */
data class AuditSession(val timestamp: Long, val items: Int, val mismatches: List<Pair<String, AuditRecord>>)

data class AuditUiState(
    val loading: Boolean = true,
    val scope: AuditScope = AuditScope.ALL,
    val lines: List<AuditLine> = emptyList(),
    val history: List<AuditSession> = emptyList(),
    val submitting: Boolean = false,
    val message: String? = null,
    val now: Long = 0,
)

private data class AuditDraft(val scope: AuditScope = AuditScope.ALL, val counted: Map<Long, Int> = emptyMap(), val notes: Map<Long, String> = emptyMap(), val submitting: Boolean = false, val message: String? = null)

@HiltViewModel
class AuditViewModel @Inject constructor(
    observeShop: ObserveShop,
    private val inventory: InventoryRepository,
    catalog: CatalogRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val draft = MutableStateFlow(AuditDraft())

    val state = combine(observeShop(), inventory.audits(), catalog.vendors(), draft) { shop, audits, vendors, d ->
        if (shop == null) return@combine AuditUiState(loading = false)
        val rows = InventoryRows.build(shop, audits, vendors, time.today(), time.nowMillis())
        val titles = shop.items.associate { it.id to it.title }
        val inScope = rows.filter {
            when (d.scope) {
                AuditScope.ALL -> true
                AuditScope.OWNED -> it.item.ownership == Ownership.OWNED
                AuditScope.BORROWED -> it.item.ownership == Ownership.BORROWED
            }
        }.sortedWith(compareByDescending<InventoryRow> { it.needsAudit }.thenBy { it.item.title })
        val history = audits.groupBy { it.timestamp }.map { (ts, records) ->
            AuditSession(ts, records.size, records.filter { !it.matches }.map { titles[it.itemId].orEmpty() to it })
        }.sortedByDescending { it.timestamp }.take(10)
        AuditUiState(
            loading = false, scope = d.scope,
            lines = inScope.map { AuditLine(it, d.counted[it.item.id] ?: it.stock.inStore, d.notes[it.item.id].orEmpty()) },
            history = history, submitting = d.submitting, message = d.message, now = time.nowMillis(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuditUiState())

    fun setScope(s: AuditScope) = draft.update { it.copy(scope = s) }
    fun setCount(itemId: Long, n: Int) = draft.update { it.copy(counted = it.counted + (itemId to n.coerceAtLeast(0))) }
    fun setNotes(itemId: Long, text: String) = draft.update { it.copy(notes = it.notes + (itemId to text)) }

    fun submit() = viewModelScope.launch {
        val s = state.value
        if (s.submitting || s.lines.isEmpty()) return@launch
        draft.update { it.copy(submitting = true) }
        val r = inventory.submitAudit(s.lines.map { AuditEntry(it.row.item.id, it.expected, it.counted, it.notes) })
        val mismatches = s.lines.count { !it.matches }
        draft.update {
            AuditDraft(
                scope = it.scope,
                message = when (r) {
                    is Outcome.Success -> if (mismatches == 0) "Audit saved: all ${r.value} items match." else "Audit saved: $mismatches of ${r.value} items don't match."
                    is Outcome.Failure -> r.error.message()
                },
            )
        }
    }

    fun messageShown() = draft.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(onBack: () -> Unit, viewModel: AuditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock audit") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { showHistory = !showHistory }) { Text(if (showHistory) "Count" else "History") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!showHistory) Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    val off = state.lines.count { !it.matches }
                    Text(
                        if (off == 0) "All ${state.lines.size} items match what should be in the store."
                        else "$off of ${state.lines.size} items don't match. Add a note to explain.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (off == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    PrimaryButton("Save audit (${state.lines.size} items)", viewModel::submit, Modifier.fillMaxWidth(), enabled = state.lines.isNotEmpty(), loading = state.submitting)
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> SkeletonList(modifier = Modifier.padding(padding))
            showHistory -> History(state, Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        "Count what's physically in the store. Units out with customers are already excluded.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        AuditScope.entries.forEachIndexed { i, s ->
                            SegmentedButton(state.scope == s, { viewModel.setScope(s) }, SegmentedButtonDefaults.itemShape(i, AuditScope.entries.size)) { Text(s.label) }
                        }
                    }
                }
                items(state.lines, key = { it.row.item.id }) { line -> AuditCard(line, state.now, viewModel) }
            }
        }
    }
}

@Composable
private fun AuditCard(line: AuditLine, now: Long, vm: AuditViewModel) {
    val row = line.row
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (line.matches) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemArt(row.item.photos.firstOrNull().orEmpty(), Modifier.size(52.dp).clip(MaterialTheme.shapes.small), iconSize = 22.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.item.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(
                            row.vendorName?.let { "Borrowed from $it" } ?: "Owned",
                            row.stock.out.takeIf { it > 0 }?.let { "$it out" },
                            row.lastAudit?.let { "last counted ${auditAge(it.timestamp, now)}" } ?: "never counted",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Expected ${line.expected}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("Found", style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { vm.setCount(row.item.id, line.counted - 1) }, enabled = line.counted > 0) { Icon(Icons.Rounded.Remove, "One fewer") }
                Text("${line.counted}", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { vm.setCount(row.item.id, line.counted + 1) }) { Icon(Icons.Rounded.Add, "One more") }
            }
            if (!line.matches) OutlinedTextField(
                line.notes, { vm.setNotes(row.item.id, it) }, label = { Text("What happened?") }, singleLine = true,
                shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun History(state: AuditUiState, modifier: Modifier) {
    if (state.history.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp)) { Text("No audits yet. Your saved counts will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.history, key = { it.timestamp }) { s ->
            Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val date = Instant.ofEpochMilli(s.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (s.mismatches.isEmpty()) Icons.Rounded.TaskAlt else Icons.Rounded.Warning, null,
                            tint = if (s.mismatches.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("${DateFormats.full(date)} · ${s.items} items", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(auditAge(s.timestamp, state.now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (s.mismatches.isEmpty()) Text("Everything matched.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    s.mismatches.forEach { (title, r) ->
                        Text(
                            "$title: found ${r.counted} of ${r.expected}" + if (r.notes.isNotBlank()) " (${r.notes})" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
