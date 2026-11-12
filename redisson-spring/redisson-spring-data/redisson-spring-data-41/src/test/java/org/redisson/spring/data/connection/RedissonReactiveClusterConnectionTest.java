package org.redisson.spring.data.connection;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.RedissonReactive;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.ClusterSlotHashUtil;
import org.springframework.data.redis.connection.ReactiveSetCommands;
import org.springframework.data.redis.connection.RedisClusterNode;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonReactiveClusterConnectionTest extends RedisDockerTest {

    @Test
    public void testSetCommandsAcrossSlots() {
        testInCluster(c -> {
            RedissonClient redisson = (RedissonClient) c.getNativeConnection();
            RedissonReactiveRedisClusterConnection connection = new RedissonReactiveRedisClusterConnection(((RedissonReactive) redisson.reactive()).getCommandExecutor());
            ByteBuffer set1 = ByteBuffer.wrap("set1".getBytes());
            ByteBuffer set2 = ByteBuffer.wrap("set2".getBytes());
            ByteBuffer set3 = ByteBuffer.wrap("set3".getBytes());
            ByteBuffer dest = ByteBuffer.wrap("dest".getBytes());
            assertThat(ClusterSlotHashUtil.isSameSlotForAllKeys(set1, set2, set3, dest)).isFalse();

            c.sAdd("set1".getBytes(), "a".getBytes(), "b".getBytes(), "c".getBytes());
            c.sAdd("set2".getBytes(), "b".getBytes(), "c".getBytes(), "d".getBytes());
            c.sAdd("set3".getBytes(), "c".getBytes(), "e".getBytes());
            ReactiveSetCommands commands = connection.setCommands();

            assertThat(commands.sInter(Arrays.asList(set1, set2)).collectList().block()).containsExactlyInAnyOrder(buffer("b"), buffer("c"));
            assertThat(commands.sUnion(Arrays.asList(set1, set3)).collectList().block()).containsExactlyInAnyOrder(buffer("a"), buffer("b"), buffer("c"), buffer("e"));
            assertThat(commands.sDiff(Arrays.asList(set1, set2)).collectList().block()).containsExactly(buffer("a"));

            c.sAdd("dest".getBytes(), "stale".getBytes());
            assertThat(commands.sInterStore(dest, Arrays.asList(set1, set2)).block()).isEqualTo(2);
            assertThat(commands.sUnion(Arrays.asList(dest, set3)).collectList().block()).containsExactlyInAnyOrder(buffer("b"), buffer("c"), buffer("e"));
            assertThat(commands.sUnionStore(dest, Arrays.asList(set1, set3)).block()).isEqualTo(4);
            assertThat(commands.sDiffStore(dest, Arrays.asList(set1, set2)).block()).isEqualTo(1);
            assertThat(commands.sUnion(Arrays.asList(dest, set3)).collectList().block()).containsExactlyInAnyOrder(buffer("a"), buffer("c"), buffer("e"));
            assertThat(commands.sDiffStore(dest, Arrays.asList(set2, set2)).block()).isZero();
            assertThat(connection.keyCommands().exists(Arrays.asList(dest, set1)).block()).isEqualTo(1);

            assertThat(commands.sMove(set1, set2, buffer("a")).block()).isTrue();
            assertThat(commands.sMove(set1, set2, buffer("b")).block()).isTrue();
            assertThat(commands.sMove(set1, set2, buffer("x")).block()).isFalse();
            assertThat(commands.sUnion(Arrays.asList(set1, set3)).collectList().block()).containsExactlyInAnyOrder(buffer("c"), buffer("e"));
        });
    }

    @Test
    public void testKeyAndListCommandsAcrossSlots() {
        testInCluster(c -> {
            RedissonClient redisson = (RedissonClient) c.getNativeConnection();
            RedissonReactiveRedisClusterConnection connection = new RedissonReactiveRedisClusterConnection(((RedissonReactive) redisson.reactive()).getCommandExecutor());
            ByteBuffer key1 = buffer("key1");
            ByteBuffer key2 = buffer("key2");
            ByteBuffer key3 = buffer("key3");
            assertThat(ClusterSlotHashUtil.isSameSlotForAllKeys(key1, key2, key3)).isFalse();

            c.set("key1".getBytes(), "value".getBytes());
            c.set("key2".getBytes(), "value".getBytes());
            assertThat(connection.keyCommands().exists(Arrays.asList(key1, key2, key3)).block()).isEqualTo(2);
            assertThat(connection.keyCommands().touch(Arrays.asList(key1, key2, key3)).block()).isEqualTo(2);

            ByteBuffer src = buffer("src");
            ByteBuffer dst = buffer("dst");
            assertThat(ClusterSlotHashUtil.isSameSlotForAllKeys(src, dst)).isFalse();
            c.rPush("src".getBytes(), "a".getBytes(), "b".getBytes());
            assertThat(connection.listCommands().rPopLPush(src, dst).block()).isEqualTo(buffer("b"));
            assertThat(connection.listCommands().rPopLPush(src, dst).block()).isEqualTo(buffer("a"));
            assertThat(connection.listCommands().rPopLPush(src, dst).block()).isNull();
            assertThat(c.lPop("dst".getBytes(), 3)).containsExactly("a".getBytes(), "b".getBytes());
        });
    }

    private static ByteBuffer buffer(String value) {
        return ByteBuffer.wrap(value.getBytes());
    }

    @Test
    public void testClusterForgetIsSentToEveryNode() {
        testInCluster(c -> {
            RedissonClient redisson = (RedissonClient) c.getNativeConnection();
            RedissonReactiveRedisClusterConnection connection = new RedissonReactiveRedisClusterConnection(((RedissonReactive) redisson.reactive()).getCommandExecutor());
            Iterable<RedisClusterNode> nodes = c.clusterGetNodes();
            for (RedisClusterNode node : nodes) {
                c.resetConfigStats(node);
            }

            assertThatThrownBy(() -> connection.clusterForget(new RedisClusterNode("0123456789abcdef0123456789abcdef01234567")).block())
                    .hasMessageContaining("Unknown node");
            for (RedisClusterNode node : nodes) {
                assertThat(c.info(node, "commandstats").getProperty("cmdstat_cluster|forget")).startsWith("calls=1,");
            }
        });
    }

    @Test
    public void testClusterMeetIsSentToTheCluster() {
        testInCluster(c -> {
            RedissonClient redisson = (RedissonClient) c.getNativeConnection();
            RedissonReactiveRedisClusterConnection connection = new RedissonReactiveRedisClusterConnection(((RedissonReactive) redisson.reactive()).getCommandExecutor());

            assertThatThrownBy(() -> connection.clusterMeet(new RedisClusterNode("127.0.0.1", 0)).block())
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("Invalid node address specified");
        });
    }

    @Test
    public void testClusterReplicate() {
        testInCluster(c -> {
            RedissonClient redisson = (RedissonClient) c.getNativeConnection();
            RedissonReactiveRedisClusterConnection connection = new RedissonReactiveRedisClusterConnection(((RedissonReactive) redisson.reactive()).getCommandExecutor());
            Map<RedisClusterNode, Collection<RedisClusterNode>> map = c.clusterGetMasterReplicaMap();
            Iterator<RedisClusterNode> masters = map.keySet().iterator();
            RedisClusterNode master = masters.next();
            RedisClusterNode otherMaster = masters.next();
            RedisClusterNode replica = map.get(master).iterator().next();

            connection.clusterReplicate(otherMaster, replica).block();
            try {
                Awaitility.await().atMost(Durations.TEN_SECONDS).untilAsserted(() -> {
                    assertThat(c.clusterGetReplicas(otherMaster)).extracting(RedisClusterNode::getId).contains(replica.getId());
                });
            } finally {
                connection.clusterReplicate(master, replica).block();
                Awaitility.await().atMost(Durations.ONE_MINUTE).untilAsserted(() -> {
                    assertThat(c.clusterGetReplicas(master)).extracting(RedisClusterNode::getId).contains(replica.getId());
                    Properties replication = c.info(replica, "replication");
                    assertThat(replication.getProperty("master_link_status")).isEqualTo("up");
                    assertThat(replication.getProperty("master_sync_in_progress")).isEqualTo("0");
                });
            }
        });
    }

    @Test
    public void testNodeCommandsOnReplica() {
        testInCluster(c -> {
            RedissonClient redisson = (RedissonClient) c.getNativeConnection();
            RedissonReactiveRedisClusterConnection connection = new RedissonReactiveRedisClusterConnection(((RedissonReactive) redisson.reactive()).getCommandExecutor());
            for (int i = 0; i < 30; i++) {
                c.set(("key" + i).getBytes(), "value".getBytes());
            }
            RedisClusterNode master = c.clusterGetMasterReplicaMap().keySet().iterator().next();
            RedisClusterNode replica = c.clusterGetReplicas(master).iterator().next();
            Long keys = c.dbSize(master);
            assertThat(keys).isPositive();

            Properties replication = connection.serverCommands().info(replica, "replication").block();
            assertThat(replication.getProperty("role")).isEqualTo("slave");
            assertThat(connection.ping(replica).block()).isEqualTo("PONG");
            assertThatThrownBy(() -> connection.serverCommands().flushDb(replica).block())
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("READONLY");
            assertThat(c.dbSize(master)).isEqualTo(keys);
        });
    }

}
