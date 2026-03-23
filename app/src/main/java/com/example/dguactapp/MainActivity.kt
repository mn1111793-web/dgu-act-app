package com.example.dguactapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dguactapp.ui.theme.DguActAppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AppScreen {
    Start,
    NewAct,
    ActsList,
    ActDetails
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DguActAppTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DguActApp()
                }
            }
        }
    }
}

@Composable
private fun DguActApp() {
    val context = LocalContext.current
    val savedActs = remember {
        androidx.compose.runtime.mutableStateListOf<ActRecord>().apply {
            addAll(ActStorage.loadActs(context))
        }
    }
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Start.name) }
    var selectedActId by rememberSaveable { mutableLongStateOf(-1L) }
    val selectedAct = savedActs.firstOrNull { it.id == selectedActId }

    when (AppScreen.valueOf(currentScreen)) {
        AppScreen.Start -> StartScreen(
            onNewActClick = {
                selectedActId = -1L
                currentScreen = AppScreen.NewAct.name
            },
            onActsListClick = { currentScreen = AppScreen.ActsList.name }
        )

        AppScreen.NewAct -> NewActScreen(
            existingAct = selectedAct,
            existingActs = savedActs,
            onBackClick = {
                currentScreen = if (selectedAct != null) {
                    AppScreen.ActDetails.name
                } else {
                    AppScreen.Start.name
                }
            },
            onSaveClick = { act ->
                ActStorage.upsertAct(context, act)
                val existingIndex = savedActs.indexOfFirst { it.id == act.id }
                if (existingIndex >= 0) {
                    savedActs.removeAt(existingIndex)
                }
                savedActs.add(0, act)
                selectedActId = act.id
                currentScreen = AppScreen.ActDetails.name
            }
        )

        AppScreen.ActsList -> ActsListScreen(
            acts = savedActs,
            onBackClick = { currentScreen = AppScreen.Start.name },
            onActClick = { act ->
                selectedActId = act.id
                currentScreen = AppScreen.ActDetails.name
            }
        )

        AppScreen.ActDetails -> if (selectedAct != null) {
            ActDetailsScreen(
                act = selectedAct,
                onBackClick = { currentScreen = AppScreen.ActsList.name },
                onEditClick = { currentScreen = AppScreen.NewAct.name }
            )
        } else {
            ActsListScreen(
                acts = savedActs,
                onBackClick = { currentScreen = AppScreen.Start.name },
                onActClick = { act ->
                    selectedActId = act.id
                    currentScreen = AppScreen.ActDetails.name
                }
            )
        }
    }
}

