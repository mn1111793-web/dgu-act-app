package com.example.dguactapp

import android.content.Context
import android.util.AtomicFile
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException

/** One authority. A read never repairs or writes records. Legacy preferences remain untouched. */
object LocalRepository {
    data class Snapshot(val requests: List<RequestRecord>, val acts: List<ActRecord>, val sequence: Int,
                        val drafts: JSONObject = JSONObject())
    private val lock = Any()
    private fun file(context: Context) = AtomicFile(File(context.filesDir, "local_snapshot_v1.json"))

    fun read(context: Context): Snapshot = synchronized(lock) {
        val file = file(context)
        val backup = File(file.baseFile.path + ".bak")
        val pending = File(file.baseFile.path + ".new")
        if (backup.exists()) {
            // Do not let AtomicFile replace a good base with malformed or conflicting backup bytes.
            val backupBytes = backup.readBytes()
            val recovered = parse(backupBytes)
            validate(recovered)
            if (file.baseFile.exists()) {
                val baseBytes = file.baseFile.readBytes()
                if (runCatching { parse(baseBytes).also(::validate) }.isSuccess) {
                    require(baseBytes.contentEquals(backupBytes)) { "Два разных допустимых снимка base/bak; требуется отдельное восстановление" }
                }
            }
        }
        val snapshot = if (file.baseFile.exists() || backup.exists()) {
            parse(file.openRead().use { it.readBytes() })
        } else {
            require(!pending.exists()) { "Остался незавершённый первый снимок .new. Автоматическая пустая база запрещена" }
            legacy(context)
        }
        validate(snapshot)
        snapshot
    }

