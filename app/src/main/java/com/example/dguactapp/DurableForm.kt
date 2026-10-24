package com.example.dguactapp

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** UI scroll/focus/picker registry stays owned by Compose/Activity; only this DTO reaches disk. */
internal class ActDraftSession(val restored: ActFormDraft?) {
    var capture by mutableStateOf<(() -> ActFormDraft)?>(null)
}
internal val LocalActDraftSession = staticCompositionLocalOf { ActDraftSession(null) }

@Composable
internal fun DurableForm(key: String, content: @Composable (flush: () -> Boolean) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var error by remember(key) { mutableStateOf<String?>(null) }
    val restored = remember(key) {
        runCatching { LocalRepository.draft(context, key)?.let { ActFormDraftCodec.decode(it, key).also { draft -> validateDraftOwnership(draft, key, context.filesDir, LocalRepository.read(context).acts) } } }
    }
    if (restored.isFailure) {
        Text("Черновик не прочитан. Запись заблокирована: ${restored.exceptionOrNull()?.message}", color = MaterialTheme.colorScheme.error)
        return
    }
    val session = remember(key) { ActDraftSession(restored.getOrNull()) }
    val writer = remember(key) { DraftPersistence(key) { json ->
        validateDraftOwnership(ActFormDraftCodec.decode(json,key),key,context.filesDir,LocalRepository.read(context).acts)
        LocalRepository.saveDraft(context,key,json)
    } }
    val flush: () -> Boolean = {
        runCatching {
            writer.save(checkNotNull(session.capture) { "Форма ещё не подготовлена" }.invoke())
            error = null
        }.onFailure {
            error = if (it is SnapshotCommitUncertainException) "Запись черновика не подтверждена: ${it.message}"
                else "Черновик не записан: ${it.message}. Не закрывайте приложение."
        }.isSuccess
    }
    LaunchedEffect(key, session) {
        snapshotFlow { session.capture?.invoke() }.collect { draft ->
            if (draft != null) flush()
        }
    }
    DisposableEffect(owner, session) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) flush() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        CompositionLocalProvider(LocalActDraftSession provides session) { content(flush) }
    }
}
