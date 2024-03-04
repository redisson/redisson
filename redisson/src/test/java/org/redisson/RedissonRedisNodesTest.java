package org.redisson;

import com.github.dockerjava.api.model.ContainerNetwork;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.api.redisnode.*;
import org.redisson.client.protocol.Time;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.misc.RedisURI;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonRedisNodesTest extends RedisDockerTest {

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
    public void testSentinelFailover() throws InterruptedException {
        withSentinel((nns, config) -> {
            RedissonClient redisson = Redisson.create(config);
            RedisSentinelMasterSlave nodes = redisson.getRedisNodes(RedisNodes.SENTINEL_MASTER_SLAVE);
            RedisSentinel sentinel = nodes.getSentinels().iterator().next();
            sentinel.failover(config.useSentinelServers().getMasterName());

            redisson.shutdown();
        }, 2);
    }

    @Test
    public void testCluster() {
        testInCluster(redisson -> {
            RedisCluster nodes = redisson.getRedisNodes(RedisNodes.CLUSTER);
            RedisClusterMaster n = nodes.getMasters().iterator().next();
            assertThat(nodes.getMaster("redis://" + n.getAddr().getHostString() + ":" + n.getAddr().getPort())).isNotNull();
            assertThat(nodes.getMaster("redis://127.0.0.1:6899")).isNull();
            assertThat(nodes.getMasters()).hasSize(3);
            assertThat(nodes.getSlaves()).hasSize(3);

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
        });
    }

    @Test
    public void testSentinel() throws InterruptedException {
        withSentinel((nns, config) -> {
            RedissonClient redisson = Redisson.create(config);

            RedisSentinelMasterSlave nodes = redisson.getRedisNodes(RedisNodes.SENTINEL_MASTER_SLAVE);
            assertThat(nodes.getSentinels()).hasSize(3);
            assertThat(nodes.getSlaves()).hasSize(2);
            assertThat(nodes.getMaster()).isNotNull();

            for (RedisSentinel sentinel : nodes.getSentinels()) {
                Assertions.assertTrue(sentinel.ping());
                RedisURI addr = sentinel.getMasterAddr(config.useSentinelServers().getMasterName());

                Map<String, ContainerNetwork> ss = nns.get(0).getContainerInfo().getNetworkSettings().getNetworks();
                ContainerNetwork s = ss.values().iterator().next();
                Integer port = nns.get(0).getExposedPorts().get(0);

                assertThat(addr.getHost()).isEqualTo(s.getIpAddress());
                assertThat(addr.getPort()).isEqualTo(port);

                Map<String, String> masterMap = sentinel.getMaster(config.useSentinelServers().getMasterName());
                assertThat(masterMap).isNotEmpty();

                List<Map<String, String>> masters = sentinel.getMasters();
                assertThat(masters).hasSize(1);
                Map<String, String> m = masters.get(0);
                assertThat(m.get("ip")).isEqualTo(s.getIpAddress());
                assertThat(Integer.valueOf(m.get("port"))).isEqualTo(port);

                List<Map<String, String>> slaves = sentinel.getSlaves(config.useSentinelServers().getMasterName());
                assertThat(slaves).hasSize(2);
            }
            nodes.getSlaves().forEach((node) -> {
                Assertions.assertTrue(node.ping());
            });

            redisson.shutdown();
        }, 2);
    }

    @Test
    public void testNodesInCluster() {
        testInCluster(redisson -> {
            RedisCluster nodes = redisson.getRedisNodes(RedisNodes.CLUSTER);
            assertThat(nodes.getMasters()).hasSize(3);
            for (RedisClusterMaster node : nodes.getMasters()) {
                assertThat(node.info(RedisNode.InfoSection.ALL)).isNotEmpty();
            }
            assertThat(nodes.getSlaves()).hasSize(3);
            for (RedisClusterSlave node : nodes.getSlaves()) {
                assertThat(node.info(RedisNode.InfoSection.ALL)).isNotEmpty();
            }
        });
    }

    @Test
    public void testPing() {
        RedisSingle nodes = redisson.getRedisNodes(RedisNodes.SINGLE);
        RedisMaster node = nodes.getInstance();

        Assertions.assertTrue(node.ping());
        Assertions.assertTrue(nodes.pingAll());
    }

}
