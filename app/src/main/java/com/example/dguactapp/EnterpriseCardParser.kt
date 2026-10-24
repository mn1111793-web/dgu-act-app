package com.example.dguactapp

import java.util.Locale
import java.util.zip.ZipInputStream
import java.io.InputStream
import java.io.ByteArrayInputStream

internal enum class CompanyCardFormat { PDF, DOCX }
internal fun companyCardFormat(mime: String, fileName: String, header: ByteArray): CompanyCardFormat {
    val m=mime.lowercase(Locale.ROOT).substringBefore(';').trim()
    val n=fileName.lowercase(Locale.ROOT)
    val unknown=m.isBlank() || m=="application/octet-stream"
    if ((m=="application/pdf" || (unknown && n.endsWith(".pdf"))) && header.take(5).toByteArray().contentEquals("%PDF-".toByteArray())) return CompanyCardFormat.PDF
    if ((m=="application/vnd.openxmlformats-officedocument.wordprocessingml.document" || (unknown && n.endsWith(".docx"))) && header.size>=4 && header[0]==80.toByte() && header[1]==75.toByte() && header[2]==3.toByte() && header[3]==4.toByte()) return CompanyCardFormat.DOCX
    error("Поддерживаются текстовые PDF и DOCX; тип/содержимое файла не совпадают")
}
internal fun readCardBytes(input: InputStream, limit: Int = 20 * 1024 * 1024): ByteArray {
    val output=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    while (true) {
        val n=input.read(buffer); if(n<0) break
        require(output.size().toLong()+n<=limit) { "Документ превышает лимит ${limit / (1024 * 1024)} МБ" }
        output.write(buffer,0,n)
    }
    return output.toByteArray()
}
internal fun readDocxText(bytes: ByteArray): String {
    val xml=ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        var entry=zip.nextEntry;var count=0;var found: String?=null
        while(entry!=null) {
            require(++count<=10000) { "Слишком много записей DOCX" }
            if(entry.name=="word/document.xml") { found=readCardBytes(zip,8*1024*1024).toString(Charsets.UTF_8);break }
            zip.closeEntry();entry=zip.nextEntry
        }
        found ?: error("DOCX не содержит word/document.xml")
    }
    val text=Regex("""<w:p\b[\s\S]*?</w:p>""").findAll(xml).map { paragraph ->
        Regex("""<w:t\b[^>]*>([\s\S]*?)</w:t>""").findAll(paragraph.value).joinToString("") { it.groupValues[1] }
    }.joinToString("\n")
    return text.replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&apos;","'").replace("&amp;","&")
}

internal data class EnterpriseCardExtract(
    val organizationName: String,
    val organizationInn: String,
    val organizationOgrn: String,
    val organizationAddress: String,
    val organizationPhone: String,
    val organizationEmail: String
)

internal fun parseEnterpriseCardText(rawText: String): EnterpriseCardExtract? {
    val text = rawText.trim()
    if (text.isBlank()) return null
    val lines = text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { Regex("""^(?:банк|БИК|р/с|к/с)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        .toList()
    if (lines.isEmpty()) return null

    val flattenedText = lines.joinToString(" ") { it }
        .replace(Regex("""\s+"""), " ")
        .trim()

    val organizationLabelPrefix = """(?:организац(?:ия|ии)|компан(?:ия|ии)|предприят(?:ие|ия))\s*"""
    val legalFormPrefix = """(?:ООО|ОАО|ЗАО|АО|ПАО|ИП)\s*"""
    val name = (
        Regex(
            """$organizationLabelPrefix(?:[:\-]\s*)?(\"[^\"]+\"|«[^»]+»|[^,\n;]+)""",
            RegexOption.IGNORE_CASE
        ).find(flattenedText)?.groupValues?.getOrNull(1)
            ?: Regex(
                """$legalFormPrefix(?:\"[^\"]+\"|«[^»]+»|[A-Za-zА-Яа-я0-9\.\- ]{2,})""",
                RegexOption.IGNORE_CASE
            ).find(flattenedText)?.value.orEmpty()
        ).normalizeExtractedField()

    val inn = Regex("""\bИНН\b\D*(\d{12}|\d{10})(?!\d)""", RegexOption.IGNORE_CASE)
        .find(flattenedText)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    val ogrn = Regex("""\bОГРН(?:ИП)?\b\D*(\d{15}|\d{13})(?!\d)""", RegexOption.IGNORE_CASE)
        .find(flattenedText)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    val address = extractFieldByLabel(
        text = flattenedText,
        labelRegex = """(?:юридическ(?:ий|ого)\s+адрес|адрес\s+организац(?:ии|ия)|адрес)"""
    ).ifBlank {
        Regex(
            """\b(индекс\s*\d{6}|\d{6})\b[^;\n]*?(?:г\.|город|ул\.|улица|проспект|пр-т|д\.|дом|помещ\.?)""",
            RegexOption.IGNORE_CASE
        ).find(flattenedText)?.value.orEmpty()
    }.normalizeExtractedField()

    val phone = extractFieldByLabel(
        text = flattenedText,
        labelRegex = """(?:телефон\s+организац(?:ии|ия)|контактн(?:ый|ого)\s+телефон|телефон|тел\.?)"""
    ).let { extractedPhoneBlock ->
        Regex("""(?<!\d)(\+?7|8)[\d\-\s\(\)]{9,}(?!\d)""")
            .find(extractedPhoneBlock.ifBlank { flattenedText })
            ?.value
            .orEmpty()
            .normalizeExtractedField()
    }
    val email = Regex("""[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)
        .find(
            extractFieldByLabel(
                text = flattenedText,
                labelRegex = """(?:e-?mail|электронн(?:ая|ой)\s+почта|почта)"""
            ).ifBlank { flattenedText }
        )
        ?.value
        .orEmpty()
        .normalizeExtractedField()

    if (name.isBlank() && inn.isBlank() && ogrn.isBlank() && address.isBlank() && phone.isBlank() && email.isBlank()) return null
    return EnterpriseCardExtract(
        organizationName = name,
        organizationInn = inn,
        organizationOgrn = ogrn,
        organizationAddress = address,
        organizationPhone = phone,
        organizationEmail = email
    )
}

private fun extractFieldByLabel(text: String, labelRegex: String): String {
    val valuePattern = Regex(
        """(?:$labelRegex)\s*(?:[:\-]\s*|\s+)(.+?)(?=(?:,?\s*(?:ИНН|КПП|ОГРН(?:ИП)?|(?:юридическ(?:ий|ого)\s+)?адрес|телефон|тел\.?|контактн(?:ый|ого)\s+телефон|банк|БИК|р/с|расч[её]тн(?:ый|ого)\s+сч[её]т|к/с|корреспондентск(?:ий|ого)\s+сч[её]т)\b)|$)""",
        RegexOption.IGNORE_CASE
    )
    return valuePattern.find(text)?.groupValues?.getOrNull(1).orEmpty().normalizeExtractedField()
}

private fun String.containsBankRequisitesMarker(): Boolean = Regex(
    """\b(?:банк|БИК|р/с|расч[её]тн(?:ый|ого)\s+сч[её]т|к/с|корреспондентск(?:ий|ого)\s+сч[её]т)\b""",
    RegexOption.IGNORE_CASE
).containsMatchIn(this)

private fun String.normalizeExtractedField(): String = trim()
    .trim(',', ';', '.')
    .replace(Regex("""\s+"""), " ")
