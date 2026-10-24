package com.example.dguactapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ActPdfGenerator {
    enum class PdfMode {
        Filled,
        Blank
    }

    private const val pageWidth = 595
    private const val pageHeight = 842
    private const val margin = 36f
    private const val blockGap = 12f
    private const val executorRequisites =
        "Индивидуальный предприниматель Малышева Наталья Александровна, ЯНАО, г. Новый Уренгой, проспект Губкина, 21 б, кв. 52, ИНН 890411627911, ОГРНИП 318890100008958, \"ДГУ Ямал\"."

    private val legalSection = listOf(
        "3.1. Заказчик подтверждает, что оборудование передано Исполнителю для проведения диагностики.",
        "3.2. Заказчик согласен на проведение диагностики и её оплату согласно действующему прайс-листу Исполнителя либо согласованной стоимости.",
        "3.3. Ремонт оборудования выполняется только после дополнительного согласования с Заказчиком объёма работ, сроков и стоимости.",
        "3.4. В случае отказа Заказчика от ремонта после проведения диагностики, стоимость диагностики подлежит оплате в полном объёме.",
        "3.5. Работы выполняются в порядке очередности поступления оборудования с учётом производственной загрузки Исполнителя.",
        "5.1. Стороны договорились, что настоящий Акт может быть подписан собственноручно на бумажном носителе, рукописной подписью на устройстве ввода (планшет, стилус и т.п.) или с использованием одноразового кода, направленного по SMS.",
        "5.2. Любой из указанных способов подписания признаётся сторонами согласованным способом выражения воли и подтверждения содержания Акта.",
        "5.3. Акт, подписанный любым согласованным способом, имеет юридическую силу, эквивалентную документу на бумажном носителе с собственноручными подписями сторон.",
        "5.4. Лицо, подписывающее Акт от имени Заказчика, подтверждает наличие необходимых полномочий."
    )

    private val transferSubjectSection = listOf(
        "1.1. Настоящий Акт подтверждает факт передачи оборудования Заказчиком Исполнителю для проведения диагностики.",
        "1.2. Передаваемое оборудование принимается Исполнителем во временное хранение на период диагностики."
    )
    private val transferAcceptanceSection = listOf(
        "3.1. Оборудование принято в состоянии, указанном Заказчиком при передаче.",
        "3.2. Комплектность и внешнее состояние фиксируются на основании осмотра при приёме.",
        "3.3. Выявленные недостатки и дополнительные замечания указываются в тексте настоящего Акта."
    )
    private val transferStorageSection = listOf(
        "4.1. До завершения диагностики оборудование хранится у Исполнителя с соблюдением стандартных условий хранения.",
        "4.2. Доступ к оборудованию и любые работы осуществляются уполномоченными сотрудниками Исполнителя."
    )

    private fun DocumentType.pdfFilePrefix(): String = when (this) {
        DocumentType.DiagnosticAct -> "akt_diagnostiki"
        DocumentType.TransferAcceptanceAct -> "akt_priema_peredachi"
        DocumentType.AcceptanceAct -> "akt_sdachi_priema"
    }

    fun generate(context: Context, act: ActRecord, mode: PdfMode = PdfMode.Filled): Result<File> = runCatching {
        val pdfDirectory = File(context.filesDir, "act_pdfs")
        if (!pdfDirectory.exists() && !pdfDirectory.mkdirs()) {
            throw IOException("Не удалось создать каталог для PDF")
        }

        val suffix = if (mode == PdfMode.Blank) "blank" else "filled"
        val fileName = "${act.documentType.pdfFilePrefix()}_${act.id}_v${act.revision}_${suffix}_${java.util.UUID.randomUUID()}.pdf"
            .replace("[^a-zA-Z0-9а-яА-Я._-]".toRegex(), "_")
        val outputFile = File(pdfDirectory, fileName)

        val temporary = File(pdfDirectory, fileName + ".pending")
        val document = PdfDocument()
        var activePage: PdfDocument.Page? = null
        try {
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        activePage = page
        var canvas = page.canvas

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10.5f
        }
        val titlePaint = Paint(bodyPaint).apply {
            textSize = 14f
            isFakeBoldText = true
        }
        val sectionPaint = Paint(bodyPaint).apply {
            textSize = 11.5f
            isFakeBoldText = true
        }
        val smallPaint = Paint(bodyPaint).apply {
            textSize = 9.5f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        var y = margin

        fun newPage() {
            document.finishPage(page)
            activePage = null
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            activePage = page
            canvas = page.canvas
            y = margin
        }

        fun ensureSpace(height: Float) {
            if (y + height > pageHeight - margin) newPage()
        }

        fun lineHeight(paint: Paint): Float {
            val fm = paint.fontMetrics
            return (fm.descent - fm.ascent) + 2f
        }

        fun drawLine(text: String, paint: Paint = bodyPaint, x: Float = margin) {
            ensureSpace(lineHeight(paint))
            y += -paint.fontMetrics.ascent
            canvas.drawText(text, x, y, paint)
            y += paint.fontMetrics.descent + 2f
        }

        fun wrapText(text: String, paint: Paint): List<String> =
            LocalRules.wrap(text, pageWidth - margin * 2) { paint.measureText(it) }

        fun drawParagraph(text: String, paint: Paint = bodyPaint, after: Float = 4f) {
            if (text.isBlank()) return
            wrapText(text, paint).forEach { line -> drawLine(line, paint) }
            y += after
        }

        fun drawSection(title: String) {
            drawLine(title, sectionPaint)
            y += 2f
        }

        fun drawKeyValue(label: String, value: String, blankLines: Int = 1) {
            if (mode == PdfMode.Blank) {
                drawLine("$label:", bodyPaint)
                repeat(blankLines) {
                    ensureSpace(18f)
                    val top = y + 2f
                    canvas.drawLine(margin, top + 12f, pageWidth - margin, top + 12f, linePaint)
                    y += 16f
                }
                y += 2f
            } else {
                drawParagraph("$label: ${value.ifBlank { "Не заполнено" }}")
            }
        }

        fun drawSignatureBlock(title: String, signature: List<SignatureStroke>, decoding: String) {
            val boxHeight = 74f
            ensureSpace(boxHeight + 60f + lineHeight(bodyPaint))
            drawLine(title, bodyPaint)
            val rect = RectF(margin, y + 2f, pageWidth - margin, y + 2f + boxHeight)
            canvas.drawRect(rect, linePaint)
            if (mode == PdfMode.Filled) {
                val bitmap = signatureToBitmap(signature, (rect.width() - 12f).toInt(), (rect.height() - 12f).toInt())
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, rect.left + 6f, rect.top + 6f, null)
                    bitmap.recycle()
                }
            }
            y = rect.bottom + 14f
            ensureSpace(12f)
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 12f
            drawLine(if (mode == PdfMode.Blank || decoding.isBlank()) "(подпись / расшифровка)" else "(подпись / $decoding)", smallPaint)
            y += 4f
        }

        fun drawSharedHeader(title: String, subtitle: String? = null) {
            drawLine("ДГУ Ямал", titlePaint)
            drawParagraph(title, sectionPaint)
            if (mode == PdfMode.Filled) drawParagraph("Заявка ${act.requestNumber} · ${act.date} · версия ${act.revision}", smallPaint)
            if (mode == PdfMode.Blank) {
                drawLine("Пустой бланк для ручного заполнения", smallPaint)
            } else if (!subtitle.isNullOrBlank()) {
                drawLine(subtitle, smallPaint)
            }
            y += 6f
        }

        fun drawBaseInfoSections(documentType: DocumentType) {
            drawSection("Основные сведения")
            drawKeyValue("Номер заявки", act.requestNumber)
            drawKeyValue("Дата создания акта", act.createdAt)
            drawKeyValue("Дата документа", act.date)
            drawKeyValue("Заказчик", act.customer)
            drawKeyValue("Телефон", act.phone)
            drawKeyValue("Адрес заказчика", act.customerAddress)
            drawSection("Исполнитель")
            drawKeyValue("Реквизиты", executorRequisites, blankLines = 3)
            drawSection("Данные заказчика")
            drawKeyValue("Наименование организации", act.organizationName)
            drawKeyValue("ИНН", act.organizationInn)
            drawKeyValue("ОГРН", act.organizationOgrn)
            drawKeyValue("Адрес организации", act.organizationAddress)
            drawKeyValue("Телефон организации", act.organizationPhone)
            drawKeyValue("E-mail организации", act.organizationEmail)
            drawSection("Представитель заказчика")
            drawKeyValue("Представитель заказчика", act.customerRepresentative)
            drawKeyValue("Телефон представителя заказчика", act.customerRepresentativePhone)
            y += 2f
            drawSection("2. Сведения об оборудовании")
            drawKeyValue("Код оборудования", act.equipmentCode)
            drawKeyValue("Наименование оборудования", act.equipmentName)
            drawKeyValue("Бренд", act.brand)
            drawKeyValue("Модель", act.model)
            drawKeyValue("Серийный номер", act.serialNumber)
            drawKeyValue("Наработка", act.operatingTime)
            if (documentType == DocumentType.TransferAcceptanceAct) {
                drawKeyValue("Комплектность", act.completeness, blankLines = 2)
                drawKeyValue("Внешнее состояние", act.externalCondition, blankLines = 2)
                drawKeyValue("Описание неисправности", act.malfunctionDescription, blankLines = 3)
            }
        }

        fun drawCustomerRepresentativeBlock() {
            drawSection("Представитель заказчика")
            drawKeyValue("Представитель заказчика", act.customerRepresentative)
            drawKeyValue("Телефон представителя заказчика", act.customerRepresentativePhone)
        }

        fun drawDataTable(headers: List<String>, rows: List<List<String>>, fractions: List<Float>) {
            val left = margin
            val width = pageWidth - margin * 2
            val widths = fractions.map { width * it }
            val x = listOf(left) + widths.runningFold(left) { acc, value -> acc + value }.drop(1)
            val lh = lineHeight(bodyPaint)
            val headerLines = headers.mapIndexed { index, text -> LocalRules.wrap(text, widths[index] - 12f) { smallPaint.measureText(it) } }
            val headerHeight = headerLines.maxOf { it.size } * lineHeight(smallPaint) + 12f
            var headerPage = -1
            rows.forEach { values ->
                val lines = values.mapIndexed { index, text -> LocalRules.wrap(text, widths[index] - 12f) { bodyPaint.measureText(it) } }
                val totalRows = lines.maxOf { it.size }
                var offset = 0
                do {
                    // Check BEFORE all coordinates. A break also requires a fresh header.
                    if (y + (if (headerPage != pageNumber) headerHeight else 0f) + lh + 24f > pageHeight - margin) newPage()
                    if (headerPage != pageNumber) {
                        val top = y + 4f
                        val bottom = top + headerHeight
                        canvas.drawRect(left, top, left + width, bottom, linePaint)
                        x.drop(1).dropLast(1).forEach { canvas.drawLine(it, top, it, bottom, linePaint) }
                        headerLines.forEachIndexed { column, header -> header.forEachIndexed { row, text ->
                            canvas.drawText(text, x[column] + 6f, top + 6f - smallPaint.fontMetrics.ascent + row * lineHeight(smallPaint), smallPaint)
                        } }
                        y = bottom
                        headerPage = pageNumber
                    }
                    val count = minOf(totalRows - offset, LocalRules.tableRowsThatFit(y, pageHeight.toFloat(), margin, 0f, lh))
                    require(count > 0) { "Строка таблицы не помещается на страницу" }
                    val top = y
                    val bottom = top + count * lh + 12f
                    canvas.drawRect(left, top, left + width, bottom, linePaint)
                    x.drop(1).dropLast(1).forEach { canvas.drawLine(it, top, it, bottom, linePaint) }
                    lines.forEachIndexed { column, body -> body.drop(offset).take(count).forEachIndexed { row, text ->
                        canvas.drawText(text, x[column] + 6f, top + 6f - bodyPaint.fontMetrics.ascent + row * lh, bodyPaint)
                    } }
                    y = bottom
                    offset += count
                    if (offset < totalRows) newPage()
                } while (offset < totalRows)
            }
            y += 10f
        }

        fun drawAcceptanceEquipmentTable() {
            val values = if (mode == PdfMode.Blank) listOf("", "", "") else listOf(
                listOf(act.equipmentName, act.brand, act.model).filter { it.isNotBlank() }.joinToString(" / ") +
                    "\nS/N: " + act.serialNumber.ifBlank { "Не заполнено" },
                "1", act.equipmentTransferState.ifBlank { "Не заполнено" })
            drawDataTable(listOf("Наименование и серийный номер", "Кол-во", "Состояние"), listOf(values), listOf(.57f, .13f, .30f))
        }

        drawSharedHeader(
            title = act.documentType.title
        )

        drawBaseInfoSections(act.documentType)

        when (act.documentType) {
            DocumentType.DiagnosticAct -> {
                y += 2f
                drawSection("3. РЕЗУЛЬТАТЫ ДИАГНОСТИКИ")
                drawKeyValue("Тип диагностики", act.diagnosisType.title)
                drawKeyValue("Комплектность", act.completeness, blankLines = 2)
                drawKeyValue("Внешнее состояние", act.externalCondition, blankLines = 2)
                drawKeyValue("Описание неисправности", act.malfunctionDescription, blankLines = 3)

                if (mode == PdfMode.Filled && act.checklistItems.isNotEmpty()) {
                    drawLine("Данные формы:", sectionPaint)
                    drawDataTable(listOf("Проверка", "Результат", "Комментарий"), act.checklistItems.mapIndexed { index, item ->
                        listOf("${index + 1}. ${item.title}",
                            (if (item.checked) "Проверено" else "Не проверено") + ", " + (if (item.faulty) "Есть замечания" else "Без замечаний"),
                            item.comment)
                    }, listOf(.38f, .27f, .35f))
                    y += 4f
                }

                drawKeyValue("Предварительное заключение", act.preliminaryConclusion, blankLines = 3)
                if (mode == PdfMode.Filled && act.diagnosisType == DiagnosisType.Advanced) {
                    drawKeyValue("Конкретная причина неисправности", act.rootCause, blankLines = 2)
                    drawDataTable(listOf("Перечень требуемых работ"), listOf(listOf(act.requiredWorks.ifBlank { "Не заполнено" })), listOf(1f))
                } else if (mode == PdfMode.Blank) {
                    drawKeyValue("Конкретная причина неисправности", "", blankLines = 2)
                    drawKeyValue("Перечень требуемых работ", "", blankLines = 2)
                }

                drawSection("4. ПРАВОВЫЕ УСЛОВИЯ")
                legalSection.take(5).forEach { drawParagraph(it) }
                drawSection("5. ПОДПИСАНИЕ АКТА")
                legalSection.drop(5).forEach { drawParagraph(it) }
                drawSection("6. ХРАНЕНИЕ ОБОРУДОВАНИЯ")
                drawParagraph("На период нахождения оборудования у Исполнителя оно хранится на складе / в ремонтной зоне Исполнителя.")
            }

            DocumentType.TransferAcceptanceAct -> {
                drawSection("1. Предмет акта")
                transferSubjectSection.forEach { drawParagraph(it) }
                drawSection("3. Условия приёма")
                transferAcceptanceSection.forEach { drawParagraph(it) }
                drawSection("4. Хранение оборудования")
                transferStorageSection.forEach { drawParagraph(it) }
                drawSection("5. Электронное подписание")
                legalSection.drop(5).forEach { drawParagraph(it) }
            }

            DocumentType.AcceptanceAct -> {
                drawSection("1. Предмет акта")
                drawParagraph("1.1. Исполнитель передаёт, а Заказчик принимает оборудование после выполненного ремонта.")
                y += 2f
                drawKeyValue("Место передачи имущества", act.compilationPlace)
                drawSection("3. Оборудование")
                drawAcceptanceEquipmentTable()
                drawSection("4. Подтверждение передачи")
                drawParagraph("4.1. Оборудование передано Заказчику в месте передачи имущества, указанном в настоящем Акте.")
                drawParagraph("4.2. Заказчик подтверждает факт возврата оборудования из ремонта и претензий по передаче не имеет.")
                drawKeyValue("Оборудование передается в", act.equipmentTransferState)
                drawParagraph("Заказчик претензий не имеет к состоянию и внешнему виду оборудования.")
                drawParagraph("Гарантия на произведенные работы составляет 14 дней, либо до момента погрузки оборудования.")
                drawParagraph("Гарантия на запасные части — согласно документации производителя.")
            }
        }

        drawSection("6. Подписи сторон")
        drawSignatureBlock(
            "Заказчик",
            act.customerSignature,
            act.customerSignatureDecoding.ifBlank { act.signatureDecoding }
        )
        drawSignatureBlock(
            "Исполнитель",
            act.executorSignature,
            act.executorSignatureDecoding.ifBlank { act.signatureDecoding }
        )
        drawSignatureBlock(
            "Утверждено директором",
            act.directorSignature,
            act.directorSignatureDecoding.ifBlank { act.signatureDecoding }
        )

        if (mode == PdfMode.Filled) {
            act.photos.forEachIndexed { index, photo ->
                val source = File(photo.filePath)
                require(source.isFile && source.length() > 0) { "Оригинал фотографии ${index + 1} недоступен" }
                val bitmap = decodePhoto(source)
                try {
                    newPage()
                    drawParagraph("ДГУ Ямал · ${act.requestNumber} · ${act.documentType.title}", smallPaint)
                    drawLine("Фотография ${index + 1}", sectionPaint)
                    val availableWidth = pageWidth - margin * 2
                    val availableHeight = pageHeight - margin - y - 8f
                    val scale = minOf(availableWidth / bitmap.width, availableHeight / bitmap.height)
                    val width = bitmap.width * scale
                    val height = bitmap.height * scale
                    canvas.drawBitmap(bitmap, null, RectF(margin + (availableWidth - width) / 2, y + 4f,
                        margin + (availableWidth + width) / 2, y + 4f + height), null)
                } finally { bitmap.recycle() }
            }
        }

        document.finishPage(page)
        activePage = null
        FileOutputStream(temporary).use { output -> document.writeTo(output); output.fd.sync() }
        require(!outputFile.exists() && temporary.renameTo(outputFile)) { "Не удалось сохранить PDF" }
        outputFile
        } finally {
            activePage?.let { document.finishPage(it) }
            document.close()
            temporary.delete()
        }
    }

    private fun decodePhoto(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Формат фотографии не распознан" }
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val source = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Фотографию не удалось декодировать")
        try {
            val exif = ExifInterface(file.path)
            val matrix = Matrix()
            if (exif.isFlipped) matrix.postScale(-1f, 1f)
            matrix.postRotate(exif.rotationDegrees.toFloat())
            if (matrix.isIdentity) return source
            val oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            if (oriented !== source) source.recycle()
            return oriented
        } catch (error: OutOfMemoryError) {
            source.recycle()
            throw IOException("Недостаточно памяти для фотографии; оригинал сохранён", error)
        } catch (error: Exception) { source.recycle(); throw error }
    }

    private fun signatureToBitmap(signature: List<SignatureStroke>, width: Int, height: Int): Bitmap? {
        if (signature.isEmpty() || width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        signature.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            val path = Path()
            val first = stroke.points.first()
            path.moveTo(first.x * width, first.y * height)
            stroke.points.drop(1).forEach { point ->
                path.lineTo(point.x * width, point.y * height)
            }
            if (stroke.points.size == 1) {
                canvas.drawCircle(first.x * width, first.y * height, 1.5f, paint)
            } else {
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }
}
