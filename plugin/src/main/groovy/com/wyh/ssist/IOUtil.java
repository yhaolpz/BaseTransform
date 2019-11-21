package com.wyh.ssist;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author WangYingHao
 * @since 2019/11/21
 */
public class IOUtil {

    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static void closeQuietly(Closeable... closeables) {
        if (closeables != null) {
            for (Closeable closeable : closeables) {
                closeQuietly(closeable);
            }
        }
    }
}
