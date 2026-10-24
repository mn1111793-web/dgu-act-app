package android.os
/** Minimal platform surfaces needed by real Compose State on a JVM; no Android lifecycle is simulated. */
class Looper(val thread: Thread = Thread.currentThread()) { companion object { @JvmStatic fun getMainLooper() = Looper() } }
interface Parcelable {
    fun describeContents(): Int
    fun writeToParcel(dest: Parcel, flags: Int)
    interface Creator<T> { fun createFromParcel(source: Parcel): T; fun newArray(size: Int): Array<T?> }
    interface ClassLoaderCreator<T> : Creator<T> { fun createFromParcel(source: Parcel, loader: ClassLoader?): T }
}
class Parcel {
    fun readValue(loader: ClassLoader?): Any? = error("Parcel not used in source tests")
    fun writeValue(value: Any?) { error("Parcel not used in source tests") }
    fun readInt(): Int = error("Parcel not used in source tests")
    fun writeInt(value: Int) { error("Parcel not used in source tests") }
}
