package com.example.dguactapp

import android.content.Context
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.json.JSONArray
import android.util.DiskFault
import androidx.compose.runtime.saveable.SaveableStateRegistry

private var reviewPassed = 0
private var reviewFailed = 0
private fun reviewCase(name: String, body: () -> Unit) {
    runCatching(body).onSuccess { reviewPassed++; println("PASS $name") }
        .onFailure { reviewFailed++; println("FAIL $name :: ${it.javaClass.simpleName}: ${it.message}") }
    DiskFault.reset()
}
private fun rejected(body: () -> Unit) { check(runCatching(body).isFailure) { "Expected blocked read/write" } }
private fun reviewRequest(id: Long) = RequestRecord(id, "DG-$id-010126", "01.01.2026", "01.01.2026", "Synthetic customer", "", "", "", "", "", "", "", "", "", "", "", "", "DG", "Generator", "Brand", "Model", "SERIAL-$id", createdAtMillis = 1000L + id, equipmentId = "eq-$id")
private fun reviewAct(id: Long, r: RequestRecord, type: DocumentType = DocumentType.DiagnosticAct) = ActRecord(
    id = id, requestId = r.id, documentType = type, createdAt = "01.01.2026", requestNumber = r.requestNumber,
    date = r.date, customer = r.customer, customerAddress = "", equipmentCode = r.equipmentCode,
    equipmentName = r.equipmentName, brand = r.brand, model = r.model, serialNumber = r.serialNumber,
    operatingTime = "", completeness = "", externalCondition = "", malfunctionDescription = "",
    createdAtMillis = 2000L + id, equipmentId = r.equipmentId)
private fun reviewContext(root: File, name: String) = Context(File(root, "$name/files").apply { mkdirs() })

/** Tiny valid RGB PNG with distinct pixels, without Android bitmap APIs or real customer photos. */
private fun fixturePng(red: Int, green: Int, blue: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    output.write(byteArrayOf(137.toByte(),80,78,71,13,10,26,10))
    fun chunk(type: String, data: ByteArray) {
        val tag = type.toByteArray(Charsets.US_ASCII)
        val stream = java.io.DataOutputStream(output)
        stream.writeInt(data.size);stream.write(tag);stream.write(data)
        val crc = java.util.zip.CRC32().apply {update(tag);update(data)}
        stream.writeInt(crc.value.toInt())
    }
    chunk("IHDR",java.nio.ByteBuffer.allocate(13).putInt(1).putInt(1).put(8).put(2).put(0).put(0).put(0).array())
    val pixels = java.io.ByteArrayOutputStream()
    java.util.zip.DeflaterOutputStream(pixels).use {it.write(byteArrayOf(0,red.toByte(),green.toByte(),blue.toByte()))}
    chunk("IDAT",pixels.toByteArray());chunk("IEND",byteArrayOf())
    return output.toByteArray()
}

