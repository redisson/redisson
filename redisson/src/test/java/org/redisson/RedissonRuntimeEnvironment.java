package org.redisson;

import java.util.Locale;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonRuntimeEnvironment {

    public static final boolean isTravis = "true".equalsIgnoreCase(System.getProperty("travisEnv"));
    public static final String redisBinaryPath = System.getProperty("redisBinary", "C:\\Devel\\projects\\redis\\Redis-x64-3.2.100\\redis-server.exe");
    public static final String tempDir = System.getProperty("java.io.tmpdir");
    public static final String OS;
    public static final boolean isWindows;

    static {
        OS = System.getProperty("os.name", "generic");
        isWindows = OS.toLowerCase(Locale.ENGLISH).contains("win");
    }
}
