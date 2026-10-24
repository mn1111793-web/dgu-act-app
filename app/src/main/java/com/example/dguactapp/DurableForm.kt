package com.example.dguactapp

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONArray
import org.json.JSONObject

/** Durable small form state. Photos are files, signatures/checklists use their existing list savers. */
@Composable
internal fun DurableForm(key: String, content: @Composable (flush: () -> Boolean) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var error by remember(key) { mutableStateOf<String?>(null) }
    val restored = remember(key) { runCatching { LocalRepository.draft(context, key)?.let(DraftCodec::decodeState) } }
    if (restored.isFailure) {
        Text("Черновик не прочитан. Запись заблокирована: ${restored.exceptionOrNull()?.message}", color = MaterialTheme.colorScheme.error)
        return
    }
    val registry = remember(key) { SaveableStateRegistry(restored.getOrNull()) { true } }
    val lastSaved = remember(key) { arrayOf("") }
    val flush: () -> Boolean = {
        runCatching {
            val json = DraftCodec.encodeState(registry.performSave())
            val encoded = json.toString()
            if (encoded != lastSaved[0]) { LocalRepository.saveDraft(context, key, json); lastSaved[0] = encoded }
            error = null
        }.onFailure { error = if (it is SnapshotCommitUncertainException) "Запись черновика не подтверждена: ${it.message}" else "Черновик не записан: ${it.message}. Не закрывайте приложение." }.isSuccess
    }
    LaunchedEffect(key, registry) {
        // performSave reads State/List savers; snapshotFlow observes edits, including strokes and photo callbacks.
        snapshotFlow {
            runCatching { DraftCodec.encodeState(registry.performSave()).toString() }
                .getOrElse { "Ошибка сериализации: ${it.message}" }
        }.collect { flush() }
    }
    DisposableEffect(owner, registry) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) flush() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { content(flush) }
    }
}