    private fun parse(bytes: ByteArray): Snapshot {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.get("schemaVersion") is Number && json.get("schemaVersion").toString().toBigDecimal().compareTo(java.math.BigDecimal.ONE) == 0) { "Неизвестная версия хранилища; запись запрещена" }
        require(json.keys().asSequence().toSet() == setOf("schemaVersion", "requests", "acts", "sequence", "drafts")) { "Неполный или неизвестный состав снимка" }
        require(json.get("sequence") is Number && json.get("sequence").toString().toBigDecimal().compareTo(json.getInt("sequence").toBigDecimal()) == 0) { "Повреждён счётчик" }
        return decode(json)
    }
    private fun decode(json: JSONObject) = Snapshot(
        requests = json.getJSONArray("requests").objects().map { item ->
            with(RequestStorage) { item.toRequestRecord().also { strictShape(item, it.toJson()) } }
        },
        acts = json.getJSONArray("acts").objects().map { item ->
            with(ActStorage) { item.toActRecord().also { strictShape(item, it.toJson()) } }
        },
        sequence = json.getInt("sequence"), drafts = json.getJSONObject("drafts")
    )
    /** Current schema is complete. Missing/wrong-typed arrays must not decode as empty lists. */
    private fun strictShape(actual: Any, expected: Any) {
        when (expected) {
            is JSONObject -> {
                require(actual is JSONObject)
                require(actual.keys().asSequence().toSet() == expected.keys().asSequence().toSet()) { "Неполная или неизвестная запись" }
                expected.keys().forEach { strictShape(actual.get(it), expected.get(it)) }
            }
            is JSONArray -> {
                require(actual is JSONArray && actual.length() == expected.length()) { "Повреждён массив записи" }
                for (index in 0 until expected.length()) strictShape(actual.get(index), expected.get(index))
            }
            is String -> require(actual is String) { "Ожидалась строка" }
            is Boolean -> require(actual is Boolean) { "Ожидалось логическое значение" }
            is Number -> {
                require(actual is Number) { "Ожидалось число" }
                if (expected is Int || expected is Long) require(actual.toString().toBigDecimal().compareTo(expected.toString().toBigDecimal()) == 0) { "Дробное или изменённое целое значение" }
            }
            else -> error("Неподдерживаемая структура JSON")
        }
    }
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    // Read XML directly: SharedPreferences can mask a corrupt XML file as an empty map.
    private fun legacyString(context: Context, name: String, key: String): String? {
        val source = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
        require(!File(source.path + ".bak").exists()) { "Обнаружен незавершённый legacy backup; запись заблокирована" }
        if (!source.exists()) return null
        return source.inputStream().use { input ->
            val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
            var result: String? = null
            var root = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    if (!root) { require(parser.name == "map"); root = true }
                    else if (parser.getAttributeValue(null, "name") == key) {
                        require(parser.name == "string" && result == null)
                        result = parser.nextText()
                    }
                }
                event = parser.next()
            }
            require(root) { "Повреждены legacy preferences" }
            require(result != null) { "В legacy preferences отсутствует $key; автоматическая запись запрещена" }
            result
        }
    }

    private fun legacy(context: Context): Snapshot {
        val actsRaw = legacyString(context, "saved_acts", "acts_json")
        val requestsRaw = legacyString(context, "saved_requests", "requests_json")
        val acts = actsRaw?.let { JSONArray(it).objects().map { item -> with(ActStorage) { item.toActRecord() } } }.orEmpty()
        val requests = requestsRaw?.let { JSONArray(it).objects().map { item -> with(RequestStorage) { item.toRequestRecord() } } }
            ?: acts.groupBy { it.requestId }.values.map { it.first().toRequestRecord() }
        // Preserve the old high-water mark as well as numbers in remaining records.
        val sequenceFile = File(context.applicationInfo.dataDir, "shared_prefs/saved_acts.xml")
        var previous = 0
        if (sequenceFile.exists()) sequenceFile.inputStream().use { input ->
            val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "int" &&
                    parser.getAttributeValue(null, "name") == "last_sequence_number") {
                    previous = parser.getAttributeValue(null, "value").toInt()
                }
                parser.next()
            }
        }
        return Snapshot(requests, acts, maxOf(previous, ActStorage.nextSequence(acts, requests) - 1))
    }

    private fun validate(snapshot: Snapshot) {
        require(snapshot.sequence >= 0)
        require(snapshot.requests.map { it.id }.distinct().size == snapshot.requests.size)
        require(snapshot.requests.map { it.equipmentId }.distinct().size == snapshot.requests.size)
        require(snapshot.acts.map { it.id }.distinct().size == snapshot.acts.size)
        val requests = snapshot.requests.associateBy { it.id }
        snapshot.requests.forEach { require(it.id > 0 && it.equipmentId.isNotBlank() && it.createdAtMillis > 0 && it.reservedSequence >= 0) }
        require(snapshot.sequence >= (snapshot.requests.maxOfOrNull { it.reservedSequence } ?: 0)) { "Счётчик меньше выделенного резерва" }
        require(snapshot.sequence >= ActStorage.nextSequence(snapshot.acts, snapshot.requests) - 1) { "Счётчик меньше существующего номера" }
        snapshot.drafts.keys().forEach { require(snapshot.drafts.get(it) is JSONObject) { "Повреждена запись черновика" } }
        snapshot.acts.forEach { act ->
            val request = requireNotNull(requests[act.requestId]) { "Акт без заявки; запись заблокирована" }
            LocalRules.assertLink(request.id, request.equipmentId, act.requestId, act.equipmentId)
            require(act.id > 0 && act.createdAtMillis > 0 && act.revision > 0)
            require(act.photos.map { it.id }.distinct().size == act.photos.size)
            (act.customerSignature + act.executorSignature + act.directorSignature).forEach { stroke ->
                require(stroke.points.isNotEmpty())
                stroke.points.forEach { point -> require(point.x.isFinite() && point.y.isFinite() && point.x in 0f..1f && point.y in 0f..1f) }
            }
        }
        val acts = snapshot.acts.associateBy { it.id }
        snapshot.acts.forEach { act ->
            val seen = mutableSetOf(act.id)
            var version = act
            while (version.supersedesId != 0L) {
                val parent = requireNotNull(acts[version.supersedesId]) { "Нет исходной версии акта" }
                require(seen.add(parent.id) && parent.requestId == act.requestId && parent.equipmentId == act.equipmentId &&
                    parent.documentType == act.documentType && version.revision == parent.revision + 1) { "Повреждена цепочка версий" }
                version = parent
            }
        }
        fun ancestry(act: ActRecord): Set<Long> = buildSet {
            var version = act
            add(version.id)
            while (version.supersedesId != 0L) { version = acts.getValue(version.supersedesId); add(version.id) }
        }
        val owners = mutableMapOf<String, Set<Long>>()
        snapshot.acts.forEach { act -> act.photos.forEach { photo ->
            val lineage = ancestry(act)
            val path = File(photo.filePath).canonicalFile
            val parent = requireNotNull(path.parentFile)
            if (parent.name.matches(Regex("act_[0-9]+"))) {
                val equipment = requireNotNull(parent.parentFile)
                val request = requireNotNull(equipment.parentFile)
                require(parent.name.removePrefix("act_").toLongOrNull() in lineage &&
                    equipment.name == "equipment_${act.equipmentId}" && request.name == "request_${act.requestId}" &&
                    request.parentFile?.name == "act_photos") { "Путь фото принадлежит другому акту или оборудованию" }
            } else require(parent.name == "act_photos") { "Неизвестный путь оригинала фото" }
            val previous = owners.putIfAbsent(path.path, lineage)
            require(previous == null || previous.intersect(lineage).isNotEmpty()) { "Оригинал фото разделён между несвязанными актами" }
        } }
    }

    private fun write(context: Context, snapshot: Snapshot) {
        validate(snapshot)
        val json = JSONObject().put("schemaVersion", 1).put("sequence", snapshot.sequence)
            .put("requests", JSONArray().apply { snapshot.requests.forEach { put(with(RequestStorage) { it.toJson() }) } })
            .put("acts", JSONArray().apply { snapshot.acts.forEach { put(with(ActStorage) { it.toJson() }) } })
            .put("drafts", snapshot.drafts)
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        AtomicSnapshotWriter(file(context)).write(payload)
    }

    private fun transaction(context: Context, change: (Snapshot) -> Snapshot) = synchronized(lock) {
        val before = read(context) // Throw on corrupt input; never overwrite it with an empty collection.
        val after = change(before)
        write(context, after)
        after
    }
    private fun isSigned(act: ActRecord) = act.customerSignature.isNotEmpty() || act.executorSignature.isNotEmpty() || act.directorSignature.isNotEmpty()
    private fun assertRequestUpdate(before: Snapshot, request: RequestRecord, sameUnitCorrection: Boolean = false) {
        val old = before.requests.firstOrNull { it.id == request.id } ?: return
        require(old.equipmentId == request.equipmentId && old.createdAtMillis == request.createdAtMillis)
        require(!old.changesAssignedEquipment(request) || sameUnitCorrection) {
            "Изменение заполненных реквизитов требует подтверждения исправления той же единицы. Для другой единицы нужна отдельная заявка"
        }
        require(old.reservedSequence == request.reservedSequence) { "Нельзя менять резерв счётчика заявки" }
    }

    fun saveDocument(context: Context, act: ActRecord, request: RequestRecord, draftKey: String, sameUnitCorrection: Boolean = false) = transaction(context) { before ->
        assertRequestUpdate(before, request, sameUnitCorrection)
        val originalNumber = before.requests.firstOrNull { it.id == request.id }?.requestNumber
        require(request.requestNumber.isBlank() || request.requestNumber == originalNumber || before.requests.none { it.id != request.id && it.requestNumber == request.requestNumber }) { "Номер уже занят другой заявкой; укажите свободный номер" }
        LocalRules.assertLink(request.id, request.equipmentId, act.requestId, act.equipmentId)
        require(act.equipmentCode == request.equipmentCode && act.equipmentName == request.equipmentName &&
            act.brand == request.brand && act.model == request.model && act.serialNumber == request.serialNumber) { "Реквизиты оборудования акта не совпадают с заявкой" }
        val old = before.acts.firstOrNull { it.id == act.id }
        require(old == null || old.requestId == act.requestId && old.equipmentId == act.equipmentId &&
            old.createdAtMillis == act.createdAtMillis) { "Нельзя менять идентификаторы и время создания акта" }
        require(old == null || !isSigned(old) || old == act) { "Подписанный акт неизменяем; создайте новую версию" }
        val lineage = mutableSetOf(act.id)
        var parentId = act.supersedesId
        while (parentId != 0L) {
            require(lineage.add(parentId)) { "Цикл версий" }
            val parent = requireNotNull(before.acts.firstOrNull { it.id == parentId })
            LocalRules.assertLink(request.id, request.equipmentId, parent.requestId, parent.equipmentId)
            parentId = parent.supersedesId
        }
        act.photos.forEach { photo ->
            val path = File(photo.filePath).canonicalFile
            val ownedPath = lineage.any { ownerId ->
                LocalRules.isWithinDirectory(path, File(context.filesDir,
                    "act_photos/request_${act.requestId}/equipment_${act.equipmentId}/act_$ownerId"))
            }
            val legacyReference = before.acts.any { previous -> previous.id in lineage &&
                previous.requestId == act.requestId && previous.equipmentId == act.equipmentId &&
                previous.photos.any { it.filePath == photo.filePath } }
            require(ownedPath || legacyReference) { "Фото не принадлежит этому акту или его предыдущей версии" }
            require(LocalRules.isWithinDirectory(path, File(context.filesDir, "act_photos")))
            require(path.isFile && path.length() > 0) { "Не сохранён оригинал фотографии" }
        }
        if (act.supersedesId != 0L) {
            val parent = requireNotNull(before.acts.firstOrNull { it.id == act.supersedesId }) { "Нет исходной версии" }
            require(parent.requestId == act.requestId && parent.equipmentId == act.equipmentId &&
                act.revision == parent.revision + 1) { "Неверная цепочка версий" }
        }
        val drafts = JSONObject(before.drafts.toString()).apply { remove(draftKey) }
        before.copy(requests = before.requests.filterNot { it.id == request.id } + request,
            acts = before.acts.filterNot { it.id == act.id }.map { previous ->
                if (previous.requestId == request.id && !isSigned(previous)) previous.withRequest(request) else previous
            } + act,
            sequence = maxOf(before.sequence, request.reservedSequence, ActStorage.nextSequence(listOf(act), listOf(request)) - 1), drafts = drafts)
    }
    fun saveRequest(context: Context, request: RequestRecord) = transaction(context) { before ->
        assertRequestUpdate(before, request)
        require(request.reservedSequence >= 0)
        require(request.reservedSequence == 0 || before.requests.none { it.id != request.id && it.reservedSequence == request.reservedSequence }) { "Счётчик уже зарезервирован" }
        before.copy(requests = before.requests.filterNot { it.id == request.id } + request,
            acts = before.acts.map { if (it.requestId == request.id && !isSigned(it)) it.withRequest(request) else it },
            sequence = maxOf(before.sequence, request.reservedSequence))
    }
    fun saveAct(context: Context, act: ActRecord) {
        val request = read(context).requests.first { it.id == act.requestId }
        saveDocument(context, if (isSigned(act)) act else act.withRequest(request), request, "")
    }
    fun deleteAct(context: Context, id: Long): ActRecord? {
        val old = read(context).acts.firstOrNull { it.id == id } ?: return null
        require(!isSigned(old)) { "Подписанные версии не удаляются" }
        transaction(context) { it.copy(acts = it.acts.filterNot { act -> act.id == id }) }
        return old
    }
    fun deleteRequest(context: Context, id: Long) = transaction(context) { before ->
        require(before.acts.none { it.requestId == id && isSigned(it) }) { "Заявка содержит подписанные версии" }
        before.copy(requests = before.requests.filterNot { it.id == id }, acts = before.acts.filterNot { it.requestId == id })
    }
    fun draft(context: Context, key: String): JSONObject? = read(context).drafts.let { drafts ->
        if (drafts.has(key)) drafts.getJSONObject(key) else null
    }
    fun saveDraft(context: Context, key: String, draft: JSONObject) = transaction(context) { before ->
        before.copy(drafts = JSONObject(before.drafts.toString()).put(key, draft))
    }
}

/** Updating an unsigned request refreshes unsigned linked acts only. Signed snapshots never change. */
internal fun ActRecord.withRequest(request: RequestRecord): ActRecord = copy(
    requestNumber = request.requestNumber, customer = request.customer, phone = request.phone,
    customerAddress = request.customerAddress, organizationName = request.organizationName,
    organizationInn = request.organizationInn, organizationOgrn = request.organizationOgrn,
    organizationAddress = request.organizationAddress, organizationPhone = request.organizationPhone,
    organizationEmail = request.organizationEmail, customerRepresentative = request.customerRepresentative,
    customerRepresentativePhone = request.customerRepresentativePhone, compilationPlace = request.compilationPlace,
    equipmentCode = request.equipmentCode, equipmentName = request.equipmentName, brand = request.brand,
    model = request.model, serialNumber = request.serialNumber
)

internal fun RequestRecord.changesAssignedEquipment(next: RequestRecord): Boolean =
    listOf(equipmentCode to next.equipmentCode, brand to next.brand, model to next.model, serialNumber to next.serialNumber)
        .any { (previous, value) -> previous.isNotBlank() && previous != value }
