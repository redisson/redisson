package org.redisson.spring.data.connection;

import net.bytebuddy.utility.RandomString;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.api.RedissonClient;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.ClusterInfo;
import org.springframework.data.redis.connection.ClusterSlotHashUtil;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.DefaultSortParameters;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode.NodeType;
import org.springframework.data.redis.connection.RedisServerCommands.FlushOption;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.KeyScanOptions;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.RedisClientInfo;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

public class RedissonClusterConnectionTest extends RedisDockerTest {

    @Test
    public void testRandomKey() {
        testInCluster(connection -> {
            RedissonClient redisson = (RedissonClient) connection.getNativeConnection();
            StringRedisTemplate redisTemplate = new StringRedisTemplate();
            redisTemplate.setConnectionFactory(new RedissonConnectionFactory(redisson));
            redisTemplate.afterPropertiesSet();

            for (int i = 0; i < 10; i++) {
                redisTemplate.opsForValue().set("i" + i, "i" + i);
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            for (RedisClusterNode clusterNode : redisTemplate.getConnectionFactory().getClusterConnection().clusterGetNodes()) {
                String key = redisTemplate.opsForCluster().randomKey(clusterNode);
                assertThat(key).isNotNull();
            }
        });
    }

    @Test
    public void testDel() {
        testInCluster(connection -> {
            List<byte[]> keys = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                byte[] key = ("test" + i).getBytes();
                keys.add(key);
                connection.set(key, ("test" + i).getBytes());
            }
            assertThat(connection.del(keys.toArray(new byte[0][]))).isEqualTo(10);
        });
    }

    @Test
    public void testExecute() {
        testInCluster(connection -> {
            for (int i = 0; i < 10; i++) {
                byte[] key = ("execute" + i).getBytes();
                assertThat(connection.execute("SET", key, ("value" + i).getBytes())).isEqualTo("OK".getBytes());
                assertThat(connection.execute("GET", key)).isEqualTo(("value" + i).getBytes());
                Long length = connection.execute("STRLEN", key, Collections.emptyList());
                assertThat(length).isEqualTo(6L);
            }
            assertThat(connection.execute("PING")).isEqualTo("PONG".getBytes());
        });
    }

    @Test
    public void testScan() {
        testInCluster(connection -> {
            Map<byte[], byte[]> map = new HashMap<>();
            for (int i = 0; i < 10000; i++) {
                map.put(RandomString.make(32).getBytes(), RandomString.make(32).getBytes(StandardCharsets.UTF_8));
            }
            connection.mSet(map);

            Cursor<byte[]> b = connection.scan(ScanOptions.scanOptions().build());
            Set<String> sett = new HashSet<>();
            int counter = 0;
            while (b.hasNext()) {
                byte[] tt = b.next();
                sett.add(new String(tt));
                counter++;
            }
            assertThat(sett.size()).isEqualTo(map.size());
            assertThat(counter).isEqualTo(map.size());
        });
    }

    @Test
    public void testMSet() {
        testInCluster(connection -> {
            Map<byte[], byte[]> map = new HashMap<>();
            for (int i = 0; i < 10; i++) {
                map.put(("test" + i).getBytes(), ("test" + i*100).getBytes());
            }
            connection.mSet(map);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            for (Map.Entry<byte[], byte[]> entry : map.entrySet()) {
                assertThat(connection.get(entry.getKey())).isEqualTo(entry.getValue());
            }
        });
    }

    @Test
    public void testMGet() {
        testInCluster(connection -> {
            Map<byte[], byte[]> map = new HashMap<>();
            for (int i = 0; i < 10; i++) {
                map.put(("test" + i).getBytes(), ("test" + i*100).getBytes());
            }
            connection.mSet(map);

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            List<byte[]> r = connection.mGet(map.keySet().toArray(new byte[0][]));
            assertThat(r).containsExactly(map.values().toArray(new byte[0][]));
        });
    }

