package org.redisson;

import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.Before;
import org.junit.BeforeClass;
import org.redisson.config.NatMapper;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.redisson.misc.RedisURI;
import org.redisson.spring.data.connection.RedissonClusterConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class BaseTest {

    protected static final String NOTIFY_KEYSPACE_EVENTS = "--notify-keyspace-events";

    protected static final GenericContainer<?> REDIS = createRedis();

    protected static final Protocol protocol = Protocol.RESP2;

    protected static RedissonClient redisson;

    protected static RedissonClient redissonCluster;

    private static GenericContainer<?> REDIS_CLUSTER;

    protected static GenericContainer<?> createRedis() {
        return createRedis("latest");
    }

    protected static GenericContainer<?> createRedis(String version) {
        return new GenericContainer<>("redis:" + version)
                .withCreateContainerCmdModifier(cmd -> {
                    cmd.withCmd("redis-server", "--save", "''");
                })
                .withExposedPorts(6379);
    }

    @BeforeClass
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

    protected void testInCluster(Consumer<RedissonClusterConnection> redissonCallback) {
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

        redissonCallback.accept(new RedissonClusterConnection(redissonCluster));
    }

    protected void withSentinel(BiConsumer<List<GenericContainer<?>>, Config> callback, int slaves) throws InterruptedException {
        Network network = Network.newNetwork();

        List<GenericContainer<? extends GenericContainer<?>>> nodes = new ArrayList<>();

        GenericContainer<?> master =
                new GenericContainer<>("bitnami/redis:7.2.4")
                        .withNetwork(network)
                        .withEnv("REDIS_REPLICATION_MODE", "master")
                        .withEnv("ALLOW_EMPTY_PASSWORD", "yes")
                        .withNetworkAliases("redis")
                        .withExposedPorts(6379);
        master.start();
        assert master.getNetwork() == network;
        int masterPort = master.getFirstMappedPort();
        master.withCreateContainerCmdModifier(cmd -> {
            cmd.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindPort(Integer.valueOf(masterPort)),
                            cmd.getExposedPorts()[0]));
        });
        nodes.add(master);

        for (int i = 0; i < slaves; i++) {
            GenericContainer<?> slave =
                    new GenericContainer<>("bitnami/redis:7.2.4")
                            .withNetwork(network)
                            .withEnv("REDIS_REPLICATION_MODE", "slave")
                            .withEnv("REDIS_MASTER_HOST", "redis")
                            .withEnv("ALLOW_EMPTY_PASSWORD", "yes")
                            .withNetworkAliases("slave" + i)
                            .withExposedPorts(6379);
            slave.start();
            int slavePort = slave.getFirstMappedPort();
            slave.withCreateContainerCmdModifier(cmd -> {
                cmd.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindPort(Integer.valueOf(slavePort)),
                                cmd.getExposedPorts()[0]));
            });
            nodes.add(slave);
        }

        GenericContainer<?> sentinel1 =
                new GenericContainer<>("bitnami/redis-sentinel:7.2.4")

                        .withNetwork(network)
                        .withEnv("REDIS_SENTINEL_DOWN_AFTER_MILLISECONDS", "5000")
                        .withEnv("REDIS_SENTINEL_FAILOVER_TIMEOUT", "10000")
                        .withNetworkAliases("sentinel1")
                        .withExposedPorts(26379);
        sentinel1.start();
        int sentinel1Port = sentinel1.getFirstMappedPort();
        sentinel1.withCreateContainerCmdModifier(cmd -> {
            cmd.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindPort(Integer.valueOf(sentinel1Port)),
                            cmd.getExposedPorts()[0]));
        });
        nodes.add(sentinel1);

        GenericContainer<?> sentinel2 =
                new GenericContainer<>("bitnami/redis-sentinel:7.2.4")
                        .withNetwork(network)
                        .withEnv("REDIS_SENTINEL_DOWN_AFTER_MILLISECONDS", "5000")
                        .withEnv("REDIS_SENTINEL_FAILOVER_TIMEOUT", "10000")
                        .withNetworkAliases("sentinel2")
                        .withExposedPorts(26379);
        sentinel2.start();
        int sentinel2Port = sentinel2.getFirstMappedPort();
        sentinel2.withCreateContainerCmdModifier(cmd -> {
            cmd.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindPort(Integer.valueOf(sentinel2Port)),
                            cmd.getExposedPorts()[0]));
        });
        nodes.add(sentinel2);

        GenericContainer<?> sentinel3 =
                new GenericContainer<>("bitnami/redis-sentinel:7.2.4")
                        .withNetwork(network)
                        .withEnv("REDIS_SENTINEL_DOWN_AFTER_MILLISECONDS", "5000")
                        .withEnv("REDIS_SENTINEL_FAILOVER_TIMEOUT", "10000")
                        .withNetworkAliases("sentinel3")
                        .withExposedPorts(26379);
        sentinel3.start();
        int sentinel3Port = sentinel3.getFirstMappedPort();
        sentinel3.withCreateContainerCmdModifier(cmd -> {
            cmd.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindPort(Integer.valueOf(sentinel3Port)),
                            cmd.getExposedPorts()[0]));
        });
        nodes.add(sentinel3);

        Thread.sleep(5000);

        Config config = new Config();
        config.setProtocol(protocol);
        config.useSentinelServers()
                .setPingConnectionInterval(0)
                .setNatMapper(new NatMapper() {

                    @Override
                    public RedisURI map(RedisURI uri) {
                        for (GenericContainer<? extends GenericContainer<?>> node : nodes) {
                            if (node.getContainerInfo() == null) {
                                continue;
                            }

                            Ports.Binding[] mappedPort = node.getContainerInfo().getNetworkSettings()
                                    .getPorts().getBindings().get(new ExposedPort(uri.getPort()));

                            Map<String, ContainerNetwork> ss = node.getContainerInfo().getNetworkSettings().getNetworks();
                            ContainerNetwork s = ss.values().iterator().next();

                            if (uri.getPort() == 6379
                                    && !uri.getHost().equals("redis")
                                    && BaseTest.this.getClass() == BaseTest.class
                                    && node.getNetworkAliases().contains("slave0")) {
                                return new RedisURI(uri.getScheme(), "127.0.0.1", Integer.valueOf(mappedPort[0].getHostPortSpec()));
                            }

                            if (mappedPort != null
                                    && s.getIpAddress().equals(uri.getHost())) {
                                return new RedisURI(uri.getScheme(), "127.0.0.1", Integer.valueOf(mappedPort[0].getHostPortSpec()));
                            }
                        }
                        return uri;
                    }
                })
                .addSentinelAddress("redis://127.0.0.1:" + sentinel1.getFirstMappedPort())
                .setMasterName("mymaster");

        callback.accept(nodes, config);

        nodes.forEach(n -> n.stop());
        network.close();
    }

    @Before
    public void beforeEach() {
        redisson.getKeys().flushall();
        if (redissonCluster != null) {
            redissonCluster.getKeys().flushall();
        }
    }
}
