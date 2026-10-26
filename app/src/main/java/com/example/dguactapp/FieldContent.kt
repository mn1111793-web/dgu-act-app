package com.example.dguactapp

data class FieldBlock(val title: String, val text: String = "", val headers: List<String> = emptyList(), val rows: List<List<String>> = emptyList())
object FieldContent {
    val titles=mapOf("intake" to "Акт приёма-передачи оборудования на диагностику", "diagnostic" to "Акт диагностики оборудования", "return" to "Акт сдачи-приёма оборудования из ремонта", "letter" to "Сопроводительное письмо к смете", "notice" to "Уведомление о необходимости вывоза оборудования")
    fun roles(kind: String)=when(kind){"intake"->listOf("driver","master");"diagnostic"->listOf("diagnost","approver");"return"->listOf("returnMaster","receiver","quality");"letter"->listOf("letterSigner");else->listOf("noticeSigner")}
    fun blocks(c: FieldCase, kind: String): List<FieldBlock> = buildList {
        fun value(title: String, text: String) { if(text.isNotBlank()) add(FieldBlock(title,text)) }
        fun f(title: String,key: String)=value(title,c.f(key))
        fun table(title: String, headers: List<String>, rows: List<List<String>>) { if(rows.isNotEmpty()) add(FieldBlock(title,headers=headers,rows=rows)) }
        value("Заявка",c.f("number"));f(if(kind=="intake")"Дата оформления" else "Дата акта приёма / заявки","date");f("Исполнитель","executorName");f("Реквизиты Исполнителя","executorRequisites");f("Заказчик","customer");f("ИНН","inn");f("Контакт Заказчика","phone");f("Адрес Заказчика","address")
        value("Оборудование",listOf(c.f("equipment"),c.f("brand"),c.f("model")).filter { it.isNotBlank() }.joinToString(" "))
        f("Серийный номер","serial");value("Мощность",listOf(c.f("power"),c.f("unit")).filter { it.isNotBlank() }.joinToString(" "));f("Наработка при приёме, ч","hours");if(kind=="diagnostic")f("Наработка при диагностике, ч","diagnosticHours");if(kind=="return")f("Наработка при выдаче, ч","returnHours")
        if(kind=="intake") {
            f("Передающее лицо","driver");f("Автомобиль","vehicle");f("Фактическое нахождение","location");f("Место передачи","transferPlace")
            value("Заявленная неисправность",(c.choices["complaints"].orEmpty()+listOf(c.f("complaint"))).filter { it.isNotBlank() }.joinToString("; "))
            table("Комплектность",listOf("Элемент","Состояние"),c.inventory.map { listOf(it.key,it.value) })
            value("Внешнее состояние",(c.choices["exterior"].orEmpty()+listOf(c.f("exterior"))).filter { it.isNotBlank() }.joinToString("; "))
            f("Объём первичной диагностики","scope")
            if(c.f("amount").isNotBlank()) value(if(c.diagnosticAgreed()) "Согласованная стоимость диагностики, руб." else "Предложенная стоимость диагностики, руб.",c.f("amount"))
            if(c.f("consent").isNotBlank()) {
                value("Ответ Заказчика о диагностике",if(c.diagnosticAgreed()) "Согласована указанная диагностика и цена" else "Отказ от диагностики")
                f("Согласовавшее лицо","consentPerson");f("Дата ответа","consentDate");f("Основание / полномочия","consentBasis");f("Способ подтверждения","consentMethod")
            }
            value("Условия","Первичная диагностика — самостоятельная платная услуга. Согласованная и фактически выполненная диагностика оплачивается независимо от решения о ремонте. Ремонт, дополнительные работы и детали согласуются до выполнения / поставки. Работы выполняются в порядке очереди с учётом загрузки и согласованных сроков.")
            value("Получение оборудования","При отказе, несогласовании ремонта или завершении согласованных работ Заказчик организует получение оборудования с учётом договорённостей и закона. При необходимости Исполнитель вправе направить уведомление о вывозе. Платное хранение только при отдельном согласовании, без автоматического начисления. Сохранность и ответственность определяются законом.")
            value("Физическая передача","Подпись передающего лица подтверждает передачу, комплектность и зафиксированное внешнее состояние. Коммерческие условия согласуются Заказчиком / представителем с соответствующими полномочиями.")
            f("Отметка об отказе от подписи","driverRefusal");f("Примечания","notes")
        }
        if(kind=="diagnostic") {
            f("Дата фактической диагностики","diagnosticDate");f("Диагностику выполнил","diagnost")
            val done=c.checks.filter { it.result in setOf("ok","fault") && it.fact.isNotBlank() }
            table("Результаты диагностики",listOf("Узел / проверка","Фактический результат","Дефект"),done.map { listOf(listOf(it.node,it.method).filter(String::isNotBlank).joinToString(" / "),it.fact,it.defect) })
            table("Измерения",listOf("Узел / метод","Значение / единица","Условия"),done.filter { it.value.isNotBlank() }.map { listOf(it.node+" / "+it.method,it.value+" "+it.unit,it.conditions) })
            f("Предполагаемые причины","hypothesis");f("Техническое заключение","conclusion")
            table("Рекомендуемые работы и материалы",listOf("Узел","Рекомендация"),done.filter { it.recommendation.isNotBlank() }.map { listOf(it.node,it.recommendation) })
            f("Рекомендации","recommendations")
            value("Существенные непроведённые проверки",c.checks.filter { it.important && it.result=="unchecked" }.joinToString("\n") { it.node+": "+it.fact })
            f("Ограничения вывода","limitations")
        }
        if(kind=="return") {
            c.quotes.lastOrNull()?.takeIf { it.decision in setOf("agreed","partial") }?.let { q->value("Согласованные позиции сметы №${q.number}, редакция ${q.version}",q.items.filter { it.id in q.selected }.joinToString("; ") { it.description }) }
            f("Фактически выполненные работы","works");f("Установленные детали / количество","parts");f("Контрольные проверки / измерения","control");f("Состояние в объёме контроля","condition");f("Ограничения / замечания","returnNotes");f("Рекомендации","recommendations")
            f("Гарантия на работы","warrantyWork");f("Начало гарантии","warrantyStart");f("Основание гарантийных условий","warrantyBasis");f("Условия на детали","warrantyParts");f("Гарантийный талон / факт передачи","coupon")
            f("Фактическое место передачи","transferPlace");f("Оборудование физически получил","receiver");f("Дата фактической выдачи","handoverDate")
            if("quality" in c.confirmed) { f("Приёмка результата ремонта — представитель","qualityPerson");f("Полномочия","qualityBasis");f("Результат приёмки / замечания","qualityResult") }
        }
        if(kind=="letter") {
            c.quotes.lastOrNull()?.let { q->value("Смета","№ ${q.number} от ${q.date}, редакция ${q.version}") }
            value("","Направляем смету на ремонт оборудования. Просим сообщить о согласовании, замечаниях либо отказе от ремонта. При отказе от ремонта просим согласовать дату вывоза оборудования.")
        }
        if(kind=="notice") {
            f("Основание необходимости вывоза","noticeReason");value("","Просим организовать получение оборудования и согласовать дату и порядок передачи.");f("Фактический адрес нахождения","location")
        }
        if(kind in setOf("notice","letter")) {
            if((c.f("dueCount").toIntOrNull() ?: 0)>0 && c.f("dueUnit") in setOf("рабочих дней","календарных дней") && (c.f("dueBasis")=="предлагается для согласования" || (c.f("dueBasis")=="согласован сторонами" && c.f("dueProof").isNotBlank()))) {
                value("Срок — ${c.f("dueBasis")}",c.f("dueCount")+" "+c.f("dueUnit"));f("Основание принятого срока","dueProof");f("Исходное событие","dueEvent");f("Дата события","dueEventDate");f("Подтверждённое получение","receivedDate");f("Конечная дата","dueDate")
            }
        }
        f("Контакты Исполнителя","executorContact")
    }
}
