package org.redisson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.*;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.redisson.misc.RedisURI;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class RedisDockerTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.2")
                    .withExposedPorts(6379);

    protected static RedissonClient redisson;

    @BeforeAll
    public static void beforeAll() {
        Config config = createConfig();
        redisson = Redisson.create(config);
    }

    protected static Config createConfig() {
        Config config = new Config();
        config.setProtocol(Protocol.RESP3);
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + REDIS.getFirstMappedPort());
        return config;
    }

    protected void testInCluster(Consumer<RedissonClient> redissonCallback) {
        GenericContainer<?> redisClusterContainer =
                new GenericContainer<>("vishnunair/docker-redis-cluster")
                        .withExposedPorts(6379, 6380, 6381, 6382, 6383, 6384)
                        .withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(7)));
        redisClusterContainer.start();

        Config config = new Config();
        config.setProtocol(Protocol.RESP3);
        config.useClusterServers()
                .setNatMapper(new NatMapper() {
                    @Override
                    public RedisURI map(RedisURI uri) {
                        if (redisClusterContainer.getMappedPort(uri.getPort()) == null) {
                            return uri;
                        }
                        return new RedisURI(uri.getScheme(), redisClusterContainer.getHost(), redisClusterContainer.getMappedPort(uri.getPort()));
                    }
                })
                .addNodeAddress("redis://127.0.0.1:" + redisClusterContainer.getFirstMappedPort());
        RedissonClient redisson = Redisson.create(config);

        try {
            redissonCallback.accept(redisson);
        } finally {
            redisson.shutdown();
            redisClusterContainer.stop();
        }

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
