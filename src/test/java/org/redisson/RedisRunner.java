package org.redisson;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.Objects;

public class RedisRunner {

    public static final int REDIS_EXIT_CODE;

    private static final String defaultRedisDirectory = "C:\\Devel\\projects\\redis\\Redis-x64-3.0.500\\";
    private static final String defaultRedisExecutable = defaultRedisDirectory + "redis-server.exe";
    private static final String redisExecutable;

    static {
        String path = System.getenv("PATH");
        if (path != null) {
            String os = System.getProperty("os.name");
            String pathSeparator = ":";
            String executableName = "redis-server";
            if (os.toLowerCase().startsWith("windows")) {
                pathSeparator = ";";
                executableName = "redis-server.exe";
                REDIS_EXIT_CODE = 1;
            } else {
                REDIS_EXIT_CODE = 0;
            }

            String[] pathEntries = path.split(pathSeparator);
            Path fullExecutablePath = null;
            for (String pathEntry : pathEntries) {
                fullExecutablePath = Paths.get(pathEntry, executableName);
                if (Files.exists(fullExecutablePath)) {
                    break;
                }
            }

            if (fullExecutablePath != null) {
                redisExecutable = fullExecutablePath.toString();
            } else {
                redisExecutable = defaultRedisExecutable;
            }
        } else {
            redisExecutable = defaultRedisExecutable;
            REDIS_EXIT_CODE = 1;
        }

        if (!new File(redisExecutable).exists()) {
            throw new RuntimeException("Redis executable not found");
        }
    }

    public static Process runRedis(String configPath) throws IOException, InterruptedException {
        URL resource = RedisRunner.class.getResource(configPath);
        String fullConfigPath = Paths.get(resource.getFile()).toAbsolutePath().toString();

        ProcessBuilder master = new ProcessBuilder(redisExecutable, fullConfigPath, "--dir", getWorkingDirectory());
        master.directory(new File(getWorkingDirectory()));
        Process p = master.start();
        Thread.sleep(1000);

        if (!p.isAlive()) {
            throw new RuntimeException("Redis executable stopped with exit code " + p.exitValue());
        }

        return p;
    }

    private static String getWorkingDirectory() {
        if (redisExecutable.equals(defaultRedisExecutable)) {
            return defaultRedisDirectory;
        }
        return System.getProperty("user.dir");
    }
}
