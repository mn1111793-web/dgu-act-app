package libcore.io;
import java.io.Closeable;
public class IoUtils {
    public static void closeQuietly(Closeable stream) { if(stream!=null)try {stream.close();}catch(Exception ignored){} }
}
