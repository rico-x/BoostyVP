// SPDX-License-Identifier: MIT
package android.util;

/**
 * JVM unit tests run against android.jar stubs whose Log throws "not mocked".
 * The mappers log unusual input on purpose, so in this module's tests logging
 * is a no-op instead of a false crash, and fuzzing does not report it.
 *
 * OkHttp 5 asks isLoggable and logs through println when it finds an Android Log,
 * so those must exist too; a missing method is a NoSuchMethodError that breaks
 * every HTTP test in the module.
 */
public final class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    private Log() {}
    public static boolean isLoggable(String tag, int level) { return false; }
    public static int println(int priority, String tag, String msg) { return 0; }
    public static int v(String tag, String msg) { return 0; }
    public static int v(String tag, String msg, Throwable tr) { return 0; }
    public static int d(String tag, String msg) { return 0; }
    public static int d(String tag, String msg, Throwable tr) { return 0; }
    public static int i(String tag, String msg) { return 0; }
    public static int i(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, String msg) { return 0; }
    public static int w(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, Throwable tr) { return 0; }
    public static int e(String tag, String msg) { return 0; }
    public static int e(String tag, String msg, Throwable tr) { return 0; }
}
