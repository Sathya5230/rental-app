package com.rentnest.app.ui.provider

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rentnest.app.data.photos.PhotoStore
import com.rentnest.app.domain.ADMIN_USER_ID
import com.rentnest.app.domain.DomainError
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.format.DateFormats
import com.rentnest.app.domain.format.MoneyFormatter
import com.rentnest.app.domain.message
import com.rentnest.app.domain.model.*
import com.rentnest.app.domain.repository.BookingRepository
import com.rentnest.app.domain.repository.CatalogRepository
import com.rentnest.app.domain.repository.InventoryRepository
import com.rentnest.app.domain.rules.ItemDraft
import com.rentnest.app.domain.rules.ItemField
import com.rentnest.app.domain.time.DateMillis
import com.rentnest.app.domain.time.TimeProvider
import com.rentnest.app.ui.components.ItemArt
import com.rentnest.app.ui.components.PrimaryButton
import com.rentnest.app.ui.navigation.ItemEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    val unitValue: String = "",
    val ownership: Ownership = Ownership.OWNED,
    val vendorId: Long? = null,
    val vendorCost: String = "",
    val vendorReturnBy: LocalDate? = null,
)

data class UnitUi(val unit: ItemUnit, val busy: Boolean)

data class ItemEditorUiState(
    val loading: Boolean = true,
    val itemId: Long = 0,
    val form: EditorForm = EditorForm(),
    val errors: Set<ItemField> = emptySet(),
    val categories: List<Category> = emptyList(),
    val vendors: List<Vendor> = emptyList(),
    val units: List<UnitUi> = emptyList(),
    val saving: Boolean = false,
    val importingPhoto: Boolean = false,
    val message: String? = null,
    val closed: Boolean = false,
) { val isNew get() = itemId == 0L }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val inventory: InventoryRepository,
    private val photos: PhotoStore,
    bookings: BookingRepository,
    time: TimeProvider,
) : ViewModel() {
    private val local = MutableStateFlow(ItemEditorUiState(itemId = savedState.toRoute<ItemEditorRoute>().itemId))
    private var providerId = 0L

    init {
        viewModelScope.launch {
            providerId = catalog.providerForUser(ADMIN_USER_ID).filterNotNull().first().id
            val id = local.value.itemId
            val item = if (id == 0L) null else catalog.item(id).first()
            local.update { s ->
                s.copy(loading = false, form = item?.let { i ->
                    EditorForm(i.title, i.categoryId, i.description, i.photos, MoneyFormatter.toInput(i.dailyRate), MoneyFormatter.toInput(i.weeklyRate),
                        MoneyFormatter.toInput(i.deposit), i.lowStockThreshold, i.specs.map { SpecRow(it.key, it.value) }, i.isActive,
                        MoneyFormatter.toInput(i.unitValue), i.ownership, i.vendorId, MoneyFormatter.toInput(i.vendorCostPerDay), i.vendorReturnBy)
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

    val state = combine(local, catalog.categories(), catalog.vendors(), units) { s, cats, vendors, u -> s.copy(categories = cats, vendors = vendors, units = u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun update(transform: (EditorForm) -> EditorForm) = local.update { it.copy(form = transform(it.form)) }

    fun newCaptureUri(): Uri = photos.newCaptureUri()

    fun addPhotos(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        local.update { it.copy(importingPhoto = true) }
        val keys = uris.mapNotNull { photos.import(it) }
        local.update {
            it.copy(
                importingPhoto = false,
                form = it.form.copy(photos = (it.form.photos + keys).distinct().take(MAX_PHOTOS)),
                errors = if (keys.isNotEmpty()) it.errors - ItemField.PHOTOS else it.errors,
                message = if (keys.size < uris.size) "Couldn't read ${uris.size - keys.size} photo(s). Try another." else it.message,
            )
        }
    }

    fun removePhoto(key: String) = update { it.copy(photos = it.photos - key) }
    fun makeCover(key: String) = update { it.copy(photos = listOf(key) + (it.photos - key)) }

    fun addCategory(name: String) = viewModelScope.launch {
        when (val r = catalog.addCategory(name)) {
            is Outcome.Success -> update { it.copy(categoryId = r.value.id) }
            is Outcome.Failure -> local.update { it.copy(message = r.error.message()) }
        }
    }

    fun addVendor(name: String, phone: String) = viewModelScope.launch {
        when (val r = catalog.addVendor(name, phone)) {
            is Outcome.Success -> update { it.copy(vendorId = r.value.id) }
            is Outcome.Failure -> local.update { it.copy(message = r.error.message()) }
        }
    }

    fun save() = viewModelScope.launch {
        val s = local.value
        if (s.saving) return@launch
        local.update { it.copy(saving = true) }
        val f = s.form
        val borrowed = f.ownership == Ownership.BORROWED
        val draft = ItemDraft(
            id = s.itemId, providerId = providerId, title = f.title, categoryId = f.categoryId, description = f.description,
            photos = f.photos, dailyRate = MoneyFormatter.parseRupees(f.daily), weeklyRate = MoneyFormatter.parseRupees(f.weekly),
            deposit = MoneyFormatter.parseRupees(f.deposit), specs = f.specs.map { it.key to it.value },
            lowStockThreshold = f.threshold, isActive = f.isActive,
            unitValue = MoneyFormatter.parseRupees(f.unitValue.ifBlank { "0" }), ownership = f.ownership,
            vendorId = f.vendorId.takeIf { borrowed },
            vendorCostPerDay = if (borrowed) MoneyFormatter.parseRupees(f.vendorCost.ifBlank { "0" }) else 0,
            vendorReturnBy = f.vendorReturnBy.takeIf { borrowed },
        )
        when (val r = catalog.saveItem(draft)) {
            is Outcome.Success -> local.update {
                if (it.isNew) it.copy(saving = false, errors = emptySet(), itemId = r.value, message = "Item added with 1 unit. Add more units in the Units tab.")
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

    private companion object { const val MAX_PHOTOS = 6 }
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

@Composable
private fun PhotosSection(state: ItemEditorUiState, vm: ItemEditorViewModel) {
    val photos = state.form.photos
    val missing = ItemField.PHOTOS in state.errors
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { vm.addPhotos(it) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        pendingCapture?.let { if (saved) vm.addPhotos(listOf(Uri.parse(it))) }
        pendingCapture = null
    }
    Text("Photos", style = MaterialTheme.typography.titleSmall, color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    Text(
        if (missing) "Add at least one photo of the product." else "The first photo is the cover customers see. Tap a photo to make it the cover.",
        style = MaterialTheme.typography.bodySmall, color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.forEachIndexed { i, key ->
            Box(Modifier.size(96.dp).clip(MaterialTheme.shapes.small).clickable { vm.makeCover(key) }) {
                ItemArt(key, Modifier.fillMaxSize(), iconSize = 32.dp)
                if (i == 0) Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)) {
                    Text("Cover", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable { vm.removePhoto(key) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Remove photo", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
        }
        if (state.importingPhoto) Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            Modifier.weight(1f), shape = MaterialTheme.shapes.small, enabled = !state.importingPhoto,
        ) { Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Gallery") }
        OutlinedButton(
            onClick = { vm.newCaptureUri().let { pendingCapture = it.toString(); camera.launch(it) } },
            Modifier.weight(1f), shape = MaterialTheme.shapes.small, enabled = !state.importingPhoto,
        ) { Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Camera") }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DetailsForm(state: ItemEditorUiState, vm: ItemEditorViewModel) {
    val f = state.form
    val e = state.errors
    var newCategory by remember { mutableStateOf(false) }
    var newVendor by remember { mutableStateOf(false) }
    var pickReturnDate by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PhotosSection(state, vm)
        Field("Product name", f.title, ItemField.TITLE in e, "What customers will search for") { v -> vm.update { it.copy(title = v) } }
        Text("Category", style = MaterialTheme.typography.titleSmall, color = if (ItemField.CATEGORY in e) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.categories.forEach { c -> FilterChip(f.categoryId == c.id, { vm.update { it.copy(categoryId = c.id) } }, { Text(c.name) }) }
            AssistChip(onClick = { newCategory = true }, label = { Text("New category") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
        }
        OutlinedTextField(f.description, { v -> vm.update { it.copy(description = v) } }, label = { Text("Description") }, minLines = 3, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())

        Text("Rental pricing", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Per day ₹", f.daily, ItemField.DAILY_RATE in e, "Also the late fee per day", Modifier.weight(1f), number = true) { v -> vm.update { it.copy(daily = v) } }
            Field("Per week ₹", f.weekly, ItemField.WEEKLY_RATE in e, "≤ 7 × daily", Modifier.weight(1f), number = true) { v -> vm.update { it.copy(weekly = v) } }
        }
        Field("Advance ₹", f.deposit, ItemField.DEPOSIT in e, "Collected at pickup, refunded on return", number = true) { v -> vm.update { it.copy(deposit = v) } }

        Text("Stock & value", style = MaterialTheme.typography.titleMedium)
        Field("Value per unit ₹", f.unitValue, ItemField.UNIT_VALUE in e, "Purchase or replacement cost, for inventory worth", number = true) { v -> vm.update { it.copy(unitValue = v) } }
        Text("Source", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Ownership.entries.forEachIndexed { i, o ->
                SegmentedButton(f.ownership == o, { vm.update { it.copy(ownership = o) } }, SegmentedButtonDefaults.itemShape(i, Ownership.entries.size)) {
                    Text(if (o == Ownership.OWNED) "Owned" else "Borrowed from vendor")
                }
            }
        }
        if (f.ownership == Ownership.BORROWED) {
            Text("Vendor", style = MaterialTheme.typography.titleSmall, color = if (ItemField.VENDOR in e) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.vendors.forEach { v -> FilterChip(f.vendorId == v.id, { vm.update { it.copy(vendorId = v.id) } }, { Text(v.name) }) }
                AssistChip(onClick = { newVendor = true }, label = { Text("New vendor") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
            }
            state.vendors.firstOrNull { it.id == f.vendorId }?.phone?.takeIf { it.isNotBlank() }?.let {
                Text("Contact: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Field("Vendor cost per unit per day ₹", f.vendorCost, ItemField.VENDOR_COST in e, "What you pay the vendor", number = true) { v -> vm.update { it.copy(vendorCost = v) } }
            OutlinedCard(onClick = { pickReturnDate = true }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Event, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Return to vendor by", style = MaterialTheme.typography.titleSmall)
                        Text(f.vendorReturnBy?.let(DateFormats::full) ?: "Not set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (f.vendorReturnBy != null) IconButton(onClick = { vm.update { it.copy(vendorReturnBy = null) } }) { Icon(Icons.Rounded.Close, "Clear date") }
                }
            }
        }
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
        PrimaryButton(if (state.isNew) "Create item" else "Save changes", vm::save, Modifier.fillMaxWidth(), loading = state.saving, enabled = !state.importingPhoto)
        Spacer(Modifier.height(24.dp))
    }
    if (newCategory) NameDialog("New category", "Category name", null, onDismiss = { newCategory = false }) { name, _ -> newCategory = false; vm.addCategory(name) }
    if (newVendor) NameDialog("New vendor", "Vendor name", "Phone (optional)", onDismiss = { newVendor = false }) { name, phone -> newVendor = false; vm.addVendor(name, phone) }
    if (pickReturnDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = f.vendorReturnBy?.let(DateMillis::toUtcMillis))
        DatePickerDialog(
            onDismissRequest = { pickReturnDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { ms -> vm.update { it.copy(vendorReturnBy = DateMillis.toLocalDate(ms)) } }
                    pickReturnDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickReturnDate = false }) { Text("Cancel") } },
        ) { DatePicker(picker) }
    }
}

@Composable
private fun NameDialog(title: String, nameLabel: String, phoneLabel: String?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(nameLabel) }, singleLine = true, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth())
                if (phoneLabel != null) OutlinedTextField(
                    phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16) }, label = { Text(phoneLabel) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, phone) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
