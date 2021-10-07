package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.api.redisnode.*;
import org.redisson.client.protocol.Time;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;
import org.redisson.misc.RedisURI;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonRedisNodesTest extends BaseTest {

    @Test
    public void testNode() {
        RedisSingle nodes = redisson.getRedisNodes(RedisNodes.SINGLE);
        assertThat(nodes.getInstance().info(RedisNode.InfoSection.ALL)).isNotEmpty();
    }

    @Test
    public void testInfo() {
        RedisSingle nodes = redisson.getRedisNodes(RedisNodes.SINGLE);
        RedisMaster node = nodes.getInstance();

        Map<String, String> allResponse = node.info(RedisNode.InfoSection.ALL);
        assertThat(allResponse).containsKeys("redis_version", "connected_clients");

        Map<String, String> defaultResponse = node.info(RedisNode.InfoSection.DEFAULT);
        assertThat(defaultResponse).containsKeys("redis_version", "connected_clients");

        Map<String, String> serverResponse = node.info(RedisNode.InfoSection.SERVER);
        assertThat(serverResponse).containsKey("redis_version");

        Map<String, String> clientsResponse = node.info(RedisNode.InfoSection.CLIENTS);
        assertThat(clientsResponse).containsKey("connected_clients");

        Map<String, String> memoryResponse = node.info(RedisNode.InfoSection.MEMORY);
        assertThat(memoryResponse).containsKey("used_memory_human");

        Map<String, String> persistenceResponse = node.info(RedisNode.InfoSection.PERSISTENCE);
        assertThat(persistenceResponse).containsKey("rdb_last_save_time");

        Map<String, String> statsResponse = node.info(RedisNode.InfoSection.STATS);
        assertThat(statsResponse).containsKey("pubsub_patterns");

        Map<String, String> replicationResponse = node.info(RedisNode.InfoSection.REPLICATION);
        assertThat(replicationResponse).containsKey("repl_backlog_first_byte_offset");

        Map<String, String> cpuResponse = node.info(RedisNode.InfoSection.CPU);
        assertThat(cpuResponse).containsKey("used_cpu_sys");

        Map<String, String> commandStatsResponse = node.info(RedisNode.InfoSection.COMMANDSTATS);
        assertThat(commandStatsResponse).containsKey("cmdstat_flushall");

        Map<String, String> clusterResponse = node.info(RedisNode.InfoSection.CLUSTER);
        assertThat(clusterResponse).containsKey("cluster_enabled");

        Map<String, String> keyspaceResponse = node.info(RedisNode.InfoSection.KEYSPACE);
        assertThat(keyspaceResponse).isEmpty();
    }

    @Test
    public void testMemoryStatistics() {
        RedisSingle nodes = redisson.getRedisNodes(RedisNodes.SINGLE);
        Map<String, String> stats = nodes.getInstance().getMemoryStatistics();
        assertThat(stats.get("keys.count")).isEqualTo("0");
    }

    @Test
    public void testTime() {
        RedisSingle nodes = redisson.getRedisNodes(RedisNodes.SINGLE);
        Time time = nodes.getInstance().time();

        assertThat(time.getSeconds()).isGreaterThan(time.getMicroseconds());
        assertThat(time.getSeconds()).isGreaterThan(1000000000);
        assertThat(time.getMicroseconds()).isGreaterThan(10000);
    }

    @Test
    public void testConfig() {
        RedisSingle nodes = redisson.getRedisNodes(RedisNodes.SINGLE);
        assertThat(nodes.getInstance().getConfig("appendonly").get("appendonly")).isEqualTo("no");
        nodes.getInstance().setConfig("appendonly", "yes");
        assertThat(nodes.getInstance().getConfig("appendonly").get("appendonly")).isEqualTo("yes");
    }

    @Test
    public void testSentinelFailover() throws IOException, InterruptedException {
        RedisRunner.RedisProcess master = new RedisRunner()
                .nosave()
                .randomDir()
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess sentinel1 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        RedisRunner.RedisProcess sentinel2 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        RedisRunner.RedisProcess sentinel3 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();

        Config config = new Config();
        config.useSentinelServers()
                .setLoadBalancer(new RandomLoadBalancer())
                .addSentinelAddress(sentinel3.getRedisServerAddressAndPort()).setMasterName("myMaster");

        long t = System.currentTimeMillis();
        RedissonClient redisson = Redisson.create(config);

        RedisSentinelMasterSlave nodes = redisson.getRedisNodes(RedisNodes.SENTINEL_MASTER_SLAVE);
        RedisSentinel sentinel = nodes.getSentinels().iterator().next();
        sentinel.failover("myMaster");

        redisson.shutdown();

        sentinel1.stop();
        sentinel2.stop();
        sentinel3.stop();
        master.stop();
        slave1.stop();
        slave2.stop();
    }

    @Test
    public void testCluster() throws Exception {
        RedisRunner master1 = new RedisRunner().port(6890).randomDir().nosave();
        RedisRunner master2 = new RedisRunner().port(6891).randomDir().nosave();
        RedisRunner master3 = new RedisRunner().port(6892).randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().port(6900).randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().port(6901).randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().port(6902).randomDir().nosave();
        RedisRunner slave4 = new RedisRunner().port(6903).randomDir().nosave();

        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1, slave4)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterRunner.ClusterProcesses process = clusterRunner.run();

        Thread.sleep(5000);

        Config config = new Config();
        config.useClusterServers()
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RedisCluster nodes = redisson.getRedisNodes(RedisNodes.CLUSTER);
        assertThat(nodes.getMaster("redis://127.0.0.1:6890")).isNotNull();
        assertThat(nodes.getMaster("redis://127.0.0.1:6899")).isNull();
        assertThat(nodes.getMasters()).hasSize(3);
        assertThat(nodes.getSlaves()).hasSize(4);

        for (RedisClusterMaster master : nodes.getMasters()) {
            master.clusterDeleteSlots(1, 2);
            master.clusterAddSlots(1, 2);
            master.clusterCountKeysInSlot(1);
            List<String> keys = master.clusterGetKeysInSlot(1, 10);
            assertThat(keys).isEmpty();;
            String nodeId = master.clusterId();
            assertThat(nodeId).isNotNull();

            assertThat(master.clusterCountFailureReports(nodeId)).isZero();
            Map<ClusterSlotRange, Set<String>> slots = master.clusterSlots();
            assertThat(slots.entrySet().size()).isBetween(3, 5);
        }
        for (RedisClusterSlave slave : nodes.getSlaves()) {
            slave.clusterDeleteSlots(1, 2);
            slave.clusterAddSlots(1, 2);
            slave.clusterCountKeysInSlot(1);
            List<String> keys = slave.clusterGetKeysInSlot(1, 10);
            assertThat(keys).isEmpty();;
            String nodeId = slave.clusterId();
            assertThat(nodeId).isNotNull();

            assertThat(slave.clusterCountFailureReports(nodeId)).isZero();
            Map<ClusterSlotRange, Set<String>> slots = slave.clusterSlots();
            assertThat(slots.entrySet().size()).isBetween(3, 5);
        }
        redisson.shutdown();
        process.shutdown();
    }

    @Test
    public void testSentinel() throws IOException, InterruptedException {
        RedisRunner.RedisProcess master = new RedisRunner()
                .nosave()
                .randomDir()
                .run();
        RedisRunner.RedisProcess slave1 = new RedisRunner()
                .port(6380)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess slave2 = new RedisRunner()
                .port(6381)
                .nosave()
                .randomDir()
                .slaveof("127.0.0.1", 6379)
                .run();
        RedisRunner.RedisProcess sentinel1 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26379)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        RedisRunner.RedisProcess sentinel2 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26380)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();
        RedisRunner.RedisProcess sentinel3 = new RedisRunner()
                .nosave()
                .randomDir()
                .port(26381)
                .sentinel()
                .sentinelMonitor("myMaster", "127.0.0.1", 6379, 2)
                .run();

        Config config = new Config();
        config.useSentinelServers()
            .setLoadBalancer(new RandomLoadBalancer())
            .addSentinelAddress(sentinel3.getRedisServerAddressAndPort()).setMasterName("myMaster");

        long t = System.currentTimeMillis();
        RedissonClient redisson = Redisson.create(config);

        RedisSentinelMasterSlave nodes = redisson.getRedisNodes(RedisNodes.SENTINEL_MASTER_SLAVE);
        assertThat(nodes.getSentinels()).hasSize(3);
        assertThat(nodes.getSlaves()).hasSize(2);
        assertThat(nodes.getMaster()).isNotNull();

        for (RedisSentinel sentinel : nodes.getSentinels()) {
            Assertions.assertTrue(sentinel.ping());
            RedisURI addr = sentinel.getMasterAddr("myMaster");
            assertThat(addr.getHost()).isEqualTo("127.0.0.1");
            assertThat(addr.getPort()).isEqualTo(master.getRedisServerPort());

            Map<String, String> masterMap = sentinel.getMaster("myMaster");
            assertThat(masterMap).isNotEmpty();

            List<Map<String, String>> masters = sentinel.getMasters();
            assertThat(masters).hasSize(1);
            Map<String, String> m = masters.get(0);
            assertThat(m.get("ip")).isEqualTo("127.0.0.1");
            assertThat(Integer.valueOf(m.get("port"))).isEqualTo(master.getRedisServerPort());

            List<Map<String, String>> slaves = sentinel.getSlaves("myMaster");
            assertThat(slaves).hasSize(2);
        }
        nodes.getSlaves().forEach((node) -> {
            Assertions.assertTrue(node.ping());
        });

        redisson.shutdown();

        sentinel1.stop();
        sentinel2.stop();
        sentinel3.stop();
        master.stop();
        slave1.stop();
        slave2.stop();
    }


    @Test
    public void testNodesInCluster() throws Exception {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slot1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slot2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slot3 = new RedisRunner().randomPort().randomDir().nosave();

        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slot1)
                .addNode(master2, slot2)
                .addNode(master3, slot3);
        ClusterRunner.ClusterProcesses process = clusterRunner.run();

        Config config = new Config();
        config.useClusterServers()
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RedisCluster nodes = redisson.getRedisNodes(RedisNodes.CLUSTER);
        assertThat(nodes.getMasters()).hasSize(3);
        for (RedisClusterMaster node : nodes.getMasters()) {
            assertThat(node.info(RedisNode.InfoSection.ALL)).isNotEmpty();
        }
        assertThat(nodes.getSlaves()).hasSize(3);
        for (RedisClusterSlave node : nodes.getSlaves()) {
            assertThat(node.info(RedisNode.InfoSection.ALL)).isNotEmpty();
        }

        redisson.shutdown();
        process.shutdown();
    }

    @Test
    public void testPing() {
        RedisSingle nodes = redisson.getRedisNodes(RedisNodes.SINGLE);
        RedisMaster node = nodes.getInstance();

        Assertions.assertTrue(node.ping());
        Assertions.assertTrue(nodes.pingAll());
    }

}
