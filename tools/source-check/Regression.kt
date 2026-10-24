package com.example.dguactapp

import android.content.Context
import android.util.DiskFault
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateRegistry
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

private var passed = 0
private fun checkCase(name: String, body: () -> Unit) { body(); passed++; println("PASS $name") }
private fun mustFail(body: () -> Unit) { check(runCatching(body).isFailure) }
private fun request(id: Long, number: String = "DG-1-010126") = RequestRecord(id, number, "01.01.2026", "01.01.2026", "Fixture", "", "", "", "", "", "", "", "", "", "", "", "", "DG", "Generator", "B", "M", "SN$id", createdAtMillis = 1000L + id, equipmentId = "eq-$id")
private fun act(id: Long, r: RequestRecord, type: DocumentType = DocumentType.DiagnosticAct) = ActRecord(
    id = id, requestId = r.id, documentType = type, createdAt = "02.01.2026", requestNumber = r.requestNumber,
    date = "02.01.2026", customer = r.customer, customerAddress = "", equipmentCode = r.equipmentCode,
    equipmentName = r.equipmentName, brand = r.brand, model = r.model, serialNumber = r.serialNumber,
    operatingTime = "", completeness = "", externalCondition = "", malfunctionDescription = "",
    createdAtMillis = 2000L + id, equipmentId = r.equipmentId)

