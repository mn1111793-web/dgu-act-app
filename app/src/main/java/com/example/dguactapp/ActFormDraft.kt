package com.example.dguactapp

import org.json.JSONObject
import org.json.JSONArray
import androidx.compose.runtime.MutableState

/** Disk schema v2. Only named business values; no Compose/Parcelable registry state. */
internal data class ActFormDraft(
    val formActId: Long = 0L,
    val formCreatedAtMillis: Long = 0L,
    val formRequestId: Long = 0L,
    val formEquipmentId: String = "",
    val equipmentCode: String = "",
    val documentType: DocumentType = DocumentType.DiagnosticAct,
    val createdAt: String = "",
    val equipmentName: String = "",
    val date: String = "",
    val customer: String = "",
    val phone: String = "",
    val customerAddress: String = "",
    val organizationName: String = "",
    val organizationInn: String = "",
    val organizationOgrn: String = "",
    val organizationAddress: String = "",
    val organizationPhone: String = "",
    val organizationEmail: String = "",
    val compilationPlace: String = "",
    val enterpriseCardUri: String = "",
    val enterpriseCardFileName: String = "",
    val enterpriseCardDetectedName: String = "",
    val enterpriseCardDetectedInn: String = "",
    val enterpriseCardDetectedOgrn: String = "",
    val enterpriseCardDetectedAddress: String = "",
    val enterpriseCardDetectedPhone: String = "",
    val enterpriseCardDetectedEmail: String = "",
    val showEnterpriseCardDetectedDialog: Boolean = false,
    val brandSelection: String = "",
    val customBrand: String = "",
    val modelSelection: String = "",
    val customModel: String = "",
    val serialNumber: String = "",
    val operatingTime: String = "",
    val customerRepresentative: String = "",
    val customerRepresentativePhone: String = "",
    val malfunctionDescription: String = "",
    val transferCompleteness: String = "",
    val transferExternalCondition: String = "",
    val diagnosisType: DiagnosisType = DiagnosisType.Primary,
    val checklistItems: List<ChecklistItemState> = emptyList(),
    val preliminaryConclusion: String = "",
    val rootCause: String = "",
    val requiredWorks: String = "",
    val pdfPath: String = "",
    val comment: String = "",
    val photos: List<ActPhoto> = emptyList(),
    val nameplatePhotoId: String = "",
    val customerSignatureDraft: List<SignatureStroke> = emptyList(),
    val customerSignatureSaved: List<SignatureStroke> = emptyList(),
    val executorSignatureDraft: List<SignatureStroke> = emptyList(),
    val executorSignatureSaved: List<SignatureStroke> = emptyList(),
    val directorSignatureDraft: List<SignatureStroke> = emptyList(),
    val directorSignatureSaved: List<SignatureStroke> = emptyList(),
    val signatureDecoding: String = "",
    val customerSignatureDecoding: String = "",
    val executorSignatureDecoding: String = "",
    val directorSignatureDecoding: String = "",
    val pendingCameraPath: String = "",
    val pendingNameplateCameraPath: String = "",
    val requestNumber: String = "",
    val requestNumberEditedManually: Boolean = false,
    val equipmentTransferState: EquipmentTransferState = EquipmentTransferState.Working,
    val confirmedEquipmentCorrection: String = "",
    val showEquipmentCorrectionDialog: Boolean = false,
    val signatureBinding: String = "",
    val legacyRegistryArchive: String = ""
)