fun main() {
    val root = Files.createTempDirectory(File(requireNotNull(System.getenv("DGU_CHECK_FIXTURES"))).toPath(), "review-").toFile()
    reviewCase("first-write pending .new is never treated as fresh empty database") {
        val c = reviewContext(root, "pending")
        val pending = File(c.filesDir, "local_snapshot_v1.json.new").apply { writeText("{partial") }
        rejected { LocalRepository.read(c) }
        rejected { LocalRepository.saveRequest(c, reviewRequest(1)) }
        check(pending.readText() == "{partial")
    }
    reviewCase("partial authoritative JSON cannot silently drop photo collection") {
        val c = reviewContext(root, "partial"); val r = reviewRequest(1)
        LocalRepository.saveDocument(c, reviewAct(10, r), r, "")
        val file = File(c.filesDir, "local_snapshot_v1.json")
        val damaged = JSONObject(file.readText()).apply { getJSONArray("acts").getJSONObject(0).remove("photos") }.toString()
        file.writeText(damaged)
        rejected { LocalRepository.read(c) }; rejected { LocalRepository.saveRequest(c, reviewRequest(2)) }
        check(file.readText() == damaged)
    }
    reviewCase("signed snapshot codec roundtrip is exact for new format including intentionally blank fields") {
        val c = reviewContext(root, "signed"); val r = reviewRequest(1)
        val a = reviewAct(10, r).copy(createdAt = "", signatureDecoding = "Generic fixture name",
            customerSignature = listOf(SignatureStroke(listOf(SignaturePoint(.1f, .2f)))))
        LocalRepository.saveDocument(c, a, r, "")
        check(LocalRepository.read(c).acts.single() == a) { "Decoder changed signed snapshot" }
        LocalRepository.saveDocument(c, a, r, "")
        check(LocalRepository.read(c).acts.size == 1)
    }
    reviewCase("saveRequest entry point centrally updates unsigned acts") {
        val c = reviewContext(root, "adapter"); val r = reviewRequest(1)
        LocalRepository.saveDocument(c, reviewAct(10, r), r, "")
        LocalRepository.saveRequest(c, r.copy(customer = "Corrected synthetic customer"))
        check(LocalRepository.read(c).acts.single().customer == "Corrected synthetic customer")
    }

    reviewCase("empty/whitespace/array JSON, missing fields, wrong types, broken links block without byte changes") {
        val c=reviewContext(root,"bad-matrix"); val r=reviewRequest(1)
        LocalRepository.saveDocument(c,reviewAct(10,r),r,"")
        val file=File(c.filesDir,"local_snapshot_v1.json");val baseline=file.readText()
        val cases=listOf("", "  ", "[]", "{}", "{partial",
            JSONObject(baseline).apply { getJSONArray("acts").getJSONObject(0).put("photos","wrong type") }.toString(),
            JSONObject(baseline).apply { getJSONArray("acts").getJSONObject(0).put("equipmentId", "") }.toString(),
            JSONObject(baseline).apply { getJSONArray("acts").getJSONObject(0).put("requestId",9999) }.toString(),
            JSONObject(baseline).apply { getJSONArray("acts").getJSONObject(0).put("supersedesId",9999) }.toString(),
            JSONObject(baseline).apply { getJSONArray("acts").getJSONObject(0).put("id",10.5) }.toString(),
            JSONObject(baseline).put("sequence",1.5).toString(),
            JSONObject(baseline).put("sequence",0).toString(),
            JSONObject(baseline).put("drafts",JSONObject().put("form","invalid saved draft")).toString(),
            JSONObject(baseline).apply { getJSONArray("requests").put(getJSONArray("requests").getJSONObject(0)) }.toString())
        cases.forEach { bad -> file.writeText(bad);rejected { LocalRepository.read(c) };rejected { LocalRepository.saveRequest(c,reviewRequest(2)) };check(file.readText()==bad) }
    }
    reviewCase("read-denied injection never becomes empty data or writes replacement") {
        val c=reviewContext(root,"unreadable");val r=reviewRequest(1)
        LocalRepository.saveDocument(c,reviewAct(10,r),r,"")
        val file=File(c.filesDir,"local_snapshot_v1.json");val before=file.readBytes()
        DiskFault.denyRead=true;rejected { LocalRepository.read(c) };rejected { LocalRepository.saveRequest(c,reviewRequest(2)) }
        DiskFault.reset();check(file.readBytes().contentEquals(before))
    }
    reviewCase("start denied, partial ENOSPC and failed staging cleanup preserve committed bytes and held memory") {
        listOf("start","write","cleanup").forEach { mode ->
            val c=reviewContext(root,"fault-$mode");val r=reviewRequest(1)
            LocalRepository.saveDocument(c,reviewAct(10,r),r,"")
            val file=File(c.filesDir,"local_snapshot_v1.json");val before=file.readBytes();val memory=LocalRepository.read(c)
            when(mode) { "start" -> DiskFault.denyStart=true; "write" -> DiskFault.fail=true;else -> {DiskFault.fail=true;DiskFault.cleanupFailure=true} }
            rejected { LocalRepository.saveDocument(c,reviewAct(20,reviewRequest(2)),reviewRequest(2),"") }
            check(file.readBytes().contentEquals(before));check(memory.requests.size==1 && memory.acts.size==1 && memory.sequence==1)
            DiskFault.reset();check(LocalRepository.read(c).acts.size==1)
            LocalRepository.saveDocument(c,reviewAct(20,reviewRequest(2)),reviewRequest(2),"")
            check(LocalRepository.read(c).acts.size==2)
        }
    }
    reviewCase("post-finish uncertain outcome may be old OR new; no failWrite; same-ID retry keeps one record") {
        listOf("no-rename","throw-before","throw-after").forEach { mode ->
            val c=reviewContext(root,"finish-$mode");val r=reviewRequest(1)
            LocalRepository.saveDocument(c,reviewAct(10,r),r,"")
            val memory=LocalRepository.read(c)
            DiskFault.finishMode=mode
            val result=runCatching { LocalRepository.saveDocument(c,reviewAct(20,reviewRequest(2)),reviewRequest(2),"") }
            check(result.exceptionOrNull() is SnapshotCommitUncertainException && DiskFault.rollbackCalls==0)
            check(memory.acts.size==1 && memory.requests.size==1)
            DiskFault.reset()
            val recovered=LocalRepository.read(c)
            check(recovered.acts.size==(if(mode=="throw-after") 2 else 1))
            LocalRepository.saveDocument(c,reviewAct(20,reviewRequest(2)),reviewRequest(2),"")
            LocalRepository.saveDocument(c,reviewAct(20,reviewRequest(2)),reviewRequest(2),"")
            check(LocalRepository.read(c).acts.size==2 && LocalRepository.read(c).sequence==2)
        }
    }
    reviewCase("valid backup recovery; malformed/conflicting backups preserve all candidate bytes and block writes") {
        val c=reviewContext(root,"backup");val r=reviewRequest(1)
        LocalRepository.saveDocument(c,reviewAct(10,r),r,"")
        val file=File(c.filesDir,"local_snapshot_v1.json");val original=file.readText();val backup=File(file.path+".bak")
        backup.writeText(original);file.writeText("bad base");check(LocalRepository.read(c).acts.single().id==10L)
        check(file.readText()==original && !backup.exists())
        backup.writeText("bad backup");rejected { LocalRepository.read(c) };rejected { LocalRepository.saveRequest(c,reviewRequest(2)) }
        check(file.readText()==original && backup.readText()=="bad backup")
        backup.writeText(JSONObject(original).put("sequence",99).toString())
        rejected { LocalRepository.read(c) };check(file.readText()==original && backup.exists())
    }
    reviewCase("interrupted .new with existing committed snapshot recovers existing data, not empty") {
        val c=reviewContext(root,"interrupted");val r=reviewRequest(1)
        LocalRepository.saveDocument(c,reviewAct(10,r),r,"")
        File(c.filesDir,"local_snapshot_v1.json.new").writeText("partially written")
        check(LocalRepository.read(c).acts.single().id==10L)
    }
    reviewCase("one customer two units three act types each three originals: legacy equal numbers, correction, signed history, drafts and reopen") {
        val c=reviewContext(root,"two-units")
        val inputRequests=listOf(reviewRequest(1),reviewRequest(2)).map { it.copy(requestNumber="LEGACY-SAME") }
        val originals=linkedMapOf<String,ByteArray>()
        val inputActs=inputRequests.flatMap { r -> DocumentType.values().mapIndexed { index,type ->
            val a=reviewAct(r.id*100+index,r,type)
            val photos=(1..3).map { n ->
                val file=File(c.filesDir,"act_photos/photo-${a.id}-$n.png").apply {parentFile.mkdirs();writeBytes(fixturePng(a.id.toInt(),n,r.id.toInt()*31))}
                originals[file.path]=file.readBytes();ActPhoto(id="${a.id}-$n",filePath=file.path)
            }
            a.copy(photos=photos,comment="initial-${a.id}")
        } }
        val reqArray=JSONArray().apply { inputRequests.forEach { r -> put(with(RequestStorage){r.toJson()}.apply { listOf("createdAtMillis","equipmentId","reservedSequence").forEach {remove(it)} }) } }
        val actArray=JSONArray().apply { inputActs.forEach { a -> put(with(ActStorage){a.toJson()}.apply { listOf("createdAtMillis","equipmentId","revision","supersedesId","nameplatePhotoId").forEach {remove(it)} }) } }
        fun xml(value:String)="<map><string name=\"%KEY%\">"+value.replace("&","&amp;").replace("<","&lt;")+"</string></map>"
        val prefs=File(c.applicationInfo.dataDir,"shared_prefs").apply {mkdirs()}
        val reqXml=File(prefs,"saved_requests.xml").apply {writeText(xml(reqArray.toString()).replace("%KEY%","requests_json"))}
        val actXml=File(prefs,"saved_acts.xml").apply {writeText(xml(actArray.toString()).replace("%KEY%","acts_json"))}
        val xmlBefore=listOf(reqXml.readText(),actXml.readText())
        check(LocalRepository.read(c).acts.size==6 && !File(c.filesDir,"local_snapshot_v1.json").exists())
        val initial=LocalRepository.read(c)
        val requests=initial.requests;val acts=initial.acts
        val r1=requests.first();val r2=requests.last()
        val neighborBefore=acts.filter {it.requestId==r2.id}
        val edited=acts.first().copy(comment="changed diagnostics only unit 1")
        LocalRepository.saveDocument(c,edited,r1,"") // unchanged legacy duplicate number permitted, never a join key
        check(LocalRepository.read(c).acts.filter {it.requestId==r2.id}==neighborBefore)
        val signature=listOf(SignatureStroke(listOf(SignaturePoint(.3f,.4f))))
        val signed=acts[1].copy(customerSignature=signature)
        LocalRepository.saveDocument(c,signed,r1,"")
        val corrected=r1.copy(serialNumber="SERIAL-1-CORRECTED")
        rejected { LocalRepository.saveDocument(c,edited.withRequest(corrected),corrected,"") }
        LocalRepository.saveDocument(c,edited.withRequest(corrected),corrected,"",sameUnitCorrection=true)
        var loaded=LocalRepository.read(c)
        check(loaded.requests.first {it.id==1L}.equipmentId==r1.equipmentId)
        check(loaded.acts.first {it.id==signed.id}==signed)
        check(loaded.acts.filter {it.requestId==1L && it.id!=signed.id}.all {it.serialNumber==corrected.serialNumber})
        check(loaded.acts.filter {it.requestId==2L}==neighborBefore)
        val registry=SaveableStateRegistry(null){true}
        registry.registerProvider("photos"){edited.photos.flatMap { listOf(it.id,it.filePath) }}
        registry.registerProvider("pendingCamera"){"request1-act100-pending"}
        val draft=DraftCodec.encodeState(registry.performSave())
        LocalRepository.saveDraft(c,"form-v1-1-100-diagnostic_act",draft)
        val restored=DraftCodec.decodeState(LocalRepository.draft(c,"form-v1-1-100-diagnostic_act")!!)
        check(restored["photos"]!!.single()==edited.photos.flatMap {listOf(it.id,it.filePath)})
        LocalRepository.saveDocument(c,edited.withRequest(corrected),corrected,"form-v1-1-100-diagnostic_act")
        check(LocalRepository.draft(c,"form-v1-1-100-diagnostic_act")==null)
        loaded=LocalRepository.read(c);check(loaded.requests.size==2 && loaded.acts.size==6 && loaded.acts.sumOf {it.photos.size}==18)
        originals.forEach {(path,bytes)->check(File(path).readBytes().contentEquals(bytes))}
        check(originals.values.map {java.security.MessageDigest.getInstance("SHA-256").digest(it).toList()}.distinct().size==18)
        val currentFile=File(c.filesDir,"local_snapshot_v1.json");val good=currentFile.readText()
        val crossed=JSONObject(good).apply {
            val entries=getJSONArray("acts")
            val one=entries.getJSONObject(0).getJSONArray("photos")
            val another=entries.getJSONObject(1).getJSONArray("photos").getJSONObject(0)
            one.put(0,another)
        }.toString()
        currentFile.writeText(crossed);rejected { LocalRepository.read(c) };check(currentFile.readText()==crossed)
        currentFile.writeText(good)
        val newRequest=reviewRequest(3)
        LocalRepository.saveDocument(c,reviewAct(300,newRequest),newRequest,"")
        check(LocalRepository.read(c).acts.size==7)
        check(reqXml.readText()==xmlBefore[0] && actXml.readText()==xmlBefore[1])
        rejected { LocalRepository.saveDocument(c,reviewAct(400,reviewRequest(4).copy(requestNumber="LEGACY-SAME")),reviewRequest(4).copy(requestNumber="LEGACY-SAME"),"") }
    }
    println("REVIEW RESULTS passed=$reviewPassed failed=$reviewFailed; JVM synthetic only")
    if (reviewFailed > 0) kotlin.system.exitProcess(1)
}
