package android.util
import java.io.*
/** Fault-injection MODEL only. Separate run_aosp.py checks the pinned Android13 algorithm with host shims. */
object DiskFault {
    var fail = false
    var denyRead = false
    var denyStart = false
    var finishMode = "normal"
    var cleanupFailure = false
    var rollbackCalls = 0
    fun reset() { fail=false;denyRead=false;denyStart=false;finishMode="normal";cleanupFailure=false;rollbackCalls=0 }
}
class AtomicFile(val baseFile: File) {
    private val pending = File(baseFile.path + ".new")
    private val backup = File(baseFile.path + ".bak")
    fun openRead(): FileInputStream {
        if (DiskFault.denyRead) throw IOException("injected read denied")
        if (backup.exists()) check(backup.renameTo(baseFile))
        if (pending.exists() && baseFile.exists()) check(pending.delete())
        return FileInputStream(baseFile)
    }
    fun startWrite(): FileOutputStream {
        if (DiskFault.denyStart) throw IOException("injected start denied")
        baseFile.parentFile.mkdirs()
        if (backup.exists()) check(backup.renameTo(baseFile))
        return object : FileOutputStream(pending) {
            override fun write(bytes: ByteArray) {
                if (DiskFault.fail) { super.write(bytes, 0, minOf(10, bytes.size)); throw IOException("injected disk full") }
                super.write(bytes)
            }
        }
    }
    fun finishWrite(stream: FileOutputStream) {
        stream.close()
        if (DiskFault.finishMode == "no-rename") return
        if (DiskFault.finishMode == "throw-before") throw IOException("injected finish before rename")
        check(pending.renameTo(baseFile))
        if (DiskFault.finishMode == "throw-after") throw IOException("injected finish after rename")
    }
    fun failWrite(stream: FileOutputStream) {
        DiskFault.rollbackCalls++
        stream.close()
        if (DiskFault.cleanupFailure) throw IOException("injected cleanup failure")
        pending.delete()
    }
}
