package com.example.dguactapp

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal

/** Explicit business DTOs, independent of Compose/saveable-state registries. */
data class FieldPoint(val x: Float, val y: Float)
data class FieldPhoto(val path: String, val category: String, val stage: String)
data class FieldCheck(val id: String, val node: String = "", val method: String = "", val result: String = "",
    val fact: String = "", val defect: String = "", val value: String = "", val unit: String = "",
    val conditions: String = "", val recommendation: String = "", val important: Boolean = false)
data class FieldItem(val id: String, val description: String = "", val price: String = "")
data class FieldQuote(val version: Int, val number: String = "", val date: String = "",
    val items: List<FieldItem> = emptyList(), val decision: String = "", val selected: Set<String> = emptySet(),
    val person: String = "", val decisionDate: String = "", val basis: String = "") {
    fun decide(value: String, positions: Set<String>, who: String, whenAt: String, evidence: String): FieldQuote {
        require(number.isNotBlank() && date.isNotBlank()) { "Укажите номер и дату сметы" }
        require(value in setOf("agreed", "partial", "refused", "comments"))
        require(who.isNotBlank() && whenAt.isNotBlank() && evidence.isNotBlank()) { "Нужны лицо, дата и основание ответа" }
        val ids=items.filter { it.description.isNotBlank() }.map { it.id }.toSet()
        if (value in setOf("agreed","partial")) {
            require(ids.isNotEmpty()) { "Добавьте позиции сметы" }
            require(items.filter { it.id in ids }.all { fieldMoney(it.price)!=null }) { "Укажите цены позиций" }
        }
        val chosen=if(value=="agreed") ids else if(value=="partial") positions else emptySet()
        if(value=="partial") require(chosen.isNotEmpty() && chosen.all { it in ids } && chosen.size<ids.size) { "Выберите часть конкретных позиций" }
        return copy(decision=value,selected=chosen,person=who,decisionDate=whenAt,basis=evidence)
    }
    fun next()=copy(version=version+1,decision="",selected=emptySet(),person="",decisionDate="",basis="")
}
data class FieldDocument(val kind: String, val version: Int, val path: String, val status: String, val snapshot: String)
data class FieldCase(val id: Long, val revision: Int = 0, val step: Int = 1,
    val fields: Map<String,String> = emptyMap(), val choices: Map<String,Set<String>> = emptyMap(),
    val inventory: Map<String,String> = emptyMap(), val photos: List<FieldPhoto> = emptyList(),
    val ink: Map<String,List<List<FieldPoint>>> = emptyMap(), val confirmed: Set<String> = emptySet(),
    val checks: List<FieldCheck> = emptyList(), val quotes: List<FieldQuote> = emptyList(),
    val documents: List<FieldDocument> = emptyList(), val process: String = "",
    val pendingCamera: String = "", val pendingCategory: String = "", val pendingStage: String = "") {
    fun f(key: String)=fields[key].orEmpty()
    fun set(key: String, value: String): FieldCase {
        require(key in FIELD_KEYS) { "Неизвестное бизнес-поле" }
        if(f(key)==value) return this
        val affected=when(key) {
            "driver","driverRefusal"->setOf("driver")
            "master"->setOf("master")
            "returnMaster"->setOf("returnMaster")
            "letterSigner"->setOf("letterSigner")
            "noticeSigner"->setOf("noticeSigner")
            "diagnost"->setOf("diagnost")
            "approver"->setOf("approver")
            "receiver","handoverDate"->setOf("receiver")
            "qualityPerson","qualityBasis","qualityResult"->setOf("quality")
            "amount","scope","consentPerson","consentDate","consentBasis","consentMethod"->setOf("master")
            "importCustomer","importInn","importPhone","importAddress","galleryCategory","galleryStage","eventDocument","eventDate","eventChannel","eventRecipient","eventProof"->emptySet()
            else->confirmed
        }
        var next=copy(fields=fields+(key to value), confirmed=confirmed-affected)
        if(key in setOf("amount","scope","customer","equipment","serial","brand","model","consentPerson","consentDate","consentBasis","consentMethod"))
            next=next.copy(fields=next.fields+mapOf("consent" to "", "acceptedAmount" to ""))
        return next
    }
    fun consent(decision: String): FieldCase {
        require(decision in setOf("agreed","refused",""))
        if(decision.isNotEmpty()) require(listOf("consentPerson","consentDate","consentBasis","consentMethod").all { f(it).isNotBlank() }) { "Для фактического ответа укажите лицо, дату, основание и способ" }
        if(decision=="agreed") require(fieldMoney(f("amount"))!=null && f("scope").isNotBlank()) { "Нужны сумма и объём диагностики" }
        return copy(fields=fields+mapOf("consent" to decision,"acceptedAmount" to if(decision=="agreed") f("amount") else ""),confirmed=confirmed-"master")
    }
    fun diagnosticAgreed()=f("consent")=="agreed" && fieldMoney(f("amount"))!=null && f("acceptedAmount")==f("amount") && listOf("scope","consentPerson","consentDate","consentBasis","consentMethod").all { f(it).isNotBlank() }
    fun baseErrors(): List<String> = buildList {
        if(f("customer").isBlank()) add("Заказчик *")
        if(f("equipment").isBlank()) add("Оборудование *")
        if(f("date").isBlank()) add("Дата оформления *")
    }
    fun pdfErrors(kind: String): List<String> = baseErrors()+buildList {
        if(kind=="intake" && f("amount").isNotBlank() && fieldMoney(f("amount"))==null) add("Цена: число рублей, не более двух знаков после запятой")
        if(kind=="diagnostic") {
            if(f("diagnosticDate").isBlank()) add("Дата диагностики *")
            if(!diagnosticAgreed()) add("Сначала зафиксируйте фактическое согласие на диагностику")
            if(checks.none { it.result in setOf("ok","fault") && it.fact.isNotBlank() }) add("Нужна выполненная проверка с фактическим результатом")
            if(checks.any { it.result=="fault" && it.defect.isBlank() }) add("Для выявленного дефекта нужно описание")
            if(checks.any { it.important && it.result=="unchecked" && it.fact.isBlank() }) add("Для существенной непроведённой проверки нужна причина")
            if(checks.any { (it.value.isNotBlank() && it.unit.isBlank()) || (it.value.isBlank() && it.unit.isNotBlank()) }) add("Измерение требует значения и единицы")
            if(f("conclusion").isBlank()) add("Техническое заключение *")
        }
        if(kind=="return") {
            if(f("receiver").isBlank() || f("handoverDate").isBlank()) add("Нужны получатель и фактическая дата выдачи")
            if("receiver" !in confirmed) add("Подтвердите подпись физического получения")
            if(f("parts").isNotBlank() && f("works").isBlank()) add("Для установленных деталей укажите фактические работы")
            if(f("works").isNotBlank() && quotes.lastOrNull()?.decision !in setOf("agreed","partial")) add("Для выполненного ремонта нужна согласованная смета / её позиции")
            if(f("works").isNotBlank() && (f("control").isBlank() || f("condition").isBlank())) add("Нужны контроль и фактическое состояние после работ")
        }
        if(kind=="letter" && (quotes.lastOrNull()?.number.isNullOrBlank() || quotes.lastOrNull()?.date.isNullOrBlank())) add("Нужны номер и дата выбранной сметы")
        if(kind=="notice" && (f("noticeReason").isBlank() || f("location").isBlank() || f("executorContact").isBlank())) add("Укажите одну причину, фактический адрес и контакт")
    }
    fun confirmRole(role: String): FieldCase {
        require(role in ROLES && ink[role].orEmpty().any { it.size>1 }) { "Сначала нанесите подпись" }
        val personKey=if(role=="quality")"qualityPerson" else role
        require(f(personKey).isNotBlank()) { "Укажите ФИО подписанта" }
        if(role=="quality") require(f("qualityBasis").isNotBlank() && f("qualityResult").isNotBlank()) { "Нужны полномочия и отдельный результат приёмки качества" }
        return copy(confirmed=confirmed+role)
    }
    fun changeProcess(value: String): FieldCase {
        require(value in PROCESS)
        if(value in setOf("Диагностика выполняется","Диагностика завершена")) require(diagnosticAgreed()) { "Нет подтверждённого заказа диагностики" }
        if(value in setOf("Ремонт согласован","В ремонте","Ожидаются запчасти")) require(quotes.lastOrNull()?.decision in setOf("agreed","partial")) { "Нет согласия с текущей сметой" }
        if(value=="Оборудование выдано") require(pdfErrors("return").isEmpty()) { "Сначала подтвердите физическую выдачу" }
        return copy(process=value)
    }
    companion object {
        val ROLES=setOf("driver","master","diagnost","approver","returnMaster","receiver","quality","letterSigner","noticeSigner")
        val PROCESS=listOf("Оборудование принято","Диагностика выполняется","Диагностика завершена","Ожидается решение по смете","Ремонт согласован","В ремонте","Ожидаются запчасти","Готово к выдаче","Ожидается вывоз","Оборудование выдано")
        val FIELD_KEYS=setOf("number","date","customer","inn","phone","address","equipment","brand","model","serial","power","unit","hours","diagnosticHours","returnHours","equipmentKey","complaint","exterior","location","transferPlace","driver","vehicle","master","scope","amount","consent","acceptedAmount","consentPerson","consentDate","consentBasis","consentMethod","driverRefusal","diagnost","diagnosticDate","approver","hypothesis","conclusion","limitations","recommendations","works","parts","control","condition","returnNotes","receiver","handoverDate","qualityPerson","qualityBasis","qualityResult","warrantyWork","warrantyStart","warrantyBasis","warrantyParts","coupon","executorContact","noticeReason","dueCount","dueUnit","dueBasis","dueProof","dueEvent","dueEventDate","receivedDate","dueDate","eventDocument","eventDate","eventChannel","eventRecipient","eventProof","notes","importCustomer","importInn","importPhone","importAddress","galleryCategory","galleryStage","executorName","executorRequisites","legacyDraftArchive","legacyPendingNameplate","legacyStage","returnMaster","letterSigner","noticeSigner")
    }
}
fun fieldMoney(raw: String): BigDecimal? = raw.replace(',','.').takeIf { it.matches(Regex("\\d+(?:\\.\\d{1,2})?")) }?.toBigDecimalOrNull()

