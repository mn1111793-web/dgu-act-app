package com.example.dguactapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.dguactapp.ui.theme.DguActAppTheme
import java.io.BufferedInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.launch

private enum class AppScreen {
    Start,
    NewAct,
    RequestsList,
    RequestDetails,
    ActDetails
}

private enum class EquipmentTransferState(val value: String) {
    Working("в рабочем состоянии"),
    NotWorking("в нерабочем состоянии"),
    PartiallyWorking("частично в рабочем состоянии");

    companion object {
        fun fromValue(value: String): EquipmentTransferState =
            values().firstOrNull { it.value == value } ?: Working
    }
}

private const val EXECUTOR_REQUISITES =
    "Индивидуальный предприниматель Малышева Наталья Александровна, ЯНАО, г. Новый Уренгой, проспект Губкина, 21 б, кв. 52, ИНН 890411627911, ОГРНИП 318890100008958, \"ДГУ Ямал\"."

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
    val savedActs = remember { androidx.compose.runtime.mutableStateListOf<ActRecord>() }
    val savedRequests = remember { androidx.compose.runtime.mutableStateListOf<RequestRecord>() }

    LaunchedEffect(Unit) {
        savedActs.clear()
        savedActs.addAll(ActStorage.loadActs(context))
        savedRequests.clear()
        savedRequests.addAll(RequestStorage.loadRequests(context, savedActs))
    }

    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Start.name) }
    var selectedActId by rememberSaveable { mutableLongStateOf(-1L) }
    var selectedRequestId by rememberSaveable { mutableLongStateOf(-1L) }
    var requestedDocumentType by rememberSaveable { mutableStateOf(DocumentType.TransferAcceptanceAct.storageValue) }

    val selectedAct = savedActs.firstOrNull { it.id == selectedActId }
    val selectedRequest = savedRequests.firstOrNull { it.id == selectedRequestId }

    fun upsertRequest(request: RequestRecord) {
        RequestStorage.upsertRequest(context, request)
        val existingIndex = savedRequests.indexOfFirst { it.id == request.id }
        if (existingIndex >= 0) savedRequests.removeAt(existingIndex)
        savedRequests.add(0, request)
    }

    fun deleteAct(act: ActRecord) {
        val deletedAct = ActStorage.deleteAct(context, act.id) ?: return
        deletedAct.photos.forEach(PhotoStorage::deletePhoto)
        savedActs.removeAll { it.id == act.id }
        if (selectedActId == act.id) selectedActId = -1L

        val hasMoreActs = savedActs.any { it.requestId == deletedAct.requestId }
        if (!hasMoreActs) {
            RequestStorage.deleteRequest(context, deletedAct.requestId)
            savedRequests.removeAll { it.id == deletedAct.requestId }
            if (selectedRequestId == deletedAct.requestId) selectedRequestId = -1L
        }
    }

    fun deleteRequest(request: RequestRecord) {
        val requestActs = savedActs.filter { it.requestId == request.id }
        requestActs.forEach { act ->
            act.photos.forEach(PhotoStorage::deletePhoto)
            ActStorage.deleteAct(context, act.id)
        }
        savedActs.removeAll { it.requestId == request.id }
        RequestStorage.deleteRequest(context, request.id)
        savedRequests.removeAll { it.id == request.id }
        if (selectedRequestId == request.id) selectedRequestId = -1L
        if (selectedActId != -1L && requestActs.any { it.id == selectedActId }) selectedActId = -1L
    }

    when (AppScreen.valueOf(currentScreen)) {
        AppScreen.Start -> StartScreen(
            onCreateRequestClick = {
                val newRequest = RequestRecord(
                    id = System.currentTimeMillis(),
                    requestNumber = "",
                    createdAt = currentDateDisplay(),
                    date = currentDateDisplay(),
                    customer = "",
                    phone = "",
                    customerAddress = "",
                    organizationName = "",
                    organizationInn = "",
                    organizationOgrn = "",
                    organizationAddress = "",
                    organizationPhone = "",
                    customerRepresentative = "",
                    customerRepresentativePhone = "",
                    compilationPlace = "",
                    enterpriseCardUri = "",
                    equipmentCode = "",
                    equipmentName = "",
                    brand = "",
                    model = "",
                    serialNumber = ""
                )
                upsertRequest(newRequest)
                selectedRequestId = newRequest.id
                currentScreen = AppScreen.RequestDetails.name
            },
            onOpenRequestsClick = { currentScreen = AppScreen.RequestsList.name }
        )

        AppScreen.NewAct -> NewActScreen(
            existingAct = selectedAct,
            existingRequest = selectedRequest,
            existingActs = savedActs,
            initialDocumentType = DocumentType.fromStorageValue(requestedDocumentType),
            onBackClick = {
                currentScreen = if (selectedAct != null) AppScreen.ActDetails.name else AppScreen.RequestDetails.name
            },
            onSaveClick = { act, request ->
                upsertRequest(request)
                ActStorage.upsertAct(context, act)
                val existingIndex = savedActs.indexOfFirst { it.id == act.id }
                if (existingIndex >= 0) savedActs.removeAt(existingIndex)
                savedActs.add(0, act)
                selectedRequestId = request.id
                selectedActId = act.id
                currentScreen = AppScreen.ActDetails.name
            }
        )

        AppScreen.RequestsList -> RequestsListScreen(
            requests = savedRequests,
            acts = savedActs,
            onBackClick = { currentScreen = AppScreen.Start.name },
            onRequestClick = { request ->
                selectedRequestId = request.id
                currentScreen = AppScreen.RequestDetails.name
            },
            onDeleteRequest = { request -> deleteRequest(request) }
        )

        AppScreen.RequestDetails -> if (selectedRequest != null) {
            RequestDetailsScreen(
                request = selectedRequest,
                acts = savedActs.filter { it.requestId == selectedRequest.id }.sortedByDescending { it.createdAt },
                onBackClick = { currentScreen = AppScreen.RequestsList.name },
                onCreateDocument = { docType ->
                    requestedDocumentType = docType.storageValue
                    selectedActId = -1L
                    currentScreen = AppScreen.NewAct.name
                },
                onOpenAct = { act ->
                    selectedActId = act.id
                    currentScreen = AppScreen.ActDetails.name
                }
            )
        } else {
            RequestsListScreen(
                requests = savedRequests,
                acts = savedActs,
                onBackClick = { currentScreen = AppScreen.Start.name },
                onRequestClick = { request ->
                    selectedRequestId = request.id
                    currentScreen = AppScreen.RequestDetails.name
                },
                onDeleteRequest = { request -> deleteRequest(request) }
            )
        }

        AppScreen.ActDetails -> if (selectedAct != null) {
            ActDetailsScreen(
                act = selectedAct,
                onBackClick = { currentScreen = AppScreen.RequestDetails.name },
                onEditClick = {
                    selectedRequestId = selectedAct.requestId
                    requestedDocumentType = selectedAct.documentType.storageValue
                    currentScreen = AppScreen.NewAct.name
                },
                onDeleteAct = { act ->
                    deleteAct(act)
                    currentScreen = AppScreen.RequestDetails.name
                }
            )
        }
    }
}