internal object ActFormDraftCodec {
    private val names = setOf("formActId","formCreatedAtMillis","formRequestId","formEquipmentId","equipmentCode","documentType","createdAt","equipmentName","date","customer","phone","customerAddress","organizationName","organizationInn","organizationOgrn","organizationAddress","organizationPhone","organizationEmail","compilationPlace","enterpriseCardUri","enterpriseCardFileName","enterpriseCardDetectedName","enterpriseCardDetectedInn","enterpriseCardDetectedOgrn","enterpriseCardDetectedAddress","enterpriseCardDetectedPhone","enterpriseCardDetectedEmail","showEnterpriseCardDetectedDialog","brandSelection","customBrand","modelSelection","customModel","serialNumber","operatingTime","customerRepresentative","customerRepresentativePhone","malfunctionDescription","transferCompleteness","transferExternalCondition","diagnosisType","checklistItems","preliminaryConclusion","rootCause","requiredWorks","pdfPath","comment","photos","nameplatePhotoId","customerSignatureDraft","customerSignatureSaved","executorSignatureDraft","executorSignatureSaved","directorSignatureDraft","directorSignatureSaved","signatureDecoding","customerSignatureDecoding","executorSignatureDecoding","directorSignatureDecoding","pendingCameraPath","pendingNameplateCameraPath","requestNumber","requestNumberEditedManually","equipmentTransferState","confirmedEquipmentCorrection","showEquipmentCorrectionDialog","signatureBinding","legacyRegistryArchive")
    fun encode(d: ActFormDraft, ownerKey: String): JSONObject = JSONObject().put("format", 2).put("ownerKey", ownerKey).put("business", JSONObject().apply {
        put("formActId", d.formActId.toString())
        put("formCreatedAtMillis", d.formCreatedAtMillis.toString())
        put("formRequestId", d.formRequestId.toString())
        put("formEquipmentId", d.formEquipmentId)
        put("equipmentCode", d.equipmentCode)
        put("documentType", d.documentType.name)
        put("createdAt", d.createdAt)
        put("equipmentName", d.equipmentName)
        put("date", d.date)
        put("customer", d.customer)
        put("phone", d.phone)
        put("customerAddress", d.customerAddress)
        put("organizationName", d.organizationName)
        put("organizationInn", d.organizationInn)
        put("organizationOgrn", d.organizationOgrn)
        put("organizationAddress", d.organizationAddress)
        put("organizationPhone", d.organizationPhone)
        put("organizationEmail", d.organizationEmail)
        put("compilationPlace", d.compilationPlace)
        put("enterpriseCardUri", d.enterpriseCardUri)
        put("enterpriseCardFileName", d.enterpriseCardFileName)
        put("enterpriseCardDetectedName", d.enterpriseCardDetectedName)
        put("enterpriseCardDetectedInn", d.enterpriseCardDetectedInn)
        put("enterpriseCardDetectedOgrn", d.enterpriseCardDetectedOgrn)
        put("enterpriseCardDetectedAddress", d.enterpriseCardDetectedAddress)
        put("enterpriseCardDetectedPhone", d.enterpriseCardDetectedPhone)
        put("enterpriseCardDetectedEmail", d.enterpriseCardDetectedEmail)
        put("showEnterpriseCardDetectedDialog", d.showEnterpriseCardDetectedDialog)
        put("brandSelection", d.brandSelection)
        put("customBrand", d.customBrand)
        put("modelSelection", d.modelSelection)
        put("customModel", d.customModel)
        put("serialNumber", d.serialNumber)
        put("operatingTime", d.operatingTime)
        put("customerRepresentative", d.customerRepresentative)
        put("customerRepresentativePhone", d.customerRepresentativePhone)
        put("malfunctionDescription", d.malfunctionDescription)
        put("transferCompleteness", d.transferCompleteness)
        put("transferExternalCondition", d.transferExternalCondition)
        put("diagnosisType", d.diagnosisType.name)
        put("checklistItems", JSONArray().apply { d.checklistItems.forEach { put(checkItem(it)) } })
        put("preliminaryConclusion", d.preliminaryConclusion)
        put("rootCause", d.rootCause)
        put("requiredWorks", d.requiredWorks)
        put("pdfPath", d.pdfPath)
        put("comment", d.comment)
        put("photos", JSONArray().apply { d.photos.forEach { put(photo(it)) } })
        put("nameplatePhotoId", d.nameplatePhotoId)
        put("customerSignatureDraft", JSONArray().apply { d.customerSignatureDraft.forEach { put(stroke(it)) } })
        put("customerSignatureSaved", JSONArray().apply { d.customerSignatureSaved.forEach { put(stroke(it)) } })
        put("executorSignatureDraft", JSONArray().apply { d.executorSignatureDraft.forEach { put(stroke(it)) } })
        put("executorSignatureSaved", JSONArray().apply { d.executorSignatureSaved.forEach { put(stroke(it)) } })
        put("directorSignatureDraft", JSONArray().apply { d.directorSignatureDraft.forEach { put(stroke(it)) } })
        put("directorSignatureSaved", JSONArray().apply { d.directorSignatureSaved.forEach { put(stroke(it)) } })
        put("signatureDecoding", d.signatureDecoding)
        put("customerSignatureDecoding", d.customerSignatureDecoding)
        put("executorSignatureDecoding", d.executorSignatureDecoding)
        put("directorSignatureDecoding", d.directorSignatureDecoding)
        put("pendingCameraPath", d.pendingCameraPath)
        put("pendingNameplateCameraPath", d.pendingNameplateCameraPath)
        put("requestNumber", d.requestNumber)
        put("requestNumberEditedManually", d.requestNumberEditedManually)
        put("equipmentTransferState", d.equipmentTransferState.name)
        put("confirmedEquipmentCorrection", d.confirmedEquipmentCorrection)
        put("showEquipmentCorrectionDialog", d.showEquipmentCorrectionDialog)
        put("signatureBinding", d.signatureBinding)
        put("legacyRegistryArchive", d.legacyRegistryArchive)
    })
    fun decode(json: JSONObject, ownerKey: String): ActFormDraft {
        val format = json.get("format")
        require(format is Number && format.toDouble() in listOf(1.0,2.0)) { "Неизвестный формат черновика; данные не заменены" }
        if (format.toDouble() == 1.0) return migrateLegacy(json)
        require(json.keys().asSequence().toSet() == setOf("format","ownerKey","business")) { "Неизвестный состав черновика" }
        require(json.getInt("format") == 2) { "Неизвестный формат черновика; данные не заменены" }
        require(json.getString("ownerKey") == ownerKey) { "Черновик другой заявки/акта" }
        val b = json.getJSONObject("business")
        require(b.keys().asSequence().toSet() == names) { "Неполный или неизвестный состав черновика" }
        return ActFormDraft(
            formActId = string(b,"formActId").toLong(),
            formCreatedAtMillis = string(b,"formCreatedAtMillis").toLong(),
            formRequestId = string(b,"formRequestId").toLong(),
            formEquipmentId = string(b,"formEquipmentId"),
            equipmentCode = string(b,"equipmentCode"),
            documentType = DocumentType.valueOf(string(b,"documentType")),
            createdAt = string(b,"createdAt"),
            equipmentName = string(b,"equipmentName"),
            date = string(b,"date"),
            customer = string(b,"customer"),
            phone = string(b,"phone"),
            customerAddress = string(b,"customerAddress"),
            organizationName = string(b,"organizationName"),
            organizationInn = string(b,"organizationInn"),
            organizationOgrn = string(b,"organizationOgrn"),
            organizationAddress = string(b,"organizationAddress"),
            organizationPhone = string(b,"organizationPhone"),
            organizationEmail = string(b,"organizationEmail"),
            compilationPlace = string(b,"compilationPlace"),
            enterpriseCardUri = string(b,"enterpriseCardUri"),
            enterpriseCardFileName = string(b,"enterpriseCardFileName"),
            enterpriseCardDetectedName = string(b,"enterpriseCardDetectedName"),
            enterpriseCardDetectedInn = string(b,"enterpriseCardDetectedInn"),
            enterpriseCardDetectedOgrn = string(b,"enterpriseCardDetectedOgrn"),
            enterpriseCardDetectedAddress = string(b,"enterpriseCardDetectedAddress"),
            enterpriseCardDetectedPhone = string(b,"enterpriseCardDetectedPhone"),
            enterpriseCardDetectedEmail = string(b,"enterpriseCardDetectedEmail"),
            showEnterpriseCardDetectedDialog = boolean(b,"showEnterpriseCardDetectedDialog"),
            brandSelection = string(b,"brandSelection"),
            customBrand = string(b,"customBrand"),
            modelSelection = string(b,"modelSelection"),
            customModel = string(b,"customModel"),
            serialNumber = string(b,"serialNumber"),
            operatingTime = string(b,"operatingTime"),
            customerRepresentative = string(b,"customerRepresentative"),
            customerRepresentativePhone = string(b,"customerRepresentativePhone"),
            malfunctionDescription = string(b,"malfunctionDescription"),
            transferCompleteness = string(b,"transferCompleteness"),
            transferExternalCondition = string(b,"transferExternalCondition"),
            diagnosisType = DiagnosisType.valueOf(string(b,"diagnosisType")),
            checklistItems = objects(b.getJSONArray("checklistItems")) { readCheck(it) },
            preliminaryConclusion = string(b,"preliminaryConclusion"),
            rootCause = string(b,"rootCause"),
            requiredWorks = string(b,"requiredWorks"),
            pdfPath = string(b,"pdfPath"),
            comment = string(b,"comment"),
            photos = objects(b.getJSONArray("photos")) { readPhoto(it) },
            nameplatePhotoId = string(b,"nameplatePhotoId"),
            customerSignatureDraft = objects(b.getJSONArray("customerSignatureDraft")) { readStroke(it) },
            customerSignatureSaved = objects(b.getJSONArray("customerSignatureSaved")) { readStroke(it) },
            executorSignatureDraft = objects(b.getJSONArray("executorSignatureDraft")) { readStroke(it) },
            executorSignatureSaved = objects(b.getJSONArray("executorSignatureSaved")) { readStroke(it) },
            directorSignatureDraft = objects(b.getJSONArray("directorSignatureDraft")) { readStroke(it) },
            directorSignatureSaved = objects(b.getJSONArray("directorSignatureSaved")) { readStroke(it) },
            signatureDecoding = string(b,"signatureDecoding"),
            customerSignatureDecoding = string(b,"customerSignatureDecoding"),
            executorSignatureDecoding = string(b,"executorSignatureDecoding"),
            directorSignatureDecoding = string(b,"directorSignatureDecoding"),
            pendingCameraPath = string(b,"pendingCameraPath"),
            pendingNameplateCameraPath = string(b,"pendingNameplateCameraPath"),
            requestNumber = string(b,"requestNumber"),
            requestNumberEditedManually = boolean(b,"requestNumberEditedManually"),
            equipmentTransferState = EquipmentTransferState.valueOf(string(b,"equipmentTransferState")),
            confirmedEquipmentCorrection = string(b,"confirmedEquipmentCorrection"),
            showEquipmentCorrectionDialog = boolean(b,"showEquipmentCorrectionDialog"),
            signatureBinding = string(b,"signatureBinding"),
            legacyRegistryArchive = string(b,"legacyRegistryArchive")
        )
    }
    private fun string(j: JSONObject, k: String): String = j.get(k).also { require(it is String) { "Повреждена строка $k" } } as String
    private fun boolean(j: JSONObject, k: String): Boolean = j.get(k).also { require(it is Boolean) { "Повреждён флаг $k" } } as Boolean
    private fun <T> objects(a: JSONArray, read: (JSONObject) -> T): List<T> = (0 until a.length()).map { read(a.getJSONObject(it)) }
    private fun photo(p: ActPhoto) = JSONObject().put("id",p.id).put("filePath",p.filePath)
    private fun readPhoto(j: JSONObject) = ActPhoto(string(j,"id"), string(j,"filePath"))
    private fun checkItem(c: ChecklistItemState) = JSONObject().put("key",c.key).put("title",c.title).put("checked",c.checked).put("faulty",c.faulty).put("comment",c.comment)
    private fun readCheck(j: JSONObject) = ChecklistItemState(string(j,"key"),string(j,"title"),boolean(j,"checked"),boolean(j,"faulty"),string(j,"comment"))
    private fun stroke(s: SignatureStroke) = JSONObject().put("points",JSONArray().apply { s.points.forEach { p -> require(p.x.isFinite() && p.y.isFinite()); put(JSONObject().put("x",p.x.toDouble()).put("y",p.y.toDouble())) } })
    private fun readStroke(j: JSONObject) = SignatureStroke(objects(j.getJSONArray("points")) { SignaturePoint(it.getDouble("x").toFloat(),it.getDouble("y").toFloat()).also { p -> require(p.x.isFinite() && p.y.isFinite()) } })

