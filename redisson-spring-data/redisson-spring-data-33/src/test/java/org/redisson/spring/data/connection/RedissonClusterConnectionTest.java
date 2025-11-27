package org.redisson.spring.data.connection;

import net.bytebuddy.utility.RandomString;
import org.junit.Test;
import org.redisson.BaseTest;
import org.redisson.api.RedissonClient;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.springframework.data.redis.connection.ClusterInfo;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisNode.NodeType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.RedisClientInfo;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonClusterConnectionTest extends BaseTest {

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
                // slave node delay
                Thread.sleep(50);
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
                // slave node delay
                Thread.sleep(50);
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
    public void testClusterPing() {
        testInCluster(connection -> {
            RedisClusterNode master = getFirstMaster(connection);
            String res = connection.ping(master);
            assertThat(res).isEqualTo("PONG");
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
