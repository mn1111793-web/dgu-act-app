package com.example.dguactapp

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/** Explicitly typed JSON, no Java deserialization; state format is independent of Android Bundle internals. */
internal object DraftCodec {
    private fun encode(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is String, is Boolean, is Int -> value
        is Long -> JSONObject().put("long", value.toString())
        is Float -> JSONObject().put("float", value.toDouble())
        is Double -> JSONObject().put("double", value)
        is MutableState<*> -> JSONObject().put("state", encode(value.value))
        is Enum<*> -> JSONObject().put("enum", value.javaClass.name).put("value", value.name)
        is Map<*, *> -> JSONObject().put("map", JSONArray().apply {
            value.forEach { (key, item) -> put(JSONArray().put(encode(key)).put(encode(item))) }
        })
        is Set<*> -> JSONObject().put("set", JSONArray().apply { value.forEach { put(encode(it)) } })
        is IntArray -> JSONObject().put("ints", JSONArray(value.toList()))
        is LongArray -> JSONObject().put("longs", JSONArray().apply { value.forEach { put(it.toString()) } })
        is List<*> -> JSONArray().apply { value.forEach { put(encode(it)) } }
        else -> error("Неподдерживаемый тип черновика: ${value.javaClass.simpleName}")
    }
    private fun decode(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONArray -> (0 until value.length()).map { decode(value.get(it)) }
        is JSONObject -> when {
            value.has("map") -> value.getJSONArray("map").let { pairs ->
                val result = linkedMapOf<Any?, Any?>()
                for (index in 0 until pairs.length()) {
                    val pair = pairs.getJSONArray(index)
                    require(pair.length() == 2)
                    val key = decode(pair.get(0))
                    require(!result.containsKey(key))
                    result[key] = decode(pair.get(1))
                }
                result
            }
            value.has("set") -> (decode(value.getJSONArray("set")) as List<*>).toSet()
            value.has("ints") -> value.getJSONArray("ints").let { values -> IntArray(values.length()) { values.getInt(it) } }
            value.has("longs") -> value.getJSONArray("longs").let { values -> LongArray(values.length()) { values.getString(it).toLong() } }
            value.has("state") -> mutableStateOf(decode(value.get("state")))
            value.has("long") -> value.getString("long").toLong()
            value.has("float") -> value.getDouble("float").toFloat()
            value.has("double") -> value.getDouble("double")
            value.has("enum") -> when (value.getString("enum")) {
                DocumentType::class.java.name -> DocumentType.valueOf(value.getString("value"))
                DiagnosisType::class.java.name -> DiagnosisType.valueOf(value.getString("value"))
                EquipmentTransferState::class.java.name -> EquipmentTransferState.valueOf(value.getString("value"))
                else -> error("Неизвестный enum черновика")
            }
            else -> error("Повреждён черновик")
        }
        else -> value
    }
    fun encodeState(state: Map<String, List<Any?>>): JSONObject = JSONObject().put("format", 1).put("state",
        JSONObject().apply { state.forEach { (key, values) -> put(key, encode(values)) } })
    @Suppress("UNCHECKED_CAST")
    fun decodeState(json: JSONObject): Map<String, List<Any?>> {
        require(json.getInt("format") == 1) { "Неизвестный формат черновика" }
        val state = json.getJSONObject("state")
        return state.keys().asSequence().associateWith { decode(state.getJSONArray(it)) as List<Any?> }
    }
}
