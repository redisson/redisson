package org.redisson;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RedisRunner {

    
    private static final String redisFolder = "C:\\Devel\\projects\\redis\\Redis-x64-3.0.500\\";
    private static final String redisBinary = redisFolder + "redis-server.exe";
    
    private final List<String> options = new ArrayList<>();

    {
        options.add(Optional.ofNullable(System.getProperty("redisBinary")).orElse(redisBinary));
    }
    
    /**
     * To change the <b>redisBinary</b> system property for running the test,
     * use <i>argLine</i> option from surefire plugin:
     *
     * $ mvn -DargLine="-DredisBinary=`which redis-server`" -Punit-test clean \
     * verify
     *
     * @param configPath
     * @return Process running redis instance
     * @throws IOException
     * @throws InterruptedException
     * @see
     * <a href="http://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html#argLine">
     * http://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html#argLine</a>
     */
    public static Process runRedisWithConfigFile(String configPath) throws IOException, InterruptedException {
        URL resource = RedisRunner.class.getResource(configPath);

        ProcessBuilder master = new ProcessBuilder(
                Optional.ofNullable(System.getProperty("redisBinary")).orElse(redisBinary),
                resource.getFile().substring(1));
        master.directory(new File(redisFolder));
        Process p = master.start();
        Thread.sleep(1000);
        return p;
    }

    public RedisRunner withPort(int port) {
        this.options.add("--port " + port);
        return this;
    }
    
    public Process run() throws IOException, InterruptedException {
        ProcessBuilder master = new ProcessBuilder(options);
        master.directory(new File(redisBinary).getParentFile());
        Process p = master.start();
        Thread.sleep(1000);
        return p;
    }

}
