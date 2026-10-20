package org.redisson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBitSet;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.api.bitset.BitFieldArgs;
import org.redisson.api.bitset.BitOffset;
import org.redisson.api.listener.TrackingListener;
import org.redisson.api.options.PlainOptions;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.api.redisnode.RedisSlave;
import org.redisson.client.FailedCommandsTimeoutDetector;
import org.redisson.client.FailedConnectionDetector;
import org.redisson.client.RedisException;
import org.redisson.config.Config;
import org.redisson.config.ConstantDelay;
import org.redisson.config.Protocol;
import org.redisson.config.ReadMode;
import org.redisson.connection.ConnectionListener;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class RedissonAvailabilityZoneReadModeTest extends RedisDockerTest {

    private static final String VALKEY_IMAGE = "valkey/valkey:8.0";

    private static final int READS = 30;

    private static Network network;
    private static GenericContainer<?> master;
    private static GenericContainer<?> slave1;
    private static GenericContainer<?> slave2;

    @BeforeAll
    public static void startNodes() {
        network = Network.newNetwork();
        master = valkey("master");
        master.start();
        slave1 = valkey("slave1", "--replicaof", "master", "6379");
        slave2 = valkey("slave2", "--replicaof", "master", "6379");
        slave1.start();
        slave2.start();
        await().atMost(Duration.ofSeconds(30)).until(() ->
                cli(slave1, "INFO", "replication").contains("master_link_status:up")
                        && cli(slave2, "INFO", "replication").contains("master_link_status:up"));
    }

    @AfterAll
    public static void stopNodes() {
        slave2.stop();
        slave1.stop();
        master.stop();
        network.close();
    }

    private static GenericContainer<?> valkey(String alias, String... args) {
        List<String> command = new ArrayList<>(List.of("valkey-server", "--protected-mode", "no"));
        command.addAll(List.of(args));
        return new GenericContainer<>(VALKEY_IMAGE)
                .withNetwork(network)
                .withNetworkAliases(alias)
                .withExposedPorts(6379)
                .withCommand(command.toArray(new String[0]))
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
    }

    private static String cli(ContainerState node, String... args) {
        String[] command = Stream.concat(Stream.of("valkey-cli", "-e"), Arrays.stream(args)).toArray(String[]::new);
        try {
            Container.ExecResult result = node.execInContainer(command);
            assertThat(result.getExitCode())
                    .as("%s: %s%s", String.join(" ", command), result.getStdout(), result.getStderr())
                    .isZero();
            return result.getStdout();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static GenericContainer<?> startSlave(String alias, String masterAlias, String zone) {
        GenericContainer<?> slave = valkey(alias, "--replicaof", masterAlias, "6379");
        slave.start();
        await().atMost(Duration.ofSeconds(30)).until(() ->
                cli(slave, "INFO", "replication").contains("master_link_status:up"));
        cli(slave, "CONFIG", "SET", "availability-zone", zone);
        return slave;
    }

    private static void zones(String masterZone, String slave1Zone, String slave2Zone) {
        cli(master, "CONFIG", "SET", "availability-zone", masterZone);
        cli(slave1, "CONFIG", "SET", "availability-zone", slave1Zone);
        cli(slave2, "CONFIG", "SET", "availability-zone", slave2Zone);
    }

    private static String address(GenericContainer<?> node) {
        return "redis://127.0.0.1:" + node.getFirstMappedPort();
    }

    private static Config config(ReadMode readMode, String clientZone) {
        Config config = new Config();
        config.useMasterSlaveServers()
                .setMasterAddress(address(master))
                .addSlaveAddress(address(slave1), address(slave2))
                .setReadMode(readMode)
                .setClientAvailabilityZone(clientZone);
        return config;
    }

    private static void withClient(Config config, Consumer<RedissonClient> test) {
        withClient(config, List.of(master, slave1, slave2), test);
    }

    private static void withClient(Config config, List<? extends ContainerState> nodes, Consumer<RedissonClient> test) {
        for (ContainerState node : nodes) {
            cli(node, "CONFIG", "RESETSTAT");
        }

        RedissonClient redisson = Redisson.create(config);
        try {
            for (ContainerState node : nodes) {
                // 10 s against a zone read measured at under 100 ms from the client's start, even with the
                // minimum idle sizes at 0 where the read makes the connection it uses: enough headroom for a
                // loaded machine, and short enough that a change stopping the reads altogether, which every
                // one of these tests then waits out, is reported in minutes rather than tens of them
                await().atMost(Duration.ofSeconds(10))
                        .until(() -> !cli(node, "LATENCY", "HISTOGRAM", "info").isBlank());
            }
            test.accept(redisson);
        } finally {
            redisson.shutdown();
        }
    }

    private static long calls(ContainerState node, String command) {
        String prefix = "cmdstat_" + command + ":calls=";
        for (String line : cli(node, "INFO", "commandstats").split("\r?\n")) {
            if (line.startsWith(prefix)) {
                return Long.parseLong(line.substring(prefix.length(), line.indexOf(',')));
            }
        }
        return 0;
    }

    record Served(long master, long slave1, long slave2) {
    }

    /**
     * Runs the reads and returns how many calls of the command master, slave1 and slave2 served.
     */
    private static Served served(String command, Runnable reads) {
        for (GenericContainer<?> node : List.of(master, slave1, slave2)) {
            cli(node, "CONFIG", "RESETSTAT");
        }
        reads.run();
        return new Served(calls(master, command), calls(slave1, command), calls(slave2, command));
    }

    /**
     * Reads the key {@link #READS} times and returns how many reads a node answered, taking the failures a
     * node that is being taken out of use, or that is turning connections away, gives the reads it is still
     * offered. The reads that pass are counted by the nodes themselves, as everywhere else here.
     */
    private static int readsAnswered(RBucket<?> bucket) {
        int answered = 0;
        for (int i = 0; i < READS; i++) {
            try {
                bucket.get();
                answered++;
            } catch (RedisException e) {
                // a read the balancer offered the node that is on its way out of use
            }
        }
        return answered;
    }

    private static Runnable gets(RBucket<?> bucket) {
        return () -> {
            for (int i = 0; i < READS; i++) {
                bucket.get();
            }
        };
    }

    private static Served servedGets(RedissonClient redisson) {
        return served("get", gets(redisson.getBucket("key")));
    }

    @Test
    public void azAffinityReadsFromSlavesInClientZone() {
        zones("az-a", "az-a", "az-b");

        withClient(config(ReadMode.AZ_AFFINITY, "az-b"), redisson ->
                assertThat(servedGets(redisson)).isEqualTo(new Served(0, 0, READS)));
        // the master is in az-a too, and AZ_AFFINITY still reads from the slave only
        withClient(config(ReadMode.AZ_AFFINITY, "az-a"), redisson ->
                assertThat(servedGets(redisson)).isEqualTo(new Served(0, READS, 0)));
    }

    @Test
    public void azAffinityFallsBackToSlavesInOtherZones() {
        zones("az-a", "az-b", "az-c");

        withClient(config(ReadMode.AZ_AFFINITY, "az-a"), redisson ->
                assertThat(servedGets(redisson)).isEqualTo(new Served(0, READS / 2, READS / 2)));
    }

    @Test
    public void slavesAndMasterReadsFromMasterInClientZoneBeforeLeavingIt() {
        zones("az-a", "az-b", "az-c");

        withClient(config(ReadMode.AZ_AFFINITY_SLAVES_AND_MASTER, "az-a"), redisson ->
                assertThat(servedGets(redisson)).isEqualTo(new Served(READS, 0, 0)));
        withClient(config(ReadMode.AZ_AFFINITY_SLAVES_AND_MASTER, "az-b"), redisson ->
                assertThat(servedGets(redisson)).isEqualTo(new Served(0, READS, 0)));
        withClient(config(ReadMode.AZ_AFFINITY_SLAVES_AND_MASTER, "az-z"), redisson ->
                assertThat(servedGets(redisson)).isEqualTo(new Served(0, READS / 2, READS / 2)));
    }

    @Test
    public void slavesAndMasterPrefersSlaveOverMasterInClientZone() {
        zones("az-a", "az-a", "az-b");

        withClient(config(ReadMode.AZ_AFFINITY_SLAVES_AND_MASTER, "az-a"), redisson ->
                assertThat(servedGets(redisson)).isEqualTo(new Served(0, READS, 0)));
    }

    @Test
    public void masterSlaveSpreadsOverClientZoneThenOverAllNodes() {
        zones("az-a", "az-a", "az-b");

        withClient(config(ReadMode.AZ_AFFINITY_MASTER_SLAVE, "az-a"), redisson ->
                assertThat(servedGets(redisson)).isEqualTo(new Served(READS / 2, READS / 2, 0)));
        withClient(config(ReadMode.AZ_AFFINITY_MASTER_SLAVE, "az-z"), redisson ->
                assertThat(servedGets(redisson)).isEqualTo(new Served(READS / 3, READS / 3, READS / 3)));
    }

    @Test
    public void perObjectReadModeOverride() {
        zones("az-a", "az-a", "az-b");

        withClient(config(ReadMode.SLAVE, "az-b"), redisson -> {
            RBucket<String> zoned = redisson.getBucket(PlainOptions.name("key").readMode(ReadMode.AZ_AFFINITY));
            assertThat(served("get", gets(zoned))).isEqualTo(new Served(0, 0, READS));
            assertThat(servedGets(redisson)).isEqualTo(new Served(0, READS / 2, READS / 2));
        });
    }

    @Test
    public void perObjectAzAffinityKeepsOffTheMasterInThePool() {
        zones("az-a", "az-b", "az-c");

        withClient(config(ReadMode.MASTER_SLAVE, "az-a"), redisson -> {
            RBucket<String> zoned = redisson.getBucket(PlainOptions.name("key").readMode(ReadMode.AZ_AFFINITY));
            assertThat(served("get", gets(zoned))).isEqualTo(new Served(0, READS / 2, READS / 2));
        });
    }

    @Test
    public void zonesAreReadOnlyWhereTheyCanMatter() {
        zones("az-a", "az-a", "az-b");

        assertThat(served("info", () -> withClient(config(ReadMode.SLAVE, null), List.of(), redisson -> { })))
                .isEqualTo(new Served(0, 0, 0));

        assertThat(served("info", () -> withClient(config(ReadMode.SLAVE, "az-a"), redisson -> { })))
                .isEqualTo(new Served(1, 1, 1));

        assertThat(served("info", () -> withClient(config(ReadMode.MASTER, "az-a"), List.of(), redisson -> { })))
                .isEqualTo(new Served(0, 0, 0));
    }

    @Test
    public void nodesWithoutZoneServeAsOtherZones() {
        zones("", "", "");

        withClient(config(ReadMode.AZ_AFFINITY, "az-a"), redisson ->
                assertThat(servedGets(redisson)).isEqualTo(new Served(0, READS / 2, READS / 2)));
    }

    @Test
    public void zoneNamesMayContainColons() {
        // Oracle Cloud availability domains look like this
        zones("Uocm:PHX-AD-1", "Uocm:PHX-AD-1", "Uocm:PHX-AD-2");

        withClient(config(ReadMode.AZ_AFFINITY, "Uocm:PHX-AD-2"), redisson ->
                assertThat(servedGets(redisson)).isEqualTo(new Served(0, 0, READS)));
    }

    @Test
    public void zoneThatCannotBeReadLeavesTheNodeUsable() {
        zones("az-a", "az-a", "az-b");
        for (GenericContainer<?> node : List.of(master, slave1, slave2)) {
            cli(node, "ACL", "SETUSER", "noinfo", "on", ">secret", "~*", "&*", "+@all", "-info");
        }
        try {
            Config config = config(ReadMode.AZ_AFFINITY, "az-b");
            config.setUsername("noinfo").setPassword("secret");

            withClient(config, List.of(), redisson ->
                    assertThat(servedGets(redisson)).isEqualTo(new Served(0, READS / 2, READS / 2)));
        } finally {
            for (GenericContainer<?> node : List.of(master, slave1, slave2)) {
                cli(node, "ACL", "DELUSER", "noinfo");
            }
        }
    }

    @Test
    public void readingZonesReportsNoNodeAsDisconnected() {
        zones("az-a", "az-a", "az-b");
        List<InetSocketAddress> disconnected = new CopyOnWriteArrayList<>();
        Config config = config(ReadMode.AZ_AFFINITY, "az-b");
        config.setConnectionListener(new ConnectionListener() {
            @Override
            public void onConnect(InetSocketAddress addr) {
            }

            @Override
            public void onDisconnect(InetSocketAddress addr) {
                disconnected.add(addr);
            }
        });

        withClient(config, redisson -> {
            assertThat(servedGets(redisson)).isEqualTo(new Served(0, 0, READS));
            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).until(disconnected::isEmpty);
        });
    }

    @Test
    public void zoneIsReadAgainWhenTheNodeReconnects() {
        zones("az-a", "az-a", "az-c");
        Config config = config(ReadMode.AZ_AFFINITY, "az-b");
        config.useMasterSlaveServers()
                .setFailedSlaveReconnectionInterval(500)
                .setFailedSlaveNodeDetector(new FailedCommandsTimeoutDetector(2000, 1))
                .setRetryAttempts(0)
                .setTimeout(300);

        withClient(config, redisson -> {
            assertThat(servedGets(redisson)).isEqualTo(new Served(0, READS / 2, READS / 2));

            cli(slave1, "CONFIG", "SET", "availability-zone", "az-b");
            assertThat(servedGets(redisson)).isEqualTo(new Served(0, READS / 2, READS / 2));

            RBucket<String> bucket = redisson.getBucket("key");
            slave1.getDockerClient().pauseContainerCmd(slave1.getContainerId()).exec();
            try {
                readsAnswered(bucket);
            } finally {
                slave1.getDockerClient().unpauseContainerCmd(slave1.getContainerId()).exec();
            }

            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(servedGets(redisson)).isEqualTo(new Served(0, READS, 0)));
        });
    }

    @Test
    public void trackingListenerWorksWhileASlaveIsDown() {
        zones("az-a", "az-b", "az-c");
        // in the client's zone, so that the reads below show when Redisson has taken it out of use
        GenericContainer<?> slave3 = startSlave("slave-frozen", "master", "az-a");
        try {
            Config config = config(ReadMode.AZ_AFFINITY_MASTER_SLAVE, "az-a");
            config.setProtocol(Protocol.RESP3);
            config.useMasterSlaveServers()
                    .addSlaveAddress(address(slave3))
                    // gives up on a node whose connection attempts have been failing for this long
                    .setFailedSlaveNodeDetector(new FailedConnectionDetector(500))
                    // long enough to show in addListener's time if a subscription has to be retried
                    .setRetryDelay(new ConstantDelay(Duration.ofSeconds(5)));

            // its address, while the container can still be asked for the port it is mapped to
            String downAddress = address(slave3);

            withClient(config, List.of(master, slave1, slave2, slave3), redisson -> {
                RBucket<String> bucket = redisson.getBucket("key");

                await().atMost(Duration.ofSeconds(10)).until(() -> {
                    bucket.get();
                    return calls(slave3, "get") > 0;
                });
                slave3.stop();

                RedisSlave down = redisson.getRedisNodes(RedisNodes.MASTER_SLAVE).getSlave(downAddress);
                await().atMost(Duration.ofSeconds(60)).until(() -> {
                    try {
                        down.ping();
                    } catch (RedisException e) {
                        // the stopped node, which is what the attempt is for
                    }
                    long readsStart = System.nanoTime();
                    try {
                        bucket.get();
                        bucket.get();
                    } catch (RedisException e) {
                        return false;
                    }
                    return System.nanoTime() - readsStart < Duration.ofSeconds(1).toNanos();
                });

                AtomicReference<String> changed = new AtomicReference<>();
                long start = System.nanoTime();
                bucket.addListener((TrackingListener) changed::set);
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));

                assertThat(served("get", bucket::get)).isEqualTo(new Served(1, 0, 0));
                redisson.getBucket("key").set("value");
                await().atMost(Duration.ofSeconds(2)).until(() -> "key".equals(changed.get()));
            });
        } finally {
            slave3.stop();
        }
    }

    @Test
    public void zoneIsReadAfterTheNodeTurnsConnectionsAway() throws IOException {
        zones("az-a", "az-b", "az-c");

        GenericContainer<?> busy = startSlave("slave-busy", "master", "az-d");
        try {
            String prefix = "connected_clients:";
            String line = Arrays.stream(cli(busy, "INFO", "clients").split("\r?\n"))
                    .filter(l -> l.startsWith(prefix))
                    .findFirst()
                    .orElseThrow();

            int maxClients = Integer.parseInt(line.substring(prefix.length()).trim()) + 1;
            cli(busy, "CONFIG", "SET", "maxclients", String.valueOf(maxClients));

            Socket holder = new Socket(InetAddress.getLoopbackAddress(), busy.getFirstMappedPort());
            Socket holder2 = new Socket(InetAddress.getLoopbackAddress(), busy.getFirstMappedPort());
            Config config = config(ReadMode.AZ_AFFINITY, "az-d");
            config.setProtocol(Protocol.RESP2);
            config.useMasterSlaveServers()
                    .setPingConnectionInterval(0)
                    .addSlaveAddress(address(busy))
                    .setFailedSlaveReconnectionInterval(500)
                    .setSlaveConnectionMinimumIdleSize(0)
                    .setSubscriptionConnectionMinimumIdleSize(0)
                    .setRetryDelay(new ConstantDelay(Duration.ofMillis(100)))
                    .setTimeout(300);

            // the busy node answers no zone read yet, so it is not among the nodes waited for
            withClient(config, List.of(master, slave1, slave2), redisson -> {
                RBucket<String> bucket = redisson.getBucket("key");
                AtomicInteger answered = new AtomicInteger();
                Runnable reads = () -> answered.set(readsAnswered(bucket));

                Served turnedAway = served("get", reads);
                assertThat(turnedAway.master()).isZero();
                assertThat(turnedAway.slave1() + turnedAway.slave2()).isPositive();

                try {
                    holder.close();
                    holder2.close();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }

                await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                    assertThat(served("get", reads)).isEqualTo(new Served(0, 0, 0));
                    assertThat(answered).hasValue(READS);
                });
            });
        } finally {
            busy.stop();
        }
    }

    @Test
    public void readOnlyBitFieldFollowsTheZone() {
        zones("az-a", "az-a", "az-b");

        withClient(config(ReadMode.AZ_AFFINITY, "az-b"), redisson -> {
            RBitSet bits = redisson.getBitSet("bits");
            assertThat(served("bitfield_ro", () -> {
                for (int i = 0; i < READS; i++) {
                    bits.bitField(BitFieldArgs.create().getUnsigned(8, BitOffset.bit(0)));
                }
            })).isEqualTo(new Served(0, 0, READS));
        });
    }

    @Test
    public void clusterReadsFollowTheZone() {
        withNewCluster("src/test/resources/docker-compose-valkey-cluster.yml", data -> {
            List<ContainerState> masters = new ArrayList<>();
            List<ContainerState> slaves = new ArrayList<>();
            for (ContainerState node : data.nodes()) {
                if (cli(node, "INFO", "replication").contains("role:master")) {
                    masters.add(node);
                    cli(node, "CONFIG", "SET", "availability-zone", "az-m");
                } else {
                    slaves.add(node);
                    cli(node, "CONFIG", "SET", "availability-zone", "az-s");
                }
            }
            assertThat(masters).hasSize(3);
            assertThat(slaves).hasSize(3);

            Config config = new Config(data.redisson().getConfig());

            config.useClusterServers().setReadMode(ReadMode.AZ_AFFINITY).setClientAvailabilityZone("az-s");
            withClient(config, data.nodes(), redisson ->
                    assertThat(servedInCluster(redisson, masters, slaves)).isEqualTo(new ServedInCluster(0, 60)));

            config.useClusterServers().setReadMode(ReadMode.AZ_AFFINITY_SLAVES_AND_MASTER).setClientAvailabilityZone("az-m");
            withClient(config, data.nodes(), redisson ->
                    assertThat(servedInCluster(redisson, masters, slaves)).isEqualTo(new ServedInCluster(60, 0)));

            config.useClusterServers().setReadMode(ReadMode.AZ_AFFINITY_MASTER_SLAVE).setClientAvailabilityZone("az-s");
            withClient(config, data.nodes(), redisson ->
                    assertThat(servedInCluster(redisson, masters, slaves)).isEqualTo(new ServedInCluster(0, 60)));
        });
    }

    record ServedInCluster(long masters, long slaves) {
    }

    private static ServedInCluster servedInCluster(RedissonClient redisson, List<ContainerState> masters, List<ContainerState> slaves) {
        for (ContainerState node : masters) {
            cli(node, "CONFIG", "RESETSTAT");
        }
        for (ContainerState node : slaves) {
            cli(node, "CONFIG", "RESETSTAT");
        }
        for (int i = 0; i < 60; i++) {
            redisson.getBucket("key" + i).get();
        }
        long fromMasters = masters.stream().mapToLong(n -> calls(n, "get")).sum();
        long fromSlaves = slaves.stream().mapToLong(n -> calls(n, "get")).sum();
        return new ServedInCluster(fromMasters, fromSlaves);
    }
}
