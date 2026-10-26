package com.example.dguactapp

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream
import org.json.JSONObject

fun main() {
    var checks=0
    fun verify(name: String,run:()->Unit) { run();checks++;println("PASS $name") }
    fun reject(run:()->Unit) { check(runCatching(run).isFailure) }
    fun base()=FieldCase(100,fields=mapOf("number" to "SYN-100","date" to "01.01.2030","customer" to "Синтетический заказчик","equipment" to "ДГУ","scope" to "Визуальный осмотр","equipmentKey" to "synthetic-equipment"))
    fun agreed()=base().copy(fields=base().fields+mapOf("amount" to "1234,50","consentPerson" to "Уполномоченный тестовый представитель","consentDate" to "01.01.2030","consentBasis" to "Синтетическое подтверждение полномочий","consentMethod" to "Подписанный тестовый заказ")).consent("agreed")
    val root=kotlin.io.path.createTempDirectory("field-workflow-synthetic-").toFile()
    verify("empty price, driver signature never implies commercial consent") {
        val x=base().set("driver","Тестовый водитель").copy(ink=mapOf("driver" to listOf(listOf(FieldPoint(.1f,.1f),FieldPoint(.5f,.5f))))).confirmRole("driver")
        check("driver" in x.set("master","Тестовый мастер").confirmed);check(!x.diagnosticAgreed());check(x.f("amount").isEmpty());check(x.pdfErrors("intake").isEmpty());reject{x.consent("agreed")}
        val content=FieldContent.blocks(x,"intake").joinToString { it.title+it.text };check(!content.contains("5000") && !content.contains("10 рабочих") && !content.contains("5 рабочих"))
    }
    verify("price accepted once; changing scope/amount/person invalidates consent") {
        val x=agreed();check(x.diagnosticAgreed());check(x.f("acceptedAmount")=="1234,50")
        check(!x.set("amount","2000").diagnosticAgreed());check(!x.set("scope","Разборка").diagnosticAgreed());check(!x.set("consentPerson","Другое лицо").diagnosticAgreed())
        check(!base().copy(fields=base().fields+("amount" to "100")).diagnosticAgreed())
        reject{base().consent("refused")};check(fieldMoney("-1")==null && fieldMoney("1.234")==null && fieldMoney("0")!=null)
    }
    verify("partial quote requires exact subset; new revision inherits no consent") {
        val q=FieldQuote(1,"SYN-Q","01.01.2030",listOf(FieldItem("a","Операция A","100"),FieldItem("b","Операция B","200")))
        reject{q.decide("partial",emptySet(),"Лицо","Дата","Основание")};reject{q.decide("partial",setOf("a","b"),"Лицо","Дата","Основание")};reject{q.decide("partial",setOf("missing"),"Лицо","Дата","Основание")}
        val yes=q.decide("partial",setOf("a"),"Лицо","Дата","Основание");check(yes.selected==setOf("a"));val next=yes.next();check(next.version==2 && next.decision.isEmpty() && next.selected.isEmpty() && next.person.isEmpty())
        reject{q.decide("agreed",emptySet(),"","Дата","Основание")}
    }
    verify("diagnostic PDF emits facts, real units and significant limitations only") {
        var x=agreed().set("diagnosticDate","02.01.2030").set("conclusion","Вывод только по выполненному осмотру")
        check(x.pdfErrors("diagnostic").isNotEmpty())
        x=x.copy(checks=listOf(FieldCheck("done","Узел","Осмотр","fault","Фактический след повреждения","Повреждение","12","В","Стенд","Уточнить объём"),FieldCheck("not","Непроверенный узел",result="unchecked"),FieldCheck("limit","Запуск",result="unchecked",fact="Безопасность не подтверждена",important=true)))
        check(x.pdfErrors("diagnostic").isEmpty());val b=FieldContent.blocks(x,"diagnostic");val all=b.toString();check(all.contains("12 В") && all.contains("Безопасность не подтверждена") && !all.contains("Непроверенный узел"))
        check(x.copy(checks=x.checks.map { if(it.id=="done")it.copy(unit="") else it }).pdfErrors("diagnostic").isNotEmpty())
        check(x.copy(checks=x.checks.map { if(it.id=="done")it.copy(defect="") else it }).pdfErrors("diagnostic").isNotEmpty())
    }
    verify("physical handover does not accept quality or assume warranty/working state") {
        var x=base().set("receiver","Получатель").set("handoverDate","Дата передачи")
        check(x.pdfErrors("return").isNotEmpty());x=x.copy(ink=mapOf("receiver" to listOf(listOf(FieldPoint(.1f,.1f),FieldPoint(.8f,.8f))))).confirmRole("receiver")
        check("master" !in FieldContent.roles("return") && "returnMaster" in FieldContent.roles("return"));check("master" !in FieldContent.roles("letter"));check(x.pdfErrors("return").isEmpty());check(x.changeProcess("Оборудование выдано").process=="Оборудование выдано")
        val content=FieldContent.blocks(x,"return").toString();check(!content.contains("Приёмка результата") && !content.contains("Гарантия") && !content.contains("претензий не имеет"))
        check(x.set("works","Реальная операция").pdfErrors("return").isNotEmpty());reject{base().changeProcess("Оборудование выдано")}
    }
    verify("full business DTO survives process reconstruction including common data, photo and ink") {
        var x=agreed().set("brand","Синтетический бренд").set("hours","77")
        x=x.copy(step=7,inventory=mapOf("Кабель" to "НЕ ПРОВЕРЕНО"),photos=listOf(FieldPhoto("/synthetic/a.jpg","Шильдик","intake")),pendingCamera="/synthetic/pending.jpg",pendingCategory="Общий вид",pendingStage="diagnostic",ink=mapOf("driver" to listOf(listOf(FieldPoint(.1f,.2f),FieldPoint(.3f,.4f)))),choices=mapOf("complaints" to setOf("Утечка")))
        val restored=FieldCodec.decode(JSONObject(FieldCodec.encode(x).toString()));check(restored==x)
        for(kind in listOf("intake","diagnostic","return")) {val text=FieldContent.blocks(restored,kind).toString();check(text.contains("Синтетический бренд") && text.contains("77"))}
        val bad=FieldCodec.encode(x).put("futureField",true);reject{FieldCodec.decode(bad)}
    }
    verify("atomic sidecar, backup, stale-revision rejection and immutable saved snapshots") {
        val f=File(root,"main.json");val store=FieldStore(f);val first=store.save(base(),0);val next=store.save(first.set("hours","2"),first.revision)
        reject{store.save(first.set("hours","OLD"),first.revision)};check(FieldStore(f).read().second.single().f("hours")=="2")
        val doc=FieldDocument("intake",1,"/synthetic/a.pdf","saved","immutable synthetic snapshot")
        val signed=store.save(next.copy(documents=listOf(doc)),next.revision);reject{store.save(signed.copy(documents=emptyList()),signed.revision)}
        check(store.read().second.single().documents.single()==doc)
    }
    verify("fault after staging / backup preserves previous complete record") {
        for(stage in listOf("staged","backedUp")) {
            val f=File(root,"fault-$stage.json");val first=FieldStore(f).save(base(),0)
            reject{FieldStore(f){if(it==stage)error("synthetic disk fault")}.save(first.set("hours","LOST"),first.revision)}
            check(FieldStore(f).read().second.single()==first)
        }
    }
    verify("partial first write blocks empty reset; corruption recovery visible; future schema blocked") {
        val initial=File(root,"first.json");reject{FieldStore(initial){if(it=="staged")error("synthetic interruption")}.save(base(),0)};reject{FieldStore(initial).read()}
        val f=File(root,"recovery.json");val s=FieldStore(f);val old=s.save(base(),0);s.save(old.set("hours","new"),old.revision);f.writeText("broken{");val recovery=FieldStore(f);check(recovery.read().second.single()==old && recovery.recoveredBackup)
        f.writeText("{\"schema\":2,\"active\":0,\"records\":[]}");reject{FieldStore(f).read()};check(f.readText().contains("\"schema\":2"))
        val shape=File(root,"shape.json");val v=FieldStore(shape).save(base(),0);FieldStore(shape).save(v.set("hours","v2"),v.revision);shape.writeText(JSONObject(shape.readText()).put("future",1).toString());reject{FieldStore(shape).read()}
    }
    verify("legacy bytes unchanged by additive workflow; active request/step restored") {
        val legacy=File(root,"local_snapshot_v1.json");legacy.writeText("synthetic legacy bytes");val prior=legacy.readBytes()
        val f=File(root,"workflow.json");val s=FieldStore(f);val x=s.save(base().copy(step=7),0);s.home();check(s.read().first==0L);s.activate(x.id);val (active,list)=FieldStore(f).read();check(active==x.id && list.single().step==7);check(legacy.readBytes().contentEquals(prior))
    }
    verify("notice emits only chosen reason; deadlines absent without complete basis") {
        val x=base().set("noticeReason","Работы завершены").set("location","Синтетическая площадка").set("executorContact","Синтетический контакт")
        check(x.pdfErrors("notice").isEmpty());val b=FieldContent.blocks(x,"notice");check(b.count { it.title=="Основание необходимости вывоза" }==1);check(b.none { it.title.startsWith("Срок") })
        check(FieldContent.blocks(x.set("dueCount","7"),"notice").none { it.title.startsWith("Срок") })
        val specified=x.set("dueCount","7").set("dueUnit","календарных дней").set("dueBasis","предлагается для согласования");check(FieldContent.blocks(specified,"notice").any { it.title.contains("предлагается") })
    }
    verify("import separates INN/phone, keeps multiline address, handles DOCX namespaces/breaks/entities") {
        val parsed=requireNotNull(parseEnterpriseCardText("ООО Синтетический стенд\nИНН: 7701234567\nАдрес: 123456, г. Тестовый\nул. Учебная, дом 4, корпус 2\nТелефон: +7 (999) 111-22-33\nhttps://test.invalid/footer"))
        check(parsed.organizationName=="ООО Синтетический стенд");check(parsed.organizationPhone=="+7 (999) 111-22-33");check(parsed.organizationAddress.contains("корпус 2") && !parsed.organizationAddress.contains("https"))
        check(parseEnterpriseCardText("ООО Стенд\nИНН: 7701234567")!!.organizationPhone.isEmpty())
        val out=ByteArrayOutputStream();ZipOutputStream(out).use { it.putNextEntry(ZipEntry("word/document.xml"));it.write("<x:document xmlns:x='urn:test'><x:p><x:r><x:t>А&#49;</x:t><x:tab/><x:t>Б</x:t><x:br/><x:t>&amp;</x:t></x:r></x:p></x:document>".toByteArray());it.closeEntry() };check(readDocxText(out.toByteArray())=="А1\tБ\n&")
    }
    verify("nine-stage business scenario reopens SAME request and retains immutable intake") {
        val file=File(root,"nine-stage.json");val store=FieldStore(file)
        var x=store.save(base().set("driver","Тестовый водитель").copy(step=1),0)
        x=store.save(x.set("serial","SYNTHETIC-UNIT").copy(step=3,photos=listOf(FieldPhoto("/synthetic/photo.jpg","Шильдик","intake"))),x.revision)
        val consent=agreed().fields.filterKeys { it in setOf("scope","amount","consent","acceptedAmount","consentPerson","consentDate","consentBasis","consentMethod") }
        x=store.save(x.copy(step=4,fields=x.fields+consent),x.revision)
        x=store.save(x.copy(step=5,ink=mapOf("driver" to listOf(listOf(FieldPoint(.1f,.1f),FieldPoint(.8f,.8f))))).confirmRole("driver"),x.revision)
        check(x.pdfErrors("intake").isEmpty());val snapshot=FieldCodec.encode(x.copy(documents=emptyList())).toString()
        x=store.save(x.copy(step=6,documents=listOf(FieldDocument("intake",1,"/synthetic/intake.pdf","saved",snapshot))),x.revision)
        store.home();store.activate(x.id);val reopened=FieldStore(file).read().second.single();check(reopened.id==100L && reopened.f("serial")=="SYNTHETIC-UNIT" && reopened.photos==x.photos && "driver" in reopened.confirmed)
        x=store.save(reopened.set("diagnosticDate","02.01.2030").set("conclusion","Фактический ограниченный вывод").copy(step=7,checks=listOf(FieldCheck("actual","Узел A","Осмотр","ok","Фактический осмотр выполнен"))),reopened.revision)
        check(x.pdfErrors("diagnostic").isEmpty())
        val q=FieldQuote(1,"SYN-Q","02.01.2030",listOf(FieldItem("a","Операция A","100"),FieldItem("b","Операция B","200"))).decide("partial",setOf("a"),"Лицо","Дата","Тестовое основание")
        x=store.save(x.copy(step=8,quotes=listOf(q)),x.revision);check(x.changeProcess("Ремонт согласован").process=="Ремонт согласован")
        x=x.set("receiver","Тестовый получатель").set("handoverDate","03.01.2030").set("works","Операция A").set("control","Фактический контроль A").set("condition","Состояние только проверенного узла")
        x=x.copy(step=9,ink=x.ink+("receiver" to listOf(listOf(FieldPoint(.2f,.2f),FieldPoint(.6f,.6f))))).confirmRole("receiver")
        check(x.pdfErrors("return").isEmpty());x=store.save(x.changeProcess("Оборудование выдано"),x.revision)
        check(x.documents.single().snapshot==snapshot && x.process=="Оборудование выдано" && "quality" !in x.confirmed)
        val content=FieldContent.blocks(x,"return").toString();check(content.contains("Операция A") && !content.contains("Операция B") && !content.contains("претензий не имеет"))
    }
    println("$checks meaningful production-code regression groups PASS; Android runtime NOT RUN")
}
