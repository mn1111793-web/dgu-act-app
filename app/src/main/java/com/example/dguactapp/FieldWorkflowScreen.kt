package com.example.dguactapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val steps=listOf("Приём от водителя","Заказчик и оборудование","Жалобы, комплектность, фото","Цена и согласование диагностики","Подпись передавшего","Сохранение акта и PDF","Диагностика той же заявки","Решение по смете","Выдача оборудования")
private val categories=listOf("Общий вид","Шильдик","Моточасы","Повреждения","Комплектность","Другое")
private fun today()=SimpleDateFormat("dd.MM.yyyy",Locale.ROOT).format(Date())

@Composable internal fun FieldWorkflowScreen() {
    val context=LocalContext.current
    val store=remember { FieldStore(File(context.filesDir,"field_workflow_v1.json")) }
    var error by remember { mutableStateOf("") };var bootFailed by remember { mutableStateOf(false) }
    var record by remember { mutableStateOf<FieldCase?>(null) };var records by remember { mutableStateOf(emptyList<FieldCase>()) }
    var legacy by remember { mutableStateOf(emptyList<RequestRecord>()) };var oldActs by remember { mutableStateOf(emptyList<ActRecord>()) }
    var legacyDrafts by remember { mutableStateOf(emptyMap<String,org.json.JSONObject>()) }
    var category by remember { mutableStateOf(categories.first()) }
    fun load() { try {
        val snapshot=LocalRepository.read(context);legacy=snapshot.requests;oldActs=snapshot.acts
        legacyDrafts=snapshot.drafts.keys().asSequence().associateWith { snapshot.drafts.getJSONObject(it) }
        val (active,all)=store.read();records=all;record=all.firstOrNull { it.id==active }
        if(store.recoveredBackup) error="Workflow восстановлен из предыдущей целой записи. Проверьте последние изменения. Прежние акты сохранены."
    } catch(e: Exception){error="Чтение остановлено, данные не сбрасывались: ${e.message}";bootFailed=true} }
    LaunchedEffect(Unit) { load() }
    fun commit(invalidate: Boolean=true, transform: (FieldCase)->FieldCase) {
        val current=record ?: return
        try {
            val changed=transform(current);if(changed==current)return
            val next=if(invalidate) changed.copy(confirmed=emptySet()) else changed
            record=store.save(next,current.revision);records=store.read().second;error=""
        } catch(e: Exception) { error="Не сохранено: ${e.message}. Последняя целая версия сохранена на диске." }
    }
    fun set(key: String,value: String)=commit(false) { it.set(key,value) }
    fun open(id: Long) { try { store.activate(id);load() } catch(e:Exception){error=e.message.orEmpty()} }
    fun create(old: RequestRecord?=null) { try {
        // One-time byte backup, before this additive sidecar first touches a legacy request.
        val main=File(context.filesDir,"local_snapshot_v1.json");val backup=File(context.filesDir,"local_snapshot_v1.before_field_workflow.json")
        if(main.isFile && !backup.exists()) { java.io.FileOutputStream(backup).use { it.write(main.readBytes());it.fd.sync() } }
        val id=old?.id ?: maxOf(System.currentTimeMillis(),(records.map { it.id }+legacy.map { it.id }).maxOrNull()?.plus(1) ?: 1)
        val existing=records.firstOrNull { it.id==id };if(existing!=null){open(id);return}
        val fields=if(old==null) mapOf("number" to "Т-$id","date" to today(),"equipmentKey" to UUID.randomUUID().toString()) else mapOf(
            "number" to old.requestNumber,"date" to old.date.ifBlank { today() },"customer" to old.customer.ifBlank { old.organizationName },"inn" to old.organizationInn,"phone" to old.phone.ifBlank { old.organizationPhone },"address" to old.customerAddress.ifBlank { old.organizationAddress },
            "equipment" to old.equipmentName,"brand" to old.brand,"model" to old.model,"serial" to old.serialNumber,"equipmentKey" to old.equipmentId,"location" to old.compilationPlace,"hours" to oldActs.lastOrNull { it.requestId==old.id && it.documentType==DocumentType.TransferAcceptanceAct }?.operatingTime.orEmpty())
        record=store.save(FieldCase(id,fields=fields),0);records=store.read().second
    }catch(e:Exception){error="Не удалось открыть заявку: ${e.message}"} }
    fun continueDraft(key: String) { try {
        val raw=legacyDrafts.getValue(key);val d=ActFormDraftCodec.decode(raw,key)
        val id=d.formRequestId.takeIf { it>0 } ?: error("В прежнем черновике нет идентификатора заявки; автоматическая новая запись запрещена")
        if(records.any { it.id==id }) { open(id);error="Открыта текущая версия заявки. Более старый черновик её не перезаписал; оригинал сохранён.";return }
        val s=when(d.documentType){DocumentType.TransferAcceptanceAct->"intake";DocumentType.DiagnosticAct->"diagnostic";else->"return"}
        fun ink(strokes: List<SignatureStroke>)=strokes.map { stroke->stroke.points.map { FieldPoint(it.x,it.y) } }
        val fields=mapOf("number" to d.requestNumber,"date" to d.date,"customer" to d.customer.ifBlank { d.organizationName },"inn" to d.organizationInn,"phone" to d.phone.ifBlank { d.organizationPhone },"address" to d.customerAddress.ifBlank { d.organizationAddress },"equipment" to d.equipmentName,"brand" to d.customBrand.ifBlank { d.brandSelection },"model" to d.customModel.ifBlank { d.modelSelection },"serial" to d.serialNumber,"equipmentKey" to d.formEquipmentId.ifBlank { "field-$id" },(if(s=="intake")"hours" else if(s=="diagnostic")"diagnosticHours" else "returnHours") to d.operatingTime,"complaint" to d.malfunctionDescription,"exterior" to d.transferExternalCondition,"location" to d.compilationPlace,"notes" to listOf(d.transferCompleteness,d.comment).filter(String::isNotBlank).joinToString("\n"),"diagnosticDate" to (if(s=="diagnostic")d.date else ""),"hypothesis" to d.rootCause,"conclusion" to d.preliminaryConclusion,"recommendations" to d.requiredWorks,"legacyDraftArchive" to raw.toString(),"legacyPendingNameplate" to d.pendingNameplateCameraPath,"legacyStage" to s)
        val c=FieldCase(id,step=if(s=="intake")3 else if(s=="diagnostic")7 else 9,fields=fields,
            photos=d.photos.map { FieldPhoto(it.filePath,if(it.id==d.nameplatePhotoId)"Шильдик" else "Другое",s) },
            ink=mapOf("legacyCustomer" to ink(d.customerSignatureSaved.ifEmpty { d.customerSignatureDraft }),"legacyExecutor" to ink(d.executorSignatureSaved.ifEmpty { d.executorSignatureDraft }),"legacyDirector" to ink(d.directorSignatureSaved.ifEmpty { d.directorSignatureDraft })),
            checks=d.checklistItems.map { FieldCheck(it.key,it.title,result=if(!it.checked)"unchecked" else if(it.faulty)"fault" else "ok",fact=it.comment,defect=if(it.faulty)it.comment else "") },pendingCamera=d.pendingCameraPath,pendingCategory="Другое",pendingStage=s)
        val main=File(context.filesDir,"local_snapshot_v1.json");val backup=File(context.filesDir,"local_snapshot_v1.before_field_workflow.json")
        if(main.isFile && !backup.exists())java.io.FileOutputStream(backup).use { it.write(main.readBytes());it.fd.sync() }
        record=store.save(c,0);records=store.read().second;error="Продолжен прежний черновик. Фото и штрихи сохранены; коммерческое согласие и новые роли подписей не подставлены."
    }catch(e:Exception){error="Прежний черновик не изменён: ${e.message}"} }
    fun home() { try {store.home();record=null;records=store.read().second}catch(e:Exception){error=e.message.orEmpty()} }
    fun view(path: String) { try {
        val file=File(path).canonicalFile;require(file.path.startsWith(File(context.filesDir,"act_pdfs").canonicalPath+File.separator))
        val uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch(e:Exception){error="PDF сохранён. Не удалось открыть просмотр: ${e.message}"} }
    fun pdf(kind: String) { val c=record ?: return;try {
        val errors=c.pdfErrors(kind);require(errors.isEmpty()){errors.joinToString("\n")}
        val file=FieldPdf.create(context,c,kind)
        val status=if("approver" in c.confirmed && kind=="diagnostic") "approved" else if(FieldContent.roles(kind).count { it in c.confirmed }>=2) "signed" else "saved"
        val doc=FieldDocument(kind,c.documents.count { it.kind==kind }+1,file.path,status,FieldCodec.encode(c.copy(documents=emptyList())).toString())
        commit(false){val process=when { kind=="intake" && "driver" in it.confirmed->"Оборудование принято";kind=="diagnostic"->"Диагностика завершена";else->it.process };it.copy(documents=it.documents+doc,process=process)}
        // A failed snapshot commit leaves a harmless unlisted PDF, never a false successful document.
        if(record?.documents?.any { it.path==file.path }==true) view(file.path)
    }catch(e:Exception){error=e.message.orEmpty()} }
    fun resolvePhoto() { val c=record ?: store.read().let { (id,all)->all.firstOrNull { it.id==id } } ?: return;record=c;try {
        val path=c.pendingCamera;require(path.isNotBlank())
        val photo=PhotoStorage.confirmCameraPhoto(ActPhoto(filePath=path)).getOrThrow()
        commit { it.copy(photos=(it.photos+FieldPhoto(photo.filePath,it.pendingCategory,it.pendingStage)).distinctBy { p->p.path },pendingCamera="",pendingCategory="",pendingStage="") }
    }catch(e:Exception){error="Фото пока не подтверждено: ${e.message}. Можно повторить проверку или отменить ожидание."} }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success->if(success)resolvePhoto() else error="Камера отменена. Ранее сохранённые данные и фото не удалены." }
    fun stage()=when(record?.step){7->"diagnostic";9->"return";else->"intake"}
    fun cameraStart() { val c=record ?: return;try {
        require(c.pendingCamera.isBlank()){ "Сначала завершите предыдущее ожидание камеры" }
        val photo=PhotoStorage.createCameraPhoto(context,c.id,c.f("equipmentKey").ifBlank { "field-${c.id}" },c.id)
        commit(false){it.copy(pendingCamera=photo.filePath,pendingCategory=category,pendingStage=stage())}
        require(record?.pendingCamera==photo.filePath){"Не сохранён адрес ожидаемой фотографии"}
        camera.launch(FileProvider.getUriForFile(context,context.packageName+".fileprovider",File(photo.filePath)))
    }catch(e:Exception){error=e.message.orEmpty()} }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted->if(granted)cameraStart() else error="Доступ к камере не предоставлен; можно выбрать фото из галереи." }
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris->
        val c=record ?: store.read().let { (id,all)->all.firstOrNull { it.id==id } }
        if(c!=null) { val imported=uris.mapNotNull { PhotoStorage.importPhotoFromUri(context,it,c.id,c.f("equipmentKey").ifBlank { "field-${c.id}" },c.id) };record=c;val cat=c.f("galleryCategory").ifBlank { category };val s=c.f("galleryStage").ifBlank { stage() }
            commit { it.copy(photos=(it.photos+imported.map { p->FieldPhoto(p.filePath,cat,s) }).distinctBy { p->p.path },fields=it.fields+mapOf("galleryCategory" to "","galleryStage" to "")) }
            if(imported.size!=uris.size)error="Некоторые фото не импортированы. Сохранённые оригиналы не удалялись; повторите выбор." }
    }
    val importCard=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri->
        if(uri!=null)try {
            val bytes=context.contentResolver.openInputStream(uri)?.use { readCardBytes(it) } ?: error("Нет доступа")
            val text=if(bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray())) {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(bytes).use { com.tom_roush.pdfbox.text.PDFTextStripper().getText(it) }
            }else readDocxText(bytes)
            val data=requireNotNull(parseEnterpriseCardText(text)){"Реквизиты не найдены"}
            // Imported candidates remain editable, and never imply commercial consent.
            commit(false) { current->current.copy(fields=current.fields+mapOf("importCustomer" to data.organizationName,"importInn" to data.organizationInn,"importPhone" to data.organizationPhone,"importAddress" to data.organizationAddress)) }
            error="Проверьте импортированные реквизиты Заказчика. Подтверждение цены не подставлено."
        }catch(e:Exception){error="Импорт не применён: ${e.message}"}
    }
    BackHandler(record!=null) { val c=record!!;if(c.step>1)commit(false){it.copy(step=it.step-1)} else home() }
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        if(error.isNotBlank()) { Text(error,modifier=Modifier.fillMaxWidth().padding(12.dp),color=MaterialTheme.colorScheme.error);if(!bootFailed)TextButton({error=""}){Text("Скрыть сообщение")} }
        if(bootFailed) { Text("Запись заблокирована до восстановления. Не очищайте данные приложения.",Modifier.padding(16.dp));return@Column }
        val c=record
        if(c==null) {
            LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item { Text("Заявки · полевой workflow",style=MaterialTheme.typography.headlineSmall);Text("Сохранённые тестовые заявки и акты остаются на устройстве.");FieldAction("Новая приёмка — шаг 1"){create()} }
                items(records.size,key={records[it].id}) { i->val x=records[i];Card { Column(Modifier.padding(16.dp)) { Text(x.f("number")+" · "+x.f("customer"));Text(listOf(x.f("equipment"),x.f("serial")).filter(String::isNotBlank).joinToString(" · "));if(x.process.isNotBlank())Text(x.process);FieldAction("Открыть ту же заявку · шаг ${x.step}"){open(x.id)} } } }
                if(legacy.isNotEmpty())item { Text("Ранее созданные заявки",style=MaterialTheme.typography.titleLarge) }
                items(legacy.filter { old->records.none { it.id==old.id } }.size) { i->val old=legacy.filter { old->records.none { it.id==old.id } }[i];FieldAction(old.requestNumber+" · "+old.customer+" — продолжить"){create(old)} }
                if(oldActs.isNotEmpty())item { FieldFold("Прежние PDF — просмотр без изменений") { oldActs.forEach { act->if(act.pdfPath.isNotBlank())FieldAction(act.requestNumber+" · "+act.documentType.title){view(act.pdfPath)} } } }
                if(legacyDrafts.isNotEmpty())item { FieldFold("Продолжить ранее начатый черновик") { legacyDrafts.keys.forEach { key->FieldAction(key){continueDraft(key)} } } }
                item { Text("Русская офлайн-диктовка, серверные роли и синхронизация не реализованы. Никаких внешних отправок.") }
            };return@Column
        }
        Text("${c.f("number")} · ${c.step}/9",Modifier.padding(horizontal=16.dp,vertical=6.dp),style=MaterialTheme.typography.titleMedium)
        Text(steps[c.step-1],Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.headlineSmall)
        Text("* — обязательно для соответствующего действия; остальные поля необязательны. Изменения сохраняются на устройстве.",Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item(key="step-${c.step}") {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    @Composable fun input(label: String,key: String,numeric: Boolean=false)=FieldInput(label,c.f(key),numeric){set(key,it)}
                    @Composable fun photos() {
                        FieldFold("Фото · ${c.photos.count { it.stage==stage() }}") {
                            FieldSelect("Категория",category,categories){category=it}
                            FieldAction("Камера") { if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)cameraStart() else permission.launch(Manifest.permission.CAMERA) }
                            FieldAction("Несколько фото из галереи") { commit(false){it.copy(fields=it.fields+mapOf("galleryCategory" to category,"galleryStage" to stage()))}; if(record?.f("galleryCategory")==category)gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            c.photos.filter { it.stage==stage() }.forEach { photo->Text(photo.category);AsyncImage(model=File(photo.path),contentDescription=photo.category,modifier=Modifier.fillMaxWidth().height(140.dp)) }
                        }
                    }
                    @Composable fun signature(role: String,label: String) {
                        val key=if(role=="quality")"qualityPerson" else role
                        FieldFold(label) {
                            input("ФИО подписанта *",key)
                            if(role=="quality"){input("Основание полномочий *","qualityBasis");input("Результат приёмки / замечания *","qualityResult")}
                            val strokes=c.ink[role].orEmpty().map { points->SignatureStroke(points.map { SignaturePoint(it.x,it.y) }) }
                            SignaturePad(strokes,{ ink->commit(false){it.copy(ink=it.ink+(role to ink.map { s->s.points.map { pt->FieldPoint(pt.x,pt.y) } }),confirmed=it.confirmed-role)} },true)
                            Text(if(role in c.confirmed)"Подпись подтверждена для текущих данных" else "Подпись пока не подтверждена")
                            FieldAction("Подтвердить эту подпись") { commit(false){it.confirmRole(role)} }
                            TextButton({commit(false){it.copy(ink=it.ink-role,confirmed=it.confirmed-role)}}){Text("Очистить эту подпись")}
                        }
                    }
                    when(c.step) {
                        1->{input("Передающее лицо / водитель (необязательно)","driver");input("Дата оформления *","date");FieldFold("Автомобиль и места") {input("Автомобиль","vehicle");input("Фактическое нахождение","location");input("Место передачи","transferPlace")};Text("Обычная передача не требует от водителя согласия на цену или ремонт.")}
                        2->{
                            input("Заказчик * — название / ФИО","customer")
                            FieldFold("Выбрать существующего Заказчика") {
                                (records.map { mapOf("customer" to it.f("customer"),"inn" to it.f("inn"),"phone" to it.f("phone"),"address" to it.f("address")) }+legacy.map { mapOf("customer" to it.customer.ifBlank { it.organizationName },"inn" to it.organizationInn,"phone" to it.phone.ifBlank { it.organizationPhone },"address" to it.customerAddress.ifBlank { it.organizationAddress }) }).filter { it["customer"].orEmpty().isNotBlank() && (c.f("customer").isBlank() || it["customer"].orEmpty().contains(c.f("customer"),true)) }.distinctBy { it["customer"] to it["inn"] }.forEach { who->FieldAction(who["customer"].orEmpty()){commit { x->x.copy(fields=x.fields+who+mapOf("consent" to "","acceptedAmount" to "")) }} }
                            }
                            FieldFold("Реквизиты · проверка / импорт PDF или DOCX") { input("ИНН","inn");input("Телефон","phone");input("Адрес","address");FieldAction("Импортировать и проверить"){importCard.launch(arrayOf("application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document"))};Text("Импорт заполняет кандидаты. Проверьте поля перед сохранением акта.")
                                if(listOf("importCustomer","importInn","importPhone","importAddress").any { c.f(it).isNotBlank() }) {
                                    input("Кандидат: Заказчик","importCustomer");input("Кандидат: ИНН","importInn");input("Кандидат: телефон","importPhone");input("Кандидат: адрес","importAddress")
                                    FieldAction("Применить проверенные реквизиты") { commit { current->val keys=mapOf("customer" to "importCustomer","inn" to "importInn","phone" to "importPhone","address" to "importAddress");current.copy(fields=current.fields+keys.mapValues { (key,source)->current.f(source).ifBlank { current.f(key) } }+keys.values.associateWith { "" }+mapOf("consent" to "","acceptedAmount" to "")) } }
                                }
                            }
                            FieldSelect("Тип оборудования *",c.f("equipment"),listOf("ДГУ","Сварочный аппарат","ИБП","Другое")){set("equipment",it)}
                            input("Оборудование / Другое *","equipment")
                            FieldFold("Выбрать оборудование из истории") { records.filter { it.id!=c.id && it.f("equipment").isNotBlank() }.distinctBy { it.f("equipmentKey") }.forEach { equipment->FieldAction(listOf(equipment.f("equipment"),equipment.f("brand"),equipment.f("model"),equipment.f("serial")).filter(String::isNotBlank).joinToString(" ")) { commit { old->old.copy(fields=old.fields+equipment.fields.filterKeys { it in setOf("equipment","brand","model","serial","power","unit","equipmentKey") }+mapOf("consent" to "","acceptedAmount" to "")) } } } }
                            FieldSelect("Производитель — справочник",c.f("brand"),EquipmentCatalog.equipmentTypes.flatMap { it.brands }.map { it.name }.distinct()+"Другое"){set("brand",it)}
                            input("Производитель / Другое","brand")
                            FieldSelect("Модель — справочник",c.f("model"),EquipmentCatalog.equipmentTypes.flatMap { it.brands }.filter { it.name==c.f("brand") }.flatMap { it.models }.distinct()+"Другое"){set("model",it)}
                            input("Модель / Другое","model");input("Серийный номер","serial");FieldFold("Мощность / наработка") {input("Мощность","power",true);FieldSelect("Единица",c.f("unit"),listOf("кВт","кВА","Другое")){set("unit",it)};input("Единица / Другое","unit");input("Наработка, ч","hours",true)}
                        }
                        3->{
                            Text("Жалобы — можно несколько")
                            listOf("Не запускается","Нет напряжения","Нестабильная работа","Шум / вибрация","Утечка","Другое").forEach { value->FieldTick(value,value in c.choices["complaints"].orEmpty()){checked->commit { val list=it.choices["complaints"].orEmpty();it.copy(choices=it.choices+("complaints" to if(checked)list+value else list-value)) }} }
                            input("Комментарий / Другое","complaint")
                            FieldFold("Комплектность — только нужные элементы") {
                                listOf("Кабели","Кожух","Документы","Принадлежности").forEach { item->FieldSelect(item,c.inventory[item].orEmpty(),listOf("Имеется","Отсутствует","Повреждено","НЕ ПРОВЕРЕНО")){value->commit { it.copy(inventory=if(value.isBlank())it.inventory-item else it.inventory+(item to value)) }} }
                                input("Другие элементы / описание","notes")
                            }
                            FieldFold("Внешнее состояние") { listOf("Загрязнение","Коррозия","Повреждения","Следы утечки","Другое").forEach { value->FieldTick(value,value in c.choices["exterior"].orEmpty()){checked->commit { val list=it.choices["exterior"].orEmpty();it.copy(choices=it.choices+("exterior" to if(checked)list+value else list-value)) }} };input("Примечание к внешнему виду","exterior") }
                            photos()
                        }
                        4->{input("Объём первичной диагностики (для согласования *)","scope");input("Стоимость диагностики, руб. — вписывает мастер","amount",true)
                            Text(when(c.f("consent")){"agreed"->"Согласованы указанные объём и сумма";"refused"->"Зафиксирован отказ от диагностики";else->"Согласие не подтверждено. Сумма не является согласием."})
                            FieldFold("Фактическое согласование / отказ") {input("ФИО / должность согласовавшего *","consentPerson");input("Дата ответа *","consentDate");input("Основание / полномочия / документ *","consentBasis");input("Способ подтверждения *","consentMethod");FieldAction("Зафиксировать согласие с этой суммой"){commit(false){it.consent("agreed")}};FieldAction("Зафиксировать отказ"){commit(false){it.consent("refused")}};TextButton({commit(false){it.consent("")}}){Text("Снять неподтверждённую отметку")} }
                            Text("Отказ / отсутствие согласия не мешает сохранить физическую приёмку. Выполненная согласованная диагностика оплачивается независимо от ремонта.")
                        }
                        5->{signature("driver","Подпись передавшего — физическая передача");FieldFold("Отказ от подписи") {input("Кто / когда зафиксировал отказ","driverRefusal")};signature("master","Подпись принимающего мастера")}
                        6->{FieldFold("Исполнитель / контакты") {input("Исполнитель — наименование / ФИО","executorName");input("Реквизиты Исполнителя","executorRequisites");input("Контакт Исполнителя","executorContact")};Text("Все изменения уже сохранены. PDF создаётся отдельной неизменяемой версией.");c.baseErrors().forEach { Text(it,color=MaterialTheme.colorScheme.error) };FieldAction("Сохранить акт приёма и открыть PDF"){pdf("intake")};FieldAction("К списку заявок — можно продолжить позднее"){home()}}
                        7->{
                            Text("Та же заявка: ${c.f("customer")} · ${c.f("equipment")} · ${c.f("serial")}")
                            input("Дата фактической диагностики *","diagnosticDate");input("Диагност — ФИО","diagnost");input("Фактическая наработка при диагностике, ч (необязательно)","diagnosticHours",true)
                            c.checks.forEach { check->
                                FieldFold("Проверка: "+check.node.ifBlank { "узел не выбран" }) {
                                    fun update(change:(FieldCheck)->FieldCheck)=commit { it.copy(checks=it.checks.map { old->if(old.id==check.id)change(old) else old }) }
                                    FieldSelect("Узел",check.node,listOf("Двигатель","Альтернатор","Электрическая часть","Топливная система","Охлаждение","Другое")){v->update{it.copy(node=v)}}
                                    FieldInput("Узел / Другое",check.node){v->update{it.copy(node=v)}}
                                    FieldInput("Проверка / метод / прибор",check.method){v->update{it.copy(method=v)}}
                                    FieldSelect("Результат",check.result,mapOf("ok" to "Проверено без выявленного дефекта","fault" to "Выявлен дефект","unchecked" to "Не проверено")){v->update{it.copy(result=v)}}
                                    FieldInput(if(check.result=="unchecked")"Причина непроведения" else "Фактический результат *",check.fact){v->update{it.copy(fact=v)}}
                                    if(check.result=="fault")FieldInput("Описание дефекта *",check.defect){v->update{it.copy(defect=v)}}
                                    FieldInput("Измеренное значение",check.value,true){v->update{it.copy(value=v)}};FieldInput("Единица",check.unit){v->update{it.copy(unit=v)}};FieldInput("Условия / режим",check.conditions){v->update{it.copy(conditions=v)}}
                                    FieldInput("Рекомендация по узлу",check.recommendation){v->update{it.copy(recommendation=v)}}
                                    if(check.result=="unchecked")FieldTick("Существенное ограничение — включить в PDF",check.important){v->update{it.copy(important=v)}}
                                }
                            }
                            FieldAction("Добавить проверку узла"){commit { it.copy(checks=it.checks+FieldCheck(UUID.randomUUID().toString())) }}
                            input("Техническое заключение *","conclusion");FieldFold("Гипотезы / рекомендации / ограничения") {input("Предполагаемые причины — не установленные факты","hypothesis");input("Рекомендации","recommendations");input("Ограничения","limitations")};photos();signature("diagnost","Подпись диагноста");signature("approver","Утверждение Исполнителем")
                            FieldAction("Сохранить диагностический акт и PDF"){pdf("diagnostic")}
                        }
                        8->{
                            val q=c.quotes.lastOrNull()
                            if(q==null)FieldAction("Зарегистрировать смету — редакция 1"){commit{it.copy(quotes=it.quotes+FieldQuote(1))}}
                            else {
                                Text("Смета · редакция ${q.version} · ${quoteDecision(q.decision)}")
                                fun quote(change:(FieldQuote)->FieldQuote)=commit { val current=it.quotes.last();require(current.decision.isBlank()){ "Для изменения согласованной сметы создайте новую редакцию" };it.copy(quotes=it.quotes.dropLast(1)+change(current)) }
                                if(q.decision.isBlank()) {
                                    FieldInput("Номер сметы *",q.number){v->quote{it.copy(number=v)}};FieldInput("Дата сметы *",q.date){v->quote{it.copy(date=v)}}
                                    q.items.forEach { item->FieldInput("Позиция",item.description){v->quote{it.copy(items=it.items.map { x->if(x.id==item.id)x.copy(description=v) else x })}};FieldInput("Стоимость позиции, руб.",item.price,true){v->quote{it.copy(items=it.items.map { x->if(x.id==item.id)x.copy(price=v) else x })}};FieldTick("Позиция выбрана для частичного согласования",item.id in q.selected){checked->quote{it.copy(selected=if(checked)it.selected+item.id else it.selected-item.id)}} }
                                    FieldAction("Добавить позицию"){quote{it.copy(items=it.items+FieldItem(UUID.randomUUID().toString()))}}
                                    FieldInput("ФИО / должность ответившего *",q.person){v->quote{it.copy(person=v)}};FieldInput("Дата ответа *",q.decisionDate){v->quote{it.copy(decisionDate=v)}};FieldInput("Основание / полномочия / подтверждение *",q.basis){v->quote{it.copy(basis=v)}}
                                    mapOf("agreed" to "Согласована вся смета","partial" to "Согласованы только выбранные позиции","refused" to "Отказ от ремонта","comments" to "Замечания — требуется решение").forEach { (key,label)->FieldAction(label){commit(false){ current->val last=current.quotes.last();current.copy(quotes=current.quotes.dropLast(1)+last.decide(key,last.selected,last.person,last.decisionDate,last.basis),process=if(key in setOf("agreed","partial"))"Ремонт согласован" else "Ожидается решение по смете") }} }
                                } else { q.items.forEach { Text((if(it.id in q.selected)"Согласовано: " else "Не согласовано: ")+it.description) };Text("Ответ: ${q.person}, ${q.decisionDate}; ${q.basis}") }
                                FieldAction("Новая редакция — без прежнего согласия"){commit{it.copy(quotes=it.quotes+it.quotes.last().next())}}
                                signature("letterSigner","Подпись Исполнителя на письме (необязательно)");FieldAction("PDF короткого письма к этой смете"){pdf("letter")}
                            }
                            FieldFold("Индивидуальный срок / событие") { input("Количество дней — без значения по умолчанию","dueCount",true);FieldSelect("Вид дней",c.f("dueUnit"),listOf("рабочих дней","календарных дней")){set("dueUnit",it)};FieldSelect("Основание срока",c.f("dueBasis"),listOf("согласован сторонами","предлагается для согласования")){set("dueBasis",it)};input("Документ / подтверждение принятия срока","dueProof");input("Исходное событие / документ","dueEvent");input("Дата события","dueEventDate");input("Подтверждённое получение, если нужно","receivedDate");input("Конечная дата / ручная правка","dueDate");Text("Без основания и подтверждения нет автоматической просрочки.") }
                            FieldFold("Простое событие направления / получения") { input("Документ / вид события","eventDocument");input("Дата","eventDate");input("Канал","eventChannel");input("Адресат","eventRecipient");input("Подтверждение","eventProof") }
                        }
                        9->{input("ФИО фактически получающего *","receiver");input("Дата фактической передачи *","handoverDate");input("Место передачи","transferPlace");input("Фактическая наработка при выдаче, ч (необязательно)","returnHours",true)
                            FieldFold("Фактически выполненный ремонт / контроль") {input("Работы","works");input("Установленные детали / количество","parts");input("Контрольные проверки / измерения","control");input("Фактическое состояние в объёме контроля","condition");input("Ограничения / замечания","returnNotes");input("Рекомендации","recommendations")}
                            FieldFold("Индивидуальная гарантия / талон") {input("На работы — согласованные условия","warrantyWork");input("Начало","warrantyStart");input("Основание","warrantyBasis");input("Условия на детали","warrantyParts");input("Талон / дата / факт передачи","coupon")}
                            photos();signature("returnMaster","Подпись Исполнителя при выдаче");signature("receiver","Подпись физического получения");signature("quality","Отдельная приёмка результата ремонта")
                            FieldAction("Зафиксировать физическую выдачу"){commit(false){it.changeProcess("Оборудование выдано")}}
                            FieldAction("Акт выдачи / возврата и PDF"){pdf("return")}
                            FieldFold("Уведомление о вывозе — только при необходимости") {FieldSelect("Одна причина",c.f("noticeReason"),listOf("Отказ от ремонта","Ремонт не согласован","Работы завершены","Другое")){set("noticeReason",it)};input("Выбранная причина / Другое","noticeReason");input("Фактический адрес","location");input("Контакты Исполнителя","executorContact");signature("noticeSigner","Подпись уполномоченного лица на уведомлении");FieldAction("Создать PDF уведомления — без отправки"){pdf("notice")}}
                        }
                    }
                    if(c.pendingCamera.isNotBlank()) {Text("Ожидается фотография камеры; адрес сохранён.");FieldAction("Проверить и сохранить фото камеры"){resolvePhoto()};TextButton({commit(false){it.copy(pendingCamera="",pendingCategory="",pendingStage="")}}){Text("Отменить ожидание, не удаляя прежние фото")}}
                    if(c.f("legacyPendingNameplate").isNotBlank())FieldAction("Восстановить ожидавшее фото шильдика прежней формы") { commit(false){ require(it.pendingCamera.isBlank()){ "Сначала завершите текущее ожидание камеры" };it.copy(pendingCamera=it.f("legacyPendingNameplate"),pendingCategory="Шильдик",pendingStage=it.f("legacyStage"),fields=it.fields+("legacyPendingNameplate" to "")) } }
                    if(c.ink.keys.any { it.startsWith("legacy") })FieldFold("Подписи прежнего черновика — сохранённые штрихи") {
                        Text("Это прежние роли. Они не назначены автоматически водителю и не подтверждают новую цену или новый документ.")
                        c.ink.filterKeys { it.startsWith("legacy") }.forEach { (role,points)->if(points.isNotEmpty()){Text(role);SignaturePad(points.map { stroke->SignatureStroke(stroke.map { SignaturePoint(it.x,it.y) }) },{},false)} }
                    }
                    FieldFold("Состояние процесса / документы") {
                        FieldSelect("Одно состояние процесса",c.process,FieldCase.PROCESS){value->if(value.isNotBlank())commit(false){it.changeProcess(value)}}
                        Text("Статус не создаёт обязательств. Направление / получение фиксируются отдельно.")
                        c.documents.reversed().forEach { doc->FieldAction("${FieldContent.titles[doc.kind]} · v${doc.version} · ${ActStatus.fromStorageValue(doc.status).title}"){view(doc.path)} }
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal=16.dp,vertical=8.dp)) {
            if(c.step<9)FieldAction("Далее · ${c.step+1}/9"){commit(false){it.copy(step=it.step+1)}}
            if(c.step>1)TextButton({commit(false){it.copy(step=it.step-1)}}){Text("Назад — данные сохранены")}
            TextButton({home()}){Text("К списку заявок")}
        }
    }
}

