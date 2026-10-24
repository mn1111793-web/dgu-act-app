package android.util;
/** Host-only nonfunctional logging shims. All AtomicFile I/O/rename branches stay in AOSP source. */
class Log {
    public static int e(String tag,String text) { System.out.println("AOSP LOG: "+text); return 0; }
    public static int e(String tag,String text,Throwable error) { return e(tag,text); }
}
class SystemConfigFileCommitEventLogger {
    SystemConfigFileCommitEventLogger(String tag) {}
    void setStartTime(long time) {}
    void onStartWrite() {}
    void onFinishWrite() {}
}
class ExceptionUtils {
    static RuntimeException propagate(Throwable error) { return error instanceof RuntimeException ? (RuntimeException)error : new RuntimeException(error); }
}
