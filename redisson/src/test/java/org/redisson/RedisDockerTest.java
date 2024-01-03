package org.redisson;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.NatMapper;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.redisson.misc.RedisURI;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class RedisDockerTest {

    protected static final String NOTIFY_KEYSPACE_EVENTS = "--notify-keyspace-events";

    private static final GenericContainer<?> REDIS = createRedis();

    protected static final Protocol protocol = Protocol.RESP2;

    protected static RedissonClient redisson;

    protected static RedissonClient redissonCluster;

    private static GenericContainer<?> REDIS_CLUSTER;

    protected static GenericContainer<?> createRedis() {
        return new GenericContainer<>("redis:7.2")
                .withCreateContainerCmdModifier(cmd -> {
                    cmd.withCmd("redis-server", "--save", "''");
                })
                .withExposedPorts(6379);
    }

    @BeforeAll
    public static void beforeAll() {
        if (redisson == null) {
            REDIS.start();
            Config config = createConfig();
            redisson = Redisson.create(config);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                redisson.shutdown();
                REDIS.stop();
                if (redissonCluster != null) {
                    redissonCluster.shutdown();
                    redissonCluster = null;
                }
                if (REDIS_CLUSTER != null) {
                    REDIS_CLUSTER.stop();
                    REDIS_CLUSTER = null;
                }
            }));
        }
    }

    protected static Config createConfig() {
        Config config = new Config();
        config.setProtocol(protocol);
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + REDIS.getFirstMappedPort());
        return config;
    }

    protected static RedissonClient createInstance() {
        Config config = createConfig();
        return Redisson.create(config);
    }

    protected void testWithParams(Consumer<RedissonClient> redissonCallback, String... params) {
        GenericContainer<?> redis =
                new GenericContainer<>("redis:7.2")
                        .withCreateContainerCmdModifier(cmd -> {
                            List<String> args = new ArrayList<>();
                            args.add("redis-server");
                            args.addAll(Arrays.asList(params));
                            cmd.withCmd(args);
                        })
                        .withExposedPorts(6379);
        redis.start();

        Config config = new Config();
        config.setProtocol(protocol);
        config.useSingleServer().setAddress("redis://127.0.0.1:" + redis.getFirstMappedPort());
        RedissonClient redisson = Redisson.create(config);

        try {
            redissonCallback.accept(redisson);
        } finally {
            redisson.shutdown();
            redis.stop();
        }

    }

    protected void testInCluster(Consumer<RedissonClient> redissonCallback) {
        if (redissonCluster == null) {
            REDIS_CLUSTER = new GenericContainer<>("vishnunair/docker-redis-cluster")
                            .withExposedPorts(6379, 6380, 6381, 6382, 6383, 6384)
                            .withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(15)));
            REDIS_CLUSTER.start();

            Config config = new Config();
            config.setProtocol(protocol);
            config.useClusterServers()
                    .setNatMapper(new NatMapper() {
                        @Override
                        public RedisURI map(RedisURI uri) {
                            if (REDIS_CLUSTER.getMappedPort(uri.getPort()) == null) {
                                return uri;
                            }
                            return new RedisURI(uri.getScheme(), REDIS_CLUSTER.getHost(), REDIS_CLUSTER.getMappedPort(uri.getPort()));
                        }
                    })
                    .addNodeAddress("redis://127.0.0.1:" + REDIS_CLUSTER.getFirstMappedPort());
            redissonCluster = Redisson.create(config);
        }

        redissonCallback.accept(redissonCluster);
    }

    @BeforeEach
    public void beforeEach() {
        redisson.getKeys().flushall();
        if (redissonCluster != null) {
            redissonCluster.getKeys().flushall();
        }
    }

}
