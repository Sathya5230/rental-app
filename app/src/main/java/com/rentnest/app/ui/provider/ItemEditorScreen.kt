package com.rentnest.app.ui.provider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rentnest.app.domain.DEMO_USER_ID
import com.rentnest.app.domain.DomainError
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.message
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.BookingRepository
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.repository.InventoryRepository
import com.rentnest.app.domain.rules.ItemDraft
import com.rentnest.app.domain.rules.ItemField
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.ItemArt
import com.rentnest.app.ui.components.PhotoKeys
import com.rentnest.app.ui.components.PrimaryButton
import com.rentnest.app.ui.navigation.ItemEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpecRow(val key: String, val value: String)

data class EditorForm(
    val title: String = "",
    val categoryId: Long? = null,
    val description: String = "",
    val photos: List<String> = emptyList(),
    val daily: String = "",
    val weekly: String = "",
    val deposit: String = "0",
    val threshold: Int = 1,
    val specs: List<SpecRow> = emptyList(),
    val isActive: Boolean = true,
)

data class UnitUi(val unit: ItemUnit, val busy: Boolean)

data class ItemEditorUiState(
    val loading: Boolean = true,
    val itemId: Long = 0,
    val form: EditorForm = EditorForm(),
    val errors: Set<ItemField> = emptySet(),
    val categories: List<Category> = emptyList(),
    val units: List<UnitUi> = emptyList(),
    val saving: Boolean = false,
    val message: String? = null,
    val closed: Boolean = false,
) { val isNew get() = itemId == 0L }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val inventory: InventoryRepository,
    bookings: BookingRepository,
    time: TimeProvider,
) : ViewModel() {
    private val local = MutableStateFlow(ItemEditorUiState(itemId = savedState.toRoute<ItemEditorRoute>().itemId))
    private var providerId = 0L

    init {
        viewModelScope.launch {
            providerId = catalog.providerForUser(DEMO_USER_ID).filterNotNull().first().id
            val id = local.value.itemId
            val item = if (id == 0L) null else catalog.item(id).first()
            local.update { s ->
                s.copy(loading = false, form = item?.let { i ->
                    EditorForm(i.title, i.categoryId, i.description, i.photos, MoneyFormatter.toInput(i.dailyRate), MoneyFormatter.toInput(i.weeklyRate),
                        MoneyFormatter.toInput(i.deposit), i.lowStockThreshold, i.specs.map { SpecRow(it.key, it.value) }, i.isActive)
                } ?: s.form)
            }
        }
    }

    private val units = local.map { it.itemId }.distinctUntilChanged().flatMapLatest { id ->
        if (id == 0L) flowOf(emptyList())
        else combine(inventory.unitsForItem(id), bookings.bookingsForItem(id)) { u, b ->
            val today = time.today()
            u.map { unit -> UnitUi(unit, b.any { it.unitId == unit.id && (it.status == BookingStatus.ACTIVE || (it.status == BookingStatus.ACCEPTED && !it.endDate.isBefore(today))) }) }
        }
    }

    val state = combine(local, catalog.categories(), units) { s, cats, u -> s.copy(categories = cats, units = u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun update(transform: (EditorForm) -> EditorForm) = local.update { it.copy(form = transform(it.form)) }

    fun selectCategory(c: Category) = update { f ->
        val keepPhotos = f.photos.isNotEmpty() && f.photos.all { PhotoKeys.parse(it).first == c.iconKey }
        f.copy(categoryId = c.id, photos = if (keepPhotos) f.photos else listOf("${c.iconKey}:0"))
    }

    fun save() = viewModelScope.launch {
        val s = local.value
        if (s.saving) return@launch
        local.update { it.copy(saving = true) }
        val f = s.form
        val draft = ItemDraft(
            id = s.itemId, providerId = providerId, title = f.title, categoryId = f.categoryId, description = f.description,
            photos = f.photos, dailyRate = MoneyFormatter.parseRupees(f.daily), weeklyRate = MoneyFormatter.parseRupees(f.weekly),
            deposit = MoneyFormatter.parseRupees(f.deposit), specs = f.specs.map { it.key to it.value },
            lowStockThreshold = f.threshold, isActive = f.isActive,
        )
        when (val r = catalog.saveItem(draft)) {
            is Outcome.Success -> local.update {
                if (it.isNew) it.copy(saving = false, errors = emptySet(), itemId = r.value, message = "Item added with 1 unit. Add more units below.")
                else it.copy(saving = false, errors = emptySet(), message = "Changes saved")
            }
            is Outcome.Failure -> local.update {
                it.copy(saving = false, errors = (r.error as? DomainError.ValidationFailed)?.fields.orEmpty(), message = r.error.message())
            }
        }
    }

    fun addUnit() = viewModelScope.launch {
        val unit = inventory.addUnit(local.value.itemId)
        local.update { it.copy(message = "Added unit ${unit.tag}") }
    }

    fun updateUnit(unit: ItemUnit) = viewModelScope.launch {
        val r = inventory.updateUnit(unit)
        if (r is Outcome.Failure) local.update { it.copy(message = r.error.message()) }
    }

    fun messageShown() = local.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemEditorScreen(onBack: () -> Unit, viewModel: ItemEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New item" else "Edit item") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Details") })
                Tab(tab == 1, { tab = 1 }, enabled = !state.isNew, text = { Text(if (state.isNew) "Units (save first)" else "Units (${state.units.size})") })
            }
            if (tab == 0) DetailsForm(state, viewModel) else UnitsTab(state, viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsForm(state: ItemEditorUiState, vm: ItemEditorViewModel) {
    val f = state.form
    val e = state.errors
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Category", style = MaterialTheme.typography.titleSmall, color = if (ItemField.CATEGORY in e) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.categories.forEach { c -> FilterChip(f.categoryId == c.id, { vm.selectCategory(c) }, { Text(c.name) }) }
        }
        val categoryKey = state.categories.firstOrNull { it.id == f.categoryId }?.iconKey
        if (categoryKey != null) {
            Text("Photos (tap to include)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoKeys.variants(categoryKey).forEach { key ->
                    val on = key in f.photos
                    Box(
                        Modifier.size(72.dp).clip(MaterialTheme.shapes.small)
                            .border(if (on) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                            .clickable { vm.update { it.copy(photos = if (on) it.photos - key else it.photos + key) } },
                    ) {
                        ItemArt(key, Modifier.fillMaxSize(), iconSize = 28.dp)
                        if (on) Icon(Icons.Rounded.CheckCircle, "Selected", Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        Field("Title", f.title, ItemField.TITLE in e, "Give your item a name") { v -> vm.update { it.copy(title = v) } }
        OutlinedTextField(f.description, { v -> vm.update { it.copy(description = v) } }, label = { Text("Description") }, minLines = 3, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Daily ₹", f.daily, ItemField.DAILY_RATE in e, "Required", Modifier.weight(1f), number = true) { v -> vm.update { it.copy(daily = v) } }
            Field("Weekly ₹", f.weekly, ItemField.WEEKLY_RATE in e, "≤ 7 × daily", Modifier.weight(1f), number = true) { v -> vm.update { it.copy(weekly = v) } }
        }
        Field("Refundable deposit ₹", f.deposit, ItemField.DEPOSIT in e, "0 or more", number = true) { v -> vm.update { it.copy(deposit = v) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Low-stock alert", style = MaterialTheme.typography.titleSmall)
                Text("Warn when free units drop to this", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.update { it.copy(threshold = (it.threshold - 1).coerceAtLeast(0)) } }) { Icon(Icons.Rounded.Remove, "Decrease") }
            Text("${f.threshold}", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { vm.update { it.copy(threshold = it.threshold + 1) } }) { Icon(Icons.Rounded.Add, "Increase") }
        }
        Text("Specifications", style = MaterialTheme.typography.titleSmall)
        f.specs.forEachIndexed { i, s ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(s.key, { v -> vm.update { it.copy(specs = it.specs.toMutableList().also { l -> l[i] = s.copy(key = v) }) } }, label = { Text("Name") }, singleLine = true, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f))
                OutlinedTextField(s.value, { v -> vm.update { it.copy(specs = it.specs.toMutableList().also { l -> l[i] = s.copy(value = v) }) } }, label = { Text("Value") }, singleLine = true, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.update { it.copy(specs = it.specs.filterIndexed { j, _ -> j != i }) } }) { Icon(Icons.Rounded.Delete, "Remove spec") }
            }
        }
        TextButton(onClick = { vm.update { it.copy(specs = it.specs + SpecRow("", "")) } }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add specification") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Listed", style = MaterialTheme.typography.titleSmall)
                Text("Inactive items are hidden from customers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(f.isActive, { v -> vm.update { it.copy(isActive = v) } })
        }
        PrimaryButton(if (state.isNew) "Create item" else "Save changes", vm::save, Modifier.fillMaxWidth(), loading = state.saving)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Field(label: String, value: String, error: Boolean, hint: String, modifier: Modifier = Modifier.fillMaxWidth(), number: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, isError = error, singleLine = true, shape = MaterialTheme.shapes.small,
        supportingText = { Text(hint) },
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitsTab(state: ItemEditorUiState, vm: ItemEditorViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Each unit is one physical piece you can rent out. Track its condition and take it out of service when needed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.units.forEach { u ->
            OutlinedCard(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.QrCode2, null)
                        Spacer(Modifier.width(8.dp))
                        Text(u.unit.tag, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (u.busy) AssistChip(onClick = {}, label = { Text("Booked") }, leadingIcon = { Icon(Icons.Rounded.EventAvailable, null, Modifier.size(16.dp)) })
                    }
                    Text("Condition", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        UnitCondition.entries.forEach { c ->
                            FilterChip(u.unit.condition == c, { vm.updateUnit(u.unit.copy(condition = c)) }, { Text(c.name.lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                    Text("Status", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        UnitStatus.entries.forEach { s ->
                            FilterChip(u.unit.status == s, { vm.updateUnit(u.unit.copy(status = s)) }, { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = vm::addUnit, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add unit")
        }
    }
}
