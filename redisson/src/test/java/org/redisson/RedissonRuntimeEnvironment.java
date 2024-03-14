package org.redisson;

import java.util.Locale;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
@Deprecated
public class RedissonRuntimeEnvironment {

    public static final boolean isTravis = "true".equalsIgnoreCase(System.getProperty("travisEnv"));
    public static final String redisBinaryPath = System.getProperty("redisBinary", installPathByOS());
    public static final String tempDir = System.getProperty("java.io.tmpdir");
    public static final String OS;
    public static final boolean isWindows;
    private static final String MAC_PATH = "/usr/local/opt/redis/bin/redis-server";
//    private static final String WINDOW_PATH = "C:\\redis\\redis-server2.cmd";
    private static final String WINDOW_PATH = "C:\\redis\\redis-server.exe";

    static {
        OS = System.getProperty("os.name", "generic");
        isWindows = OS.toLowerCase(Locale.ENGLISH).contains("win");
    }

    private static String installPathByOS(){
        final String OS = System.getProperty("os.name", "generic");
        final boolean isMacOS = OS.toLowerCase(Locale.ENGLISH).contains("mac");

        return isMacOS ? MAC_PATH : WINDOW_PATH;
    }
}