object FieldCodec {
    private fun strings(values: Iterable<String>)=JSONArray().apply { values.forEach { put(it) } }
    private fun <T> array(values: List<T>, encode:(T)->JSONObject)=JSONArray().apply { values.forEach { put(encode(it)) } }
    private fun obj(values: Map<String,String>)=JSONObject().apply { values.forEach { (k,v)->put(k,v) } }
    fun encode(c: FieldCase): JSONObject=JSONObject().apply {
        put("id",c.id);put("revision",c.revision);put("step",c.step);put("fields",obj(c.fields));put("process",c.process)
        put("choices",JSONObject().apply { c.choices.forEach { (k,v)->put(k,strings(v)) } });put("inventory",obj(c.inventory))
        put("photos",array(c.photos){JSONObject().put("path",it.path).put("category",it.category).put("stage",it.stage)})
        put("ink",JSONObject().apply { c.ink.forEach { (role,strokes)->put(role,JSONArray().apply { strokes.forEach { points->put(JSONArray().apply { points.forEach { put(JSONArray().put(it.x).put(it.y)) } }) } }) } })
        put("confirmed",strings(c.confirmed));put("checks",array(c.checks){x->JSONObject().put("id",x.id).put("node",x.node).put("method",x.method).put("result",x.result).put("fact",x.fact).put("defect",x.defect).put("value",x.value).put("unit",x.unit).put("conditions",x.conditions).put("recommendation",x.recommendation).put("important",x.important)})
        put("quotes",array(c.quotes){q->JSONObject().put("version",q.version).put("number",q.number).put("date",q.date).put("decision",q.decision).put("selected",strings(q.selected)).put("person",q.person).put("decisionDate",q.decisionDate).put("basis",q.basis).put("items",array(q.items){JSONObject().put("id",it.id).put("description",it.description).put("price",it.price)})})
        put("documents",array(c.documents){JSONObject().put("kind",it.kind).put("version",it.version).put("path",it.path).put("status",it.status).put("snapshot",it.snapshot)})
        put("pendingCamera",c.pendingCamera);put("pendingCategory",c.pendingCategory);put("pendingStage",c.pendingStage)
    }
    private fun map(j: JSONObject): Map<String,String> = j.keys().asSequence().associateWith { require(j.get(it) is String);j.getString(it) }
    private fun list(j: JSONArray): List<JSONObject> = (0 until j.length()).map { j.getJSONObject(it) }
    private fun set(j: JSONArray): Set<String> = (0 until j.length()).map { require(j.get(it) is String);j.getString(it) }.toSet()
    fun decode(j: JSONObject): FieldCase {
        require(j.keys().asSequence().toSet()==setOf("id","revision","step","fields","process","choices","inventory","photos","ink","confirmed","checks","quotes","documents","pendingCamera","pendingCategory","pendingStage")) { "Неизвестный состав workflow; запись запрещена" }
        val f=map(j.getJSONObject("fields"));require(f.keys.all { it in FieldCase.FIELD_KEYS }) { "Неизвестные поля: запись запрещена" }
        val i=j.getJSONObject("ink");val ink=i.keys().asSequence().associateWith { role->val a=i.getJSONArray(role); (0 until a.length()).map { n->val b=a.getJSONArray(n);(0 until b.length()).map { val point=b.getJSONArray(it); FieldPoint(point.getDouble(0).toFloat(),point.getDouble(1).toFloat()).also { require(it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f) } } } }
        val ch=j.getJSONObject("choices")
        return FieldCase(j.getLong("id"),j.getInt("revision"),j.getInt("step"),f,ch.keys().asSequence().associateWith { set(ch.getJSONArray(it)) },map(j.getJSONObject("inventory")),
            list(j.getJSONArray("photos")).map { FieldPhoto(it.getString("path"),it.getString("category"),it.getString("stage")) },ink,set(j.getJSONArray("confirmed")),
            list(j.getJSONArray("checks")).map { x->FieldCheck(x.getString("id"),x.getString("node"),x.getString("method"),x.getString("result"),x.getString("fact"),x.getString("defect"),x.getString("value"),x.getString("unit"),x.getString("conditions"),x.getString("recommendation"),x.getBoolean("important")) },
            list(j.getJSONArray("quotes")).map { q->FieldQuote(q.getInt("version"),q.getString("number"),q.getString("date"),list(q.getJSONArray("items")).map { FieldItem(it.getString("id"),it.getString("description"),it.getString("price")) },q.getString("decision"),set(q.getJSONArray("selected")),q.getString("person"),q.getString("decisionDate"),q.getString("basis")) },
            list(j.getJSONArray("documents")).map { FieldDocument(it.getString("kind"),it.getInt("version"),it.getString("path"),it.getString("status"),it.getString("snapshot")) },j.getString("process"),j.getString("pendingCamera"),j.getString("pendingCategory"),j.getString("pendingStage")).also {
                require(it.id>0 && it.revision>=0 && it.step in 1..9 && it.confirmed.all { r->r in FieldCase.ROLES })
                require(it.process.isBlank() || it.process in FieldCase.PROCESS)
                require(it.f("consent") in setOf("","agreed","refused"))
                require(it.f("consent")!="agreed" || it.diagnosticAgreed())
                require(it.inventory.values.all { value->value in setOf("Имеется","Отсутствует","Повреждено","НЕ ПРОВЕРЕНО") })
                require(it.choices.keys.all { key->key in setOf("complaints","exterior") })
                require(it.checks.all { x->x.result in setOf("","ok","fault","unchecked") })
                require(it.quotes.zipWithNext().all { (a,b)->b.version==a.version+1 })
                require(it.quotes.all { q->q.version>0 && q.decision in setOf("","agreed","partial","refused","comments") && q.selected.all { id->q.items.any { x->x.id==id } } })
                require(it.documents.all { d->d.status in setOf("draft","saved","signed","approved") && d.kind in setOf("intake","diagnostic","return","letter","notice") })
                require(it.confirmed.all { role->it.ink[role].orEmpty().any { stroke->stroke.size>1 } })
            }
    }
}

