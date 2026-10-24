package com.example.dguactapp

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Prints a generated immutable PDF. Does not generate, share, or export the application database. */
internal object PdfDelivery {
    fun print(context: Context, file: File) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(file.nameWithoutExtension, object : PrintDocumentAdapter() {
            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
                                  cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
                if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
                callback.onLayoutFinished(PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), oldAttributes != newAttributes)
            }
            override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor,
                                 cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
                try {
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { source ->
                        com.tom_roush.pdfbox.pdmodel.PDDocument().use { selected ->
                            val indices = (0 until source.numberOfPages).filter { index -> pages.any { index >= it.start && index <= it.end } }
                            if (indices.isEmpty()) { callback.onWriteFailed("Не выбраны страницы"); return }
                            indices.forEach { index ->
                                if (cancellationSignal.isCanceled) { callback.onWriteCancelled(); return }
                                selected.importPage(source.getPage(index))
                            }
                            FileOutputStream(destination.fileDescriptor).use { selected.save(it) }
                            if (cancellationSignal.isCanceled) { callback.onWriteCancelled(); return }
                            callback.onWriteFinished(indices.map { PageRange(it, it) }.toTypedArray())
                        }
                    }
                } catch (error: Exception) { callback.onWriteFailed("PDF не передан на печать: ${error.message}") }
            }
        }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR).build())
    }
}
