package android.content
import java.io.File
class ApplicationInfo(val dataDir: String)
open class Context(val filesDir: File) {
    val applicationInfo = ApplicationInfo(filesDir.parentFile.path)
}
