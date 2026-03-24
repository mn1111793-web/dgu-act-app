package com.example.dguactapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ActRecord(
    val id: Long,
    val requestNumber: String,
    val date: String,
    val customer: String,
    val customerAddress: String,
    val equipmentCode: String,
    val equipmentName: String,
    val brand: String,
    val model: String,
    val serialNumber: String,
    val operatingTime: String,
    val completeness: String,
    val externalCondition: String,
    val malfunctionDescription: String,
    val diagnosisType: DiagnosisType = DiagnosisType.Primary,
    val checklistItems: List<ChecklistItemState> = emptyList(),
    val preliminaryConclusion: String = "",
    val rootCause: String = "",
    val requiredWorks: String = "",
    val photos: List<ActPhoto> = emptyList(),
    val customerSignature: List<SignatureStroke> = emptyList(),
    val executorSignature: List<SignatureStroke> = emptyList(),
    val directorSignature: List<SignatureStroke> = emptyList()
)

object ActStorage {
    private const val preferencesName = "saved_acts"
    private const val actsKey = "acts_json"
    private val requestNumberRegex = Regex("^[A-Z]+-(\\d+)-\\d{6}$")

    fun loadActs(context: Context): List<ActRecord> {
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(actsKey, null).orEmpty()
        if (rawJson.isBlank()) return emptyList()

        return runCatching {
            val jsonArray = JSONArray(rawJson)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(index) ?: continue
                    add(item.toActRecord())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun upsertAct(context: Context, act: ActRecord) {
        val updatedActs = loadActs(context)
            .filterNot { it.id == act.id }
            .toMutableList()
            .apply { add(0, act) }
        saveActs(context, updatedActs)
    }

    fun nextSequence(acts: List<ActRecord>): Int = acts.maxOfOrNull { act ->
        requestNumberRegex.matchEntire(act.requestNumber)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }?.plus(1) ?: 1

    private fun saveActs(context: Context, acts: List<ActRecord>) {
        val jsonArray = JSONArray()
        acts.forEach { act -> jsonArray.put(act.toJson()) }
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(actsKey, jsonArray.toString())
            .apply()
    }

    private fun ActRecord.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("requestNumber", requestNumber)
        put("date", date)
        put("customer", customer)
        put("customerAddress", customerAddress)
        put("equipmentCode", equipmentCode)
        put("equipmentName", equipmentName)
        put("brand", brand)
        put("model", model)
        put("serialNumber", serialNumber)
        put("operatingTime", operatingTime)
        put("completeness", completeness)
        put("externalCondition", externalCondition)
        put("malfunctionDescription", malfunctionDescription)
        put("diagnosisType", diagnosisType.storageValue)
        put("preliminaryConclusion", preliminaryConclusion)
        put("rootCause", rootCause)
        put("requiredWorks", requiredWorks)
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
    }

    private fun JSONObject.toActRecord(): ActRecord {
        val diagnosisType = DiagnosisType.fromStorageValue(optString("diagnosisType"))
        val checklistItems = optJSONArray("checklistItems")
            ?.let { jsonArray ->
                buildList {
                    for (index in 0 until jsonArray.length()) {
                        val item = jsonArray.optJSONObject(index) ?: continue
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
                        val item = jsonArray.optJSONObject(index) ?: continue
                        val filePath = item.optString("filePath")
                        if (filePath.isNotBlank()) {
                            add(
                                ActPhoto(
                                    id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                                    filePath = filePath
                                )
                            )
                        }
                    }
                }
            }
            .orEmpty()

        val legacyRecord = ActRecord(
            id = optLong("id"),
            requestNumber = optString("requestNumber"),
            date = optString("date"),
            customer = optString("customer"),
            customerAddress = optString("customerAddress"),
            equipmentCode = optString("equipmentCode"),
            equipmentName = optString("equipmentName"),
            brand = optString("brand"),
            model = optString("model"),
            serialNumber = optString("serialNumber"),
            operatingTime = optString("operatingTime"),
            completeness = optString("completeness"),
            externalCondition = optString("externalCondition"),
            malfunctionDescription = optString("malfunctionDescription")
        )

        return legacyRecord.copy(
            diagnosisType = diagnosisType,
            checklistItems = DiagnosticChecklistCatalog.stateFor(
                type = diagnosisType,
                savedItems = checklistItems,
                legacyAct = legacyRecord
            ),
            preliminaryConclusion = optString("preliminaryConclusion").ifBlank {
                DiagnosticChecklistCatalog.primaryConclusionFromLegacy(legacyRecord)
            },
            rootCause = optString("rootCause"),
            requiredWorks = optString("requiredWorks"),
            photos = photos,
            customerSignature = optJSONArray("customerSignature").toSignatureStrokes(),
            executorSignature = optJSONArray("executorSignature").toSignatureStrokes(),
            directorSignature = optJSONArray("directorSignature").toSignatureStrokes()
        )
    }

    private fun List<SignatureStroke>.toJsonArray(): JSONArray = JSONArray().apply {
        forEach { stroke ->
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
                val strokeArray = optJSONArray(strokeIndex) ?: continue
                val points = buildList {
                    for (pointIndex in 0 until strokeArray.length()) {
                        val pointObject = strokeArray.optJSONObject(pointIndex) ?: continue
                        add(
                            SignaturePoint(
                                x = pointObject.optDouble("x").toFloat(),
                                y = pointObject.optDouble("y").toFloat()
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
