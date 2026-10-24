package com.example.dguactapp

import android.util.AtomicFile
import java.io.File
import java.io.IOException

/** finishWrite may already have committed, even when it throws or verification fails. */
class SnapshotCommitUncertainException(cause: Throwable) : IOException(
    "Результат записи не подтверждён: новые данные могли сохраниться. Не удаляйте файлы. Перечитайте данные перед продолжением; повтор должен использовать тот же ID.", cause)

/** No rollback is attempted after entering finishWrite. Read-back does not invoke openRead housekeeping. */
internal class AtomicSnapshotWriter(
    private val atomic: AtomicFile,
    private val readBack: (File) -> ByteArray = { it.readBytes() }
) {
    fun write(payload: ByteArray) {
        val output = atomic.startWrite()
        try {
            output.write(payload)
            output.fd.sync()
        } catch (error: Exception) {
            try { atomic.failWrite(output) } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            throw IOException("Запись прервана до фиксации. Данные формы оставлены для повтора.", error)
        }
        try {
            atomic.finishWrite(output)
            check(!File(atomic.baseFile.path + ".new").exists()) { "Остался незавершённый файл атомарной замены" }
            check(readBack(atomic.baseFile).contentEquals(payload)) { "Сверка записанных байтов не пройдена" }
        } catch (error: Exception) {
            // failWrite cannot undo a rename, and must not be presented as rollback here.
            throw SnapshotCommitUncertainException(error)
        }
    }
}