    @Test
    public void testClusterGetNodes() {
        testInCluster(connection -> {
            Iterable<RedisClusterNode> nodes = connection.clusterGetNodes();
            assertThat(nodes).hasSize(6);
            for (RedisClusterNode redisClusterNode : nodes) {
                assertThat(redisClusterNode.getLinkState()).isNotNull();
                assertThat(redisClusterNode.getFlags()).isNotEmpty();
                assertThat(redisClusterNode.getHost()).isNotNull();
                assertThat(redisClusterNode.getPort()).isNotNull();
                assertThat(redisClusterNode.getId()).isNotNull();
                assertThat(redisClusterNode.getType()).isNotNull();
                if (redisClusterNode.getType() == NodeType.MASTER) {
                    assertThat(redisClusterNode.getSlotRange().getSlots()).isNotEmpty();
                } else {
                    assertThat(redisClusterNode.getMasterId()).isNotNull();
                }
            }
        });
    }

    @Test
    public void testClusterGetNodesMaster() {
        testInCluster(connection -> {
            Iterable<RedisClusterNode> nodes = connection.clusterGetNodes();
            for (RedisClusterNode redisClusterNode : nodes) {
                if (redisClusterNode.getType() == NodeType.MASTER) {
                    Collection<RedisClusterNode> slaves = connection.clusterGetReplicas(redisClusterNode);
                    assertThat(slaves).hasSize(1);
                }
            }
        });
    }

    @Test
    public void testClusterGetMasterSlaveMap() {
        testInCluster(connection -> {
            Map<RedisClusterNode, Collection<RedisClusterNode>> map = connection.clusterGetMasterReplicaMap();
            assertThat(map).hasSize(3);
            for (Collection<RedisClusterNode> slaves : map.values()) {
                assertThat(slaves).hasSize(1);
            }
        });
    }

    @Test
    public void testClusterGetMasterReplicaMapListsMastersWithoutReplicas() {
        testInCluster(connection -> {
            Map<RedisClusterNode, Collection<RedisClusterNode>> map = connection.clusterGetMasterReplicaMap();
            Iterator<RedisClusterNode> masters = map.keySet().iterator();
            RedisClusterNode master = masters.next();
            RedisClusterNode otherMaster = masters.next();
            RedisClusterNode replica = map.get(master).iterator().next();

            connection.clusterReplicate(otherMaster, replica);
            try {
                Awaitility.await().atMost(Durations.TEN_SECONDS).untilAsserted(() -> {
                    Map<RedisClusterNode, Collection<RedisClusterNode>> moved = connection.clusterGetMasterReplicaMap();
                    assertThat(moved).hasSize(3);
                    for (Map.Entry<RedisClusterNode, Collection<RedisClusterNode>> entry : moved.entrySet()) {
                        if (entry.getKey().getId().equals(master.getId())) {
                            assertThat(entry.getValue()).isEmpty();
                        } else if (entry.getKey().getId().equals(otherMaster.getId())) {
                            assertThat(entry.getValue()).hasSize(2);
                        } else {
                            assertThat(entry.getValue()).hasSize(1);
                        }
                    }
                });
            } finally {
                connection.clusterReplicate(master, replica);
                Awaitility.await().atMost(Durations.ONE_MINUTE).untilAsserted(() -> {
                    assertThat(connection.clusterGetReplicas(master)).extracting(RedisClusterNode::getId).contains(replica.getId());
                    Properties replication = connection.info(replica, "replication");
                    assertThat(replication.getProperty("master_link_status")).isEqualTo("up");
                    assertThat(replication.getProperty("master_sync_in_progress")).isEqualTo("0");
                });
            }
        });
    }

    @Test
    public void testClusterGetSlotForKey() {
        testInCluster(connection -> {
            Integer slot = connection.clusterGetSlotForKey("123".getBytes());
            assertThat(slot).isNotNull();
        });
    }

    @Test
    public void testClusterGetNodeForSlot() {
        testInCluster(connection -> {
            RedisClusterNode node1 = connection.clusterGetNodeForSlot(1);
            RedisClusterNode node2 = connection.clusterGetNodeForSlot(16000);
            assertThat(node1.getId()).isNotEqualTo(node2.getId());
        });
    }

    @Test
    public void testClusterGetNodeForKey() {
        testInCluster(connection -> {
            RedisClusterNode node = connection.clusterGetNodeForKey("123".getBytes());
            assertThat(node).isNotNull();
        });
    }

    @Test
    public void testClusterGetClusterInfo() {
        testInCluster(connection -> {
            ClusterInfo info = connection.clusterGetClusterInfo();
            assertThat(info.getSlotsFail()).isEqualTo(0);
            assertThat(info.getSlotsOk()).isEqualTo(MasterSlaveConnectionManager.MAX_SLOT);
            assertThat(info.getSlotsAssigned()).isEqualTo(MasterSlaveConnectionManager.MAX_SLOT);
        });
    }