/** Separate additive sidecar. Never migrates/deletes the existing local_snapshot_v1.json. */
class FieldStore(private val file: File, private val fault: (String)->Unit = {}) {
    private val backup=File(file.path+".bak")
    private val temporary=File(file.path+".pending")
    var recoveredBackup: Boolean=false; private set
    private fun parse(bytes: ByteArray): Pair<Long,List<FieldCase>> {
        val j=JSONObject(bytes.toString(Charsets.UTF_8));require(j.getInt("schema")==1) { "Неизвестный формат workflow; запись запрещена" }
        require(j.keys().asSequence().toSet()==setOf("schema","active","records")) { "Неизвестный состав снимка; запись запрещена" }
        val a=j.getJSONArray("records");val records=(0 until a.length()).map { FieldCodec.decode(a.getJSONObject(it)) }
        require(records.map { it.id }.distinct().size==records.size)
        val active=j.getLong("active");require(active==0L || records.any { it.id==active });return active to records
    }
    @Synchronized fun read(): Pair<Long,List<FieldCase>> {
        if(file.exists()) {
            return try { parse(file.readBytes()) } catch(error: Exception) {
                // An unknown schema is not a corruption recovery opportunity: do not roll it back.
                val raw=runCatching { JSONObject(file.readText()).getInt("schema") }.getOrNull()
                if(raw!=null && raw!=1) throw error
                if(error.message.orEmpty().contains("Неизвест")) throw error
                if(!backup.exists()) throw error
                parse(backup.readBytes()).also { recoveredBackup=true }
            }
        }
        if(backup.exists()) return parse(backup.readBytes()).also { recoveredBackup=true }
        require(!temporary.exists()) { "Незавершённая первая запись; автоматический сброс запрещён" }
        return 0L to emptyList()
    }
    private fun write(active: Long, records: List<FieldCase>) {
        val data=JSONObject().put("schema",1).put("active",active).put("records",JSONArray().apply { records.forEach { put(FieldCodec.encode(it)) } }).toString().toByteArray()
        parse(data);file.parentFile?.mkdirs()
        FileOutputStream(temporary).use { it.write(data);it.fd.sync() };fault("staged")
        if(file.exists() && runCatching { parse(file.readBytes()) }.isSuccess) {
            val stagedBackup=File(backup.path+".pending")
            FileOutputStream(stagedBackup).use { it.write(file.readBytes());it.fd.sync() }
            require(stagedBackup.renameTo(backup)) { "Не удалось сохранить резерв workflow" }
        }
        fault("backedUp")
        require(temporary.renameTo(file)) { "Не удалось атомарно сохранить workflow" };fault("committed")
    }
    @Synchronized fun save(c: FieldCase, expectedRevision: Int): FieldCase {
        val (_,records)=read();val old=records.firstOrNull { it.id==c.id }
        require((old?.revision ?: 0)==expectedRevision) { "Заявка изменена. Переоткройте её: старый черновик не записан" }
        require(old==null || c.documents.take(old.documents.size)==old.documents) { "Сохранённые документы неизменны" }
        val next=c.copy(revision=expectedRevision+1);write(c.id,records.filterNot { it.id==c.id }+next);return next
    }
    @Synchronized fun activate(id: Long) { val (_,records)=read();require(records.any { it.id==id });write(id,records) }
    @Synchronized fun home() { val (_,records)=read();write(0,records) }
}
