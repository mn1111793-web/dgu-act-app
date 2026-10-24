package android.os;
import java.io.*;
/** Host-only shim: real JDK fsync; Android permissions/native filesystem are NOT simulated. */
public class FileUtils {
    public static final int S_IRWXU=0700, S_IRWXG=0070, S_IXOTH=0001;
    public static boolean sync(FileOutputStream stream) {
        if (stream == null) return true;
        try { stream.getFD().sync(); return true; } catch (IOException e) { return false; }
    }
    public static int setPermissions(String path,int mode,int uid,int gid) { return 0; }
}