    @Test
    public void testClusterAddRemoveSlots() {
        testInCluster(connection -> {
            RedisClusterNode master = getFirstMaster(connection);
            Integer slot = master.getSlotRange().getSlots().iterator().next();
            connection.clusterDeleteSlots(master, slot);
            connection.clusterAddSlots(master, slot);
        });
    }

    @Test
    public void testClusterCountKeysInSlot() {
        testInCluster(connection -> {
            Long t = connection.clusterCountKeysInSlot(1);
            assertThat(t).isZero();
        });
    }

    @Test
    public void testClusterGetKeysInSlot() {
        testInCluster(connection -> {
            connection.flushAll();
            List<byte[]> keys = connection.clusterGetKeysInSlot(12, 10);
            assertThat(keys).isEmpty();
        });
    }

    @Test
    public void testClusterGetKeysInSlotOfEveryMaster() {
        testInCluster(connection -> {
            Set<RedisClusterNode> masters = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                byte[] key = ("key" + i).getBytes();
                connection.set(key, "value".getBytes());
                masters.add(connection.clusterGetNodeForKey(key));

                assertThat(connection.clusterGetKeysInSlot(connection.clusterGetSlotForKey(key), 10)).containsExactly(key);
            }
            assertThat(masters).hasSizeGreaterThan(1);
        });
    }

    @Test
    public void testDiscardEndsTheTransactionOnEveryMaster() {
        testInCluster(connection -> {
            Map<String, byte[]> keys = new HashMap<>();
            for (int i = 0; i < 20; i++) {
                byte[] key = ("key" + i).getBytes();
                keys.putIfAbsent(connection.clusterGetNodeForKey(key).getId(), key);
            }
            assertThat(keys).hasSizeGreaterThan(1);

            connection.multi();
            for (byte[] key : keys.values()) {
                connection.set(key, "value".getBytes());
            }
            connection.discard();

            for (RedisClusterNode node : connection.clusterGetNodes()) {
                if (node.isMaster()) {
                    assertThat(connection.getClientList(node)).noneMatch(info -> info.getFlags().contains("x"));
                }
            }
            for (byte[] key : keys.values()) {
                assertThat(connection.get(key)).isNull();
            }
        });
    }

    @Test
    public void testClusterForgetIsSentToEveryNode() {
        testInCluster(connection -> {
            Iterable<RedisClusterNode> nodes = connection.clusterGetNodes();
            for (RedisClusterNode node : nodes) {
                connection.resetConfigStats(node);
            }

            assertThatThrownBy(() -> connection.clusterForget(new RedisClusterNode("0123456789abcdef0123456789abcdef01234567")))
                    .hasMessageContaining("Unknown node");
            for (RedisClusterNode node : nodes) {
                assertThat(connection.info(node, "commandstats").getProperty("cmdstat_cluster|forget")).startsWith("calls=1,");
            }
        });
    }

    @Test
    public void testClusterPing() {
        testInCluster(connection -> {
            RedisClusterNode master = getFirstMaster(connection);
            String res = connection.ping(master);
            assertThat(res).isEqualTo("PONG");
        });
    }

    @Test
    public void testNodeUnknownToCluster() {
        testInCluster(connection -> {
            RedisClusterNode unknown = new RedisClusterNode("127.0.0.1", 1234);
            assertThatThrownBy(() -> connection.ping(unknown))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Node 127.0.0.1:1234 is unknown to cluster");
            assertThatThrownBy(() -> connection.dbSize(unknown))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> connection.keys(unknown, "*".getBytes()))
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    public void testDbSize() {
        testInCluster(connection -> {
            connection.flushAll();
            RedisClusterNode master = getFirstMaster(connection);
            Long size = connection.dbSize(master);
            assertThat(size).isZero();
        });
    }

    @Test
    public void testInfo() {
        testInCluster(connection -> {
            RedisClusterNode master = getFirstMaster(connection);
            Properties info = connection.info(master);
            assertThat(info.size()).isGreaterThan(10);
        });
    }

    @Test
    public void testNodeCommandsOnReplica() {
        testInCluster(connection -> {
            for (int i = 0; i < 30; i++) {
                connection.set(("key" + i).getBytes(), "value".getBytes());
            }
            RedisClusterNode master = getFirstMaster(connection);
            RedisClusterNode replica = connection.clusterGetReplicas(master).iterator().next();
            Long keys = connection.dbSize(master);
            assertThat(keys).isPositive();

            assertThat(connection.info(replica, "replication").getProperty("role")).isEqualTo("slave");
            assertThat(connection.info(master, "replication").getProperty("role")).isEqualTo("master");
            assertThatThrownBy(() -> connection.flushDb(replica))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("READONLY");
            assertThat(connection.dbSize(master)).isEqualTo(keys);

            connection.setConfig(replica, "replica-priority", "42");
            try {
                assertThat(connection.getConfig(replica, "replica-priority").getProperty("replica-priority")).isEqualTo("42");
                assertThat(connection.getConfig(master, "replica-priority").getProperty("replica-priority")).isEqualTo("100");
            } finally {
                connection.setConfig(replica, "replica-priority", "100");
            }
        });
    }

    @Test
    public void testClusterReplicate() {
        testInCluster(connection -> {
            Map<RedisClusterNode, Collection<RedisClusterNode>> map = connection.clusterGetMasterReplicaMap();
            Iterator<RedisClusterNode> masters = map.keySet().iterator();
            RedisClusterNode master = masters.next();
            RedisClusterNode otherMaster = masters.next();
            RedisClusterNode replica = map.get(master).iterator().next();

            connection.clusterReplicate(otherMaster, replica);
            try {
                Awaitility.await().atMost(Durations.TEN_SECONDS).untilAsserted(() -> {
                    assertThat(connection.clusterGetReplicas(otherMaster)).extracting(RedisClusterNode::getId).contains(replica.getId());
                });
            } finally {
                connection.clusterReplicate(master, replica);
                Awaitility.await().atMost(Durations.ONE_MINUTE).untilAsserted(() -> {
                    assertThat(connection.clusterGetReplicas(master)).extracting(RedisClusterNode::getId).contains(replica.getId());
                    Properties replication = connection.info(replica, "replication");
                    assertThat(replication.getProperty("master_link_status")).isEqualTo("up");
                    assertThat(replication.getProperty("master_sync_in_progress")).isEqualTo("0");
                });
            }
        });
    }

    @Test
    public void testKeyCommandsAcrossSlots() {
        testInCluster(connection -> {
            byte[] key1 = "key1".getBytes();
            byte[] key2 = "key2".getBytes();
            byte[] key3 = "key3".getBytes();
            assertThat(ClusterSlotHashUtil.isSameSlotForAllKeys(key1, key2, key3)).isFalse();

            Map<byte[], byte[]> first = new LinkedHashMap<>();
            first.put(key1, "value".getBytes());
            first.put(key2, "value".getBytes());
            assertThat(connection.mSetNX(first)).isTrue();
            assertThat(connection.exists(key1, key2, key3)).isEqualTo(2);

            Map<byte[], byte[]> second = new LinkedHashMap<>();
            second.put(key2, "other".getBytes());
            second.put(key3, "other".getBytes());
            assertThat(connection.mSetNX(second)).isFalse();
            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(connection.get(key2)).isEqualTo("value".getBytes());
            });
            assertThat(connection.exists(key1, key2, key3)).isEqualTo(2);

            assertThat(connection.touch(key1, key2, key3)).isEqualTo(2);
            assertThat(connection.unlink(key1, key2, key3)).isEqualTo(2);
            assertThat(connection.exists(key1, key2, key3)).isZero();
        });
    }

    @Test
    public void testSetCommandsAcrossSlots() {
        testInCluster(connection -> {
            byte[] set1 = "set1".getBytes();
            byte[] set2 = "set2".getBytes();
            byte[] set3 = "set3".getBytes();
            byte[] dest = "dest".getBytes();
            assertThat(ClusterSlotHashUtil.isSameSlotForAllKeys(set1, set2, set3, dest)).isFalse();

            connection.sAdd(set1, "a".getBytes(), "b".getBytes(), "c".getBytes());
            connection.sAdd(set2, "b".getBytes(), "c".getBytes(), "d".getBytes());
            connection.sAdd(set3, "c".getBytes(), "e".getBytes());

            assertThat(connection.sInter(set1, set2)).containsExactlyInAnyOrder("b".getBytes(), "c".getBytes());
            assertThat(connection.sInter(set1, set2, set3)).containsExactly("c".getBytes());
            assertThat(connection.sInterCard(set1, set2)).isEqualTo(2);
            assertThat(connection.sUnion(set1, set3)).containsExactlyInAnyOrder("a".getBytes(), "b".getBytes(), "c".getBytes(), "e".getBytes());
            assertThat(connection.sDiff(set1, set2)).containsExactly("a".getBytes());

            connection.sAdd(dest, "stale".getBytes());
            assertThat(connection.sInterStore(dest, set1, set2)).isEqualTo(2);
            assertThat(connection.sUnion(dest, set3)).containsExactlyInAnyOrder("b".getBytes(), "c".getBytes(), "e".getBytes());
            assertThat(connection.sUnionStore(dest, set1, set3)).isEqualTo(4);
            assertThat(connection.sUnion(dest, set3)).containsExactlyInAnyOrder("a".getBytes(), "b".getBytes(), "c".getBytes(), "e".getBytes());
            assertThat(connection.sDiffStore(dest, set1, set2)).isEqualTo(1);
            assertThat(connection.sUnion(dest, set3)).containsExactlyInAnyOrder("a".getBytes(), "c".getBytes(), "e".getBytes());
            assertThat(connection.sDiffStore(dest, set2, set2)).isZero();
            assertThat(connection.exists(dest, set1)).isEqualTo(1);

            assertThat(connection.sMove(set1, set2, "a".getBytes())).isTrue();
            assertThat(connection.sMove(set1, set2, "b".getBytes())).isTrue();
            assertThat(connection.sMove(set1, set2, "x".getBytes())).isFalse();
            assertThat(connection.sUnion(set1, set3)).containsExactlyInAnyOrder("c".getBytes(), "e".getBytes());
            assertThat(connection.sUnion(set2, set3)).containsExactlyInAnyOrder("a".getBytes(), "b".getBytes(), "c".getBytes(), "d".getBytes(), "e".getBytes());
        });
    }

    @Test
    public void testListCommandsAcrossSlots() {
        testInCluster(connection -> {
            byte[] src = "src".getBytes();
            byte[] dst = "dst".getBytes();
            assertThat(ClusterSlotHashUtil.isSameSlotForAllKeys(src, dst)).isFalse();

            connection.rPush(src, "a".getBytes(), "b".getBytes());
            assertThat(connection.rPopLPush(src, dst)).isEqualTo("b".getBytes());
            assertThat(connection.bRPopLPush(1, src, dst)).isEqualTo("a".getBytes());
            assertThat(connection.rPopLPush(src, dst)).isNull();
            assertThat(connection.bRPopLPush(1, src, dst)).isNull();
            assertThat(connection.lPop(dst, 3)).containsExactly("a".getBytes(), "b".getBytes());
        });
    }

    @Test
    public void testBlockingPopsAcrossSlots() {
        testInCluster(connection -> {
            byte[] list1 = "list1".getBytes();
            byte[] list2 = "list2".getBytes();
            assertThat(ClusterSlotHashUtil.isSameSlotForAllKeys(list1, list2)).isFalse();

            connection.rPush(list2, "a".getBytes(), "b".getBytes());
            assertThat(connection.bLPop(1, list1, list2)).containsExactly(list2, "a".getBytes());
            assertThat(connection.bRPop(1, list1, list2)).containsExactly(list2, "b".getBytes());

            long start = System.nanoTime();
            assertThat(connection.bLPop(2, list1, list2)).isNullOrEmpty();
            assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(TimeUnit.SECONDS.toNanos(2));
            assertThatThrownBy(() -> connection.bRPop(-1, list1, list2)).isInstanceOf(IllegalArgumentException.class);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<List<byte[]>> popped = executor.submit(() -> connection.bLPop(0, list1, list2));
                Thread.sleep(1500);
                connection.rPush(list1, "x".getBytes(), "y".getBytes());

                assertThat(popped.get(10, TimeUnit.SECONDS)).containsExactly(list1, "x".getBytes());
                Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                    assertThat(connection.lRange(list1, 0, -1)).containsExactly("y".getBytes());
                });
                assertThat(connection.exists(list1, list2)).isEqualTo(1);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                executor.shutdownNow();
            }
        });
    }

    @Test
    public void testSortWithStoreKeyAcrossSlots() {
        testInCluster(connection -> {
            byte[] list = "list".getBytes();
            byte[] sorted = "sorted".getBytes();
            byte[] missing = "missing".getBytes();
            assertThat(ClusterSlotHashUtil.isSameSlotForAllKeys(list, sorted, missing)).isFalse();

            connection.rPush(list, "3".getBytes(), "1".getBytes(), "2".getBytes());
            connection.rPush(sorted, "old".getBytes());
            RedisClusterNode master = connection.clusterGetNodeForKey(list);
            connection.resetConfigStats(master);
            assertThat(connection.sort(list, new DefaultSortParameters(), sorted)).isEqualTo(3);
            assertThat(connection.info(master, "commandstats").getProperty("cmdstat_sort")).startsWith("calls=1,");
            Awaitility.await().atMost(Durations.FIVE_SECONDS).untilAsserted(() -> {
                assertThat(connection.lRange(sorted, 0, -1)).containsExactly("1".getBytes(), "2".getBytes(), "3".getBytes());
            });

            assertThat(connection.sort(missing, new DefaultSortParameters(), sorted)).isZero();
            assertThat(connection.exists(sorted, missing)).isZero();
        });
    }

    @Test
    public void testDelPipeline() {
        testInCluster(connection -> {
            byte[] k = "key".getBytes();
            byte[] v = "val".getBytes();
            connection.set(k, v);

            connection.openPipeline();
            connection.get(k);
            connection.del(k);
            List<Object> results = connection.closePipeline();
            byte[] val = (byte[])results.get(0);
            assertThat(val).isEqualTo(v);
            Long res = (Long) results.get(1);
            assertThat(res).isEqualTo(1);
        });
    }

    @Test
    public void testServerCommandsOnEveryMaster() {
        testInCluster(connection -> {
            List<RedisClusterNode> masters = new ArrayList<>(connection.clusterGetMasterReplicaMap().keySet());
            assertThat(masters).hasSize(3);

            Properties info = connection.info();
            Properties server = connection.info("server");
            Properties config = connection.getConfig("slowlog-max-len");
            for (RedisClusterNode master : masters) {
                assertThat(info.getProperty(master.asString() + ".redis_version")).isNotNull();
                assertThat(info.getProperty(master.asString() + ".role")).isEqualTo("master");
                assertThat(server.getProperty(master.asString() + ".tcp_port")).isNotNull();
                assertThat(server.getProperty(master.asString() + ".role")).isNull();
                assertThat(config.getProperty(master.asString() + ".slowlog-max-len")).isNotNull();
            }
            assertThat(config).hasSize(3);

            String slowlogMaxLen = connection.getConfig(masters.get(0), "slowlog-max-len").getProperty("slowlog-max-len");
            try {
                connection.setConfig("slowlog-max-len", "127");
                for (RedisClusterNode master : masters) {
                    assertThat(connection.getConfig(master, "slowlog-max-len").getProperty("slowlog-max-len")).isEqualTo("127");
                }
            } finally {
                connection.setConfig("slowlog-max-len", slowlogMaxLen);
            }

            for (RedisClusterNode master : masters) {
                connection.dbSize(master);
                assertThat(connection.info(master, "commandstats").getProperty("cmdstat_dbsize")).isNotNull();
            }
            connection.resetConfigStats();
            for (RedisClusterNode master : masters) {
                assertThat(connection.info(master, "commandstats").getProperty("cmdstat_dbsize")).isNull();
            }

            assertThatThrownBy(connection::rewriteConfig).hasMessageContaining("without a config file");
        });
    }

    @Test
    public void testResetConfigStats() {
        testInCluster(connection -> {
            RedisClusterNode master = getFirstMaster(connection);
            connection.resetConfigStats(master);
        });
    }

    @Test
    public void testTime() {
        testInCluster(connection -> {
            RedisClusterNode master = getFirstMaster(connection);
            Long time = connection.time(master);
            assertThat(time).isGreaterThan(1000);
        });
    }

    @Test
    public void testTimeWithUnit() {
        testInCluster(connection -> {
            RedisClusterNode master = getFirstMaster(connection);
            Long millis = connection.time(master, TimeUnit.MILLISECONDS);
            Long seconds = connection.time(master, TimeUnit.SECONDS);
            assertThat(millis).isCloseTo(System.currentTimeMillis(), within(60_000L));
            assertThat(seconds).isBetween(millis / 1000, millis / 1000 + 2);
        });
    }

    @Test
    public void testFlushDbAndFlushAllWithOption() {
        testInCluster(connection -> {
            byte[] key = "flush-with-option".getBytes();
            RedisClusterNode node = connection.clusterGetNodeForKey(key);

            connection.set(key, "value".getBytes());
            assertThat(connection.dbSize(node)).isEqualTo(1);
            connection.flushDb(node, FlushOption.SYNC);
            assertThat(connection.dbSize(node)).isZero();

            connection.set(key, "value".getBytes());
            assertThat(connection.dbSize(node)).isEqualTo(1);
            connection.flushAll(node, FlushOption.ASYNC);
            assertThat(connection.dbSize(node)).isZero();
        });
    }

    @Test
    public void testFlushDbAndFlushAllWithOptionFlushEveryMaster() {
        testInCluster(connection -> {
            List<byte[]> keys = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                keys.add(("flush-key" + i).getBytes());
            }
            Set<String> masters = new HashSet<>();
            for (byte[] key : keys) {
                masters.add(connection.clusterGetNodeForKey(key).getId());
            }
            assertThat(masters).hasSize(3);

            for (FlushOption option : FlushOption.values()) {
                keys.forEach(key -> connection.set(key, "value".getBytes()));
                connection.flushDb(option);
                assertThat(connection.exists(keys.toArray(new byte[0][]))).isZero();

                keys.forEach(key -> connection.set(key, "value".getBytes()));
                connection.flushAll(option);
                assertThat(connection.exists(keys.toArray(new byte[0][]))).isZero();
            }
        });
    }

    @Test
    public void testScanOnNodeWithTypeAndBinaryPattern() {
        testInCluster(connection -> {
            byte[] binaryKey = {'{', 's', '}', (byte) 0xFF, 'k'};
            connection.set(binaryKey, "value".getBytes());
            connection.set("{s}string".getBytes(), "value".getBytes());
            connection.zAdd("{s}zset".getBytes(), 1, "a".getBytes());
            RedisClusterNode node = connection.clusterGetNodeForKey(binaryKey);

            List<byte[]> zsets = new ArrayList<>();
            try (Cursor<byte[]> cursor = connection.scan(node, KeyScanOptions.scanOptions(DataType.ZSET).build())) {
                cursor.forEachRemaining(zsets::add);
            }
            assertThat(zsets).containsExactly("{s}zset".getBytes());

            List<byte[]> keys = new ArrayList<>();
            try (Cursor<byte[]> cursor = connection.scan(node, ScanOptions.scanOptions().match(new byte[] {'{', 's', '}', (byte) 0xFF, '*'}).build())) {
                cursor.forEachRemaining(keys::add);
            }
            assertThat(keys).containsExactly(binaryKey);
        });
    }

    @Test
    public void testRewriteConfig() {
        testInCluster(connection -> {
            RedisClusterNode master = getFirstMaster(connection);
            assertThatThrownBy(() -> connection.rewriteConfig(master))
                    .hasMessageContaining("without a config file");
        });
    }

    @Test
    public void testGetClientList() {
        testInCluster(connection -> {
            RedisClusterNode master = getFirstMaster(connection);
            List<RedisClientInfo> list = connection.getClientList(master);
            assertThat(list.size()).isGreaterThan(10);
        });
    }

    @Test
    public void testSetConfig() {
        testInCluster(connection -> {
            RedisClusterNode master = getFirstMaster(connection);
            connection.setConfig(master, "timeout", "10");
        });
    }

    @Test
    public void testGetConfig() {
        testInCluster(connection -> {
            RedisClusterNode master = getFirstMaster(connection);
            Properties config = connection.getConfig(master, "*");
            assertThat(config.size()).isGreaterThan(20);
        });
    }
    
    protected RedisClusterNode getFirstMaster(RedissonClusterConnection connection) {
        Map<RedisClusterNode, Collection<RedisClusterNode>> map = connection.clusterGetMasterReplicaMap();
        RedisClusterNode master = map.keySet().iterator().next();
        return master;
    }

    @Test
    public void testConnectionFactoryReturnsClusterConnection() {
        testInCluster(connection -> {
            RedissonClient redisson = (RedissonClient) connection.getNativeConnection();
            RedisConnectionFactory connectionFactory = new RedissonConnectionFactory(redisson);

            assertThat(connectionFactory.getConnection()).isInstanceOf(RedissonClusterConnection.class);
        });
    }

}
