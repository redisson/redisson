package org.redisson;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class RedisRunner {

    private static final String redisFolder = "C:\\Devel\\projects\\redis\\Redis-x64-3.0.500\\";

    public static Process runRedis(String configPath) throws IOException, InterruptedException {
        URL resource = RedisRunner.class.getResource(configPath);

        ProcessBuilder master = new ProcessBuilder(redisFolder + "redis-server.exe", resource.getFile().substring(1));
        master.directory(new File(redisFolder));
        Process p = master.start();
        Thread.sleep(1000);
        return p;
    }


}
