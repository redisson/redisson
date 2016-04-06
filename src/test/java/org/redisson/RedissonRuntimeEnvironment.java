package org.redisson;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RedissonRuntimeEnvironment {

    public static final boolean isTravis = "true".equalsIgnoreCase(System.getProperty("travisEnv"));
    public static final String redisBinaryPath = System.getProperty("redisBinary", "C:\\Devel\\projects\\redis\\Redis-x64-3.0.500\\redis-server.exe");
    public static final String tempDir = System.getProperty("java.io.tmpdir");
    public static final String OS;
    public static final boolean isWindows;
    public static final String redisFullVersion;
    public static final int redisMajorVersion;
    public static final int redisMinorVersion;
    public static final int redisPatchVersion;

    static {
        redisFullVersion = System.getProperty("redisVersion", "0.0.0");
        Matcher matcher = Pattern.compile("^([\\d]{0,2})\\.([\\d]{0,2})\\.([\\d]{0,2})$").matcher(redisFullVersion);
        matcher.find();
        redisMajorVersion = Integer.parseInt(matcher.group(1));
        redisMinorVersion = Integer.parseInt(matcher.group(2));
        redisPatchVersion = Integer.parseInt(matcher.group(3));
        OS = System.getProperty("os.name", "generic");
        isWindows = OS.toLowerCase(Locale.ENGLISH).contains("win");
    }
}
