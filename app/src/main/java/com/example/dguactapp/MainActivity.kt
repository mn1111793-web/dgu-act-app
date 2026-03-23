package com.example.dguactapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dguactapp.ui.theme.DguActAppTheme

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
        mutableStateListOf<ActRecord>().apply {
            addAll(ActStorage.loadActs(context))
        }
    }
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Start.name) }
    var selectedActId by rememberSaveable { mutableLongStateOf(-1L) }
    val selectedAct = savedActs.firstOrNull { it.id == selectedActId }

    when (AppScreen.valueOf(currentScreen)) {
        AppScreen.Start -> StartScreen(
            onNewActClick = { currentScreen = AppScreen.NewAct.name },
            onActsListClick = { currentScreen = AppScreen.ActsList.name }
        )

        AppScreen.NewAct -> NewActScreen(
            onBackClick = { currentScreen = AppScreen.Start.name },
            onSaveClick = { act ->
                ActStorage.saveAct(context, act)
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
                onBackClick = { currentScreen = AppScreen.ActsList.name }
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
            Spacer(modifier = Modifier.height(12.dp))
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
    onBackClick: () -> Unit = {},
    onSaveClick: (ActRecord) -> Unit = {}
) {
    val context = LocalContext.current
    var requestNumber by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var customer by rememberSaveable { mutableStateOf("") }
    var customerAddress by rememberSaveable { mutableStateOf("") }
    var equipmentName by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var serialNumber by rememberSaveable { mutableStateOf("") }
    var operatingTime by rememberSaveable { mutableStateOf("") }
    var completeness by rememberSaveable { mutableStateOf("") }
    var externalCondition by rememberSaveable { mutableStateOf("") }
    var malfunctionDescription by rememberSaveable { mutableStateOf("") }

    BackHandler(onBack = onBackClick)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.new_act_screen_title),
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
                        onValueChange = { requestNumber = it },
                        label = stringResource(id = R.string.field_request_number),
                        placeholder = stringResource(id = R.string.field_request_number_placeholder)
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
                    FormTextField(
                        value = equipmentName,
                        onValueChange = { equipmentName = it },
                        label = stringResource(id = R.string.field_equipment_name),
                        placeholder = stringResource(id = R.string.field_equipment_name_placeholder)
                    )
                    FormTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = stringResource(id = R.string.field_model),
                        placeholder = stringResource(id = R.string.field_model_placeholder)
                    )
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
                        placeholder = stringResource(id = R.string.field_operating_time_placeholder)
                    )
                    FormTextField(
                        value = completeness,
                        onValueChange = { completeness = it },
                        label = stringResource(id = R.string.field_completeness),
                        placeholder = stringResource(id = R.string.field_completeness_placeholder),
                        minLines = 2
                    )
                    FormTextField(
                        value = externalCondition,
                        onValueChange = { externalCondition = it },
                        label = stringResource(id = R.string.field_external_condition),
                        placeholder = stringResource(id = R.string.field_external_condition_placeholder),
                        minLines = 2
                    )
                    FormTextField(
                        value = malfunctionDescription,
                        onValueChange = { malfunctionDescription = it },
                        label = stringResource(id = R.string.field_malfunction_description),
                        placeholder = stringResource(id = R.string.field_malfunction_description_placeholder),
                        minLines = 4
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        val act = ActRecord(
                            id = System.currentTimeMillis(),
                            requestNumber = requestNumber.ifBlank {
                                context.getString(R.string.default_request_number)
                            },
                            date = date.ifBlank { context.getString(R.string.default_date_value) },
                            customer = customer.ifBlank {
                                context.getString(R.string.default_customer_value)
                            },
                            customerAddress = customerAddress,
                            equipmentName = equipmentName,
                            model = model,
                            serialNumber = serialNumber,
                            operatingTime = operatingTime,
                            completeness = completeness,
                            externalCondition = externalCondition,
                            malfunctionDescription = malfunctionDescription
                        )
                        onSaveClick(act)
                        Toast.makeText(
                            context,
                            context.getString(R.string.save_success_message),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActsListScreen(
    acts: List<ActRecord>,
    onBackClick: () -> Unit = {},
    onActClick: (ActRecord) -> Unit = {}
) {
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
                            Text(
                                text = act.requestNumber,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            InfoLine(
                                title = stringResource(id = R.string.field_customer),
                                value = act.customer
                            )
                            InfoLine(
                                title = stringResource(id = R.string.field_date),
                                value = act.date
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
    onBackClick: () -> Unit = {}
) {
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
                        Text(
                            text = act.requestNumber,
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
                    InfoLine(stringResource(id = R.string.field_equipment_name), act.equipmentName)
                    InfoLine(stringResource(id = R.string.field_model), act.model)
                    InfoLine(stringResource(id = R.string.field_serial_number), act.serialNumber)
                    InfoLine(stringResource(id = R.string.field_operating_time), act.operatingTime)
                }
            )
            DetailCard(
                title = stringResource(id = R.string.act_diagnostic_info_title),
                content = {
                    InfoLine(stringResource(id = R.string.field_completeness), act.completeness)
                    InfoLine(stringResource(id = R.string.field_external_condition), act.externalCondition)
                    InfoLine(
                        stringResource(id = R.string.field_malfunction_description),
                        act.malfunctionDescription
                    )
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
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label) },
        placeholder = { Text(text = placeholder) },
        shape = RoundedCornerShape(18.dp),
        minLines = minLines,
        singleLine = minLines == 1
    )
}

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
        NewActScreen()
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
                    requestNumber = "2026-015",
                    date = "22.03.2026",
                    customer = "ООО «Энерго Сервис»",
                    customerAddress = "г. Москва, ул. Центральная, д. 10",
                    equipmentName = "ДГУ 100 кВт",
                    model = "DG-100",
                    serialNumber = "SN-12345",
                    operatingTime = "1450 моточасов",
                    completeness = "Базовая комплектация",
                    externalCondition = "Без серьёзных повреждений",
                    malfunctionDescription = "Требуется диагностика системы запуска"
                )
            )
        )
    }
}
