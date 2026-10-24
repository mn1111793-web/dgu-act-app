package com.example.dguactapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RequestRecord(
    val id: Long,
    val requestNumber: String,
    val createdAt: String,
    val date: String,
    val customer: String,
    val phone: String,
    val customerAddress: String,
    val organizationName: String,
    val organizationInn: String,
    val organizationOgrn: String,
    val organizationAddress: String,
    val organizationPhone: String,
    val organizationEmail: String,
    val customerRepresentative: String,
    val customerRepresentativePhone: String,
    val compilationPlace: String,
    val enterpriseCardUri: String,
    val equipmentCode: String,
    val equipmentName: String,
    val brand: String,
    val model: String,
    val serialNumber: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val equipmentId: String = java.util.UUID.randomUUID().toString(),
    val reservedSequence: Int = 0
)

data class ActRecord(
    val id: Long,
    val requestId: Long,
    val documentType: DocumentType = DocumentType.DiagnosticAct,
    val status: ActStatus = ActStatus.Draft,
    val createdAt: String = "",
    val requestNumber: String,
    val date: String,
    val customer: String,
    val phone: String = "",
    val customerAddress: String,
    val organizationName: String = "",
    val organizationInn: String = "",
    val organizationOgrn: String = "",
    val organizationAddress: String = "",
    val organizationPhone: String = "",
    val organizationEmail: String = "",
    val compilationPlace: String = "",
    val equipmentCode: String,
    val equipmentName: String,
    val brand: String,
    val model: String,
    val serialNumber: String,
    val operatingTime: String,
    val completeness: String,
    val externalCondition: String,
    val malfunctionDescription: String,
    val customerRepresentative: String = "",
    val customerRepresentativePhone: String = "",
    val diagnosisType: DiagnosisType = DiagnosisType.Primary,
    val checklistItems: List<ChecklistItemState> = emptyList(),
    val preliminaryConclusion: String = "",
    val rootCause: String = "",
    val requiredWorks: String = "",
    val equipmentTransferState: String = "",
    val pdfPath: String = "",
    val comment: String = "",
    val photos: List<ActPhoto> = emptyList(),
    val customerSignature: List<SignatureStroke> = emptyList(),
    val executorSignature: List<SignatureStroke> = emptyList(),
    val directorSignature: List<SignatureStroke> = emptyList(),
    val signatureDecoding: String = "",
    val customerSignatureDecoding: String = "",
    val executorSignatureDecoding: String = "",
    val directorSignatureDecoding: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val equipmentId: String = "",
    val revision: Int = 1,
    val supersedesId: Long = 0L,
    val nameplatePhotoId: String = ""
)

object ActStorage {
    private const val preferencesName = "saved_acts"
    private const val actsKey = "acts_json"
    private const val lastSequenceKey = "last_sequence_number"
    private val requestNumberRegex = Regex("^[A-Z]+-(\\d+)-\\d{6}$")

    fun loadActs(context: Context): List<ActRecord> = LocalRepository.read(context).acts

    fun upsertAct(context: Context, act: ActRecord) = LocalRepository.saveAct(context, act)

    fun deleteAct(context: Context, actId: Long): ActRecord? = LocalRepository.deleteAct(context, actId)

    fun nextSequence(acts: List<ActRecord>, requests: List<RequestRecord> = emptyList()): Int {
        val actsMax = acts.maxOfOrNull { extractSequence(it.requestNumber) ?: 0 } ?: 0
        val reqMax = requests.maxOfOrNull { extractSequence(it.requestNumber) ?: 0 } ?: 0
        return maxOf(actsMax, reqMax) + 1
    }

    fun nextSequence(context: Context, acts: List<ActRecord>, requests: List<RequestRecord> = emptyList()): Int =
        maxOf(LocalRepository.read(context).sequence + 1, nextSequence(acts, requests))

    private fun extractSequence(requestNumber: String): Int? =
        requestNumberRegex.matchEntire(requestNumber)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

