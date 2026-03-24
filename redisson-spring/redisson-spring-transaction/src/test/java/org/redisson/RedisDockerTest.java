package org.redisson;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import io.netty.resolver.dns.DnsServerAddresses;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.config.NatMapper;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.redisson.connection.SequentialDnsAddressResolverFactory;
import org.redisson.misc.RedisURI;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.*;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.lifecycle.Startable;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RedisDockerTest {

    protected static final String NOTIFY_KEYSPACE_EVENTS = "--notify-keyspace-events";

    protected static final String MAXMEMORY_POLICY = "--maxmemory-policy";

    protected static final GenericContainer<?> REDIS = createRedis();

    protected static final Protocol protocol = Protocol.RESP2;

    protected static RedissonClient redisson;

    protected static RedissonClient redissonCluster;

    private static Startable REDIS_CLUSTER;

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
    
    protected void testTwoDatabase(BiConsumer<RedissonClient, RedissonClient> consumer) {
        Config config1 = createConfig();
        config1.useSingleServer().setDatabase(0);
        RedissonClient r1 = Redisson.create(config1);
        Config config2 = createConfig();
        config2.useSingleServer().setDatabase(1);
        RedissonClient r2 = Redisson.create(config2);
        
        consumer.accept(r1, r2);
        
        r1.shutdown();
        r2.shutdown();
    }
    
    protected void testWithParams(Consumer<RedissonClient> redissonCallback, String... params) {
        GenericContainer<?> redis = createRedis(params);
        redis.start();

        Config config = createConfig(redis);
        RedissonClient redisson = Redisson.create(config);

        try {
            redissonCallback.accept(redisson);
        } finally {
            redisson.shutdown();
            redis.stop();
        }
    }

    protected static void testInCluster(Consumer<RedissonClient> redissonCallback) {
        if (redissonCluster == null) {
            ClusterData data = createCluster();
            REDIS_CLUSTER = data.container;
            redissonCluster = data.redisson;
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

    record ClusterData(Startable container, RedissonClient redisson, List<ContainerState> nodes) {}

    private static ClusterData createCluster() {
        DockerComposeContainer environment =
                new DockerComposeContainer(new File("src/test/resources/docker-compose-redis-cluster.yml"))
                        // TODO fix
                        .withOptions("--compatibility")
                        .withExposedService("redis-node-1", 6379)
                        .withExposedService("redis-node-2", 6379)
                        .withExposedService("redis-node-3", 6379)
                        .withExposedService("redis-node-4", 6379)
                        .withExposedService("redis-node-5", 6379)
                        .withExposedService("redis-node-6", 6379);

        environment.start();

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        List<ContainerState> nodes = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            Optional<ContainerState> cc = environment.getContainerByServiceName("redis-node-" + i);
            nodes.add(cc.get());
        }

        Optional<ContainerState> cc2 = environment.getContainerByServiceName("redis-node-1");
        Ports.Binding[] mp = cc2.get().getContainerInfo().getNetworkSettings()
                .getPorts().getBindings().get(new ExposedPort(cc2.get().getExposedPorts().get(0)));

        Config config = new Config();
        config.useClusterServers()
                .setNatMapper(new NatMapper() {

                    @Override
                    public RedisURI map(RedisURI uri) {
                        for (ContainerState state : nodes) {
                            if (state.getContainerInfo() == null) {
                                continue;
                            }

                            InspectContainerResponse node = state.getContainerInfo();
                            Ports.Binding[] mappedPort = node.getNetworkSettings()
                                    .getPorts().getBindings().get(new ExposedPort(uri.getPort()));

                            Map<String, ContainerNetwork> ss = node.getNetworkSettings().getNetworks();
                            ContainerNetwork s = ss.values().iterator().next();

                            if (mappedPort != null
                                    && s.getIpAddress().equals(uri.getHost())) {
                                return new RedisURI(uri.getScheme(), "127.0.0.1", Integer.valueOf(mappedPort[0].getHostPortSpec()));
                            }
                        }
                        return uri;
                    }
                })
                .addNodeAddress("redis://127.0.0.1:" + mp[0].getHostPortSpec());

        RedissonClient redisson = Redisson.create(config);
        return new ClusterData(environment, redisson, nodes);
    }

    protected void withNewCluster(BiConsumer<List<ContainerState>, RedissonClient> callback) {
        ClusterData data = createCluster();

        try {
            callback.accept(data.nodes, data.redisson);
        } finally {
            data.redisson.shutdown();
            data.container.stop();
        }
    }

    protected void withNewCluster(Consumer<ClusterData> callback) {
        ClusterData data = createCluster();

        try {
            callback.accept(data);
        } finally {
            data.redisson.shutdown();
            data.container.stop();
        }
    }

    protected String execute(ContainerState node, String... commands) {
        try {
            Container.ExecResult r = node.execInContainer(commands);
            if (!r.getStderr().isBlank()) {
                throw new RuntimeException(r.getStderr());
            }
            return r.getStdout();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected List<ContainerState> getSlaveNodes(List<ContainerState> nodes) {
        return nodes.stream().filter(node -> {
            if (!node.isRunning()) {
                return false;
            }
            String r = execute(node, "redis-cli", "info", "replication");
            return r.contains("role:slave");
        }).collect(Collectors.toList());
    }

    protected List<ContainerState> getMasterNodes(List<ContainerState> nodes) {
        return nodes.stream().filter(node -> {
            if (!node.isRunning()) {
                return false;
            }
            String r = execute(node, "redis-cli", "info", "replication");
            return r.contains("role:master");
        }).collect(Collectors.toList());
    }

    protected void stop(ContainerState node) {
        execute(node, "redis-cli", "shutdown");
    }

    protected void restart(GenericContainer<?> redis) {
        redis.setPortBindings(Arrays.asList(redis.getFirstMappedPort() + ":" + redis.getExposedPorts().get(0)));
        redis.stop();
        redis.start();
    }

}