    /** Old saved drafts migrate by named business fields. All original registry bytes retained, never silently discarded. */
    private fun migrateLegacy(json: JSONObject): ActFormDraft {
        val state = DraftCodec.decodeState(json)
        fun value(key: String): Any? {
            val entries = state[key] ?: error("Старый черновик неполон ($key); сохранён без изменений")
            require(entries.size == 1) { "Неоднозначный старый черновик $key" }
            val v=entries.single(); return if (v is MutableState<*>) v.value else v
        }
        fun strings(key: String): List<String> = (value(key) as List<*>).map { it as String }
        return ActFormDraft(
            formActId = value("formActId") as Long,
            formCreatedAtMillis = value("formCreatedAtMillis") as Long,
            formRequestId = value("formRequestId") as Long,
            formEquipmentId = value("formEquipmentId") as String,
            equipmentCode = value("equipmentCode") as String,
            documentType = value("documentType") as DocumentType,
            createdAt = value("createdAt") as String,
            equipmentName = value("equipmentName") as String,
            date = value("date") as String,
            customer = value("customer") as String,
            phone = value("phone") as String,
            customerAddress = value("customerAddress") as String,
            organizationName = value("organizationName") as String,
            organizationInn = value("organizationInn") as String,
            organizationOgrn = value("organizationOgrn") as String,
            organizationAddress = value("organizationAddress") as String,
            organizationPhone = value("organizationPhone") as String,
            organizationEmail = value("organizationEmail") as String,
            compilationPlace = value("compilationPlace") as String,
            enterpriseCardUri = value("enterpriseCardUri") as String,
            enterpriseCardFileName = value("enterpriseCardFileName") as String,
            enterpriseCardDetectedName = value("enterpriseCardDetectedName") as String,
            enterpriseCardDetectedInn = value("enterpriseCardDetectedInn") as String,
            enterpriseCardDetectedOgrn = value("enterpriseCardDetectedOgrn") as String,
            enterpriseCardDetectedAddress = value("enterpriseCardDetectedAddress") as String,
            enterpriseCardDetectedPhone = value("enterpriseCardDetectedPhone") as String,
            enterpriseCardDetectedEmail = value("enterpriseCardDetectedEmail") as String,
            showEnterpriseCardDetectedDialog = value("showEnterpriseCardDetectedDialog") as Boolean,
            brandSelection = value("brandSelection") as String,
            customBrand = value("customBrand") as String,
            modelSelection = value("modelSelection") as String,
            customModel = value("customModel") as String,
            serialNumber = value("serialNumber") as String,
            operatingTime = value("operatingTime") as String,
            customerRepresentative = value("customerRepresentative") as String,
            customerRepresentativePhone = value("customerRepresentativePhone") as String,
            malfunctionDescription = value("malfunctionDescription") as String,
            transferCompleteness = value("transferCompleteness") as String,
            transferExternalCondition = value("transferExternalCondition") as String,
            diagnosisType = value("diagnosisType") as DiagnosisType,
            checklistItems = strings("checklistItems").also { require(it.size % 5 == 0) }.chunked(5).map { ChecklistItemState(it[0],it[1],it[2].toBooleanStrict(),it[3].toBooleanStrict(),it[4]) },
            preliminaryConclusion = value("preliminaryConclusion") as String,
            rootCause = value("rootCause") as String,
            requiredWorks = value("requiredWorks") as String,
            pdfPath = value("pdfPath") as String,
            comment = value("comment") as String,
            photos = strings("photos").also { require(it.size % 2 == 0) }.chunked(2).map { ActPhoto(it[0],it[1]) },
            nameplatePhotoId = value("nameplatePhotoId") as String,
            customerSignatureDraft = strings("customerSignatureDraft").map { raw -> SignatureStroke(raw.split(";").map { pair -> pair.split(",").also { require(it.size == 2) }.let { SignaturePoint(it[0].toFloat(),it[1].toFloat()) } }) },
            customerSignatureSaved = strings("customerSignatureSaved").map { raw -> SignatureStroke(raw.split(";").map { pair -> pair.split(",").also { require(it.size == 2) }.let { SignaturePoint(it[0].toFloat(),it[1].toFloat()) } }) },
            executorSignatureDraft = strings("executorSignatureDraft").map { raw -> SignatureStroke(raw.split(";").map { pair -> pair.split(",").also { require(it.size == 2) }.let { SignaturePoint(it[0].toFloat(),it[1].toFloat()) } }) },
            executorSignatureSaved = strings("executorSignatureSaved").map { raw -> SignatureStroke(raw.split(";").map { pair -> pair.split(",").also { require(it.size == 2) }.let { SignaturePoint(it[0].toFloat(),it[1].toFloat()) } }) },
            directorSignatureDraft = strings("directorSignatureDraft").map { raw -> SignatureStroke(raw.split(";").map { pair -> pair.split(",").also { require(it.size == 2) }.let { SignaturePoint(it[0].toFloat(),it[1].toFloat()) } }) },
            directorSignatureSaved = strings("directorSignatureSaved").map { raw -> SignatureStroke(raw.split(";").map { pair -> pair.split(",").also { require(it.size == 2) }.let { SignaturePoint(it[0].toFloat(),it[1].toFloat()) } }) },
            signatureDecoding = value("signatureDecoding") as String,
            customerSignatureDecoding = value("customerSignatureDecoding") as String,
            executorSignatureDecoding = value("executorSignatureDecoding") as String,
            directorSignatureDecoding = value("directorSignatureDecoding") as String,
            pendingCameraPath = value("pendingCameraPath") as String,
            pendingNameplateCameraPath = value("pendingNameplateCameraPath") as String,
            requestNumber = value("requestNumber") as String,
            requestNumberEditedManually = value("requestNumberEditedManually") as Boolean,
            equipmentTransferState = value("equipmentTransferState") as EquipmentTransferState,
            confirmedEquipmentCorrection = value("confirmedEquipmentCorrection") as String,
            showEquipmentCorrectionDialog = value("showEquipmentCorrectionDialog") as Boolean,
            signatureBinding = value("signatureBinding") as String,
            legacyRegistryArchive = json.toString()
        )
    }
}

