package com.example.dguactapp

import android.content.Context
import android.util.DiskFault
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private var passed=0
private var failed=0
private fun case(name: String, body: () -> Unit) {
    try { body();passed++;println("PASS $name") }
    catch(error: Throwable) { failed++;println("FAIL $name: ${error.javaClass.simpleName}: ${error.message}");error.printStackTrace() }
    finally { DiskFault.reset() }
}
private fun rejected(body: () -> Unit) { check(runCatching(body).isFailure) }
private fun context(name: String): Context = Context(File(System.getenv("DGU_CHECK_FIXTURES"), "draft-fix-$name-${java.util.UUID.randomUUID()}/files").apply { mkdirs() })
private fun fixture(type: DocumentType, c: Context): ActFormDraft {
    val base=File(c.filesDir,"act_photos/request_101/equipment_eq-101/act_202").apply { mkdirs() }
    val photos=(1..3).map { n -> ActPhoto("p$n",File(base,"p$n.png").apply { writeBytes(byteArrayOf(n.toByte(),42)) }.path) }
    val stroke=listOf(SignatureStroke(listOf(SignaturePoint(.1f,.2f), SignaturePoint(.5f,.9f))))
    return ActFormDraft(formActId=202,formCreatedAtMillis=1234567890123,formRequestId=101,formEquipmentId="eq-101",documentType=type,
        customer="SYNTHETIC customer АБВ",serialNumber="LONG-SERIAL-".repeat(20),comment="Long\nmultiline ".repeat(500),
        date="24.10.2026",createdAt="23.10.2026",equipmentCode="DG",equipmentName="Synthetic generator",brandSelection="Synthetic brand",modelSelection="Synthetic model",
        organizationName="SYNTHETIC company",organizationInn="000000000000",organizationOgrn="000000000000000",enterpriseCardUri="content://synthetic/card",
        enterpriseCardDetectedName="Pending confirmation",showEnterpriseCardDetectedDialog=true,
        diagnosisType=DiagnosisType.Advanced,checklistItems=listOf(ChecklistItemState("synthetic","Nested checklist",true,true,"nested notes")),photos=photos,nameplatePhotoId="p2",
        customerSignatureDraft=stroke,customerSignatureSaved=stroke,executorSignatureDraft=stroke,executorSignatureSaved=stroke,directorSignatureDraft=stroke,directorSignatureSaved=stroke,
        signatureBinding="business fingerprint",signatureDecoding="Synthetic signer",pendingCameraPath=File(base,"pending-normal.jpg").path,pendingNameplateCameraPath=File(base,"pending-nameplate.jpg").path,
        requestNumber="DG-1-241026",requestNumberEditedManually=true,confirmedEquipmentCorrection="same-unit-confirmation").copy(phone="SYNTHETIC phone", customerAddress="SYNTHETIC customerAddress", organizationAddress="SYNTHETIC organizationAddress", organizationPhone="SYNTHETIC organizationPhone", organizationEmail="SYNTHETIC organizationEmail", compilationPlace="SYNTHETIC compilationPlace", enterpriseCardFileName="SYNTHETIC enterpriseCardFileName", enterpriseCardDetectedInn="SYNTHETIC enterpriseCardDetectedInn", enterpriseCardDetectedOgrn="SYNTHETIC enterpriseCardDetectedOgrn", enterpriseCardDetectedAddress="SYNTHETIC enterpriseCardDetectedAddress", enterpriseCardDetectedPhone="SYNTHETIC enterpriseCardDetectedPhone", enterpriseCardDetectedEmail="SYNTHETIC enterpriseCardDetectedEmail", customBrand="SYNTHETIC customBrand", customModel="SYNTHETIC customModel", operatingTime="SYNTHETIC operatingTime", customerRepresentative="SYNTHETIC customerRepresentative", customerRepresentativePhone="SYNTHETIC customerRepresentativePhone", malfunctionDescription="SYNTHETIC malfunctionDescription", transferCompleteness="SYNTHETIC transferCompleteness", transferExternalCondition="SYNTHETIC transferExternalCondition", preliminaryConclusion="SYNTHETIC preliminaryConclusion", rootCause="SYNTHETIC rootCause", requiredWorks="SYNTHETIC requiredWorks", pdfPath="SYNTHETIC pdfPath", customerSignatureDecoding="SYNTHETIC customerSignatureDecoding", executorSignatureDecoding="SYNTHETIC executorSignatureDecoding", directorSignatureDecoding="SYNTHETIC directorSignatureDecoding", showEquipmentCorrectionDialog=true)
}
@Suppress("UNCHECKED_CAST")
fun main() {
    case("real Compose DefaultLazyKey + real SaveableStateHolder saver reproduce old whole-registry failure") {
        val keyClass=Class.forName("androidx.compose.foundation.lazy.layout.DefaultLazyKey")
        val lazyKey=keyClass.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible=true }.newInstance(0)
        check(lazyKey is android.os.Parcelable)
        val holderClass=Class.forName("androidx.compose.runtime.saveable.SaveableStateHolderImpl")
        val holder=holderClass.getDeclaredConstructor(Map::class.java).apply { isAccessible=true }
            .newInstance(mapOf(lazyKey to mapOf("nested-user-input" to listOf("SYNTHETIC input"))))
        val companion=holderClass.getDeclaredField("Companion").apply { isAccessible=true }.get(null)
        val saver=companion.javaClass.getMethod("getSaver").invoke(companion) as Saver<Any, Any>
        val scope=object: SaverScope { override fun canBeSaved(value: Any)=true }
        val saved=with(saver) { scope.save(holder) }
        val registry=SaveableStateRegistry(null) { true }
        registry.registerProvider("customer") { "SYNTHETIC input" }
        registry.registerProvider("actual-lazy-holder") { saved }
        val failure=runCatching { DraftCodec.encodeState(registry.performSave()) }.exceptionOrNull()
        check(failure?.message?.contains("DefaultLazyKey")==true)
        println("EXPECTED FAIL BASELINE: ${failure?.message}; actual library key/saver, no UI composition executed")
        val c=context("lazy");val model=fixture(DocumentType.DiagnosticAct,c)
        val json=ActFormDraftCodec.encode(model,"owner")
        check(!json.toString().contains("DefaultLazyKey") && ActFormDraftCodec.decode(json,"owner")==model)
        println("FIX: explicit DTO retains user fields and has no input from UI registry")
    }
    DocumentType.values().forEach { type ->
        case("disk save -> destroy state -> new form/read restore all fields, photos, signatures, nameplate, pending camera for ${type.name}") {
            val c=context(type.name);val key="form-v1-101--1-${type.storageValue}";var state: ActFormDraft?=fixture(type,c)
            DraftPersistence(key) { LocalRepository.saveDraft(c,key,it) }.save(state!!)
            val before=File(c.filesDir,"local_snapshot_v1.json").readBytes()
            val expected=state;state=null;check(state==null)
            val recreatedContext=Context(c.filesDir)
            val newForm=ActFormDraftCodec.decode(LocalRepository.draft(recreatedContext,key)!!,key)
            check(newForm==expected)
            check(newForm.nameplatePhotoId==newForm.photos[1].id && newForm.photos.all { File(it.filePath).exists() })
            DraftPersistence(key) { LocalRepository.saveDraft(recreatedContext,key,it) }.save(newForm)
            check(File(c.filesDir,"local_snapshot_v1.json").readBytes().contentEquals(before))
            check(LocalRepository.read(c).drafts.length()==1)
        }
    }
    case("business change, successful internal/system back callback allowed; unchanged save idempotent") {
        val c=context("back");val key="back";var calls=0;var navigated=0;var d=fixture(DocumentType.DiagnosticAct,c)
        val writer=DraftPersistence(key) { calls++;LocalRepository.saveDraft(c,key,it) }
        check(writer.beforeAction(d) { navigated++ }.isSuccess)
        check(writer.beforeAction(d) { navigated++ }.isSuccess)
        check(calls==1 && navigated==2)
        d=d.copy(customer="Edited before back",comment="Another nested change")
        check(writer.beforeAction(d) { navigated++ }.isSuccess)
        check(ActFormDraftCodec.decode(LocalRepository.draft(c,key)!!,key)==d && navigated==3)
    }
    listOf("start","write","cleanup","throw-before","throw-after","no-rename").forEach { mode ->
        case("draft fault $mode blocks action, retains held input/old snapshot or explicit uncertain, same-ID retry") {
            val c=context(mode);val key="fault";val old=fixture(DocumentType.DiagnosticAct,c);var action=false
            val writer=DraftPersistence(key) { LocalRepository.saveDraft(c,key,it) };writer.save(old)
            val before=File(c.filesDir,"local_snapshot_v1.json").readBytes();val edited=old.copy(customer="New unsaved input")
            when(mode) { "start"->DiskFault.denyStart=true;"write"->DiskFault.fail=true;"cleanup"->{DiskFault.fail=true;DiskFault.cleanupFailure=true};else->DiskFault.finishMode=mode }
            val outcome=writer.beforeAction(edited) {action=true}
            check(outcome.isFailure && !action && edited.customer=="New unsaved input")
            if(mode in listOf("start","write","cleanup"))check(File(c.filesDir,"local_snapshot_v1.json").readBytes().contentEquals(before))
            else check(outcome.exceptionOrNull() is SnapshotCommitUncertainException && DiskFault.rollbackCalls==0)
            DiskFault.reset()
            val recovered=ActFormDraftCodec.decode(LocalRepository.draft(c,key)!!,key)
            check(recovered==if(mode=="throw-after") edited else old)
            check(writer.beforeAction(edited) {action=true}.isSuccess && action)
            check(LocalRepository.read(c).drafts.length()==1 && ActFormDraftCodec.decode(LocalRepository.draft(c,key)!!,key).formActId==old.formActId)
        }
    }
    case("after uncertain commit reverting to prior input still writes; cached success cannot mask disk state") {
        val c=context("revert-after-uncertain");val key="revert";val old=fixture(DocumentType.DiagnosticAct,c)
        val writer=DraftPersistence(key) {LocalRepository.saveDraft(c,key,it)};writer.save(old)
        DiskFault.finishMode="throw-after"
        rejected {writer.save(old.copy(customer="Uncertain new value"))};DiskFault.reset()
        check(ActFormDraftCodec.decode(LocalRepository.draft(c,key)!!,key).customer=="Uncertain new value")
        check(writer.beforeAction(old) {}.isSuccess)
        check(ActFormDraftCodec.decode(LocalRepository.draft(c,key)!!,key)==old)
    }
    case("legacy format1 migrates all named business values and retains complete UI registry archive; malformed legacy blocks") {
        val c=context("legacy");val d=fixture(DocumentType.DiagnosticAct,c)
        val registry=SaveableStateRegistry(null) { true }
        val fields=d.javaClass.declaredFields.filter { it.name!="legacyRegistryArchive" }
        fields.forEach { field ->
            field.isAccessible=true
            val raw=field.get(d)
            val saved=when(field.name) {
                "photos" -> d.photos.flatMap { listOf(it.id,it.filePath) }
                "checklistItems" -> d.checklistItems.flatMap { listOf(it.key,it.title,it.checked.toString(),it.faulty.toString(),it.comment) }
                "customerSignatureDraft","customerSignatureSaved","executorSignatureDraft","executorSignatureSaved","directorSignatureDraft","directorSignatureSaved" ->
                    (raw as List<SignatureStroke>).map { stroke -> stroke.points.joinToString(";") { "${it.x},${it.y}" } }
                else -> raw
            }
            registry.registerProvider(field.name) { saved }
        }
        registry.registerProvider("old-ui-scroll") { listOf(15,200) }
        val legacy=DraftCodec.encodeState(registry.performSave())
        val key="form-v1-101--1-${d.documentType.storageValue}"
        LocalRepository.saveDraft(c,key,legacy)
        val migrated=ActFormDraftCodec.decode(LocalRepository.draft(c,key)!!,key)
        check(migrated==d.copy(legacyRegistryArchive=legacy.toString()))
        DraftPersistence(key) {LocalRepository.saveDraft(c,key,it)}.save(migrated)
        check(ActFormDraftCodec.decode(LocalRepository.draft(c,key)!!,key)==migrated)
        val malformed=JSONObject(legacy.toString()).apply {getJSONObject("state").remove("customer")}
        rejected {ActFormDraftCodec.decode(malformed,key)}
        check(ActFormDraftCodec.decode(LocalRepository.draft(c,key)!!,key)==migrated)
    }
    case("ownership check rejects cross-request photo/nameplate/pending paths and wrong business IDs") {
        val c=context("paths");val d=fixture(DocumentType.DiagnosticAct,c);val key="form-v1-101--1-${d.documentType.storageValue}"
        validateDraftOwnership(d,key,c.filesDir)
        rejected {validateDraftOwnership(d.copy(formRequestId=999),key,c.filesDir)}
        rejected {validateDraftOwnership(d.copy(pendingNameplateCameraPath=File(c.filesDir,"act_photos/request_999/camera.jpg").path),key,c.filesDir)}
        rejected {validateDraftOwnership(d.copy(nameplatePhotoId="other-photo"),key,c.filesDir)}
        rejected {validateDraftOwnership(d.copy(photos=d.photos+d.photos.first()),key,c.filesDir)}
    }
    case("revision retains only saved predecessor originals; pending camera remains current owner") {
        val c=context("revision");val d=fixture(DocumentType.DiagnosticAct,c)
        fun act(id:Long,parent:Long,rev:Int)=ActRecord(id=id,requestId=101,documentType=d.documentType,createdAt="24.10.2026",requestNumber=d.requestNumber,date=d.date,
            customer=d.customer,customerAddress=d.customerAddress,equipmentCode=d.equipmentCode,equipmentName=d.equipmentName,brand=d.brandSelection,model=d.modelSelection,
            serialNumber=d.serialNumber,operatingTime="",completeness="",externalCondition="",malfunctionDescription="",equipmentId=d.formEquipmentId,
            photos=d.photos,nameplatePhotoId=d.nameplatePhotoId,supersedesId=parent,revision=rev,createdAtMillis=123456L)
        val original=act(202,0,1);val revision=act(303,202,2)
        val r=d.copy(formActId=303,pendingCameraPath="",pendingNameplateCameraPath="")
        validateDraftOwnership(r,"form-v1-101-303-${d.documentType.storageValue}",c.filesDir,listOf(original,revision))
        rejected {validateDraftOwnership(r.copy(pendingCameraPath=d.pendingCameraPath),"form-v1-101-303-${d.documentType.storageValue}",c.filesDir,listOf(original,revision))}
        rejected {validateDraftOwnership(r,"form-v1-101-303-${d.documentType.storageValue}",c.filesDir,emptyList())}
        check(original.photos==d.photos && original.supersedesId==0L)
    }
    case("read error does not become empty draft; no write or reset") {
        val c=context("read");val d=fixture(DocumentType.DiagnosticAct,c);LocalRepository.saveDraft(c,"read",ActFormDraftCodec.encode(d,"read"))
        val before=File(c.filesDir,"local_snapshot_v1.json").readBytes();DiskFault.denyRead=true
        rejected { LocalRepository.draft(c,"read") };DiskFault.reset();check(File(c.filesDir,"local_snapshot_v1.json").readBytes().contentEquals(before))
    }
    case("unknown, incomplete, wrongly typed formats and different owner rejected, snapshots retained") {
        val c=context("formats");val d=fixture(DocumentType.DiagnosticAct,c);val good=ActFormDraftCodec.encode(d,"one")
        listOf(JSONObject(good.toString()).put("format",99),JSONObject(good.toString()).apply { getJSONObject("business").remove("customer") },
            JSONObject(good.toString()).apply { getJSONObject("business").put("customer",17) }).forEach { rejected { ActFormDraftCodec.decode(it,"one") } }
        rejected { ActFormDraftCodec.decode(good,"another-request") }
    }
    case("actual shared camera callbacks: cancel/false, validation error, NAMEPLATE, attachment and duplicate result") {
        val c=context("camera-result");val d=fixture(DocumentType.DiagnosticAct,c)
        var confirmed=false
        val cancelled=resolveCameraResult(d.photos,d.nameplatePhotoId,d.pendingNameplateCameraPath,CameraPhotoRole.NAMEPLATE,false) {confirmed=true;it}
        check(cancelled.photos==d.photos && cancelled.nameplateId==d.nameplatePhotoId && cancelled.pendingPath.isEmpty() && !confirmed)
        rejected {resolveCameraResult(d.photos,d.nameplatePhotoId,d.pendingNameplateCameraPath,CameraPhotoRole.NAMEPLATE,true) {error("synthetic empty/invalid image")}}
        check(d.pendingNameplateCameraPath.isNotEmpty())
        val ok=resolveCameraResult(d.photos,d.nameplatePhotoId,d.pendingNameplateCameraPath,CameraPhotoRole.NAMEPLATE,true) {it}
        check(ok.photos.first().id==ok.nameplateId && ok.pendingPath.isEmpty() && ok.photos.size==4)
        val repeated=resolveCameraResult(ok.photos,ok.nameplateId,d.pendingNameplateCameraPath,CameraPhotoRole.NAMEPLATE,true) {it}
        check(repeated==ok)
        val attachment=resolveCameraResult(d.photos,d.nameplatePhotoId,d.pendingCameraPath,CameraPhotoRole.ATTACHMENT,true) {it}
        check(attachment.photos.size==4 && attachment.nameplateId==d.nameplatePhotoId)
        rejected {resolveCameraResult(d.photos,d.nameplatePhotoId,"",CameraPhotoRole.NAMEPLATE,true) {it}}
    }
    case("PDF/DOCX route contract rejects DOC, wrong header/mime, accepts exact formats and generic extension") {
        val pdf="%PDF-1.7".toByteArray();val zip=byteArrayOf(80,75,3,4)
        check(companyCardFormat("application/pdf","synthetic.pdf",pdf)==CompanyCardFormat.PDF)
        check(companyCardFormat("application/octet-stream","synthetic.docx",zip)==CompanyCardFormat.DOCX)
        check(companyCardFormat("application/vnd.openxmlformats-officedocument.wordprocessingml.document","synthetic.docx",zip)==CompanyCardFormat.DOCX)
        rejected {companyCardFormat("application/msword","synthetic.doc",zip)}
        rejected {companyCardFormat("application/pdf","synthetic.pdf",zip)}
        rejected {companyCardFormat("","unknown",pdf)}
    }
    case("actual synthetic DOCX ZIP extracts XML entities; missing document, read error and size limit explicit") {
        val output=java.io.ByteArrayOutputStream();ZipOutputStream(output).use {z-> z.putNextEntry(ZipEntry("word/document.xml"));z.write("<w:document><w:p><w:r><w:t>ООО &amp; ТЕСТ</w:t></w:r></w:p></w:document>".toByteArray());z.closeEntry()}
        check(readDocxText(output.toByteArray())=="ООО & ТЕСТ")
        rejected { readCardBytes(java.io.ByteArrayInputStream(ByteArray(21)),20) }
        rejected { readCardBytes(object:java.io.InputStream() {override fun read():Int=throw java.io.IOException("synthetic denied")}) }
        rejected { readDocxText(byteArrayOf(80,75,3,4)) }
    }
    case("company parser keeps 12-digit INN/15-digit OGRN and fallback legal company name; no OCR invented") {
        val found=parseEnterpriseCardText("ООО «SYNTHETIC»\nИНН: 123456789012\nОГРНИП: 123456789012345\nEmail: synthetic@example.invalid")!!
        check(found.organizationName.contains("SYNTHETIC")) {found.toString()}
        check(found.organizationInn=="123456789012" && found.organizationOgrn=="123456789012345") {found.toString()}
        check(parseEnterpriseCardText("")==null)
    }
    case("test/working synthetic context scopes and distinct draft owners remain isolated") {
        val a=context("test-app");val b=context("working-synthetic");val d=fixture(DocumentType.DiagnosticAct,a)
        LocalRepository.saveDraft(a,"a",ActFormDraftCodec.encode(d,"a"));check(LocalRepository.draft(b,"a")==null)
        LocalRepository.saveDraft(a,"b",ActFormDraftCodec.encode(d.copy(formRequestId=999,customer="Other owner"),"b"))
        check(ActFormDraftCodec.decode(LocalRepository.draft(a,"a")!!,"a")==d)
    }
    println("DRAFT FIX RESULTS passed=$passed failed=$failed; actual library saver/key + JVM disk/synthetic fault tests; Android UI/lifecycle/camera NOT RUN")
    if(failed>0)kotlin.system.exitProcess(1)
}
