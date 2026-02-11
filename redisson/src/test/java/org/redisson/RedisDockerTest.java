package org.redisson;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.NatMapper;
import org.redisson.config.Protocol;
import org.redisson.config.SentinelServersConfig;
import org.redisson.misc.RedisURI;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.*;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RedisDockerTest {

    protected static final String IMAGE = "redis:latest";

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
                    if (params.length > 0) {
                        List<String> args = new ArrayList<>();
                        args.add("redis-server");
                        args.addAll(Arrays.asList(params));
                        cmd.withCmd(args);
                    }
                })
                .withExposedPorts(6379)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("redis")));
    }

    protected static GenericContainer<?> createRedis(String... params) {
        return createRedisWithVersion(IMAGE, params);
//        return createRedisWithVersion("valkey/valkey-bundle:latest", params);
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

    protected static final String MASTER_NAME = "mymaster";
    protected static final int REDIS_PORT = 6379;
    protected static final int SENTINEL_PORT = 26379;

    protected void withSentinel(BiConsumer<List<GenericContainer<?>>, Config> callback, int slaves) throws InterruptedException {
        withSentinel(callback, slaves, null);
    }

    protected void withSentinel(BiConsumer<List<GenericContainer<?>>, Config> callback, int slaves, String password) throws InterruptedException {
        Network network = Network.newNetwork();
        List<GenericContainer<?>> nodes = new ArrayList<>();

        try {
            GenericContainer<?> master = createMasterContainer(network, password);
            master.start();
            preservePortBindings(master);
            nodes.add(master);

            String masterIp = getContainerIp(master);

            Thread.sleep(1000);

            for (int i = 0; i < slaves; i++) {
                GenericContainer<?> slave = createSlaveContainer(network, i, password, masterIp);
                slave.start();
                preservePortBindings(slave);
                nodes.add(slave);
            }

            Thread.sleep(3000);

            int sentinelCount = 3;
            int quorum = 2;
            List<GenericContainer<?>> sentinels = new ArrayList<>();
            for (int i = 0; i < sentinelCount; i++) {
                GenericContainer<?> sentinel = createSentinelContainer(network, i, password, quorum, masterIp);
                sentinel.start();
                preservePortBindings(sentinel);
                sentinels.add(sentinel);
                nodes.add(sentinel);
            }

            // Wait for sentinels to discover topology
            Thread.sleep(5000);

            Config config = new Config();
            SentinelServersConfig sentinelServersConfig = config.useSentinelServers()
                    .setMasterName(MASTER_NAME)
                    .setCheckSentinelsList(false)
                    .setScanInterval(1000)
                    .setNatMapper(address -> {
                        for (GenericContainer<?> node : nodes) {
                            try {
                                // Check by IP address
                                String nodeIp = getContainerIp(node);
                                if (nodeIp.equals(address.getHost())) {
                                    int mappedPort = node.getMappedPort(address.getPort());
                                    return new RedisURI(address.getScheme(), node.getHost(), mappedPort);
                                }

                                // Check by hostname (network alias)
                                List<String> aliases = node.getNetworkAliases();
                                if (aliases != null && aliases.contains(address.getHost())) {
                                    int mappedPort = node.getMappedPort(address.getPort());
                                    return new RedisURI(address.getScheme(), node.getHost(), mappedPort);
                                }
                            } catch (Exception e) {
                                // Container may be in transitional state, skip it
                            }
                        }
                        return address;
                    });

            for (GenericContainer<?> sentinel : sentinels) {
                sentinelServersConfig.addSentinelAddress("redis://127.0.0.1:" + sentinel.getMappedPort(SENTINEL_PORT));
            }

            if (password != null && !password.isEmpty()) {
                config.setPassword(password);
                sentinelServersConfig.setSentinelPassword(password);
            }

            callback.accept(nodes, config);

        } finally {
            for (int i = nodes.size() - 1; i >= 0; i--) {
                try {
                    nodes.get(i).stop();
                } catch (Exception e) {
                    // ignore
                }
            }
            try {
                network.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    protected String getContainerIp(GenericContainer<?> container) {
        Map<String, ContainerNetwork> networks = container.getContainerInfo().getNetworkSettings().getNetworks();
        return networks.values().iterator().next().getIpAddress();
    }

    private GenericContainer<?> createMasterContainer(Network network, String password) {
        List<String> cmd = new ArrayList<>();
        cmd.add("redis-server");
        cmd.add("--bind");
        cmd.add("0.0.0.0");
        cmd.add("--protected-mode");
        cmd.add("no");

        if (password != null && !password.isEmpty()) {
            cmd.add("--requirepass");
            cmd.add(password);
            cmd.add("--masterauth");
            cmd.add(password);
        }

        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withNetwork(network)
                .withNetworkAliases("redis-master")
                .withExposedPorts(REDIS_PORT)
                .withCommand(cmd.toArray(new String[0]))
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
                .withStartupTimeout(Duration.ofSeconds(30));

        return container;
    }

    protected void preservePortBindings(GenericContainer<?> container) {
        List<String> bindings = new ArrayList<>();
        for (Integer port : container.getExposedPorts()) {
            bindings.add(container.getMappedPort(port) + ":" + port);
        }
        container.setPortBindings(bindings);
    }

    private GenericContainer<?> createSlaveContainer(Network network, int index, String password, String masterIp) {
        String hostname = "redis-slave-" + index;

        List<String> cmd = new ArrayList<>();
        cmd.add("redis-server");
        cmd.add("--bind");
        cmd.add("0.0.0.0");
        cmd.add("--protected-mode");
        cmd.add("no");
        cmd.add("--replicaof");
        cmd.add(masterIp);
        cmd.add(String.valueOf(REDIS_PORT));

        if (password != null && !password.isEmpty()) {
            cmd.add("--requirepass");
            cmd.add(password);
            cmd.add("--masterauth");
            cmd.add(password);
        }

        return new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withNetwork(network)
                .withNetworkAliases(hostname)
                .withExposedPorts(REDIS_PORT)
                .withCommand(cmd.toArray(new String[0]))
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
                .withStartupTimeout(Duration.ofSeconds(60));
    }

    private GenericContainer<?> createSentinelContainer(Network network, int index, String password, int quorum, String masterIp) {
        String hostname = "redis-sentinel-" + index;

        List<String> lines = new ArrayList<>();
        lines.add("port " + SENTINEL_PORT);
        lines.add("bind 0.0.0.0");
        lines.add("protected-mode no");
        // Use IP for master monitoring - more reliable for failover
        lines.add("sentinel monitor " + MASTER_NAME + " " + masterIp + " " + REDIS_PORT + " " + quorum);
        // Very fast detection for test environment
        lines.add("sentinel down-after-milliseconds " + MASTER_NAME + " 3000");
        lines.add("sentinel failover-timeout " + MASTER_NAME + " 5000");
        lines.add("sentinel parallel-syncs " + MASTER_NAME + " 1");

        if (password != null && !password.isEmpty()) {
            lines.add("sentinel auth-pass " + MASTER_NAME + " " + password);
            lines.add("requirepass " + password);
        }

        // Build printf command with each line as separate argument
        // Format: printf '%s\n' 'line1' 'line2' ... > /tmp/sentinel.conf
        StringBuilder cmd = new StringBuilder("printf '%s\\n'");
        for (String line : lines) {
            // Escape single quotes in the line: ' -> '\''
            String escaped = line.replace("'", "'\\''");
            cmd.append(" '").append(escaped).append("'");
        }
        cmd.append(" > /tmp/sentinel.conf && redis-sentinel /tmp/sentinel.conf");

        return new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withNetwork(network)
                .withNetworkAliases(hostname)
                .withExposedPorts(SENTINEL_PORT)
                .withCommand("/bin/sh", "-c", cmd.toString())
                .waitingFor(Wait.forLogMessage(".*\\+monitor master.*", 1))
                .withStartupTimeout(Duration.ofSeconds(30));
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

    protected <C extends ContainerState> List<C> getMasterNodes(List<C> nodes) {
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
        preservePortBindings(redis);
        redis.stop();
        redis.start();
    }

}
