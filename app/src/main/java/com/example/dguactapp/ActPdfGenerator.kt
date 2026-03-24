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
    private const val pageWidth = 595
    private const val pageHeight = 842
    private const val margin = 36f
    private const val lineGap = 6f
    private const val blockGap = 12f

    fun generate(context: Context, act: ActRecord): Result<File> = runCatching {
        val pdfDirectory = File(context.filesDir, "act_pdfs")
        if (!pdfDirectory.exists() && !pdfDirectory.mkdirs()) {
            throw IOException("Не удалось создать каталог для PDF")
        }

        val fileName = "akt_${act.requestNumber.ifBlank { act.id.toString() }}.pdf"
            .replace("[^a-zA-Z0-9а-яА-Я._-]".toRegex(), "_")
        val outputFile = File(pdfDirectory, fileName)

        val document = PdfDocument()
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
        }
        val titlePaint = Paint(bodyPaint).apply {
            textSize = 15f
            isFakeBoldText = true
        }
        val sectionPaint = Paint(bodyPaint).apply {
            textSize = 12f
            isFakeBoldText = true
        }
        val smallPaint = Paint(bodyPaint).apply {
            textSize = 10f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        fun finishPage() {
            document.finishPage(page)
        }

        fun startNewPage() {
            finishPage()
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        fun ensureSpace(height: Float) {
            if (y + height > pageHeight - margin) {
                startNewPage()
            }
        }

        fun drawWrapped(text: String, paint: Paint = bodyPaint, extraGap: Float = lineGap) {
            if (text.isBlank()) return
            val maxWidth = pageWidth - margin * 2
            val words = text.trim().split(Regex("\\s+"))
            var line = ""
            words.forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    line = candidate
                } else {
                    ensureSpace(paint.textSize + extraGap)
                    canvas.drawText(line, margin, y, paint)
                    y += paint.textSize + extraGap
                    line = word
                }
            }
            if (line.isNotBlank()) {
                ensureSpace(paint.textSize + extraGap)
                canvas.drawText(line, margin, y, paint)
                y += paint.textSize + extraGap
            }
        }

        fun drawKeyValue(title: String, value: String) {
            drawWrapped("$title: ${value.ifBlank { "Не заполнено" }}")
        }

        fun drawSignatureBlock(title: String, signature: List<SignatureStroke>) {
            val boxHeight = 90f
            val boxTopPadding = 8f
            ensureSpace(22f + boxHeight + blockGap)
            canvas.drawText(title, margin, y, sectionPaint)
            y += sectionPaint.textSize + boxTopPadding
            val rect = RectF(margin, y, pageWidth - margin, y + boxHeight)
            canvas.drawRect(rect, linePaint)

            val bitmap = signatureToBitmap(signature, (rect.width() - 16f).toInt(), (rect.height() - 16f).toInt())
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, rect.left + 8f, rect.top + 8f, null)
            }
            y += boxHeight + blockGap
        }

        canvas.drawText("АКТ ДИАГНОСТИКИ ОБОРУДОВАНИЯ", margin, y, titlePaint)
        y += titlePaint.textSize + 10f
        drawKeyValue("Номер заявки", act.requestNumber)
        drawKeyValue("Дата", act.date)
        drawKeyValue("Заказчик", act.customer)
        drawKeyValue("Адрес заказчика", act.customerAddress)

        y += 4f
        canvas.drawText("Сведения об оборудовании", margin, y, sectionPaint)
        y += sectionPaint.textSize + lineGap
        drawKeyValue("Код оборудования", act.equipmentCode)
        drawKeyValue("Наименование оборудования", act.equipmentName)
        drawKeyValue("Бренд", act.brand)
        drawKeyValue("Модель", act.model)
        drawKeyValue("Серийный номер", act.serialNumber)

        y += 4f
        canvas.drawText("Результаты диагностики", margin, y, sectionPaint)
        y += sectionPaint.textSize + lineGap
        drawKeyValue("Тип диагностики", act.diagnosisType.title)
        drawKeyValue("Наработка", act.operatingTime)
        drawKeyValue("Комплектность", act.completeness)
        drawKeyValue("Внешнее состояние", act.externalCondition)
        drawKeyValue("Описание неисправности", act.malfunctionDescription)

        if (act.checklistItems.isNotEmpty()) {
            drawWrapped("Данные формы:", sectionPaint)
            act.checklistItems.forEachIndexed { index, item ->
                val status = buildString {
                    append(if (item.checked) "Проверено" else "Не проверено")
                    append(", ")
                    append(if (item.faulty) "Есть замечания" else "Без замечаний")
                }
                drawWrapped("${index + 1}. ${item.title}: $status", smallPaint, 4f)
                if (item.comment.isNotBlank()) {
                    drawWrapped("Комментарий: ${item.comment}", smallPaint, 4f)
                }
            }
        }

        drawKeyValue("Предварительное заключение", act.preliminaryConclusion)
        if (act.diagnosisType == DiagnosisType.Advanced) {
            drawKeyValue("Конкретная причина неисправности", act.rootCause)
            drawKeyValue("Перечень требуемых работ", act.requiredWorks)
        }

        y += 4f
        canvas.drawText("3. ПРАВОВЫЕ УСЛОВИЯ", margin, y, sectionPaint)
        y += sectionPaint.textSize + lineGap
        drawWrapped("3.1. Заказчик подтверждает, что оборудование передано Исполнителю для проведения диагностики.")
        drawWrapped("3.2. Заказчик согласен на проведение диагностики и её оплату согласно действующему прайс-листу Исполнителя либо согласованной стоимости.")
        drawWrapped("3.3. Ремонт оборудования выполняется только после дополнительного согласования с Заказчиком объёма работ, сроков и стоимости.")
        drawWrapped("3.4. В случае отказа Заказчика от ремонта после проведения диагностики, стоимость диагностики подлежит оплате в полном объёме.")
        drawWrapped("3.5. Работы выполняются в порядке очередности поступления оборудования, с учётом производственной загрузки Исполнителя.")

        y += 2f
        canvas.drawText("4. ХРАНЕНИЕ ОБОРУДОВАНИЯ", margin, y, sectionPaint)
        y += sectionPaint.textSize + lineGap
        drawWrapped("На период нахождения оборудования у Исполнителя оно хранится на складе / в ремонтной зоне Исполнителя.")

        y += 2f
        canvas.drawText("5. ЭЛЕКТРОННОЕ ПОДПИСАНИЕ", margin, y, sectionPaint)
        y += sectionPaint.textSize + lineGap
        drawWrapped("5.1. Стороны договорились, что подписание настоящего Акта с использованием одноразового кода, направленного Заказчику посредством SMS, признаётся простой электронной подписью.")
        drawWrapped("5.2. Акт, подписанный указанным способом, имеет полную юридическую силу, равную акту, подписанному собственноручно.")
        drawWrapped("5.3. Заказчик подтверждает, что номер телефона, использованный для подписания, принадлежит ему и используется лично.")
        drawWrapped("5.4. Оборудование принято уполномоченным представителем Заказчика. Подписывая настоящий акт, представитель подтверждает наличие полномочий на приём оборудования и выполнение соответствующих действий от имени Заказчика.")

        y += 8f
        canvas.drawText("Подписи", margin, y, sectionPaint)
        y += sectionPaint.textSize + lineGap
        drawSignatureBlock("Заказчик", act.customerSignature)
        drawSignatureBlock("Исполнитель", act.executorSignature)
        drawSignatureBlock("Утверждено директором", act.directorSignature)

        if (act.photos.isNotEmpty()) {
            act.photos.forEachIndexed { index, photo ->
                val bitmap = BitmapFactory.decodeFile(photo.filePath) ?: return@forEachIndexed
                val caption = "Фотография ${index + 1}"
                val availableWidth = pageWidth - margin * 2
                val targetWidth = availableWidth
                val scale = targetWidth / bitmap.width
                val targetHeight = bitmap.height * scale
                ensureSpace(sectionPaint.textSize + 10f + targetHeight + blockGap)
                canvas.drawText(caption, margin, y, sectionPaint)
                y += sectionPaint.textSize + 8f
                val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth.toInt(), targetHeight.toInt(), true)
                canvas.drawBitmap(scaled, margin, y, null)
                y += targetHeight + blockGap
                if (scaled != bitmap) {
                    scaled.recycle()
                }
                bitmap.recycle()
            }
        }

        finishPage()
        FileOutputStream(outputFile).use { output ->
            document.writeTo(output)
        }
        document.close()
        outputFile
    }

    private fun signatureToBitmap(
        signature: List<SignatureStroke>,
        width: Int,
        height: Int
    ): Bitmap? {
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
