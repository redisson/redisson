package org.redisson;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RedissonRuntimeEnvironment {

    public static final boolean isTravis = "true".equalsIgnoreCase(System.getProperty("travisEnv"));
    public static final String redisBinaryPath = System.getProperty("redisBinary", "C:\\Devel\\projects\\redis\\Redis-x64-3.0.500\\redis-server.exe");
    public static final String tempDir = System.getProperty("java.io.tmpdir");
    public static final String OS;
    public static final boolean isWindows;

    static {
        OS = System.getProperty("os.name", "generic");
        isWindows = OS.toLowerCase(Locale.ENGLISH).contains("win");
    }
}