/** Shared by the UI and disk fault/callback tests; only a confirmed persist unlocks an action. */
internal class DraftPersistence(private val ownerKey: String, private val persist: (JSONObject) -> Unit) {
    private var lastSaved: String? = null
    fun save(draft: ActFormDraft) {
        val encoded = ActFormDraftCodec.encode(draft, ownerKey)
        val bytes = encoded.toString()
        if (lastSaved != bytes) {
            try { persist(encoded); lastSaved = bytes }
            catch (error: Exception) { lastSaved = null; throw error }
        }
    }
    fun beforeAction(draft: ActFormDraft, action: () -> Unit): Result<Unit> = runCatching { save(draft); action() }
}

/** Every original and pending intent is scoped to the same request/equipment/act before restore or write. */
internal fun validateDraftOwnership(d: ActFormDraft, ownerKey: String, filesDir: java.io.File, existingActs: List<ActRecord> = emptyList()) {
    val owner = Regex("form-v1-(-?\\d+)-(-?\\d+)-(.+)").matchEntire(ownerKey) ?: error("Неизвестный ключ черновика")
    require(d.formRequestId > 0 && d.formActId > 0 && d.formEquipmentId.matches(Regex("[a-zA-Z0-9-]+"))) { "Повреждён владелец черновика" }
    val request=owner.groupValues[1].toLong();val act=owner.groupValues[2].toLong()
    require((request < 0 || request == d.formRequestId) && (act < 0 || act == d.formActId)) { "Черновик другой заявки/акта" }
    val currentDir=java.io.File(filesDir,"act_photos/request_${d.formRequestId}/equipment_${d.formEquipmentId}/act_${d.formActId}")
    val ancestry=linkedSetOf(d.formActId)
    var version=existingActs.firstOrNull { it.id == d.formActId }
    while (version != null) {
        require(version.requestId == d.formRequestId && version.equipmentId == d.formEquipmentId && version.documentType == d.documentType) { "Редакция другой заявки/единицы/типа" }
        if (version.supersedesId == 0L) break
        require(ancestry.add(version.supersedesId)) { "Цикл редакций" }
        version=existingActs.firstOrNull { it.id == version!!.supersedesId } ?: error("Отсутствует родитель редакции")
    }
    val inherited=existingActs.filter { it.id in ancestry }.flatMap { it.photos }.map { java.io.File(it.filePath).canonicalPath }.toSet()
    require(d.photos.all { p ->
        val file=java.io.File(p.filePath)
        LocalRules.isWithinDirectory(file,currentDir) ||
            (file.canonicalPath in inherited && LocalRules.isWithinDirectory(file,java.io.File(filesDir,"act_photos")))
    }) { "Фото другой заявки/единицы/акта или неизвестного предка" }
    require(listOf(d.pendingCameraPath,d.pendingNameplateCameraPath).filter { it.isNotBlank() }.all { LocalRules.isWithinDirectory(java.io.File(it),currentDir) }) { "Ожидающая камера другой заявки/единицы/акта" }
    require(d.photos.map { it.id }.distinct().size == d.photos.size) { "Повторные ID фотографий" }
    require(d.nameplatePhotoId.isBlank() || d.photos.any { it.id == d.nameplatePhotoId }) { "Шильдик не связан с фото этой формы" }
}

