package org.redisson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class DockerRedisStackTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis/redis-stack-server:latest")
                    .withExposedPorts(6379);

    protected static RedissonClient redisson;

    @BeforeAll
    public static void beforeAll() {
        Config config = createConfig();
        redisson = Redisson.create(config);
    }

    protected static Config createConfig() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + REDIS.getFirstMappedPort());
        return config;
    }

    @BeforeEach
    public void beforeEach() {
        redisson.getKeys().flushall();
    }

    @AfterAll
    public static void afterAll() {
        redisson.shutdown();
    }

}
