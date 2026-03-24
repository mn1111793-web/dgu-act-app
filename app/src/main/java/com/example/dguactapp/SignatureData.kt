package com.example.dguactapp

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.toMutableStateList

data class SignaturePoint(
    val x: Float,
    val y: Float
)

data class SignatureStroke(
    val points: List<SignaturePoint>
)

data class PdfSignaturesPayload(
    val customerSignature: List<SignatureStroke>,
    val executorSignature: List<SignatureStroke>,
    val directorSignature: List<SignatureStroke>
)

fun ActRecord.toPdfSignaturesPayload(): PdfSignaturesPayload = PdfSignaturesPayload(
    customerSignature = customerSignature,
    executorSignature = executorSignature,
    directorSignature = directorSignature
)

val SignatureStrokeListSaver = listSaver<SnapshotStateList<SignatureStroke>, String>(
    save = { strokes ->
        strokes.map { stroke ->
            stroke.points.joinToString(separator = ";") { point -> "${point.x},${point.y}" }
        }
    },
    restore = { serialized ->
        serialized.mapNotNull { rawStroke ->
            val points = rawStroke
                .split(';')
                .mapNotNull { rawPoint ->
                    val parts = rawPoint.split(',')
                    val x = parts.getOrNull(0)?.toFloatOrNull()
                    val y = parts.getOrNull(1)?.toFloatOrNull()
                    if (x == null || y == null) null else SignaturePoint(x = x, y = y)
                }
            if (points.isEmpty()) null else SignatureStroke(points = points)
        }.toMutableStateList()
    }
)
