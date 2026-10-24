package com.example.dguactapp

import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Locale
import java.util.UUID
import java.io.File

/** No Android dependency: ordering and identifiers can be checked on a plain JVM. */
object LocalRules {
    /** API 24 compatible, segment-aware containment; canonicalization rejects symlink/.. escapes. */
    fun isWithinDirectory(candidate: File, directory: File): Boolean {
        val path = candidate.canonicalPath
        val root = directory.canonicalPath
        val prefix = if (root.endsWith(File.separator)) root else root + File.separator
        return path == root || path.startsWith(prefix)
    }

    fun newId(): Long = UUID.randomUUID().mostSignificantBits.and(Long.MAX_VALUE).let { if (it == 0L) newId() else it }

    fun legacyTime(value: String): Long {
        val parser = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).apply { isLenient = false }
        val position = ParsePosition(0)
        val parsed = parser.parse(value, position)
        require(parsed != null && position.index == value.length) { "Некорректная дата создания: требуется dd.MM.yyyy" }
        return parsed.time
    }

    fun <T> newestFirst(items: List<T>, time: (T) -> Long, id: (T) -> Long): List<T> =
        items.sortedWith(compareByDescending<T>(time).thenByDescending(id))

    fun assertLink(requestId: Long, equipmentId: String, actRequestId: Long, actEquipmentId: String) {
        require(requestId > 0 && requestId == actRequestId && equipmentId.isNotBlank() && equipmentId == actEquipmentId) {
            "Акт связан с другой заявкой или единицей оборудования"
        }
    }

    fun tableRowsThatFit(y: Float, pageHeight: Float, margin: Float, headerHeight: Float, lineHeight: Float): Int {
        require(lineHeight > 0 && headerHeight >= 0)
        return ((pageHeight - margin - y - headerHeight - 24f) / lineHeight).toInt().coerceAtLeast(0)
    }

    /** Wraps every character, including a serial with no spaces. Never truncates. */
    fun wrap(text: String, width: Float, measure: (String) -> Float): List<String> {
        require(width > 0)
        return text.split('\n').flatMap { paragraph ->
            if (paragraph.isEmpty()) listOf("") else buildList {
                var line = ""
                paragraph.forEach { char ->
                    if (line.isNotEmpty() && measure(line + char) > width) { add(line); line = "" }
                    line += char
                }
                add(line)
            }
        }
    }
}