    internal fun ActRecord.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("requestId", requestId)
        put("createdAtMillis", createdAtMillis)
        put("equipmentId", equipmentId)
        put("revision", revision)
        put("supersedesId", supersedesId)
        put("nameplatePhotoId", nameplatePhotoId)
        put("documentType", documentType.storageValue)
        put("status", status.storageValue)
        put("createdAt", createdAt)
        put("requestNumber", requestNumber)
        put("date", date)
        put("customer", customer)
        put("phone", phone)
        put("customerAddress", customerAddress)
        put("organizationName", organizationName)
        put("organizationInn", organizationInn)
        put("organizationOgrn", organizationOgrn)
        put("organizationAddress", organizationAddress)
        put("organizationPhone", organizationPhone)
        put("organizationEmail", organizationEmail)
        put("compilationPlace", compilationPlace)
        put("equipmentCode", equipmentCode)
        put("equipmentName", equipmentName)
        put("brand", brand)
        put("model", model)
        put("serialNumber", serialNumber)
        put("operatingTime", operatingTime)
        put("completeness", completeness)
        put("externalCondition", externalCondition)
        put("malfunctionDescription", malfunctionDescription)
        put("customerRepresentative", customerRepresentative)
        put("customerRepresentativePhone", customerRepresentativePhone)
        put("diagnosisType", diagnosisType.storageValue)
        put("preliminaryConclusion", preliminaryConclusion)
        put("rootCause", rootCause)
        put("requiredWorks", requiredWorks)
        put("equipmentTransferState", equipmentTransferState)
        put("pdfPath", pdfPath)
        put("comment", comment)
        put(
            "photos",
            JSONArray().apply {
                photos.forEach { photo ->
                    put(
                        JSONObject().apply {
                            put("id", photo.id)
                            put("filePath", photo.filePath)
                        }
                    )
                }
            }
        )
        put(
            "checklistItems",
            JSONArray().apply {
                checklistItems.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("key", item.key)
                            put("title", item.title)
                            put("checked", item.checked)
                            put("faulty", item.faulty)
                            put("comment", item.comment)
                        }
                    )
                }
            }
        )
        put("customerSignature", customerSignature.toJsonArray())
        put("executorSignature", executorSignature.toJsonArray())
        put("directorSignature", directorSignature.toJsonArray())
        put("signatureDecoding", signatureDecoding)
        put("customerSignatureDecoding", customerSignatureDecoding)
        put("executorSignatureDecoding", executorSignatureDecoding)
        put("directorSignatureDecoding", directorSignatureDecoding)
    }

    internal fun JSONObject.toActRecord(): ActRecord {
        require(getLong("id") > 0)
        require(has("requestNumber") && has("date") && has("customer") && has("equipmentCode") && has("serialNumber")) { "Неполный JSON акта" }
        if (has("documentType")) require(DocumentType.values().any { it.storageValue == getString("documentType") })
        if (has("status")) require(ActStatus.values().any { it.storageValue == getString("status") })
        if (has("diagnosisType")) require(DiagnosisType.values().any { it.storageValue == getString("diagnosisType") })
        val diagnosisType = DiagnosisType.fromStorageValue(optString("diagnosisType"))
        val documentType = DocumentType.fromStorageValue(optString("documentType"))
        val status = ActStatus.fromStorageValue(optString("status"))
        val checklistItems = optJSONArray("checklistItems")
            ?.let { jsonArray ->
                buildList {
                    for (index in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(index)
                        add(
                            ChecklistItemState(
                                key = item.optString("key"),
                                title = item.optString("title"),
                                checked = item.optBoolean("checked"),
                                faulty = item.optBoolean("faulty"),
                                comment = item.optString("comment")
                            )
                        )
                    }
                }
            }
            .orEmpty()

        val photos = optJSONArray("photos")
            ?.let { jsonArray ->
                buildList {
                    for (index in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(index)
                        val filePath = item.optString("filePath")
                        require(filePath.isNotBlank()) { "Повреждена ссылка на фото" }
                        if (filePath.isNotBlank()) {
                            add(
                                ActPhoto(
                                    id = if (has("createdAtMillis")) item.getString("id").also { require(it.isNotBlank()) } else item.optString("id").ifBlank { java.util.UUID.nameUUIDFromBytes(filePath.toByteArray(Charsets.UTF_8)).toString() },
                                    filePath = filePath
                                )
                            )
                        }
                    }
                }
            }
            .orEmpty()

        val fallbackId = getLong("id")
        val legacyRecord = ActRecord(
            id = fallbackId,
            createdAtMillis = if (has("createdAtMillis")) getLong("createdAtMillis") else LocalRules.legacyTime(optString("createdAt").ifBlank { optString("date") }),
            equipmentId = if (has("createdAtMillis")) getString("equipmentId") else optString("equipmentId").ifBlank { "legacy-equipment-${optLong("requestId", fallbackId)}" },
            revision = optInt("revision", 1),
            supersedesId = optLong("supersedesId", 0),
            nameplatePhotoId = optString("nameplatePhotoId"),
            requestId = if (has("createdAtMillis")) getLong("requestId") else optLong("requestId", fallbackId),
            documentType = documentType,
            status = status,
            createdAt = if (has("createdAtMillis")) getString("createdAt") else optString("createdAt").ifBlank { optString("date") },
            requestNumber = optString("requestNumber"),
            date = optString("date"),
            customer = optString("customer"),
            phone = optString("phone"),
            customerAddress = optString("customerAddress"),
            organizationName = optString("organizationName"),
            organizationInn = optString("organizationInn"),
            organizationOgrn = optString("organizationOgrn"),
            organizationAddress = optString("organizationAddress"),
            organizationPhone = optString("organizationPhone"),
            organizationEmail = optString("organizationEmail"),
            compilationPlace = optString("compilationPlace"),
            equipmentCode = optString("equipmentCode"),
            equipmentName = optString("equipmentName"),
            brand = optString("brand"),
            model = optString("model"),
            serialNumber = optString("serialNumber"),
            operatingTime = optString("operatingTime"),
            completeness = optString("completeness"),
            externalCondition = optString("externalCondition"),
            malfunctionDescription = optString("malfunctionDescription"),
            customerRepresentative = optString("customerRepresentative"),
            customerRepresentativePhone = optString("customerRepresentativePhone")
        )

        return legacyRecord.copy(
            diagnosisType = diagnosisType,
            checklistItems = if (has("createdAtMillis")) checklistItems else DiagnosticChecklistCatalog.stateFor(
                type = DiagnosisType.Advanced,
                savedItems = checklistItems,
                legacyAct = legacyRecord
            ),
            preliminaryConclusion = if (has("createdAtMillis")) optString("preliminaryConclusion") else optString("preliminaryConclusion").ifBlank {
                DiagnosticChecklistCatalog.primaryConclusionFromLegacy(legacyRecord)
            },
            rootCause = optString("rootCause"),
            requiredWorks = optString("requiredWorks"),
            equipmentTransferState = optString("equipmentTransferState"),
            pdfPath = optString("pdfPath"),
            comment = optString("comment"),
            photos = photos,
            customerSignature = optJSONArray("customerSignature").toSignatureStrokes(),
            executorSignature = optJSONArray("executorSignature").toSignatureStrokes(),
            directorSignature = optJSONArray("directorSignature").toSignatureStrokes(),
            signatureDecoding = optString("signatureDecoding"),
            customerSignatureDecoding = if (has("createdAtMillis")) getString("customerSignatureDecoding") else optString("customerSignatureDecoding").ifBlank { optString("signatureDecoding") },
            executorSignatureDecoding = if (has("createdAtMillis")) getString("executorSignatureDecoding") else optString("executorSignatureDecoding").ifBlank { optString("signatureDecoding") },
            directorSignatureDecoding = if (has("createdAtMillis")) getString("directorSignatureDecoding") else optString("directorSignatureDecoding").ifBlank { optString("signatureDecoding") }
        )
    }

    private fun List<SignatureStroke>.toJsonArray(): JSONArray = JSONArray().apply {
        this@toJsonArray.forEach { stroke ->
            put(
                JSONArray().apply {
                    stroke.points.forEach { point ->
                        put(
                            JSONObject().apply {
                                put("x", point.x)
                                put("y", point.y)
                            }
                        )
                    }
                }
            )
        }
    }

    private fun JSONArray?.toSignatureStrokes(): List<SignatureStroke> {
        if (this == null) return emptyList()
        return buildList {
            for (strokeIndex in 0 until length()) {
                val strokeArray = getJSONArray(strokeIndex)
                val points = buildList {
                    for (pointIndex in 0 until strokeArray.length()) {
                        val pointObject = strokeArray.getJSONObject(pointIndex)
                        add(
                            SignaturePoint(
                                x = pointObject.getDouble("x").toFloat(),
                                y = pointObject.getDouble("y").toFloat()
                            )
                        )
                    }
                }
                if (points.isNotEmpty()) {
                    add(SignatureStroke(points = points))
                }
            }
        }
    }
}

