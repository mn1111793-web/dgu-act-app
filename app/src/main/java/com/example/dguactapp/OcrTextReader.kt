package com.example.dguactapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class OcrDebugResult(
    val text: String,
    val source: String,
    val fallbackUsed: Boolean
)

internal suspend fun readOcrDebugText(context: Context, uri: Uri): OcrDebugResult = withContext(Dispatchers.IO) {
    val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
    val fileName = resolveFileName(context, uri).lowercase(Locale.ROOT)
    val isPdf = mimeType.contains("pdf") || fileName.endsWith(".pdf")

    if (isPdf) {
        val extractedText = runCatching { readPdfRawText(context, uri) }.getOrDefault("")
        val lowQualityPdfText = isLowQualityPdfText(extractedText)
        val ocrText = if (lowQualityPdfText) {
            runCatching { readPdfPagesWithOcr(context, uri) }.getOrDefault("")
        } else {
            ""
        }
        if (lowQualityPdfText && ocrText.isNotBlank()) {
            return@withContext OcrDebugResult(
                text = ocrText,
                source = "PDF OCR fallback",
                fallbackUsed = true
            )
        }
        return@withContext OcrDebugResult(
            text = extractedText,
            source = "PDF parsed text",
            fallbackUsed = false
        )
    }

    val imageText = runCatching { readImageWithOcr(context, uri) }.getOrDefault("")
    OcrDebugResult(
        text = imageText,
        source = "Image OCR",
        fallbackUsed = false
    )
}

private fun isLowQualityPdfText(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.length < 120) return true
    val weirdChars = normalized.count { ch ->
        !ch.isLetterOrDigit() && !ch.isWhitespace() && ch !in setOf('.', ',', ':', ';', '-', '"', '\'', '(', ')', '/', '\\', '№')
    }
    val weirdRatio = weirdChars.toFloat() / normalized.length.toFloat()
    return weirdRatio > 0.18f
}

private fun readPdfRawText(context: Context, uri: Uri): String {
    PDFBoxResourceLoader.init(context.applicationContext)
    val tempFile = File.createTempFile("ocr_pdf_raw_", ".pdf", context.cacheDir)
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return ""
        PDDocument.load(tempFile).use { document ->
            PDFTextStripper().getText(document)
        }
    } finally {
        tempFile.delete()
    }
}

private suspend fun readPdfPagesWithOcr(context: Context, uri: Uri): String {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return ""
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
        val textBlocks = mutableListOf<String>()
        PdfRenderer(descriptor).use { renderer ->
            for (index in 0 until renderer.pageCount) {
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(
                        (page.width * 2).coerceAtLeast(1),
                        (page.height * 2).coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val recognized = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
                    if (recognized.isNotBlank()) {
                        textBlocks.add("[page ${index + 1}]\n$recognized")
                    }
                    bitmap.recycle()
                }
            }
        }
        textBlocks.joinToString("\n\n")
    } finally {
        recognizer.close()
        descriptor.close()
    }
}

private suspend fun readImageWithOcr(context: Context, uri: Uri): String {
    val bitmap = decodeBitmap(context, uri) ?: return ""
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
    } finally {
        recognizer.close()
        bitmap.recycle()
    }
}

@Suppress("DEPRECATION")
private fun decodeBitmap(context: Context, uri: Uri): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source)
    } else {
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { exception ->
        if (continuation.isActive) continuation.resumeWithException(exception)
    }
    addOnCanceledListener {
        if (continuation.isActive) continuation.cancel()
    }
}
