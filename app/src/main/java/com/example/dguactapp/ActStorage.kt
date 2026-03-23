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
    val equipmentName: String,
    val model: String,
    val serialNumber: String,
    val operatingTime: String,
    val completeness: String,
    val externalCondition: String,
    val malfunctionDescription: String
)

object ActStorage {
    private const val preferencesName = "saved_acts"
    private const val actsKey = "acts_json"

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

    fun saveAct(context: Context, act: ActRecord) {
        val updatedActs = loadActs(context).toMutableList().apply {
            add(0, act)
        }
        saveActs(context, updatedActs)
    }

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
        put("equipmentName", equipmentName)
        put("model", model)
        put("serialNumber", serialNumber)
        put("operatingTime", operatingTime)
        put("completeness", completeness)
        put("externalCondition", externalCondition)
        put("malfunctionDescription", malfunctionDescription)
    }

    private fun JSONObject.toActRecord(): ActRecord = ActRecord(
        id = optLong("id"),
        requestNumber = optString("requestNumber"),
        date = optString("date"),
        customer = optString("customer"),
        customerAddress = optString("customerAddress"),
        equipmentName = optString("equipmentName"),
        model = optString("model"),
        serialNumber = optString("serialNumber"),
        operatingTime = optString("operatingTime"),
        completeness = optString("completeness"),
        externalCondition = optString("externalCondition"),
        malfunctionDescription = optString("malfunctionDescription")
    )
}