fun main() {
    val sandbox = Files.createTempDirectory(File(requireNotNull(System.getenv("DGU_CHECK_FIXTURES"))).toPath(), "dgu-fixtures-").toFile()
    // Test fixtures are temporary and never point at application/user data.
    val context = Context(File(sandbox, "files").apply { mkdirs() })
    val snapshotFile = File(context.filesDir, "local_snapshot_v1.json")
    val r1 = request(11); val r2 = request(22, "DG-2-010126")
    checkCase("strict dd.MM ordering across months and years") {
        val dates = listOf("31.12.2025", "02.01.2026", "01.02.2026")
        check(dates.sortedByDescending(LocalRules::legacyTime) == dates.reversed())
        mustFail { LocalRules.legacyTime("31.02.2026") }; mustFail { LocalRules.legacyTime("01.01.2026junk") }
    }
    checkCase("stable descending timestamp tie-break, not edits or input order") {
        val rows = listOf(2L to 5L, 1L to 5L, 3L to 4L)
        check(LocalRules.newestFirst(rows.reversed(), { it.second }, { it.first }) == rows)
    }
    checkCase("IDs positive and unique in sample") {
        val ids = List(10000) { LocalRules.newId() }; check(ids.all { it > 0 } && ids.toSet().size == ids.size)
    }
    checkCase("reads never create a snapshot") { LocalRepository.read(context); check(!snapshotFile.exists()) }
    checkCase("one transaction persists request act sequence, incomplete unsigned draft allowed") {
        LocalRepository.saveDocument(context, act(101, r1).copy(customer = ""), r1.copy(customer = ""), "draft")
        val state = LocalRepository.read(context)
        check(state.acts.single().customer.isBlank()); check(state.acts.single().customerSignature.isEmpty())
        check(state.requests.single().id == r1.id && state.sequence == 1)
    }
    checkCase("repeated save is idempotent and creation timestamp unchanged") {
        val a = act(101, r1)
        LocalRepository.saveDocument(context, a, r1, "draft"); LocalRepository.saveDocument(context, a, r1, "draft")
        check(LocalRepository.read(context).acts.size == 1)
        mustFail { LocalRepository.saveDocument(context, a.copy(createdAtMillis = 9876), r1, "") }
    }
    checkCase("same business number does not link two physical units") {
        val duplicate = r2.copy(requestNumber = r1.requestNumber)
        mustFail { LocalRepository.saveDocument(context, act(202, duplicate), duplicate, "") }
        LocalRepository.saveDocument(context, act(202, r2), r2, "")
        check(LocalRepository.read(context).requests.size == 2)
        val replacement = r1.copy(serialNumber = "other-unit")
        mustFail { LocalRepository.saveDocument(context, act(101, replacement), replacement, "") }
        mustFail { LocalRepository.saveDocument(context, act(303, r1).copy(requestId = r2.id), r2, "") }
        mustFail { LocalRepository.saveDocument(context, act(303, r2).copy(serialNumber = r1.serialNumber), r2, "") }
    }
    checkCase("write failure preserves all prior bytes and counter; retry works") {
        val before = snapshotFile.readBytes()
        DiskFault.fail = true
        mustFail { LocalRepository.saveDocument(context, act(303, r1.copy(requestNumber = "DG-99-010126")), r1.copy(requestNumber = "DG-99-010126"), "") }
        check(snapshotFile.readBytes().contentEquals(before)); DiskFault.fail = false
        check(LocalRepository.read(context).sequence == 2)
        LocalRepository.saveDocument(context, act(303, r1), r1, "")
        check(LocalRepository.read(context).acts.size == 3)
    }
    checkCase("corrupt JSON, future schema and broken links block writes instead of empty fallback") {
        val before = snapshotFile.readText()
        listOf("{BROKEN", JSONObject(before).put("schemaVersion", 999).toString(),
            JSONObject(before).apply { getJSONArray("acts").getJSONObject(0).put("requestId", 99999) }.toString()).forEach { damaged ->
            snapshotFile.writeText(damaged); mustFail { LocalRepository.read(context) }
            mustFail { LocalRepository.saveRequest(context, r1) }; check(snapshotFile.readText() == damaged)
        }
        snapshotFile.writeText(before)
    }
    checkCase("photo original missing or belonging to another unit refuses save without changing snapshot") {
        val before = snapshotFile.readBytes()
        val other = File(context.filesDir, "act_photos/request_22/equipment_eq-22/act_202/original.jpg").apply { parentFile.mkdirs(); writeText("fixture") }
        mustFail { LocalRepository.saveDocument(context, act(101, r1).copy(photos = listOf(ActPhoto(filePath = other.path))), r1, "") }
        val missing = File(context.filesDir, "act_photos/request_11/equipment_eq-11/act_101/missing.jpg")
        mustFail { LocalRepository.saveDocument(context, act(101, r1).copy(photos = listOf(ActPhoto(filePath = missing.path))), r1, "") }
        val neighboringAct = File(context.filesDir, "act_photos/request_11/equipment_eq-11/act_303/other.jpg").apply { parentFile.mkdirs(); writeText("fixture") }
        mustFail { LocalRepository.saveDocument(context, act(101, r1).copy(photos = listOf(ActPhoto(filePath = neighboringAct.path))), r1, "") }
        check(snapshotFile.readBytes().contentEquals(before)); check(other.exists())
    }
    checkCase("signed snapshot immutable, unsigned revision keeps old version, signed delete forbidden") {
        val signature = listOf(SignatureStroke(listOf(SignaturePoint(.2f, .3f))))
        val signed = act(101, r1).copy(customerSignature = signature)
        LocalRepository.saveDocument(context, signed, r1, "")
        val frozen = with(ActStorage) { LocalRepository.read(context).acts.first { it.id == 101L }.toJson().toString() }
        mustFail { LocalRepository.saveDocument(context, signed.copy(comment = "tampered"), r1, "") }
        mustFail { LocalRepository.deleteAct(context, 101) }; mustFail { LocalRepository.deleteRequest(context, 11) }
        mustFail { LocalRepository.saveDocument(context, act(404, r1.copy(serialNumber = "other")), r1.copy(serialNumber = "other"), "") }
        LocalRepository.saveDocument(context, act(404, r1).copy(supersedesId = 101, revision = 2), r1, "")
        check(with(ActStorage) { LocalRepository.read(context).acts.first { it.id == 101L }.toJson().toString() } == frozen)
    }
    checkCase("durable registry roundtrip text, enums, saver lists, pending camera and unfinished signature") {
        val registry = SaveableStateRegistry(null) { true }
        registry.registerProvider("text") { mutableStateOf("Неполный черновик") }
        registry.registerProvider("photoPending") { mutableStateOf("/fixture/pending.jpg") }
        registry.registerProvider("type") { mutableStateOf(DocumentType.AcceptanceAct) }
        registry.registerProvider("strokes") { listOf("0.2,0.3;0.4,0.5") }
        registry.registerProvider("lazyState") { mapOf(12L to mapOf("field" to listOf("offscreen", 3)), "scroll" to listOf(1, 99)) }
        val encoded = DraftCodec.encodeState(registry.performSave())
        LocalRepository.saveDraft(context, "form", encoded)
        val restored = DraftCodec.decodeState(LocalRepository.draft(context, "form")!!)
        val rebuilt = SaveableStateRegistry(restored) { true }
        check((rebuilt.consumeRestored("text") as androidx.compose.runtime.MutableState<*>).value == "Неполный черновик")
        check((rebuilt.consumeRestored("type") as androidx.compose.runtime.MutableState<*>).value == DocumentType.AcceptanceAct)
        check((rebuilt.consumeRestored("photoPending") as androidx.compose.runtime.MutableState<*>).value == "/fixture/pending.jpg")
        check(rebuilt.consumeRestored("strokes") == listOf("0.2,0.3;0.4,0.5"))
        check(rebuilt.consumeRestored("lazyState") == mapOf(12L to mapOf("field" to listOf("offscreen", 3)), "scroll" to listOf(1, 99)))
        DiskFault.fail = true; mustFail { LocalRepository.saveDraft(context, "form", JSONObject().put("oops", true)) }; DiskFault.fail = false
        check(LocalRepository.draft(context, "form")!!.toString() == encoded.toString())
    }
    checkCase("all three act kinds retain own photos and diagnostic fields") {
        DocumentType.values().forEachIndexed { i, type ->
            val rr = request(500L + i, "DG-${500+i}-010126")
            val aa = act(600L + i, rr, type)
            val photo = File(context.filesDir, "act_photos/request_${rr.id}/equipment_${rr.equipmentId}/act_${aa.id}/original.jpg").apply { parentFile.mkdirs(); writeText("fixture") }
            LocalRepository.saveDocument(context, aa.copy(photos = listOf(ActPhoto(id = "photo-$i", filePath = photo.path)), rootCause = "keep", requiredWorks = "keep"), rr, "")
            val loaded = LocalRepository.read(context).acts.first { it.id == aa.id }
            check(loaded.photos.single().filePath == photo.path && loaded.rootCause == "keep" && loaded.requiredWorks == "keep")
        }
    }
    checkCase("PDF wrapping retains long serial, line breaks and every character at constrained cell width") {
        val serial = "Серийный№" + "0123456789".repeat(100)
        val lines = LocalRules.wrap(serial, 17f) { it.length.toFloat() }
        check(lines.joinToString("") == serial && lines.all { it.length <= 17 })
        check(LocalRules.wrap("a\nb", 3f) { it.length.toFloat() } == listOf("a", "b"))
    }
    checkCase("PDF table pagination reserves header and padding before coordinates") {
        check(LocalRules.tableRowsThatFit(800f, 842f, 36f, 40f, 14f) == 0)
        val perPage = LocalRules.tableRowsThatFit(36f, 842f, 36f, 40f, 14f)
        check(perPage > 0)
        var remaining = 1000; var pages = 0
        while (remaining > 0) { remaining -= minOf(remaining, perPage); pages++ }
        check(pages > 1 && remaining == 0)
        check(36f + 40f + perPage * 14f + 24f <= 842f - 36f)
    }
    checkCase("new requests reserve sequence independently of saved documents") {
        val c = Context(File(sandbox, "sequence/files").apply { mkdirs() })
        val one = request(801, "").copy(reservedSequence = 1)
        LocalRepository.saveRequest(c, one)
        check(LocalRepository.read(c).sequence == 1)
        mustFail { LocalRepository.saveRequest(c, request(802, "").copy(reservedSequence = 1)) }
        LocalRepository.saveRequest(c, request(802, "").copy(reservedSequence = 2))
        LocalRepository.deleteRequest(c, 802)
        check(LocalRepository.read(c).sequence == 2)
    }
    checkCase("legacy read never writes preferences or snapshot; malformed XML/JSON blocks replacement") {
        val c = Context(File(sandbox, "legacy/files").apply { mkdirs() })
        val prefs = File(c.applicationInfo.dataDir, "shared_prefs/saved_acts.xml").apply { parentFile.mkdirs() }
        val empty = "<map><string name=\"acts_json\">[]</string><int name=\"last_sequence_number\" value=\"7\"/></map>"
        prefs.writeText(empty); check(LocalRepository.read(c).sequence == 7)
        check(prefs.readText() == empty && !File(c.filesDir, "local_snapshot_v1.json").exists())
        listOf("<map>broken", "<map><string name=\"acts_json\">bad</string></map>", "<map/>").forEach { text ->
            prefs.writeText(text); mustFail { LocalRepository.saveRequest(c, r1) }; check(prefs.readText() == text)
        }
    }
    println("ALL $passed REGRESSION CASES PASSED; fixtures only; no Android app/device execution")
}
