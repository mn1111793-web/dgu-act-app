package com.example.dguactapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    fun generate(context: Context, act: ActRecord, mode: PdfMode = PdfMode.Filled): Result<File> = runCatching {
        val pdfDirectory = File(context.filesDir, "act_pdfs")
        if (!pdfDirectory.exists() && !pdfDirectory.mkdirs()) {
            throw IOException("Не удалось создать каталог для PDF")
        }

        val suffix = if (mode == PdfMode.Blank) "blank" else "filled"
        val fileName = "akt_${act.requestNumber.ifBlank { act.id.toString() }}_$suffix.pdf"
            .replace("[^a-zA-Z0-9а-яА-Я._-]".toRegex(), "_")
        val outputFile = File(pdfDirectory, fileName)

        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
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
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
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

        fun wrapText(text: String, paint: Paint): List<String> {
            val maxWidth = pageWidth - margin * 2
            val words = text.trim().split(Regex("\\s+"))
            if (words.isEmpty()) return emptyList()
            val lines = mutableListOf<String>()
            var current = ""

            fun pushWord(word: String) {
                val candidate = if (current.isBlank()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = candidate
                } else {
                    if (current.isNotBlank()) lines += current
                    if (paint.measureText(word) <= maxWidth) {
                        current = word
                    } else {
                        var chunk = ""
                        word.forEach { char ->
                            val probe = "$chunk$char"
                            if (paint.measureText(probe) <= maxWidth) {
                                chunk = probe
                            } else {
                                if (chunk.isNotBlank()) lines += chunk
                                chunk = char.toString()
                            }
                        }
                        current = chunk
                    }
                }
            }

            words.forEach(::pushWord)
            if (current.isNotBlank()) lines += current
            return lines
        }

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

        fun drawSignatureBlock(title: String, signature: List<SignatureStroke>) {
            val boxHeight = 74f
            drawLine(title, bodyPaint)
            ensureSpace(boxHeight + 26f)
            val rect = RectF(margin, y + 2f, pageWidth - margin, y + 2f + boxHeight)
            canvas.drawRect(rect, linePaint)
            if (mode == PdfMode.Filled) {
                val bitmap = signatureToBitmap(signature, (rect.width() - 12f).toInt(), (rect.height() - 12f).toInt())
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, rect.left + 6f, rect.top + 6f, null)
                }
            }
            y = rect.bottom + 14f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 12f
            drawLine("(подпись / расшифровка)", smallPaint)
            y += 4f
        }

        drawLine("АКТ ДИАГНОСТИКИ ОБОРУДОВАНИЯ", titlePaint)
        if (mode == PdfMode.Blank) {
            drawLine("Пустой бланк для ручного заполнения", smallPaint)
        }
        y += 6f

        drawSection("1. ОБЩИЕ СВЕДЕНИЯ")
        drawKeyValue("Номер заявки", act.requestNumber)
        drawKeyValue("Дата", act.date)
        drawKeyValue("Заказчик", act.customer)
        drawKeyValue("Адрес заказчика", act.customerAddress)

        y += 2f
        drawSection("2. СВЕДЕНИЯ ОБ ОБОРУДОВАНИИ")
        drawKeyValue("Код оборудования", act.equipmentCode)
        drawKeyValue("Наименование оборудования", act.equipmentName)
        drawKeyValue("Бренд", act.brand)
        drawKeyValue("Модель", act.model)
        drawKeyValue("Серийный номер", act.serialNumber)
        drawKeyValue("Наработка", act.operatingTime)

        y += 2f
        drawSection("3. РЕЗУЛЬТАТЫ ДИАГНОСТИКИ")
        drawKeyValue("Тип диагностики", act.diagnosisType.title)
        drawKeyValue("Комплектность", act.completeness, blankLines = 2)
        drawKeyValue("Внешнее состояние", act.externalCondition, blankLines = 2)
        drawKeyValue("Описание неисправности", act.malfunctionDescription, blankLines = 3)

        if (mode == PdfMode.Filled && act.checklistItems.isNotEmpty()) {
            drawLine("Данные формы:", sectionPaint)
            act.checklistItems.forEachIndexed { index, item ->
                val status = buildString {
                    append(if (item.checked) "Проверено" else "Не проверено")
                    append(", ")
                    append(if (item.faulty) "Есть замечания" else "Без замечаний")
                }
                drawParagraph("${index + 1}. ${item.title}: $status", smallPaint, after = 2f)
                if (item.comment.isNotBlank()) {
                    drawParagraph("Комментарий: ${item.comment}", smallPaint, after = 2f)
                }
            }
            y += 4f
        }

        drawKeyValue("Предварительное заключение", act.preliminaryConclusion, blankLines = 3)
        if (mode == PdfMode.Filled && act.diagnosisType == DiagnosisType.Advanced) {
            drawKeyValue("Конкретная причина неисправности", act.rootCause, blankLines = 2)
            drawKeyValue("Перечень требуемых работ", act.requiredWorks, blankLines = 2)
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

        drawSection("7. ПОДПИСИ СТОРОН")
        drawSignatureBlock("Заказчик", act.customerSignature)
        drawSignatureBlock("Исполнитель", act.executorSignature)
        drawSignatureBlock("Утверждено директором", act.directorSignature)

        if (mode == PdfMode.Filled && act.photos.isNotEmpty()) {
            act.photos.forEachIndexed { index, photo ->
                val bitmap = BitmapFactory.decodeFile(photo.filePath) ?: return@forEachIndexed
                val caption = "Фотография ${index + 1}"
                val availableWidth = pageWidth - margin * 2
                val imageTopOffset = 8f
                val maxImageHeight = pageHeight - margin * 2 - 40f
                val scale = minOf(availableWidth / bitmap.width, maxImageHeight / bitmap.height)
                val targetWidth = bitmap.width * scale
                val targetHeight = bitmap.height * scale

                ensureSpace(20f + imageTopOffset + targetHeight + blockGap)
                drawLine(caption, sectionPaint)
                val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth.toInt(), targetHeight.toInt(), true)
                canvas.drawBitmap(scaled, margin, y + imageTopOffset, null)
                y += imageTopOffset + targetHeight + blockGap
                if (scaled != bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }

        document.finishPage(page)
        FileOutputStream(outputFile).use { output ->
            document.writeTo(output)
        }
        document.close()
        outputFile
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