@Composable
fun StartScreen(
    onNewActClick: () -> Unit = {},
    onActsListClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.app_section_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(id = R.string.start_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(id = R.string.start_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onNewActClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = stringResource(id = R.string.new_act_button),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onActsListClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.acts_list_button),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Text(
            text = stringResource(id = R.string.bottom_note),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewActScreen(
    existingAct: ActRecord? = null,
    existingActs: List<ActRecord> = emptyList(),
    onBackClick: () -> Unit = {},
    onSaveClick: (ActRecord) -> Unit = {}
) {
    val context = LocalContext.current
    val today = remember { currentDateDisplay() }
    val requestDatePart = remember { currentDateForRequestNumber() }
    val nextSequence = remember(existingActs) { ActStorage.nextSequence(existingActs) }

    var equipmentCode by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.equipmentCode.orEmpty())
    }
    var equipmentName by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.equipmentName.orEmpty())
    }
    var date by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.date?.ifBlank { today } ?: today)
    }
    var customer by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.customer.orEmpty())
    }
    var customerAddress by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.customerAddress.orEmpty())
    }
    var brandSelection by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.brand.orEmpty())
    }
    var customBrand by rememberSaveable(existingAct?.id) { mutableStateOf("") }
    var modelSelection by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.model.orEmpty())
    }
    var customModel by rememberSaveable(existingAct?.id) { mutableStateOf("") }
    var serialNumber by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.serialNumber.orEmpty())
    }
    var operatingTime by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.operatingTime.orEmpty())
    }
    var diagnosisType by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.diagnosisType ?: DiagnosisType.Primary)
    }
    val checklistItems = rememberSaveable(existingAct?.id, saver = ChecklistStateSaver) {
        DiagnosticChecklistCatalog.stateFor(
            type = diagnosisType,
            savedItems = existingAct?.checklistItems.orEmpty(),
            legacyAct = existingAct
        ).toMutableStateList()
    }
    var preliminaryConclusion by rememberSaveable(existingAct?.id) {
        mutableStateOf(
            existingAct?.preliminaryConclusion?.ifBlank {
                DiagnosticChecklistCatalog.primaryConclusionFromLegacy(existingAct)
            }.orEmpty()
        )
    }
    var rootCause by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.rootCause.orEmpty())
    }
    var requiredWorks by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.requiredWorks.orEmpty())
    }

    val brandOptions = remember(equipmentCode) {
        if (equipmentCode.isBlank()) emptyList() else EquipmentCatalog.brandOptions(equipmentCode)
    }
    val selectedEquipment = remember(equipmentCode) { EquipmentCatalog.findEquipment(equipmentCode) }
    val isExistingBrandCustom = existingAct != null &&
        existingAct.brand.isNotBlank() &&
        existingAct.brand !in EquipmentCatalog.brandOptions(existingAct.equipmentCode)
    val isExistingModelCustom = existingAct != null &&
        existingAct.model.isNotBlank() &&
        existingAct.model !in EquipmentCatalog.modelOptions(existingAct.equipmentCode, existingAct.brand)

    LaunchedEffect(existingAct?.id) {
        if (existingAct != null && customBrand.isBlank() && isExistingBrandCustom) {
            customBrand = existingAct.brand
            brandSelection = EquipmentCatalog.OTHER_OPTION
        }
        if (existingAct != null && customModel.isBlank() && isExistingModelCustom) {
            customModel = existingAct.model
            modelSelection = EquipmentCatalog.OTHER_OPTION
        }
    }

    LaunchedEffect(equipmentCode, selectedEquipment?.name) {
        if (equipmentCode.isNotBlank() && equipmentName != selectedEquipment?.name) {
            equipmentName = selectedEquipment?.name.orEmpty()
        }
    }

    LaunchedEffect(equipmentCode, brandSelection, brandOptions) {
        if (equipmentCode.isNotBlank() && brandSelection.isNotBlank() &&
            brandSelection != EquipmentCatalog.OTHER_OPTION && brandSelection !in brandOptions
        ) {
            brandSelection = ""
            modelSelection = ""
            customBrand = ""
            customModel = ""
        }
    }

    val modelOptions = remember(equipmentCode, brandSelection) {
        when {
            equipmentCode.isBlank() -> emptyList()
            brandSelection.isBlank() -> listOf(EquipmentCatalog.OTHER_OPTION)
            brandSelection == EquipmentCatalog.OTHER_OPTION -> listOf(EquipmentCatalog.OTHER_OPTION)
            else -> EquipmentCatalog.modelOptions(equipmentCode, brandSelection)
        }
    }

    LaunchedEffect(brandSelection, modelSelection, modelOptions) {
        if (brandSelection != EquipmentCatalog.OTHER_OPTION &&
            modelSelection.isNotBlank() &&
            modelSelection != EquipmentCatalog.OTHER_OPTION &&
            modelSelection !in modelOptions
        ) {
            modelSelection = ""
            customModel = ""
        }
    }

    val requestNumber = remember(existingAct?.requestNumber, equipmentCode, nextSequence, requestDatePart) {
        existingAct?.requestNumber.orEmpty().ifBlank {
            if (equipmentCode.isBlank()) "" else "$equipmentCode-$nextSequence-$requestDatePart"
        }
    }

    val resolvedBrand = if (brandSelection == EquipmentCatalog.OTHER_OPTION) customBrand else brandSelection
    val resolvedModel = if (modelSelection == EquipmentCatalog.OTHER_OPTION) customModel else modelSelection

    BackHandler(onBack = onBackClick)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (existingAct == null) {
                                stringResource(id = R.string.new_act_screen_title)
                            } else {
                                stringResource(id = R.string.edit_act_screen_title)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.new_act_screen_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(text = stringResource(id = R.string.back_button))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.form_intro_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.form_intro_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FormTextField(
                        value = requestNumber,
                        onValueChange = {},
                        label = stringResource(id = R.string.field_request_number),
                        placeholder = stringResource(id = R.string.field_request_number_placeholder),
                        readOnly = true
                    )
                    FormTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = stringResource(id = R.string.field_date),
                        placeholder = stringResource(id = R.string.field_date_placeholder)
                    )
                    FormTextField(
                        value = customer,
                        onValueChange = { customer = it },
                        label = stringResource(id = R.string.field_customer),
                        placeholder = stringResource(id = R.string.field_customer_placeholder)
                    )
                    FormTextField(
                        value = customerAddress,
                        onValueChange = { customerAddress = it },
                        label = stringResource(id = R.string.field_customer_address),
                        placeholder = stringResource(id = R.string.field_customer_address_placeholder),
                        minLines = 2
                    )
                    DropdownField(
                        value = selectedEquipment?.displayName.orEmpty(),
                        options = EquipmentCatalog.equipmentTypes.map { it.displayName },
                        label = stringResource(id = R.string.field_equipment_code),
                        placeholder = stringResource(id = R.string.field_equipment_code_placeholder),
                        onOptionSelected = { selectedDisplayName ->
                            val equipment = EquipmentCatalog.equipmentTypes.firstOrNull {
                                it.displayName == selectedDisplayName
                            } ?: return@DropdownField
                            equipmentCode = equipment.code
                            equipmentName = equipment.name
                            brandSelection = ""
                            modelSelection = ""
                            customBrand = ""
                            customModel = ""
                        }
                    )
                    FormTextField(
                        value = equipmentName,
                        onValueChange = {},
                        label = stringResource(id = R.string.field_equipment_name),
                        placeholder = stringResource(id = R.string.field_equipment_name_placeholder),
                        readOnly = true
                    )
                    DropdownField(
                        value = brandSelection,
                        options = brandOptions,
                        label = stringResource(id = R.string.field_brand),
                        placeholder = stringResource(id = R.string.field_brand_placeholder),
                        enabled = equipmentCode.isNotBlank(),
                        onOptionSelected = { selectedBrand ->
                            brandSelection = selectedBrand
                            modelSelection = ""
                            customBrand = if (selectedBrand == EquipmentCatalog.OTHER_OPTION) customBrand else ""
                            customModel = ""
                        }
                    )
                    if (brandSelection == EquipmentCatalog.OTHER_OPTION) {
                        FormTextField(
                            value = customBrand,
                            onValueChange = { customBrand = it },
                            label = stringResource(id = R.string.field_custom_brand),
                            placeholder = stringResource(id = R.string.field_custom_brand_placeholder)
                        )
                    }
                    DropdownField(
                        value = modelSelection,
                        options = modelOptions,
                        label = stringResource(id = R.string.field_model),
                        placeholder = stringResource(id = R.string.field_model_placeholder),
                        enabled = equipmentCode.isNotBlank(),
                        onOptionSelected = { selectedModel ->
                            modelSelection = selectedModel
                            customModel = if (selectedModel == EquipmentCatalog.OTHER_OPTION) customModel else ""
                        }
                    )
                    if (modelSelection == EquipmentCatalog.OTHER_OPTION) {
                        FormTextField(
                            value = customModel,
                            onValueChange = { customModel = it },
                            label = stringResource(id = R.string.field_custom_model),
                            placeholder = stringResource(id = R.string.field_custom_model_placeholder)
                        )
                    }
                    FormTextField(
                        value = serialNumber,
                        onValueChange = { serialNumber = it },
                        label = stringResource(id = R.string.field_serial_number),
                        placeholder = stringResource(id = R.string.field_serial_number_placeholder)
                    )
                    FormTextField(
                        value = operatingTime,
                        onValueChange = { operatingTime = it },
                        label = stringResource(id = R.string.field_operating_time),
                        placeholder = stringResource(id = R.string.field_operating_time_placeholder),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                }
            }

            item {
                DetailCard(title = stringResource(id = R.string.diagnosis_type_title)) {
                    Text(
                        text = stringResource(id = R.string.diagnosis_type_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DiagnosisTypeSelector(
                        selectedType = diagnosisType,
                        onTypeSelected = { selectedType ->
                            if (selectedType == diagnosisType) return@DiagnosisTypeSelector
                            diagnosisType = selectedType
                            checklistItems.replaceWith(
                                DiagnosticChecklistCatalog.stateFor(
                                    type = selectedType,
                                    savedItems = checklistItems.toList(),
                                    legacyAct = existingAct
                                )
                            )
                        }
                    )
                }
            }

            items(DiagnosticChecklistCatalog.sectionsFor(diagnosisType), key = { it.title }) { section ->
                ChecklistSectionEditor(
                    section = section,
                    items = checklistItems,
                    onItemChange = { updatedItem ->
                        val index = checklistItems.indexOfFirst { it.key == updatedItem.key }
                        if (index >= 0) {
                            checklistItems[index] = updatedItem
                        }
                    }
                )
            }

            item {
                DetailCard(title = stringResource(id = R.string.preliminary_conclusion_title)) {
                    FormTextField(
                        value = preliminaryConclusion,
                        onValueChange = { preliminaryConclusion = it },
                        label = stringResource(id = R.string.preliminary_conclusion_title),
                        placeholder = stringResource(id = R.string.preliminary_conclusion_placeholder),
                        minLines = 3
                    )
                }
            }

            if (diagnosisType == DiagnosisType.Advanced) {
                item {
                    DetailCard(title = stringResource(id = R.string.advanced_result_title)) {
                        FormTextField(
                            value = rootCause,
                            onValueChange = { rootCause = it },
                            label = stringResource(id = R.string.root_cause_title),
                            placeholder = stringResource(id = R.string.root_cause_placeholder),
                            minLines = 3
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FormTextField(
                            value = requiredWorks,
                            onValueChange = { requiredWorks = it },
                            label = stringResource(id = R.string.required_works_title),
                            placeholder = stringResource(id = R.string.required_works_placeholder),
                            minLines = 3
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val completenessSummary = checklistItems.findCommentByKey("visual_completeness")
                        val externalConditionSummary = listOf(
                            checklistItems.findCommentByKey("visual_casing_condition"),
                            checklistItems.findCommentByKey("visual_damage_traces")
                        ).filter { it.isNotBlank() }.joinToString("; ")
                        val act = ActRecord(
                            id = existingAct?.id ?: System.currentTimeMillis(),
                            requestNumber = requestNumber,
                            date = date.ifBlank { context.getString(R.string.default_date_value) },
                            customer = customer.ifBlank {
                                context.getString(R.string.default_customer_value)
                            },
                            customerAddress = customerAddress,
                            equipmentCode = equipmentCode,
                            equipmentName = equipmentName,
                            brand = resolvedBrand,
                            model = resolvedModel,
                            serialNumber = serialNumber,
                            operatingTime = operatingTime,
                            completeness = completenessSummary,
                            externalCondition = externalConditionSummary,
                            malfunctionDescription = preliminaryConclusion,
                            diagnosisType = diagnosisType,
                            checklistItems = checklistItems.toList(),
                            preliminaryConclusion = preliminaryConclusion,
                            rootCause = if (diagnosisType == DiagnosisType.Advanced) rootCause else "",
                            requiredWorks = if (diagnosisType == DiagnosisType.Advanced) requiredWorks else ""
                        )
                        onSaveClick(act)
                        Toast.makeText(
                            context,
                            if (existingAct == null) {
                                context.getString(R.string.save_success_message)
                            } else {
                                context.getString(R.string.update_success_message)
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.save_button),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DiagnosisTypeSelector(
    selectedType: DiagnosisType,
    onTypeSelected: (DiagnosisType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DiagnosisType.values().forEach { type ->
            val isSelected = type == selectedType
            OutlinedButton(
                onClick = { onTypeSelected(type) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = type.title,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ChecklistSectionEditor(
    section: ChecklistSectionDefinition,
    items: List<ChecklistItemState>,
    onItemChange: (ChecklistItemState) -> Unit
) {
    val sectionItems = section.items.mapNotNull { definition ->
        items.firstOrNull { it.key == definition.key }
    }

    DetailCard(title = section.title) {
        ChecklistTableHeader()
        sectionItems.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            ChecklistEditableRow(
                item = item,
                onItemChange = onItemChange
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActsListScreen(
    acts: List<ActRecord>,
    onBackClick: () -> Unit = {},
    onActClick: (ActRecord) -> Unit = {}
) {
    val context = LocalContext.current
    BackHandler(onBack = onBackClick)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.acts_list_screen_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.acts_list_screen_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(text = stringResource(id = R.string.back_button))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (acts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.empty_list_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = R.string.empty_list_text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = acts, key = { it.id }) { act ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onActClick(act) },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val listRequestNumber = act.requestNumber.ifBlank {
                                context.getString(R.string.default_request_number)
                            }
                            Text(
                                text = listRequestNumber,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            InfoLine(
                                title = stringResource(id = R.string.field_date),
                                value = act.date
                            )
                            InfoLine(
                                title = stringResource(id = R.string.field_customer),
                                value = act.customer
                            )
                            InfoLine(
                                title = stringResource(id = R.string.field_equipment_code),
                                value = act.equipmentCode
                            )
                            InfoLine(
                                title = stringResource(id = R.string.field_equipment_name),
                                value = act.equipmentName
                            )
                            InfoLine(
                                title = stringResource(id = R.string.diagnosis_type_title),
                                value = act.diagnosisType.title
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActDetailsScreen(
    act: ActRecord,
    onBackClick: () -> Unit = {},
    onEditClick: () -> Unit = {}
) {
    val context = LocalContext.current
    BackHandler(onBack = onBackClick)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.act_details_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val detailsRequestNumber = act.requestNumber.ifBlank {
                            context.getString(R.string.default_request_number)
                        }
                        Text(
                            text = detailsRequestNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(text = stringResource(id = R.string.back_button))
                    }
                },
                actions = {
                    TextButton(onClick = onEditClick) {
                        Text(text = stringResource(id = R.string.edit_button))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailCard(
                title = stringResource(id = R.string.act_main_info_title),
                content = {
                    InfoLine(stringResource(id = R.string.field_request_number), act.requestNumber)
                    InfoLine(stringResource(id = R.string.field_date), act.date)
                    InfoLine(stringResource(id = R.string.field_customer), act.customer)
                    InfoLine(stringResource(id = R.string.field_customer_address), act.customerAddress)
                }
            )
            DetailCard(
                title = stringResource(id = R.string.act_equipment_info_title),
                content = {
                    InfoLine(stringResource(id = R.string.field_equipment_code), act.equipmentCode)
                    InfoLine(stringResource(id = R.string.field_equipment_name), act.equipmentName)
                    InfoLine(stringResource(id = R.string.field_brand), act.brand)
                    InfoLine(stringResource(id = R.string.field_model), act.model)
                    InfoLine(stringResource(id = R.string.field_serial_number), act.serialNumber)
                    InfoLine(stringResource(id = R.string.field_operating_time), act.operatingTime)
                }
            )
            DetailCard(
                title = stringResource(id = R.string.act_diagnostic_info_title),
                content = {
                    InfoLine(stringResource(id = R.string.diagnosis_type_title), act.diagnosisType.title)
                    DiagnosticChecklistCatalog.sectionsFor(act.diagnosisType).forEach { section ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ChecklistTableHeader()
                        section.items.mapNotNull { definition ->
                            act.checklistItems.firstOrNull { it.key == definition.key }
                        }.forEachIndexed { index, item ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            ChecklistReadonlyRow(item = item)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    InfoLine(
                        stringResource(id = R.string.preliminary_conclusion_title),
                        act.preliminaryConclusion
                    )
                    if (act.diagnosisType == DiagnosisType.Advanced) {
                        InfoLine(stringResource(id = R.string.root_cause_title), act.rootCause)
                        InfoLine(stringResource(id = R.string.required_works_title), act.requiredWorks)
                    }
                }
            )
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun ChecklistTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(id = R.string.checklist_column_item),
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(id = R.string.checklist_column_checked),
            modifier = Modifier.weight(0.6f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(id = R.string.checklist_column_faulty),
            modifier = Modifier.weight(0.85f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(id = R.string.checklist_column_comment),
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ChecklistEditableRow(
    item: ChecklistItemState,
    onItemChange: (ChecklistItemState) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = item.title,
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(
            modifier = Modifier.weight(0.6f),
            contentAlignment = Alignment.Center
        ) {
            Checkbox(
                checked = item.checked,
                onCheckedChange = { onItemChange(item.copy(checked = it)) }
            )
        }
        Box(
            modifier = Modifier.weight(0.85f),
            contentAlignment = Alignment.Center
        ) {
            Checkbox(
                checked = item.faulty,
                onCheckedChange = { onItemChange(item.copy(faulty = it)) }
            )
        }
        OutlinedTextField(
            value = item.comment,
            onValueChange = { onItemChange(item.copy(comment = it)) },
            modifier = Modifier.weight(1.4f),
            placeholder = { Text(text = stringResource(id = R.string.checklist_comment_placeholder)) },
            shape = RoundedCornerShape(14.dp),
            singleLine = false,
            minLines = 2
        )
    }
}

@Composable
private fun ChecklistReadonlyRow(item: ChecklistItemState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = item.title,
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (item.checked) "Да" else "—",
            modifier = Modifier.weight(0.6f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (item.faulty) "Да" else "—",
            modifier = Modifier.weight(0.85f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = item.comment.ifBlank { stringResource(id = R.string.not_filled_value) },
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoLine(
    title: String,
    value: String
) {
    val resolvedValue = value.ifBlank { stringResource(id = R.string.not_filled_value) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$title:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = resolvedValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    minLines: Int = 1,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label) },
        placeholder = { Text(text = placeholder) },
        shape = RoundedCornerShape(18.dp),
        minLines = minLines,
        singleLine = minLines == 1,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions
    )
}

@Composable
private fun DropdownField(
    value: String,
    options: List<String>,
    label: String,
    placeholder: String,
    enabled: Boolean = true,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val canOpen = enabled && options.isNotEmpty()
    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
        expanded -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val valueColor = if (value.isBlank()) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            }
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        width = 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable(
                        enabled = canOpen,
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        expanded = !expanded
                    },
                color = containerColor,
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = value.ifBlank { placeholder },
                        color = valueColor,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (expanded) "▲" else "▼",
                        color = if (canOpen) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.94f)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun List<ChecklistItemState>.findCommentByKey(key: String): String =
    firstOrNull { it.key == key }?.comment.orEmpty()

private fun currentDateDisplay(): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

private fun currentDateForRequestNumber(): String =
    SimpleDateFormat("ddMMyy", Locale.getDefault()).format(Date())

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun StartScreenPreview() {
    DguActAppTheme(darkTheme = false) {
        StartScreen()
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun NewActScreenPreview() {
    DguActAppTheme(darkTheme = false) {
        NewActScreen(
            existingAct = ActRecord(
                id = 1L,
                requestNumber = "DGU-18-230326",
                date = "23.03.2026",
                customer = "ООО «Энерго Сервис»",
                customerAddress = "г. Москва, ул. Центральная, д. 10",
                equipmentCode = "DGU",
                equipmentName = "дизельный генератор",
                brand = "FG Wilson",
                model = "P110-3",
                serialNumber = "SN-12345",
                operatingTime = "1450 моточасов",
                completeness = "Комплект в норме",
                externalCondition = "Без серьёзных повреждений",
                malfunctionDescription = "Требуется диагностика системы запуска",
                diagnosisType = DiagnosisType.Advanced,
                checklistItems = DiagnosticChecklistCatalog.stateFor(DiagnosisType.Advanced).map { item ->
                    when (item.key) {
                        "visual_external_inspection" -> item.copy(checked = true)
                        "startup_error_indication" -> item.copy(checked = true, faulty = true, comment = "Горит аварийный индикатор")
                        "advanced_measurements" -> item.copy(checked = true, comment = "Напряжение занижено")
                        else -> item
                    }
                },
                preliminaryConclusion = "Неисправность подтверждена, требуется разборка стартера.",
                rootCause = "Короткое замыкание в цепи управления стартером.",
                requiredWorks = "Замена стартера, ревизия проводки и контрольный запуск."
            )
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ActsListScreenPreview() {
    DguActAppTheme(darkTheme = false) {
        ActsListScreen(
            acts = listOf(
                ActRecord(
                    id = 1L,
                    requestNumber = "DGU-18-230326",
                    date = "23.03.2026",
                    customer = "ООО «Энерго Сервис»",
                    customerAddress = "г. Москва, ул. Центральная, д. 10",
                    equipmentCode = "DGU",
                    equipmentName = "дизельный генератор",
                    brand = "FG Wilson",
                    model = "P110-3",
                    serialNumber = "SN-12345",
                    operatingTime = "1450 моточасов",
                    completeness = "Базовая комплектация",
                    externalCondition = "Без серьёзных повреждений",
                    malfunctionDescription = "Требуется диагностика системы запуска",
                    diagnosisType = DiagnosisType.Primary,
                    checklistItems = DiagnosticChecklistCatalog.stateFor(DiagnosisType.Primary),
                    preliminaryConclusion = "Требуется первичная диагностика"
                )
            )
        )
    }
}
