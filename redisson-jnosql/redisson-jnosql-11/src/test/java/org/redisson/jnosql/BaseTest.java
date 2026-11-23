package org.redisson.jnosql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 *
 * @author Nikita Koksharov
 *
 */
@Testcontainers
public abstract class BaseTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:latest").withExposedPorts(6379);

    static RedissonClient redisson;

    @BeforeAll
    static void createClient() {
        redisson = Redisson.create(config());
    }

    @AfterAll
    static void shutdownClient() {
        redisson.shutdown();
    }

    @BeforeEach
    void flushAll() {
        redisson.getKeys().flushall();
    }

    static String address() {
        return "redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort();
    }

    static Config config() {
        Config config = new Config();
        config.useSingleServer().setAddress(address());
        return config;
    }

}
