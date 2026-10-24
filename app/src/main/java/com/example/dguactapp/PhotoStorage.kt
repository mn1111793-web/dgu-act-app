package com.example.dguactapp

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import java.util.UUID

data class ActPhoto(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String
) {
    val uri: Uri
        get() = File(filePath).toUri()
}

object PhotoStorage {
    private const val photosDirectoryName = "act_photos"

    fun createCameraPhoto(context: Context, requestId: Long, equipmentId: String, actId: Long): ActPhoto {
        val directory = ownerDirectory(context, requestId, equipmentId, actId)
        val file = File(directory, "camera_${UUID.randomUUID()}.jpg")
        return ActPhoto(id = file.nameWithoutExtension, filePath = file.absolutePath)
    }

    fun importPhotoFromUri(context: Context, sourceUri: Uri, requestId: Long, equipmentId: String, actId: Long): ActPhoto? {
        var temporary: File? = null
        return try {
            val directory = ownerDirectory(context, requestId, equipmentId, actId)
            val file = File(directory, "gallery_${UUID.randomUUID()}.jpg")
            val staging = File(directory, file.name + ".pending")
            temporary = staging
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                java.io.FileOutputStream(staging).use { output -> input.copyTo(output); output.fd.sync() }
            } ?: throw IOException("Нет доступа к выбранному фото")
            validateOriginal(staging)
            require(staging.renameTo(file)) { "Не удалось сохранить оригинал фотографии" }
            ActPhoto(id = file.nameWithoutExtension, filePath = file.absolutePath)
        } catch (error: Exception) {
            // A failed new import never deletes earlier originals or their references.
            temporary?.delete()
            null
        }
    }

    fun confirmCameraPhoto(photo: ActPhoto): Result<ActPhoto> = runCatching {
        val source = File(photo.filePath)
        validateOriginal(source)
        java.io.FileOutputStream(source, true).use { it.fd.sync() }
        photo
    }

    private fun validateOriginal(source: File) {
        require(source.isFile && source.length() > 0) { "Пустой оригинал фотографии" }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(source.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Фотография не распознана" }
    }

    private fun ownerDirectory(context: Context, requestId: Long, equipmentId: String, actId: Long): File {
        require(requestId > 0 && actId > 0 && equipmentId.matches(Regex("[a-zA-Z0-9-]+")))
        return File(photosDirectory(context), "request_$requestId/equipment_$equipmentId/act_$actId").apply {
            if (!exists() && !mkdirs()) throw IOException("Не удалось создать каталог оригиналов")
        }
    }

    private fun photosDirectory(context: Context): File {
        val directory = File(context.filesDir, photosDirectoryName)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Не удалось создать каталог для фотографий")
        }
        return directory
    }
}


val ActPhotoListSaver = listSaver<SnapshotStateList<ActPhoto>, String>(
    save = { list ->
        list.flatMap { photo -> listOf(photo.id, photo.filePath) }
    },
    restore = { restored ->
        restored.chunked(2)
            .mapNotNull { chunk ->
                if (chunk.size < 2 || chunk[1].isBlank()) {
                    null
                } else {
                    ActPhoto(id = chunk[0], filePath = chunk[1])
                }
            }
            .toMutableStateList()
    }
)