object RequestStorage {
    private const val preferencesName = "saved_requests"
    private const val requestsKey = "requests_json"

    fun loadRequests(context: Context, acts: List<ActRecord>): List<RequestRecord> = LocalRepository.read(context).requests

    fun upsertRequest(context: Context, request: RequestRecord) = LocalRepository.saveRequest(context, request)

    fun deleteRequest(context: Context, requestId: Long) = LocalRepository.deleteRequest(context, requestId)

    internal fun RequestRecord.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("createdAtMillis", createdAtMillis)
        put("equipmentId", equipmentId)
        put("reservedSequence", reservedSequence)
        put("requestNumber", requestNumber)
        put("createdAt", createdAt)
        put("date", date)
        put("customer", customer)
        put("phone", phone)
        put("customerAddress", customerAddress)
        put("organizationName", organizationName)
        put("organizationInn", organizationInn)
        put("organizationOgrn", organizationOgrn)
        put("organizationAddress", organizationAddress)
        put("organizationPhone", organizationPhone)
        put("organizationEmail", organizationEmail)
        put("customerRepresentative", customerRepresentative)
        put("customerRepresentativePhone", customerRepresentativePhone)
        put("compilationPlace", compilationPlace)
        put("enterpriseCardUri", enterpriseCardUri)
        put("equipmentCode", equipmentCode)
        put("equipmentName", equipmentName)
        put("brand", brand)
        put("model", model)
        put("serialNumber", serialNumber)
    }

    internal fun JSONObject.toRequestRecord(): RequestRecord = RequestRecord(
        id = getLong("id"),
        reservedSequence = optInt("reservedSequence", 0),
        createdAtMillis = if (has("createdAtMillis")) getLong("createdAtMillis") else LocalRules.legacyTime(optString("createdAt").ifBlank { optString("date") }),
        equipmentId = if (has("createdAtMillis")) getString("equipmentId") else optString("equipmentId").ifBlank { "legacy-equipment-${getLong("id")}" },
        requestNumber = optString("requestNumber"),
        createdAt = optString("createdAt"),
        date = optString("date"),
        customer = optString("customer"),
        phone = optString("phone"),
        customerAddress = optString("customerAddress"),
        organizationName = optString("organizationName"),
        organizationInn = optString("organizationInn"),
        organizationOgrn = optString("organizationOgrn"),
        organizationAddress = optString("organizationAddress"),
        organizationPhone = optString("organizationPhone"),
        organizationEmail = optString("organizationEmail"),
        customerRepresentative = optString("customerRepresentative"),
        customerRepresentativePhone = optString("customerRepresentativePhone"),
        compilationPlace = optString("compilationPlace"),
        enterpriseCardUri = optString("enterpriseCardUri"),
        equipmentCode = optString("equipmentCode"),
        equipmentName = optString("equipmentName"),
        brand = optString("brand"),
        model = optString("model"),
        serialNumber = optString("serialNumber")
    )
}

internal fun ActRecord.toRequestRecord(): RequestRecord = RequestRecord(
    id = requestId,
    createdAtMillis = createdAtMillis,
    equipmentId = equipmentId,
    requestNumber = requestNumber,
    createdAt = createdAt,
    date = date,
    customer = customer,
    phone = phone,
    customerAddress = customerAddress,
    organizationName = organizationName,
    organizationInn = organizationInn,
    organizationOgrn = organizationOgrn,
    organizationAddress = organizationAddress,
    organizationPhone = organizationPhone,
    organizationEmail = organizationEmail,
    customerRepresentative = customerRepresentative,
    customerRepresentativePhone = customerRepresentativePhone,
    compilationPlace = compilationPlace,
    enterpriseCardUri = "",
    equipmentCode = equipmentCode,
    equipmentName = equipmentName,
    brand = brand,
    model = model,
    serialNumber = serialNumber
)