internal enum class CameraPhotoRole { ATTACHMENT, NAMEPLATE }
internal data class CameraResultState(val photos: List<ActPhoto>, val nameplateId: String, val pendingPath: String)
/** Called by both actual TakePicture callbacks. Failure never attaches an unconfirmed original. */
internal fun resolveCameraResult(photos: List<ActPhoto>, nameplateId: String, pendingPath: String,
    role: CameraPhotoRole, success: Boolean, confirm: (ActPhoto) -> ActPhoto): CameraResultState {
    if (!success) return CameraResultState(photos,nameplateId,"")
    require(pendingPath.isNotBlank()) { "Результат камеры без сохранённого ожидающего пути" }
    val candidate=ActPhoto(java.io.File(pendingPath).nameWithoutExtension,pendingPath)
    val confirmed=confirm(candidate)
    require(confirmed.id == candidate.id && confirmed.filePath == candidate.filePath)
    val previous=photos.firstOrNull { it.id == confirmed.id }
    require(previous == null || previous == confirmed) { "Конфликт ID фотографии" }
    val updated=if(previous != null) photos else if(role==CameraPhotoRole.NAMEPLATE) listOf(confirmed)+photos else photos+confirmed
    return CameraResultState(updated,if(role==CameraPhotoRole.NAMEPLATE) confirmed.id else nameplateId,"")
}