@Composable
fun StartScreen(
    onCreateRequestClick: () -> Unit = {},
    onOpenRequestsClick: () -> Unit = {}
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
                onClick = onCreateRequestClick,
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
                    text = stringResource(id = R.string.new_request_button),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onOpenRequestsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.open_request_button),
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
    existingRequest: RequestRecord? = null,
    existingActs: List<ActRecord> = emptyList(),
    initialDocumentType: DocumentType = DocumentType.DiagnosticAct,
    onBackClick: () -> Unit = {},
    onSaveClick: (ActRecord, RequestRecord) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val today = remember { currentDateDisplay() }
    val requestDatePart = remember { currentDateForRequestNumber() }
    val nextSequence = remember(existingActs) { ActStorage.nextSequence(context, existingActs) }

    var equipmentCode by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.equipmentCode ?: existingRequest?.equipmentCode.orEmpty())
    }
    var documentType by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.documentType ?: initialDocumentType)
    }
    val linkedTransferAct = remember(existingActs, existingRequest?.id, existingAct?.requestNumber, existingRequest?.requestNumber) {
        val targetRequestNumber = existingAct?.requestNumber?.ifBlank { null }
            ?: existingRequest?.requestNumber?.ifBlank { null }
            ?: ""
        existingActs
            .asSequence()
            .filter { it.documentType == DocumentType.TransferAcceptanceAct }
            .filter {
                (existingRequest != null && it.requestId == existingRequest.id) ||
                    (targetRequestNumber.isNotBlank() && it.requestNumber == targetRequestNumber)
            }
            .maxByOrNull { it.createdAt }
    }
    var createdAt by rememberSaveable(existingAct?.id) {
        mutableStateOf(
            existingAct?.createdAt?.ifBlank { today } ?: existingRequest?.createdAt?.ifBlank { today } ?: today
        )
    }
    var equipmentName by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.equipmentName ?: existingRequest?.equipmentName.orEmpty())
    }
    var date by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.date?.ifBlank { today } ?: existingRequest?.date?.ifBlank { today } ?: today)
    }
    var customer by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.customer ?: existingRequest?.customer.orEmpty())
    }
    var phone by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.phone ?: existingRequest?.phone.orEmpty())
    }
    var customerAddress by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.customerAddress ?: existingRequest?.customerAddress.orEmpty())
    }
    var organizationName by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.organizationName ?: existingRequest?.organizationName.orEmpty())
    }
    var organizationInn by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.organizationInn ?: existingRequest?.organizationInn.orEmpty())
    }
    var organizationOgrn by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.organizationOgrn ?: existingRequest?.organizationOgrn.orEmpty())
    }
    var organizationAddress by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.organizationAddress ?: existingRequest?.organizationAddress.orEmpty())
    }
    var organizationPhone by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.organizationPhone ?: existingRequest?.organizationPhone.orEmpty())
    }
    var compilationPlace by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.compilationPlace ?: existingRequest?.compilationPlace.orEmpty())
    }
    var enterpriseCardUri by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingRequest?.enterpriseCardUri.orEmpty())
    }
    var enterpriseCardFileName by rememberSaveable(existingAct?.id) { mutableStateOf("") }
    var enterpriseCardDetectedName by rememberSaveable(existingAct?.id) { mutableStateOf("") }
    var enterpriseCardDetectedAddress by rememberSaveable(existingAct?.id) { mutableStateOf("") }
    var showEnterpriseCardDetectedDialog by rememberSaveable(existingAct?.id) { mutableStateOf(false) }
    var brandSelection by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.brand ?: existingRequest?.brand.orEmpty())
    }
    var customBrand by rememberSaveable(existingAct?.id) { mutableStateOf("") }
    var modelSelection by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.model ?: existingRequest?.model.orEmpty())
    }
    var customModel by rememberSaveable(existingAct?.id) { mutableStateOf("") }
    var serialNumber by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.serialNumber ?: existingRequest?.serialNumber.orEmpty())
    }
    var operatingTime by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.operatingTime.orEmpty())
    }
    var customerRepresentative by rememberSaveable(existingAct?.id) {
        mutableStateOf(
            existingAct?.customerRepresentative
                ?.ifBlank { existingRequest?.customerRepresentative.orEmpty() }
                .orEmpty()
        )
    }
    var customerRepresentativePhone by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.customerRepresentativePhone ?: existingRequest?.customerRepresentativePhone.orEmpty())
    }
    var malfunctionDescription by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.malfunctionDescription.orEmpty())
    }
    var transferCompleteness by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.completeness.orEmpty())
    }
    var transferExternalCondition by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.externalCondition.orEmpty())
    }
    var diagnosisType by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.diagnosisType ?: DiagnosisType.Primary)
    }
    val checklistItems = rememberSaveable(existingAct?.id, saver = ChecklistStateSaver) {
        DiagnosticChecklistCatalog.stateFor(
            type = DiagnosisType.Advanced,
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
    var pdfPath by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.pdfPath.orEmpty())
    }
    var comment by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.comment.orEmpty())
    }
    val photos = rememberSaveable(existingAct?.id, saver = ActPhotoListSaver) {
        existingAct?.photos.orEmpty().toMutableStateList()
    }
    val customerSignatureDraft = rememberSaveable(existingAct?.id, saver = SignatureStrokeListSaver) {
        existingAct?.customerSignature.orEmpty().toMutableStateList()
    }
    val customerSignatureSaved = rememberSaveable(existingAct?.id, saver = SignatureStrokeListSaver) {
        existingAct?.customerSignature.orEmpty().toMutableStateList()
    }
    val executorSignatureDraft = rememberSaveable(existingAct?.id, saver = SignatureStrokeListSaver) {
        existingAct?.executorSignature.orEmpty().toMutableStateList()
    }
    val executorSignatureSaved = rememberSaveable(existingAct?.id, saver = SignatureStrokeListSaver) {
        existingAct?.executorSignature.orEmpty().toMutableStateList()
    }
    val directorSignatureDraft = rememberSaveable(existingAct?.id, saver = SignatureStrokeListSaver) {
        existingAct?.directorSignature.orEmpty().toMutableStateList()
    }
    val directorSignatureSaved = rememberSaveable(existingAct?.id, saver = SignatureStrokeListSaver) {
        existingAct?.directorSignature.orEmpty().toMutableStateList()
    }
    var signatureDecoding by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct?.signatureDecoding.orEmpty())
    }
    var customerSignatureDecoding by rememberSaveable(existingAct?.id) {
        mutableStateOf(
            existingAct?.customerSignatureDecoding
                ?.ifBlank { existingAct?.signatureDecoding.orEmpty() }
                .orEmpty()
        )
    }
    var executorSignatureDecoding by rememberSaveable(existingAct?.id) {
        mutableStateOf(
            existingAct?.executorSignatureDecoding
                ?.ifBlank { existingAct?.signatureDecoding.orEmpty() }
                .orEmpty()
        )
    }
    var directorSignatureDecoding by rememberSaveable(existingAct?.id) {
        mutableStateOf(
            existingAct?.directorSignatureDecoding
                ?.ifBlank { existingAct?.signatureDecoding.orEmpty() }
                .orEmpty()
        )
    }
    var pendingCameraPhoto by remember { mutableStateOf<ActPhoto?>(null) }
    var isImportingPhotos by remember { mutableStateOf(false) }
    var saveCompleted by remember { mutableStateOf(false) }
    val initialPhotoPaths = remember(existingAct?.id) {
        existingAct?.photos.orEmpty().map { it.filePath }.toSet()
    }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val createdPhoto = pendingCameraPhoto
        pendingCameraPhoto = null
        if (success && createdPhoto != null) {
            photos.add(createdPhoto)
        } else if (createdPhoto != null) {
            PhotoStorage.deletePhoto(createdPhoto)
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val createdPhoto = PhotoStorage.createCameraPhoto(context)
            pendingCameraPhoto = createdPhoto
            takePictureLauncher.launch(
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    java.io.File(createdPhoto.filePath)
                )
            )
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.photos_camera_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        isImportingPhotos = false
        val imported = uris.mapNotNull { uri -> PhotoStorage.importPhotoFromUri(context, uri) }
        photos.addAll(imported)
        if (uris.isNotEmpty() && imported.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.photos_import_error),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val enterpriseCardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            enterpriseCardUri = uri.toString()
            enterpriseCardFileName = resolveFileName(context, uri)
            val extracted = extractEnterpriseCardData(context, uri)
            if (extracted != null) {
                enterpriseCardDetectedName = extracted.organizationName
                enterpriseCardDetectedAddress = extracted.organizationAddress
                showEnterpriseCardDetectedDialog = true
            }
        }
    }

    DisposableEffect(existingAct?.id) {
        onDispose {
            if (!saveCompleted) {
                photos.filterNot { it.filePath in initialPhotoPaths }.forEach(PhotoStorage::deletePhoto)
            }
            pendingCameraPhoto?.let(PhotoStorage::deletePhoto)
        }
    }
    LaunchedEffect(enterpriseCardUri) {
        if (enterpriseCardUri.isNotBlank() && enterpriseCardFileName.isBlank()) {
            enterpriseCardFileName = resolveFileName(context, Uri.parse(enterpriseCardUri))
        }
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

    val generatedRequestNumber = remember(
        existingAct?.requestNumber,
        existingRequest?.requestNumber,
        equipmentCode,
        nextSequence,
        requestDatePart
    ) {
        existingAct?.requestNumber
            ?.ifBlank { null }
            ?: existingRequest?.requestNumber?.ifBlank { null }
            ?: if (equipmentCode.isBlank()) "" else "$equipmentCode-$nextSequence-$requestDatePart"
    }
    var requestNumber by rememberSaveable(existingAct?.id) { mutableStateOf(generatedRequestNumber) }
    var requestNumberEditedManually by rememberSaveable(existingAct?.id) {
        mutableStateOf(existingAct != null || existingRequest?.requestNumber?.isNotBlank() == true)
    }
    LaunchedEffect(generatedRequestNumber, equipmentCode) {
        if (!requestNumberEditedManually || requestNumber.isBlank()) {
            requestNumber = generatedRequestNumber
        }
    }
    var equipmentTransferState by rememberSaveable(existingAct?.id) {
        mutableStateOf(
            EquipmentTransferState.fromValue(existingAct?.equipmentTransferState.orEmpty())
        )
    }

    val resolvedBrand = if (brandSelection == EquipmentCatalog.OTHER_OPTION) customBrand else brandSelection
    val resolvedModel = if (modelSelection == EquipmentCatalog.OTHER_OPTION) customModel else modelSelection
    val isDiagnosticDocument = documentType == DocumentType.DiagnosticAct
    val isTransferAcceptanceDocument = documentType == DocumentType.TransferAcceptanceAct
    val isAcceptanceDocument = documentType == DocumentType.AcceptanceAct
    LaunchedEffect(isAcceptanceDocument, linkedTransferAct?.id, existingRequest?.id) {
        if (!isAcceptanceDocument) return@LaunchedEffect
        val source = linkedTransferAct
        requestNumber = source?.requestNumber?.ifBlank { null }
            ?: existingRequest?.requestNumber.orEmpty()
        customer = source?.customer?.ifBlank { null } ?: existingRequest?.customer.orEmpty()
        organizationPhone = source?.organizationPhone?.ifBlank { null } ?: existingRequest?.organizationPhone.orEmpty()
        customerAddress = source?.customerAddress?.ifBlank { null } ?: existingRequest?.customerAddress.orEmpty()
        customerRepresentative = existingRequest?.customerRepresentative.orEmpty()
        customerRepresentativePhone = source?.customerRepresentativePhone?.ifBlank { null }
            ?: existingRequest?.customerRepresentativePhone.orEmpty()
        equipmentCode = source?.equipmentCode?.ifBlank { null } ?: existingRequest?.equipmentCode.orEmpty()
        equipmentName = source?.equipmentName?.ifBlank { null } ?: existingRequest?.equipmentName.orEmpty()
        serialNumber = source?.serialNumber?.ifBlank { null } ?: existingRequest?.serialNumber.orEmpty()
        if (source?.brand?.isNotBlank() == true) {
            brandSelection = source.brand
        }
        if (source?.model?.isNotBlank() == true) {
            modelSelection = source.model
        }
    }
    val status = remember(
        date,
        customer,
        customerAddress,
        equipmentCode,
        equipmentName,
        resolvedBrand,
        resolvedModel,
        customerSignatureSaved.size,
        executorSignatureSaved.size,
        directorSignatureSaved.size
    ) {
        resolveActStatus(
            date = date,
            customer = customer,
            customerAddress = customerAddress,
            equipmentCode = equipmentCode,
            equipmentName = equipmentName,
            brand = resolvedBrand,
            model = resolvedModel,
            customerSignature = customerSignatureSaved.toList(),
            executorSignature = executorSignatureSaved.toList(),
            directorSignature = directorSignatureSaved.toList()
        )
    }
    val onTakePhotoClick = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val createdPhoto = PhotoStorage.createCameraPhoto(context)
            pendingCameraPhoto = createdPhoto
            takePictureLauncher.launch(
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    java.io.File(createdPhoto.filePath)
                )
            )
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val onPickPhotoClick = {
        isImportingPhotos = true
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val signatureSectionIndex = remember(isDiagnosticDocument, diagnosisType) {
        var index = 2
        if (isDiagnosticDocument) {
            index += 1 // diagnosis type block
            index += DiagnosticChecklistCatalog.sectionsFor(diagnosisType).size
            index += 1 // preliminary conclusion
            index += 1 // additional info
            if (diagnosisType == DiagnosisType.Advanced) index += 1
        }
        index
    }

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
                            text = documentType.title,
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
            state = listState,
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
                    if (!isAcceptanceDocument) {
                        OutlinedButton(
                            onClick = {
                                enterpriseCardLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(id = R.string.upload_enterprise_card))
                        }
                        if (enterpriseCardUri.isNotBlank()) {
                            Text(
                                text = stringResource(
                                    id = R.string.enterprise_card_selected,
                                    if (enterpriseCardFileName.isBlank()) enterpriseCardUri else enterpriseCardFileName
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    }
                    FormTextField(
                        value = requestNumber,
                        onValueChange = {
                            requestNumber = it
                            requestNumberEditedManually = true
                        },
                        label = stringResource(id = R.string.field_request_number),
                        placeholder = stringResource(id = R.string.field_request_number_placeholder),
                        readOnly = isAcceptanceDocument
                    )
                    DatePickerField(
                        value = createdAt,
                        onDateSelected = { createdAt = it },
                        label = stringResource(id = R.string.field_created_at),
                        placeholder = stringResource(id = R.string.field_created_at_placeholder)
                    )
                    val documentTypeOptions = DocumentType.values().toList()
                    DropdownField(
                        value = documentType.title,
                        options = documentTypeOptions.map { it.title },
                        label = stringResource(id = R.string.field_document_type),
                        placeholder = stringResource(id = R.string.field_document_type_placeholder),
                        onOptionSelected = { selectedTitle ->
                            documentType = documentTypeOptions.firstOrNull { it.title == selectedTitle }
                                ?: documentType
                        }
                    )
                    FormTextField(
                        value = status.title,
                        onValueChange = {},
                        label = stringResource(id = R.string.field_status),
                        placeholder = stringResource(id = R.string.field_status_placeholder),
                        readOnly = true
                    )
                    if (!isTransferAcceptanceDocument) {
                        FormTextField(
                            value = organizationName,
                            onValueChange = { organizationName = it },
                            label = stringResource(id = R.string.field_organization_name),
                            placeholder = stringResource(id = R.string.field_organization_name_placeholder),
                            minLines = 2
                        )
                    }
                    if (!isTransferAcceptanceDocument && !isAcceptanceDocument) {
                        FormTextField(
                            value = organizationInn,
                            onValueChange = { organizationInn = it },
                            label = stringResource(id = R.string.field_organization_inn),
                            placeholder = stringResource(id = R.string.field_organization_inn_placeholder)
                        )
                        FormTextField(
                            value = organizationOgrn,
                            onValueChange = { organizationOgrn = it },
                            label = stringResource(id = R.string.field_organization_ogrn),
                            placeholder = stringResource(id = R.string.field_organization_ogrn_placeholder)
                        )
                        FormTextField(
                            value = organizationAddress,
                            onValueChange = { organizationAddress = it },
                            label = stringResource(id = R.string.field_organization_address),
                            placeholder = stringResource(id = R.string.field_organization_address_placeholder),
                            minLines = 2
                        )
                        FormTextField(
                            value = organizationPhone,
                            onValueChange = { organizationPhone = it },
                            label = stringResource(id = R.string.field_organization_phone),
                            placeholder = stringResource(id = R.string.field_phone_placeholder),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    }
                    if (isTransferAcceptanceDocument) {
                        DatePickerField(
                            value = date,
                            onDateSelected = { date = it },
                            label = stringResource(id = R.string.field_acceptance_date),
                            placeholder = stringResource(id = R.string.field_date_placeholder)
                        )
                        FormTextField(
                            value = compilationPlace,
                            onValueChange = { compilationPlace = it },
                            label = stringResource(id = R.string.field_compilation_place),
                            placeholder = stringResource(id = R.string.field_compilation_place_placeholder),
                            minLines = 2
                        )
                        DetailCard(title = stringResource(id = R.string.organization_details_title)) {
                            FormTextField(
                                value = organizationName,
                                onValueChange = { organizationName = it },
                                label = stringResource(id = R.string.field_organization_name),
                                placeholder = stringResource(id = R.string.field_organization_name_placeholder)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FormTextField(
                                value = organizationInn,
                                onValueChange = { organizationInn = it },
                                label = stringResource(id = R.string.field_organization_inn),
                                placeholder = stringResource(id = R.string.field_organization_inn_placeholder)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FormTextField(
                                value = organizationOgrn,
                                onValueChange = { organizationOgrn = it },
                                label = stringResource(id = R.string.field_organization_ogrn),
                                placeholder = stringResource(id = R.string.field_organization_ogrn_placeholder)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FormTextField(
                                value = organizationAddress,
                                onValueChange = { organizationAddress = it },
                                label = stringResource(id = R.string.field_organization_address),
                                placeholder = stringResource(id = R.string.field_organization_address_placeholder),
                                minLines = 2
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FormTextField(
                                value = organizationPhone,
                                onValueChange = { organizationPhone = it },
                                label = stringResource(id = R.string.field_organization_phone),
                                placeholder = stringResource(id = R.string.field_phone_placeholder),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                            )
                        }
                        DetailCard(title = stringResource(id = R.string.customer_representative_block_title)) {
                            FormTextField(
                                value = customerRepresentative,
                                onValueChange = { customerRepresentative = it },
                                label = stringResource(id = R.string.field_customer_representative),
                                placeholder = stringResource(id = R.string.field_customer_representative_placeholder)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FormTextField(
                                value = customerRepresentativePhone,
                                onValueChange = { customerRepresentativePhone = it },
                                label = stringResource(id = R.string.field_customer_representative_phone),
                                placeholder = stringResource(id = R.string.field_customer_representative_phone_placeholder),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                            )
                        }
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
                        FormTextField(
                            value = transferCompleteness,
                            onValueChange = { transferCompleteness = it },
                            label = stringResource(id = R.string.field_completeness),
                            placeholder = stringResource(id = R.string.field_completeness_placeholder)
                        )
                        DropdownField(
                            value = transferExternalCondition,
                            options = listOf(
                                stringResource(id = R.string.external_condition_normal),
                                stringResource(id = R.string.external_condition_minor_dents),
                                stringResource(id = R.string.external_condition_comments)
                            ),
                            label = stringResource(id = R.string.field_external_condition),
                            placeholder = stringResource(id = R.string.field_external_condition_placeholder),
                            onOptionSelected = { selectedCondition ->
                                transferExternalCondition = selectedCondition
                            }
                        )
                        FormTextField(
                            value = malfunctionDescription,
                            onValueChange = { malfunctionDescription = it },
                            label = stringResource(id = R.string.field_malfunction_description),
                            placeholder = stringResource(id = R.string.field_malfunction_description_placeholder),
                            minLines = 3
                        )
                    } else {
                        if (isDiagnosticDocument) {
                            DatePickerField(
                                value = date,
                                onDateSelected = { date = it },
                                label = stringResource(id = R.string.field_date),
                                placeholder = stringResource(id = R.string.field_date_placeholder)
                            )
                        } else {
                            FormTextField(
                                value = date,
                                onValueChange = { date = it },
                                label = stringResource(id = R.string.field_date),
                                placeholder = stringResource(id = R.string.field_date_placeholder)
                            )
                        }
                        if (!isDiagnosticDocument && !isAcceptanceDocument) {
                            FormTextField(
                                value = customer,
                                onValueChange = { customer = it },
                                label = stringResource(id = R.string.field_customer),
                                placeholder = stringResource(id = R.string.field_customer_placeholder)
                            )
                        }
                        FormTextField(
                            value = customerRepresentative,
                            onValueChange = { customerRepresentative = it },
                            label = stringResource(id = R.string.field_customer_representative),
                            placeholder = stringResource(id = R.string.field_customer_representative_placeholder)
                        )
                        FormTextField(
                            value = customerRepresentativePhone,
                            onValueChange = { customerRepresentativePhone = it },
                            label = stringResource(id = R.string.field_customer_representative_phone),
                            placeholder = stringResource(id = R.string.field_customer_representative_phone_placeholder),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                        if (!isDiagnosticDocument) {
                            FormTextField(
                                value = customerAddress,
                                onValueChange = { customerAddress = it },
                                label = if (isAcceptanceDocument) {
                                    stringResource(id = R.string.field_transfer_place)
                                } else {
                                    stringResource(id = R.string.field_customer_address)
                                },
                                placeholder = if (isAcceptanceDocument) {
                                    stringResource(id = R.string.field_transfer_place_placeholder)
                                } else {
                                    stringResource(id = R.string.field_customer_address_placeholder)
                                },
                                minLines = 2
                            )
                        }
                        if (isAcceptanceDocument) {
                            DropdownField(
                                value = equipmentTransferState.value,
                                options = listOf(
                                    stringResource(id = R.string.equipment_state_working),
                                    stringResource(id = R.string.equipment_state_not_working),
                                    stringResource(id = R.string.equipment_state_partially_working)
                                ),
                                label = stringResource(id = R.string.field_equipment_transfer_state),
                                placeholder = stringResource(id = R.string.field_equipment_transfer_state),
                                onOptionSelected = { selectedState ->
                                    equipmentTransferState = EquipmentTransferState.fromValue(selectedState)
                                }
                            )
                            Text(
                                text = stringResource(id = R.string.acceptance_note_no_claims),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(id = R.string.acceptance_note_work_warranty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(id = R.string.acceptance_note_parts_warranty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FormTextField(
                            value = equipmentName,
                            onValueChange = {},
                            label = stringResource(id = R.string.field_equipment_name),
                            placeholder = stringResource(id = R.string.field_equipment_name_placeholder),
                            readOnly = true
                        )
                        if (isAcceptanceDocument) {
                            FormTextField(
                                value = resolvedBrand,
                                onValueChange = {},
                                label = stringResource(id = R.string.field_brand),
                                placeholder = stringResource(id = R.string.field_brand_placeholder),
                                readOnly = true
                            )
                            FormTextField(
                                value = resolvedModel,
                                onValueChange = {
                                    modelSelection = EquipmentCatalog.OTHER_OPTION
                                    customModel = it
                                },
                                label = stringResource(id = R.string.field_model),
                                placeholder = stringResource(id = R.string.field_model_placeholder)
                            )
                        } else {
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
                        }
                        FormTextField(
                            value = serialNumber,
                            onValueChange = { serialNumber = it },
                            label = stringResource(id = R.string.field_serial_number),
                            placeholder = stringResource(id = R.string.field_serial_number_placeholder)
                        )
                        if (!isDiagnosticDocument) {
                            FormTextField(
                                value = operatingTime,
                                onValueChange = { operatingTime = it },
                                label = stringResource(id = R.string.field_operating_time),
                                placeholder = stringResource(id = R.string.field_operating_time_placeholder),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                        }
                        if (isAcceptanceDocument) {
                            FormTextField(
                                value = malfunctionDescription,
                                onValueChange = { malfunctionDescription = it },
                                label = stringResource(id = R.string.field_malfunction_description),
                                placeholder = stringResource(id = R.string.field_malfunction_description_placeholder),
                                minLines = 3
                            )
                        }
                    }
                }
            }

            item {
                DetailCard(title = stringResource(id = R.string.photos_section_title)) {
                    Text(
                        text = stringResource(id = R.string.photos_section_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onTakePhotoClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(text = stringResource(id = R.string.photos_take_photo))
                        }
                        OutlinedButton(
                            onClick = onPickPhotoClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(text = stringResource(id = R.string.photos_pick_from_gallery))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (isImportingPhotos) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = stringResource(id = R.string.photos_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    PhotoGalleryEditor(
                        photos = photos,
                        onDeletePhoto = { photo ->
                            if (photo.filePath !in initialPhotoPaths) {
                                PhotoStorage.deletePhoto(photo)
                            }
                            photos.removeAll { it.id == photo.id }
                        }
                    )
                }
            }

            if (isDiagnosticDocument) {
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
                                diagnosisType = selectedType
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
            }

            if (isDiagnosticDocument) {
                item {
                    DetailCard(title = stringResource(id = R.string.additional_info_title)) {
                        FormTextField(
                            value = pdfPath,
                            onValueChange = { pdfPath = it },
                            label = stringResource(id = R.string.field_pdf_path),
                            placeholder = stringResource(id = R.string.field_pdf_path_placeholder)
                        )
                    }
                }
            }

            if (isDiagnosticDocument && diagnosisType == DiagnosisType.Advanced) {
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
                DetailCard(title = stringResource(id = R.string.signatures_section_title)) {
                    SignatureEditorCard(
                        title = stringResource(id = R.string.signature_customer_title),
                        draftSignature = customerSignatureDraft,
                        savedSignature = customerSignatureSaved,
                        onDraftChange = { updated ->
                            customerSignatureDraft.clear()
                            customerSignatureDraft.addAll(updated)
                        },
                        onClearClick = { customerSignatureDraft.clear() },
                        onSaveClick = {
                            customerSignatureSaved.clear()
                            customerSignatureSaved.addAll(customerSignatureDraft)
                            Toast.makeText(
                                context,
                                context.getString(R.string.signature_customer_saved_message),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FormTextField(
                        value = customerSignatureDecoding,
                        onValueChange = { customerSignatureDecoding = it },
                        label = "${stringResource(id = R.string.field_signature_decoding)} (${stringResource(id = R.string.signature_customer_title)})",
                        placeholder = stringResource(id = R.string.field_signature_decoding_placeholder)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SignatureEditorCard(
                        title = stringResource(id = R.string.signature_executor_title),
                        draftSignature = executorSignatureDraft,
                        savedSignature = executorSignatureSaved,
                        onDraftChange = { updated ->
                            executorSignatureDraft.clear()
                            executorSignatureDraft.addAll(updated)
                        },
                        onClearClick = { executorSignatureDraft.clear() },
                        onSaveClick = {
                            executorSignatureSaved.clear()
                            executorSignatureSaved.addAll(executorSignatureDraft)
                            Toast.makeText(
                                context,
                                context.getString(R.string.signature_executor_saved_message),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FormTextField(
                        value = executorSignatureDecoding,
                        onValueChange = { executorSignatureDecoding = it },
                        label = "${stringResource(id = R.string.field_signature_decoding)} (${stringResource(id = R.string.signature_executor_title)})",
                        placeholder = stringResource(id = R.string.field_signature_decoding_placeholder)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SignatureEditorCard(
                        title = stringResource(id = R.string.signature_director_title),
                        draftSignature = directorSignatureDraft,
                        savedSignature = directorSignatureSaved,
                        onDraftChange = { updated ->
                            directorSignatureDraft.clear()
                            directorSignatureDraft.addAll(updated)
                        },
                        onClearClick = { directorSignatureDraft.clear() },
                        onSaveClick = {
                            directorSignatureSaved.clear()
                            directorSignatureSaved.addAll(directorSignatureDraft)
                            Toast.makeText(
                                context,
                                context.getString(R.string.signature_director_saved_message),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FormTextField(
                        value = directorSignatureDecoding,
                        onValueChange = { directorSignatureDecoding = it },
                        label = "${stringResource(id = R.string.field_signature_decoding)} (${stringResource(id = R.string.signature_director_title)})",
                        placeholder = stringResource(id = R.string.field_signature_decoding_placeholder)
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        if (customerSignatureSaved.isEmpty()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.signature_customer_required_error),
                                Toast.LENGTH_SHORT
                            ).show()
                            coroutineScope.launch {
                                listState.animateScrollToItem(signatureSectionIndex)
                            }
                            return@Button
                        }
                        val completenessSummary = checklistItems.findCommentByKey("visual_completeness")
                        val externalConditionSummary = listOf(
                            checklistItems.findCommentByKey("visual_casing_condition"),
                            checklistItems.findCommentByKey("visual_damage_traces")
                        ).filter { it.isNotBlank() }.joinToString("; ")
                        val actChecklistItems = if (isDiagnosticDocument) {
                            DiagnosticChecklistCatalog.stateFor(
                                type = diagnosisType,
                                savedItems = checklistItems.toList(),
                                legacyAct = existingAct
                            )
                        } else {
                            emptyList()
                        }
                        val actStatus = resolveActStatus(
                            date = date,
                            customer = customer,
                            customerAddress = customerAddress,
                            equipmentCode = equipmentCode,
                            equipmentName = equipmentName,
                            brand = resolvedBrand,
                            model = resolvedModel,
                            customerSignature = customerSignatureSaved.toList(),
                            executorSignature = executorSignatureSaved.toList(),
                            directorSignature = directorSignatureSaved.toList()
                        )
                        val requestRecord = RequestRecord(
                            id = existingRequest?.id ?: existingAct?.requestId ?: System.currentTimeMillis(),
                            requestNumber = requestNumber,
                            createdAt = createdAt,
                            date = date.ifBlank { context.getString(R.string.default_date_value) },
                            customer = customer.ifBlank { context.getString(R.string.default_customer_value) },
                            phone = phone,
                            customerAddress = customerAddress,
                            organizationName = organizationName,
                            organizationInn = organizationInn,
                            organizationOgrn = organizationOgrn,
                            organizationAddress = organizationAddress,
                            organizationPhone = organizationPhone,
                            customerRepresentative = customerRepresentative,
                            customerRepresentativePhone = customerRepresentativePhone,
                            compilationPlace = compilationPlace,
                            enterpriseCardUri = enterpriseCardUri,
                            equipmentCode = equipmentCode,
                            equipmentName = equipmentName,
                            brand = resolvedBrand,
                            model = resolvedModel,
                            serialNumber = serialNumber
                        )
                        val act = ActRecord(
                            id = existingAct?.id ?: System.currentTimeMillis(),
                            requestId = requestRecord.id,
                            documentType = documentType,
                            status = actStatus,
                            createdAt = createdAt,
                            requestNumber = requestRecord.requestNumber,
                            date = requestRecord.date,
                            customer = requestRecord.customer,
                            phone = phone,
                            customerAddress = customerAddress,
                            organizationName = organizationName,
                            organizationInn = organizationInn,
                            organizationOgrn = organizationOgrn,
                            organizationAddress = organizationAddress,
                            organizationPhone = organizationPhone,
                            compilationPlace = compilationPlace,
                            equipmentCode = equipmentCode,
                            equipmentName = equipmentName,
                            brand = resolvedBrand,
                            model = resolvedModel,
                            serialNumber = serialNumber,
                            operatingTime = operatingTime,
                            completeness = if (isDiagnosticDocument) completenessSummary else transferCompleteness,
                            externalCondition = if (isDiagnosticDocument) externalConditionSummary else transferExternalCondition,
                            malfunctionDescription = when {
                                isDiagnosticDocument -> preliminaryConclusion
                                else -> malfunctionDescription
                            },
                            customerRepresentative = customerRepresentative,
                            customerRepresentativePhone = customerRepresentativePhone,
                            diagnosisType = if (isDiagnosticDocument) diagnosisType else DiagnosisType.Primary,
                            checklistItems = actChecklistItems,
                            preliminaryConclusion = if (isDiagnosticDocument || isAcceptanceDocument) {
                                preliminaryConclusion
                            } else {
                                ""
                            },
                            rootCause = if (isDiagnosticDocument && diagnosisType == DiagnosisType.Advanced) rootCause else "",
                            requiredWorks = if (isDiagnosticDocument && diagnosisType == DiagnosisType.Advanced) requiredWorks else "",
                            equipmentTransferState = if (isAcceptanceDocument) equipmentTransferState.value else "",
                            pdfPath = pdfPath,
                            comment = if (isDiagnosticDocument || isAcceptanceDocument) "" else comment,
                            photos = photos.toList(),
                            customerSignature = customerSignatureSaved.toList(),
                            executorSignature = executorSignatureSaved.toList(),
                            directorSignature = directorSignatureSaved.toList(),
                            signatureDecoding = signatureDecoding,
                            customerSignatureDecoding = customerSignatureDecoding,
                            executorSignatureDecoding = executorSignatureDecoding,
                            directorSignatureDecoding = directorSignatureDecoding
                        )
                        PhotoStorage.deleteMissingPhotos(existingAct?.photos.orEmpty(), act.photos)
                        saveCompleted = true
                        onSaveClick(act, requestRecord)
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
    if (showEnterpriseCardDetectedDialog) {
        AlertDialog(
            onDismissRequest = { showEnterpriseCardDetectedDialog = false },
            title = { Text(text = stringResource(id = R.string.enterprise_card_detected_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(id = R.string.enterprise_card_detected_dialog_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FormTextField(
                        value = enterpriseCardDetectedName,
                        onValueChange = { enterpriseCardDetectedName = it },
                        label = stringResource(id = R.string.field_organization_name),
                        placeholder = stringResource(id = R.string.field_organization_name_placeholder)
                    )
                    FormTextField(
                        value = enterpriseCardDetectedAddress,
                        onValueChange = { enterpriseCardDetectedAddress = it },
                        label = stringResource(id = R.string.field_organization_address),
                        placeholder = stringResource(id = R.string.field_organization_address_placeholder),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        organizationName = enterpriseCardDetectedName.ifBlank { EXECUTOR_REQUISITES }
                        organizationAddress = enterpriseCardDetectedAddress
                        showEnterpriseCardDetectedDialog = false
                    }
                ) {
                    Text(text = stringResource(id = R.string.apply_enterprise_card_data))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnterpriseCardDetectedDialog = false }) {
                    Text(text = stringResource(id = R.string.skip_enterprise_card_data))
                }
            }
        )
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
        val horizontalScroll = rememberScrollState()
        Column(modifier = Modifier.horizontalScroll(horizontalScroll)) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsListScreen(
    requests: List<RequestRecord>,
    acts: List<ActRecord>,
    onBackClick: () -> Unit = {},
    onRequestClick: (RequestRecord) -> Unit = {},
    onDeleteRequest: (RequestRecord) -> Unit = {}
) {
    var requestPendingDelete by remember { mutableStateOf<RequestRecord?>(null) }
    BackHandler(onBack = onBackClick)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.requests_list_screen_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.requests_list_screen_subtitle),
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
        if (requests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.empty_requests_text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
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
                items(items = requests, key = { it.id }) { request ->
                    val documentsCount = acts.count { it.requestId == request.id }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRequestClick(request) },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = request.requestNumber,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { requestPendingDelete = request }) {
                                    Text(text = stringResource(id = R.string.delete_button))
                                }
                            }
                            InfoLine(stringResource(id = R.string.field_created_at), request.createdAt)
                            InfoLine(stringResource(id = R.string.field_customer), request.customer)
                            InfoLine(stringResource(id = R.string.field_equipment_name), request.equipmentName)
                            InfoLine(stringResource(id = R.string.request_documents_count), documentsCount.toString())
                        }
                    }
                }
            }
        }
    }

    if (requestPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { requestPendingDelete = null },
            title = { Text(text = stringResource(id = R.string.delete_button)) },
            text = { Text(text = stringResource(id = R.string.delete_request_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        requestPendingDelete?.let(onDeleteRequest)
                        requestPendingDelete = null
                    }
                ) {
                    Text(text = stringResource(id = R.string.delete_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { requestPendingDelete = null }) {
                    Text(text = stringResource(id = R.string.cancel_button))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailsScreen(
    request: RequestRecord,
    acts: List<ActRecord>,
    onBackClick: () -> Unit = {},
    onCreateDocument: (DocumentType) -> Unit = {},
    onOpenAct: (ActRecord) -> Unit = {}
) {
    BackHandler(onBack = onBackClick)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.request_details_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = request.requestNumber,
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DetailCard(title = stringResource(id = R.string.act_main_info_title)) {
                    InfoLine(stringResource(id = R.string.field_request_number), request.requestNumber)
                    InfoLine(stringResource(id = R.string.field_created_at), request.createdAt)
                    InfoLine(stringResource(id = R.string.field_date), request.date)
                    Text(
                        text = stringResource(id = R.string.organization_details_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    InfoLine(stringResource(id = R.string.field_organization_name), request.organizationName)
                    InfoLine(stringResource(id = R.string.field_organization_inn), request.organizationInn)
                    InfoLine(stringResource(id = R.string.field_organization_ogrn), request.organizationOgrn)
                    InfoLine(stringResource(id = R.string.field_organization_address), request.organizationAddress)
                    InfoLine(stringResource(id = R.string.field_organization_phone), request.organizationPhone)
                    Text(
                        text = stringResource(id = R.string.customer_representative_block_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    InfoLine(stringResource(id = R.string.field_customer_representative), request.customerRepresentative)
                    InfoLine(
                        stringResource(id = R.string.field_customer_representative_phone),
                        request.customerRepresentativePhone
                    )
                    InfoLine(stringResource(id = R.string.field_equipment_code), request.equipmentCode)
                    InfoLine(stringResource(id = R.string.field_equipment_name), request.equipmentName)
                    InfoLine(stringResource(id = R.string.field_brand), request.brand)
                    InfoLine(stringResource(id = R.string.field_model), request.model)
                    InfoLine(stringResource(id = R.string.field_serial_number), request.serialNumber)
                }
            }
            item {
                DetailCard(title = stringResource(id = R.string.request_actions_title)) {
                    Button(onClick = { onCreateDocument(DocumentType.TransferAcceptanceAct) }, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.create_transfer_act_button))
                    }
                    OutlinedButton(onClick = { onCreateDocument(DocumentType.DiagnosticAct) }, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.create_diagnostic_act_button))
                    }
                    OutlinedButton(onClick = { onCreateDocument(DocumentType.AcceptanceAct) }, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.create_acceptance_act_button))
                    }
                }
            }
            item {
                DetailCard(title = stringResource(id = R.string.request_documents_title)) {
                    if (acts.isEmpty()) {
                        Text(text = stringResource(id = R.string.request_documents_empty))
                    } else {
                        acts.forEach { act ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAct(act) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = act.documentType.title)
                                Text(text = act.date)
                            }
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
    onEditClick: () -> Unit = {},
    onDeleteAct: (ActRecord) -> Unit = {}
) {
    val context = LocalContext.current
    var generatedPdfUri by remember(act.id) { mutableStateOf<Uri?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    BackHandler(onBack = onBackClick)

    fun generatePdf(mode: ActPdfGenerator.PdfMode): Uri? {
        val result = ActPdfGenerator.generate(context, act, mode)
        return result.getOrNull()?.let { file ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
    }

    fun sharePdf(pdfUri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_pdf_title)))
    }

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
                    TextButton(onClick = { showDeleteConfirmation = true }) {
                        Text(text = stringResource(id = R.string.delete_button))
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
                    InfoLine(stringResource(id = R.string.field_created_at), act.createdAt)
                    InfoLine(stringResource(id = R.string.field_document_type), act.documentType.title)
                    InfoLine(stringResource(id = R.string.field_status), act.resolvedStatus().title)
                    Text(
                        text = stringResource(id = R.string.organization_details_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    InfoLine(stringResource(id = R.string.field_organization_name), act.organizationName)
                    InfoLine(stringResource(id = R.string.field_organization_inn), act.organizationInn)
                    InfoLine(stringResource(id = R.string.field_organization_ogrn), act.organizationOgrn)
                    InfoLine(stringResource(id = R.string.field_organization_address), act.organizationAddress)
                    InfoLine(stringResource(id = R.string.field_organization_phone), act.organizationPhone)
                    if (act.documentType != DocumentType.DiagnosticAct) {
                        Text(
                            text = stringResource(id = R.string.customer_representative_block_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    InfoLine(stringResource(id = R.string.field_customer_representative), act.customerRepresentative)
                    InfoLine(
                        stringResource(id = R.string.field_customer_representative_phone),
                        act.customerRepresentativePhone
                    )
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
                title = stringResource(id = R.string.photos_section_title),
                content = {
                    PhotoGalleryReadonly(photos = act.photos)
                }
            )
            if (act.documentType == DocumentType.DiagnosticAct) {
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
                        val horizontalScroll = rememberScrollState()
                        Column(modifier = Modifier.horizontalScroll(horizontalScroll)) {
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
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    InfoLine(
                        stringResource(id = R.string.preliminary_conclusion_title),
                        act.preliminaryConclusion
                    )
                    InfoLine(stringResource(id = R.string.field_comment), act.comment)
                    if (act.diagnosisType == DiagnosisType.Advanced) {
                        InfoLine(stringResource(id = R.string.root_cause_title), act.rootCause)
                        InfoLine(stringResource(id = R.string.required_works_title), act.requiredWorks)
                    }
                    }
                )
                DetailCard(
                    title = stringResource(id = R.string.additional_info_title),
                    content = {
                        InfoLine(stringResource(id = R.string.field_pdf_path), act.pdfPath)
                    }
                )
            }
            DetailCard(
                title = stringResource(id = R.string.signatures_section_title),
                content = {
                    SignatureReadonlyCard(
                        title = stringResource(id = R.string.signature_customer_title),
                        signature = act.customerSignature
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SignatureReadonlyCard(
                        title = stringResource(id = R.string.signature_executor_title),
                        signature = act.executorSignature
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SignatureReadonlyCard(
                        title = stringResource(id = R.string.signature_director_title),
                        signature = act.directorSignature
                    )
                }
            )

            Button(
                onClick = {
                    val uri = generatePdf(ActPdfGenerator.PdfMode.Filled)
                    if (uri != null) {
                        generatedPdfUri = uri
                        Toast.makeText(
                            context,
                            context.getString(R.string.filled_pdf_generated_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.pdf_generated_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = stringResource(id = R.string.generate_filled_pdf_button))
            }

            OutlinedButton(
                onClick = {
                    val uri = generatePdf(ActPdfGenerator.PdfMode.Blank)
                    if (uri != null) {
                        generatedPdfUri = uri
                        Toast.makeText(
                            context,
                            context.getString(R.string.blank_pdf_generated_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.pdf_generated_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = stringResource(id = R.string.generate_blank_pdf_button))
            }

            OutlinedButton(
                onClick = {
                    val uri = generatedPdfUri ?: generatePdf(ActPdfGenerator.PdfMode.Filled)
                    if (uri != null) {
                        generatedPdfUri = uri
                        sharePdf(uri)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.pdf_share_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = stringResource(id = R.string.share_pdf_button))
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            text = {
                Text(text = stringResource(id = R.string.delete_act_confirmation))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteAct(act)
                    }
                ) {
                    Text(text = stringResource(id = R.string.delete_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(text = stringResource(id = R.string.cancel_button))
                }
            }
        )
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
private fun SignatureEditorCard(
    title: String,
    draftSignature: List<SignatureStroke>,
    savedSignature: List<SignatureStroke>,
    onDraftChange: (List<SignatureStroke>) -> Unit,
    onClearClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        SignaturePad(
            signature = draftSignature,
            onSignatureChange = onDraftChange,
            editable = true
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onClearClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = stringResource(id = R.string.signature_clear_button))
            }
            Button(
                onClick = onSaveClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = stringResource(id = R.string.signature_save_button))
            }
        }
        Text(
            text = if (savedSignature.isEmpty()) {
                stringResource(id = R.string.signature_not_saved)
            } else {
                stringResource(id = R.string.signature_saved)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SignatureReadonlyCard(
    title: String,
    signature: List<SignatureStroke>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        SignaturePad(
            signature = signature,
            onSignatureChange = {},
            editable = false
        )
    }
}

@Composable
private fun SignaturePad(
    signature: List<SignatureStroke>,
    onSignatureChange: (List<SignatureStroke>) -> Unit,
    editable: Boolean
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val strokeColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceColor)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .onSizeChanged { canvasSize = it }
            .pointerInput(editable, signature, canvasSize) {
                if (!editable || canvasSize.width == 0 || canvasSize.height == 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startPoint = down.position.normalize(canvasSize)
                    var updatedSignature = signature + SignatureStroke(points = listOf(startPoint))
                    onSignatureChange(updatedSignature)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val updatedStrokePoints =
                            updatedSignature.last().points + change.position.normalize(canvasSize)
                        updatedSignature = updatedSignature.dropLast(1) + SignatureStroke(points = updatedStrokePoints)
                        onSignatureChange(updatedSignature)
                        change.consume()
                    }
                }
            }
    ) {
        drawSignature(signature = signature, color = strokeColor)
    }
}

@Composable
private fun PhotoGalleryEditor(
    photos: List<ActPhoto>,
    onDeletePhoto: (ActPhoto) -> Unit
) {
    if (photos.isEmpty()) {
        Text(
            text = stringResource(id = R.string.photos_empty_state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items = photos, key = { it.id }) { photo ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = stringResource(id = R.string.photos_thumbnail_description),
                        modifier = Modifier
                            .size(132.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { onDeletePhoto(photo) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(id = R.string.photos_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoGalleryReadonly(photos: List<ActPhoto>) {
    if (photos.isEmpty()) {
        Text(
            text = stringResource(id = R.string.photos_empty_state_saved),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items = photos, key = { it.id }) { photo ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = stringResource(id = R.string.photos_thumbnail_description),
                    modifier = Modifier
                        .padding(10.dp)
                        .size(132.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun ChecklistTableHeader() {
    Row(
        modifier = Modifier
            .widthIn(min = 760.dp)
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
            .widthIn(min = 760.dp)
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
            .widthIn(min = 760.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    value: String,
    onDateSelected: (String) -> Unit,
    label: String,
    placeholder: String
) {
    var showDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = parseDateDisplayToMillis(value))
    OutlinedTextField(
        value = value,
        onValueChange = onDateSelected,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    showDialog = true
                }
            },
        label = { Text(text = label) },
        placeholder = { Text(text = placeholder) },
        shape = RoundedCornerShape(18.dp),
        readOnly = false
    )
    if (showDialog) {
        datePickerState.selectedDateMillis = parseDateDisplayToMillis(value)
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { onDateSelected(formatDateDisplay(it)) }
                        showDialog = false
                    }
                ) { Text(text = "OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = stringResource(id = R.string.cancel_button))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
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

private fun resolveActStatus(
    date: String,
    customer: String,
    customerAddress: String,
    equipmentCode: String,
    equipmentName: String,
    brand: String,
    model: String,
    customerSignature: List<SignatureStroke>,
    executorSignature: List<SignatureStroke>,
    directorSignature: List<SignatureStroke>
): ActStatus {
    val hasCustomerSignature = customerSignature.isNotEmpty()
    val hasExecutorSignature = executorSignature.isNotEmpty()
    val hasDirectorSignature = directorSignature.isNotEmpty()

    if (hasDirectorSignature) return ActStatus.Approved
    if (hasCustomerSignature && hasExecutorSignature) return ActStatus.Signed

    val isComplete = listOf(
        date,
        customer,
        customerAddress,
        equipmentCode,
        equipmentName,
        brand,
        model
    ).all { it.isNotBlank() }

    return if (isComplete) ActStatus.Saved else ActStatus.Draft
}

private fun ActRecord.resolvedStatus(): ActStatus = resolveActStatus(
    date = date,
    customer = customer,
    customerAddress = customerAddress,
    equipmentCode = equipmentCode,
    equipmentName = equipmentName,
    brand = brand,
    model = model,
    customerSignature = customerSignature,
    executorSignature = executorSignature,
    directorSignature = directorSignature
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSignature(
    signature: List<SignatureStroke>,
    color: Color
) {
    signature.forEach { stroke ->
        if (stroke.points.size == 1) {
            val point = stroke.points.first()
            drawCircle(
                color = color,
                radius = 2.2.dp.toPx(),
                center = Offset(point.x * size.width, point.y * size.height)
            )
        } else {
            stroke.points.windowed(2).forEach { pointsPair ->
                val start = pointsPair[0]
                val end = pointsPair[1]
                drawLine(
                    color = color,
                    start = Offset(start.x * size.width, start.y * size.height),
                    end = Offset(end.x * size.width, end.y * size.height),
                    strokeWidth = 2.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun Offset.normalize(size: IntSize): SignaturePoint {
    val safeWidth = size.width.coerceAtLeast(1)
    val safeHeight = size.height.coerceAtLeast(1)
    return SignaturePoint(
        x = (x / safeWidth.toFloat()).coerceIn(0f, 1f),
        y = (y / safeHeight.toFloat()).coerceIn(0f, 1f)
    )
}

private fun currentDateDisplay(): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

private fun currentDateForRequestNumber(): String =
    SimpleDateFormat("ddMMyy", Locale.getDefault()).format(Date())

private fun parseDateDisplayToMillis(value: String): Long? {
    val parsedDate = runCatching {
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).apply { isLenient = false }.parse(value)
    }.getOrNull() ?: return null
    return parsedDate.time
}

private fun formatDateDisplay(millis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(calendar.time)
}

private fun resolveFileName(context: android.content.Context, uri: Uri): String {
    val fileName = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
    }.getOrNull()
    return fileName.orEmpty()
}

private data class EnterpriseCardExtract(
    val organizationName: String,
    val organizationAddress: String
)

private fun extractEnterpriseCardData(
    context: android.content.Context,
    uri: Uri
): EnterpriseCardExtract? {
    val text = readEnterpriseCardText(context, uri)?.trim().orEmpty()
    if (text.isBlank()) return null
    val lines = text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
    if (lines.isEmpty()) return null

    val name = lines.firstOrNull {
        it.contains(Regex("""\b(ООО|ОАО|ЗАО|АО|ПАО|ИП)\b""", RegexOption.IGNORE_CASE))
    }.orEmpty()
    val address = lines.firstOrNull {
        it.contains("адрес", ignoreCase = true)
    }?.substringAfter(":", "").orEmpty().ifBlank {
        lines.firstOrNull {
            it.contains(Regex("""\b(г\.|город|ул\.|улица|проспект|пр-т|д\.)\b""", RegexOption.IGNORE_CASE))
        }.orEmpty()
    }

    if (name.isBlank() && address.isBlank()) return null
    return EnterpriseCardExtract(
        organizationName = name,
        organizationAddress = address
    )
}

private fun readEnterpriseCardText(context: android.content.Context, uri: Uri): String? {
    val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
    val fileName = resolveFileName(context, uri).lowercase(Locale.ROOT)
    val isPdf = mimeType == "application/pdf" || fileName.endsWith(".pdf")
    val isDocx = mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
        fileName.endsWith(".docx")

    return runCatching {
        when {
            isPdf -> null
            isDocx -> readDocxText(context, uri)
            else -> null
        }
    }.getOrNull()
}


private fun readDocxText(context: android.content.Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == "word/document.xml" }
                ?: return@use ""
            val xml = zip.readBytes().toString(Charsets.UTF_8)
            xml
                .replace("</w:p>", "\n")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
                .replace(Regex("\\n+"), "\n")
                .trim()
        }
    }.orEmpty()
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
        NewActScreen(
            existingAct = ActRecord(
                id = 1L,
                requestId = 1L,
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
private fun RequestsListScreenPreview() {
    DguActAppTheme(darkTheme = false) {
        RequestsListScreen(
            requests = listOf(
                RequestRecord(
                    id = 1L,
                    requestNumber = "DGU-18-230326",
                    createdAt = "23.03.2026",
                    date = "23.03.2026",
                    customer = "ООО «Энерго Сервис»",
                    phone = "+7 900 000-00-00",
                    customerAddress = "г. Москва, ул. Центральная, д. 10",
                    organizationName = "ООО «Энерго Сервис»",
                    organizationInn = "7701234567",
                    organizationOgrn = "1027700132195",
                    organizationAddress = "г. Москва, ул. Центральная, д. 10",
                    organizationPhone = "+7 495 000-00-00",
                    customerRepresentative = "Иванов И.И.",
                    customerRepresentativePhone = "+7 900 000-00-00",
                    compilationPlace = "г. Москва",
                    enterpriseCardUri = "",
                    equipmentCode = "DGU",
                    equipmentName = "дизельный генератор",
                    brand = "FG Wilson",
                    model = "P110-3",
                    serialNumber = "SN-12345"
                )
            ),
            acts = emptyList()
        )
    }
}
