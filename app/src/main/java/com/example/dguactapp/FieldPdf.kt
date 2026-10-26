package com.example.dguactapp

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object FieldPdf {
    fun create(context: Context,c: FieldCase,kind: String): File {
        require(c.pdfErrors(kind).isEmpty()) { c.pdfErrors(kind).joinToString("\n") }
        val dir=File(context.filesDir,"act_pdfs").apply { check(exists() || mkdirs()) }
        val out=File(dir,"workflow_${c.id}_${kind}_${UUID.randomUUID()}.pdf")
        val temp=File(out.path+".pending");val pdf=PdfDocument();var number=0;var current: PdfDocument.Page?=null;var y=60f
        val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=10.5f;color=Color.BLACK }
        val heading=Paint(textPaint).apply { isFakeBoldText=true;textSize=12f;color=Color.rgb(24,50,71) }
        fun page() {
            current?.let { pdf.finishPage(it) };number++;current=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,number).create());y=62f
            current!!.canvas.drawText("ДГУ Ямал",36f,30f,heading);current!!.canvas.drawText("Страница $number",465f,816f,textPaint)
        }
        fun ensure(height: Float) { if(current==null || y+height>790) page() }
        fun wrap(value: String,width: Float,paint: Paint=textPaint): List<String> = value.split('\n').flatMap { raw->
            val result=mutableListOf<String>();var rest=raw
            while(rest.isNotEmpty()) { var n=paint.breakText(rest,true,width,null).coerceAtLeast(1);if(n<rest.length) { val at=rest.substring(0,n).lastIndexOf(' ');if(at>0)n=at };result+=rest.take(n);rest=rest.drop(n).trimStart() };if(result.isEmpty()) listOf("") else result
        }
        fun paragraph(value: String,bold: Boolean=false) { val lines=wrap(value,523f,if(bold)heading else textPaint);for(line in lines){ensure(17f);current!!.canvas.drawText(line,36f,y,if(bold) heading else textPaint);y+=15f};y+=5 }
        fun row(cells: List<String>, columns: Int) {
            val width=523f/columns;val split=cells.map { wrap(it,width-12) };val maxLines=split.maxOfOrNull { it.size } ?: 1
            // Oversized cells continue in bounded chunks; every line is retained.
            for(offset in 0 until maxLines step 28) {
                val count=minOf(28,maxLines-offset);val height=count*14f+12;ensure(height)
                for(i in 0 until columns) {
                    current!!.canvas.drawRect(RectF(36+i*width,y-10,36+(i+1)*width,y-10+height),Paint().apply { style=Paint.Style.STROKE;strokeWidth=.4f;color=Color.LTGRAY })
                    split.getOrElse(i){ emptyList() }.drop(offset).take(count).forEachIndexed { index,s->current!!.canvas.drawText(s,42+i*width,y+index*14,textPaint) }
                };y+=height
            }
            y+=4
        }
        try {
            page();paragraph(if(kind=="return" && c.f("works").isBlank()) "Акт возврата оборудования без ремонта" else FieldContent.titles.getValue(kind),true)
            for(block in FieldContent.blocks(c,kind)) {
                if(block.title.isNotBlank()) { ensure(45f);paragraph(block.title,true) }
                if(block.text.isNotBlank()) paragraph(block.text)
                if(block.rows.isNotEmpty()) { row(block.headers,block.headers.size);block.rows.forEach { row(it,block.headers.size) } }
            }
            for(role in FieldContent.roles(kind).filter { it in c.confirmed }) {
                val label=mapOf("driver" to "Передающее лицо","master" to "Исполнитель / мастер","returnMaster" to "Исполнитель — передал при выдаче","letterSigner" to "Исполнитель — подпись письма","noticeSigner" to "Исполнитель — подпись уведомления","diagnost" to "Диагностику выполнил","approver" to "Утверждение Исполнителем","receiver" to "Физически получил","quality" to "Приёмка результата ремонта").getValue(role)
                val key=if(role=="quality") "qualityPerson" else role
                ensure(90f);paragraph(label+": "+c.f(key));val canvas=current!!.canvas
                for(stroke in c.ink[role].orEmpty()) { val path=Path();stroke.forEachIndexed { i,pt->if(i==0)path.moveTo(36+pt.x*260,y+pt.y*55) else path.lineTo(36+pt.x*260,y+pt.y*55) };canvas.drawPath(path,Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=1.4f }) };y+=70
            }
            val stage=when(kind){"intake"->"intake";"diagnostic"->"diagnostic";"return"->"return";else->""}
            for(photo in c.photos.filter { stage.isNotEmpty() && it.stage==stage }) {
                val file=File(photo.path).canonicalFile;require(file.path.startsWith(File(context.filesDir,"act_photos").canonicalPath+File.separator))
                val options=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(file.path,options)
                require(options.outWidth>0 && options.outHeight>0) { "Не читается фотография" }
                var sample=1;while(options.outWidth/sample>1600 || options.outHeight/sample>1600) sample*=2
                val bitmap=requireNotNull(BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample }))
                val orientation=androidx.exifinterface.media.ExifInterface(file).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,1)
                val matrix=android.graphics.Matrix().apply { when(orientation){2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f);5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(270f);postScale(-1f,1f)};8->setRotate(270f)} }
                val corrected=android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
                val height=minOf(320f,523f*corrected.height/corrected.width);ensure(height+40);paragraph("Фото: ${photo.category}");val width=height*corrected.width/corrected.height
                current!!.canvas.drawBitmap(corrected,null,RectF(36f,y,36f+width,y+height),null);y+=height+18;corrected.recycle();if(corrected!==bitmap)bitmap.recycle()
            }
            current?.let { pdf.finishPage(it) };current=null
            FileOutputStream(temp).use { pdf.writeTo(it);it.fd.sync() };check(temp.renameTo(out));return out
        } finally { pdf.close();if(temp.exists())temp.delete() }
    }
}
