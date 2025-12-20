package org.redisson.spring.cache;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RedisDockerTest {
    protected static final GenericContainer<?> REDIS = createRedis();
    
    protected static final Protocol protocol = Protocol.RESP2;
    
    protected static GenericContainer<?> createRedisWithVersion(String version, String... params) {
        return new GenericContainer<>(version)
                .withCreateContainerCmdModifier(cmd -> {
                    List<String> args = new ArrayList<>();
                    args.add("redis-server");
                    args.addAll(Arrays.asList(params));
                    cmd.withCmd(args);
                })
                .withExposedPorts(6379)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("redis")));
    }
    
    protected static GenericContainer<?> createRedis(String... params) {
        return createRedisWithVersion("redis:latest", params);
    }
    
    static {
        REDIS.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            REDIS.stop();
        }));
    }
    
    protected static Config createConfig() {
        return createConfig(REDIS);
    }
    
    protected static Config createConfig(GenericContainer<?> container) {
        Config config = new Config();
        config.setProtocol(protocol);
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + container.getFirstMappedPort());
        return config;
    }
    
    public static RedissonClient createInstance() {
        Config config = createConfig();
        return Redisson.create(config);
    }
}
