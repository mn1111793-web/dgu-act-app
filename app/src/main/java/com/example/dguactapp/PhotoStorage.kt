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

    fun createCameraPhoto(context: Context): ActPhoto {
        val directory = photosDirectory(context)
        val file = File(directory, "camera_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        return ActPhoto(filePath = file.absolutePath)
    }

    fun importPhotoFromUri(context: Context, sourceUri: Uri): ActPhoto? = runCatching {
        val directory = photosDirectory(context)
        val file = File(directory, "gallery_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null
        ActPhoto(filePath = file.absolutePath)
    }.getOrNull()

    fun deletePhoto(photo: ActPhoto) {
        runCatching {
            File(photo.filePath).takeIf { it.exists() }?.delete()
        }
    }

    fun deleteMissingPhotos(previous: List<ActPhoto>, current: List<ActPhoto>) {
        val currentPaths = current.map { it.filePath }.toSet()
        previous.filterNot { it.filePath in currentPaths }.forEach(::deletePhoto)
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