private fun quoteDecision(value: String)=mapOf("agreed" to "согласована","partial" to "частично согласована","refused" to "отказ","comments" to "замечания")[value] ?: "ответ не подтверждён"
@Composable private fun FieldAction(text: String,onClick:()->Unit) { OutlinedButton(onClick,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Text(text)} }
@Composable private fun FieldInput(label: String,value: String,numeric: Boolean=false,onChange:(String)->Unit) {
    OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text),minLines=1)
}
@Composable private fun FieldTick(label: String,value: Boolean,onChange:(Boolean)->Unit) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Start){Checkbox(value,onChange);Text(label,Modifier.padding(top=12.dp).weight(1f))} }
@Composable private fun FieldFold(title: String,content:@Composable ColumnScope.()->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){TextButton({expanded=!expanded},modifier=Modifier.fillMaxWidth()){Text((if(expanded)"− " else "+ ")+title)};if(expanded)content()}}
}
@Composable private fun FieldSelect(title: String,value: String,options: List<String>,onChange:(String)->Unit)=FieldSelect(title,value,options.associateWith { it },onChange)
@Composable private fun FieldSelect(title: String,value: String,options: Map<String,String>,onChange:(String)->Unit) {
    FieldFold(title+": "+(options[value] ?: value).ifBlank { "не выбрано" }) { TextButton({onChange("")}){Text("Не выбрано")};options.forEach { (key,label)->FieldAction(label){onChange(key)} } }
}
