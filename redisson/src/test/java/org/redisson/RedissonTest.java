package org.redisson;

import static org.awaitility.Awaitility.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.redisson.BaseTest.createInstance;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.ClusterNode;
import org.redisson.api.Node;
import org.redisson.api.Node.InfoSection;
import org.redisson.api.NodesGroup;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisOutOfMemoryException;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.codec.SerializationCodec;
import org.redisson.config.Config;
import org.redisson.connection.ConnectionListener;

import io.netty.buffer.Unpooled;

public class RedissonTest {

    protected RedissonClient redisson;
    protected static RedissonClient defaultRedisson;
    
    @Test
    public void testSmallPool() throws InterruptedException {
        Config config = new Config();
        config.useSingleServer()
              .setConnectionMinimumIdleSize(3)
              .setConnectionPoolSize(3)
              .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());

        RedissonClient localRedisson = Redisson.create(config);
        
        RMap<String, String> map = localRedisson.getMap("test");
        
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()*2);
        long start = System.currentTimeMillis();
        int iterations = 500_000;
        for (int i = 0; i < iterations; i++) {
            final int j = i;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    map.put("" + j, "" + j);
                }
            });
        }
        
        executor.shutdown();
        Assert.assertTrue(executor.awaitTermination(10, TimeUnit.MINUTES));
        
        assertThat(map.size()).isEqualTo(iterations);
        
        localRedisson.shutdown();
    }
    
    @Test
    public void testIteratorNotLooped() {
        RedissonBaseIterator iter = new RedissonBaseIterator() {
            int i;
            @Override
            ListScanResult iterator(RedisClient client, long nextIterPos) {
                i++;
                if (i == 1) {
                    return new ListScanResult(13L, Collections.emptyList());
                }
                if (i == 2) {
                    return new ListScanResult(0L, Collections.emptyList());
                }
                Assert.fail();
                return null;
            }

            @Override
            void remove(Object value) {
            }
            
        };
        
        Assert.assertFalse(iter.hasNext());
    }
    
    @Test
    public void testIteratorNotLooped2() {
        RedissonBaseIterator<Integer> iter = new RedissonBaseIterator<Integer>() {
            int i;
            @Override
            ListScanResult<ScanObjectEntry> iterator(RedisClient client, long nextIterPos) {
                i++;
                if (i == 1) {
                    return new ListScanResult<ScanObjectEntry>(14L, Arrays.asList(new ScanObjectEntry(Unpooled.wrappedBuffer(new byte[] {1}), 1)));
                }
                if (i == 2) {
                    return new ListScanResult(7L, Collections.emptyList());
                }
                if (i == 3) {
                    return new ListScanResult(0L, Collections.emptyList());
                }
                if (i == 4) {
                    return new ListScanResult(14L, Collections.emptyList());
                }
                Assert.fail();
                return null;
            }

            @Override
            void remove(Integer value) {
            }
            
        };
        
        Assert.assertTrue(iter.hasNext());
        assertThat(iter.next()).isEqualTo(1);
        Assert.assertFalse(iter.hasNext());
    }

    
    @BeforeClass
    public static void beforeClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
            defaultRedisson = BaseTest.createInstance();
        }
    }

    @AfterClass
    public static void afterClass() throws IOException, InterruptedException {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.shutDownDefaultRedisServerInstance();
            defaultRedisson.shutdown();
        }
    }

    @Before
    public void before() throws IOException, InterruptedException {
        if (RedissonRuntimeEnvironment.isTravis) {
            RedisRunner.startDefaultRedisServerInstance();
            redisson = createInstance();
        } else {
            if (redisson == null) {
                redisson = defaultRedisson;
            }
            redisson.getKeys().flushall();
        }
    }

    @After
    public void after() throws InterruptedException {
        if (RedissonRuntimeEnvironment.isTravis) {
            redisson.shutdown();
            RedisRunner.shutDownDefaultRedisServerInstance();
        }
    }
    
    public static class Dummy {
        private String field;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSer() {
        Config config = new Config();
        config.useSingleServer().setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());
        config.setCodec(new SerializationCodec());
        RedissonClient r = Redisson.create(config);
        r.getMap("test").put("1", new Dummy());
    }
    
    @Test(expected = RedisOutOfMemoryException.class)
    public void testMemoryScript() throws IOException, InterruptedException {
        RedisProcess p = redisTestSmallMemory();

        Config config = new Config();
        config.useSingleServer().setAddress(p.getRedisServerAddressAndPort()).setTimeout(100000);

        RedissonClient r = null;
        try {
            r = Redisson.create(config);
            r.getKeys().flushall();
            for (int i = 0; i < 10000; i++) {
                r.getMap("test").put("" + i, "" + i);
            }
        } finally {
            r.shutdown();
            p.stop();
        }
    }

    @Test(expected = RedisOutOfMemoryException.class)
    public void testMemoryCommand() throws IOException, InterruptedException {
        RedisProcess p = redisTestSmallMemory();

        Config config = new Config();
        config.useSingleServer().setAddress(p.getRedisServerAddressAndPort()).setTimeout(100000);

        RedissonClient r = null;
        try {
            r = Redisson.create(config);
            r.getKeys().flushall();
            for (int i = 0; i < 10000; i++) {
                r.getMap("test").fastPut("" + i, "" + i);
            }
        } finally {
            r.shutdown();
            p.stop();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfigValidation() {
        Config redissonConfig = new Config();
        redissonConfig.useSingleServer()
        .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort())
        .setConnectionPoolSize(2);
        Redisson.create(redissonConfig);        
    }
    
    @Test
    public void testConnectionListener() throws IOException, InterruptedException, TimeoutException {

        final RedisProcess p = redisTestConnection();

        final AtomicInteger connectCounter = new AtomicInteger();
        final AtomicInteger disconnectCounter = new AtomicInteger();

        Config config = new Config();
        config.useSingleServer().setAddress(p.getRedisServerAddressAndPort());

        RedissonClient r = Redisson.create(config);

        int id = r.getNodesGroup().addConnectionListener(new ConnectionListener() {

            @Override
            public void onDisconnect(InetSocketAddress addr) {
                assertThat(addr).isEqualTo(new InetSocketAddress(p.getRedisServerBindAddress(), p.getRedisServerPort()));
                disconnectCounter.incrementAndGet();
            }

            @Override
            public void onConnect(InetSocketAddress addr) {
                assertThat(addr).isEqualTo(new InetSocketAddress(p.getRedisServerBindAddress(), p.getRedisServerPort()));
                connectCounter.incrementAndGet();
            }
        });

        assertThat(id).isNotZero();

        r.getBucket("1").get();
        Assert.assertEquals(0, p.stop());
        
        try {
            r.getBucket("1").get();
        } catch (Exception e) {
        }

        RedisProcess pp = new RedisRunner()
                .nosave()
                .port(p.getRedisServerPort())
                .randomDir()
                .run();

        r.getBucket("1").get();

        r.shutdown();

        Assert.assertEquals(0, pp.stop());

        await().atMost(2, TimeUnit.SECONDS).until(() -> connectCounter.get() == 1);
        await().atMost(2, TimeUnit.SECONDS).until(() -> disconnectCounter.get() == 1);
    }
    
    @Test
    public void testReconnection() throws IOException, InterruptedException, TimeoutException {
        RedisProcess runner = new RedisRunner()
                .appendonly(true)
                .randomDir()
                .randomPort()
                .run();

        Config config = new Config();
        config.useSingleServer().setAddress(runner.getRedisServerAddressAndPort());

        RedissonClient r = Redisson.create(config);
        
        r.getBucket("myBucket").set(1);
        assertThat(r.getBucket("myBucket").get()).isEqualTo(1);
        
        Assert.assertEquals(0, runner.stop());
        
        AtomicBoolean hasError = new AtomicBoolean();
        try {
            r.getBucket("myBucket").get();
        } catch (Exception e) {
            // skip error
            hasError.set(true);
        }

        assertThat(hasError.get()).isTrue();
        
        RedisProcess pp = new RedisRunner()
                .appendonly(true)
                .port(runner.getRedisServerPort())
                .dir(runner.getDefaultDir())
                .run();

        assertThat(r.getBucket("myBucket").get()).isEqualTo(1);

        r.shutdown();

        Assert.assertEquals(0, pp.stop());
    }


    @Test
    public void testShutdown() {
        Config config = new Config();
        config.useSingleServer().setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());

        RedissonClient r = Redisson.create(config);
        Assert.assertFalse(r.isShuttingDown());
        Assert.assertFalse(r.isShutdown());
        r.shutdown();
        Assert.assertTrue(r.isShuttingDown());
        Assert.assertTrue(r.isShutdown());
    }

    @Test
    public void testNode() {
        Node node = redisson.getNodesGroup().getNode(RedisRunner.getDefaultRedisServerBindAddressAndPort());
        assertThat(node).isNotNull();
    }
    
    @Test
    public void testInfo() {
        Node node = redisson.getNodesGroup().getNodes().iterator().next();

        Map<String, String> allResponse = node.info(InfoSection.ALL);
        assertThat(allResponse).containsKeys("redis_version", "connected_clients");
        
        Map<String, String> defaultResponse = node.info(InfoSection.DEFAULT);
        assertThat(defaultResponse).containsKeys("redis_version", "connected_clients");
        
        Map<String, String> serverResponse = node.info(InfoSection.SERVER);
        assertThat(serverResponse).containsKey("redis_version");
        
        Map<String, String> clientsResponse = node.info(InfoSection.CLIENTS);
        assertThat(clientsResponse).containsKey("connected_clients");

        Map<String, String> memoryResponse = node.info(InfoSection.MEMORY);
        assertThat(memoryResponse).containsKey("used_memory_human");
        
        Map<String, String> persistenceResponse = node.info(InfoSection.PERSISTENCE);
        assertThat(persistenceResponse).containsKey("rdb_last_save_time");

        Map<String, String> statsResponse = node.info(InfoSection.STATS);
        assertThat(statsResponse).containsKey("pubsub_patterns");
        
        Map<String, String> replicationResponse = node.info(InfoSection.REPLICATION);
        assertThat(replicationResponse).containsKey("repl_backlog_first_byte_offset");
        
        Map<String, String> cpuResponse = node.info(InfoSection.CPU);
        assertThat(cpuResponse).containsKey("used_cpu_sys");

        Map<String, String> commandStatsResponse = node.info(InfoSection.COMMANDSTATS);
        assertThat(commandStatsResponse).containsKey("cmdstat_flushall");

        Map<String, String> clusterResponse = node.info(InfoSection.CLUSTER);
        assertThat(clusterResponse).containsKey("cluster_enabled");

        Map<String, String> keyspaceResponse = node.info(InfoSection.KEYSPACE);
        assertThat(keyspaceResponse).isEmpty();
    }
    
    @Test
    public void testTime() {
        NodesGroup<Node> nodes = redisson.getNodesGroup();
        Assert.assertEquals(1, nodes.getNodes().size());
        Iterator<Node> iter = nodes.getNodes().iterator();

        Node node1 = iter.next();
        assertThat(node1.time()).isGreaterThan(100000L);
    }

    @Test
    public void testPing() {
        NodesGroup<Node> nodes = redisson.getNodesGroup();
        Assert.assertEquals(1, nodes.getNodes().size());
        Iterator<Node> iter = nodes.getNodes().iterator();

        Node node1 = iter.next();
        Assert.assertTrue(node1.ping());

        Assert.assertTrue(nodes.pingAll());
    }

//    @Test
    public void testSentinel() {
        NodesGroup<Node> nodes = redisson.getNodesGroup();
        Assert.assertEquals(5, nodes.getNodes().size());

        nodes.getNodes().stream().forEach((node) -> {
            Assert.assertTrue(node.ping());
        });

        Assert.assertTrue(nodes.pingAll());
    }

    @Test
    public void testClusterConfig() throws IOException {
        Config originalConfig = new Config();
        originalConfig.useClusterServers().addNodeAddress("redis://123.123.1.23:1902", "redis://9.3.1.0:1902");
        String t = originalConfig.toJSON();
        Config c = Config.fromJSON(t);
        assertThat(c.toJSON()).isEqualTo(t);
    }

    @Test
    public void testSingleConfigJSON() throws IOException {
        RedissonClient r = BaseTest.createInstance();
        String t = r.getConfig().toJSON();
        Config c = Config.fromJSON(t);
        assertThat(c.toJSON()).isEqualTo(t);
    }
    
    @Test
    public void testSingleConfigYAML() throws IOException {
        RedissonClient r = BaseTest.createInstance();
        String t = r.getConfig().toYAML();
        Config c = Config.fromYAML(t);
        assertThat(c.toYAML()).isEqualTo(t);
    }


    @Test
    public void testMasterSlaveConfigJSON() throws IOException {
        Config c2 = new Config();
        c2.useMasterSlaveServers().setMasterAddress("redis://123.1.1.1:1231").addSlaveAddress("redis://82.12.47.12:1028");
        String t = c2.toJSON();
        Config c = Config.fromJSON(t);
        assertThat(c.toJSON()).isEqualTo(t);
    }

    @Test
    public void testMasterSlaveConfigYAML() throws IOException {
        Config c2 = new Config();
        c2.useMasterSlaveServers().setMasterAddress("redis://123.1.1.1:1231").addSlaveAddress("redis://82.12.47.12:1028");
        String t = c2.toYAML();
        Config c = Config.fromYAML(t);
        assertThat(c.toYAML()).isEqualTo(t);
    }
    
//    @Test
    public void testCluster() {
        NodesGroup<ClusterNode> nodes = redisson.getClusterNodesGroup();
        Assert.assertEquals(2, nodes.getNodes().size());

        nodes.getNodes().stream().forEach((node) -> {
            Map<String, String> params = node.info();
            Assert.assertNotNull(params);
            Assert.assertTrue(node.ping());
        });

        Assert.assertTrue(nodes.pingAll());
    }

    @Test(expected = RedisConnectionException.class)
    public void testSingleConnectionFail() throws InterruptedException {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.99.0.1:1111");
        Redisson.create(config);

        Thread.sleep(1500);
    }

    @Test(expected = RedisConnectionException.class)
    public void testClusterConnectionFail() throws InterruptedException {
        Config config = new Config();
        config.useClusterServers().addNodeAddress("redis://127.99.0.1:1111");
        Redisson.create(config);

        Thread.sleep(1500);
    }

    @Test(expected = RedisConnectionException.class)
    public void testElasticacheConnectionFail() throws InterruptedException {
        Config config = new Config();
        config.useElasticacheServers().addNodeAddress("redis://127.99.0.1:1111");
        Redisson.create(config);

        Thread.sleep(1500);
    }

    @Test(expected = RedisConnectionException.class)
    public void testReplicatedConnectionFail() throws InterruptedException {
        Config config = new Config();
        config.useReplicatedServers().addNodeAddress("redis://127.99.0.1:1111");
        Redisson.create(config);

        Thread.sleep(1500);
    }

    @Test(expected = RedisConnectionException.class)
    public void testMasterSlaveConnectionFail() throws InterruptedException {
        Config config = new Config();
        config.useMasterSlaveServers().setMasterAddress("redis://127.99.0.1:1111");
        Redisson.create(config);

        Thread.sleep(1500);
    }

    @Test(expected = RedisConnectionException.class)
    public void testSentinelConnectionFail() throws InterruptedException {
        Config config = new Config();
        config.useSentinelServers().addSentinelAddress("redis://127.99.0.1:1111").setMasterName("test");
        Redisson.create(config);

        Thread.sleep(1500);
    }

    @Test
    public void testManyConnections() {
        Assume.assumeFalse(RedissonRuntimeEnvironment.isTravis);
        Config redisConfig = new Config();
        redisConfig.useSingleServer()
        .setConnectionMinimumIdleSize(10000)
        .setConnectionPoolSize(10000)
        .setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort());
        RedissonClient r = Redisson.create(redisConfig);
        r.shutdown();
    }

    private RedisProcess redisTestSmallMemory() throws IOException, InterruptedException {
        return new RedisRunner()
                .maxmemory("1mb")
                .nosave()
                .randomDir()
                .randomPort()
                .run();
    }

    private RedisProcess redisTestConnection() throws IOException, InterruptedException {
        return new RedisRunner()
                .nosave()
                .randomDir()
                .randomPort()
                .run();
    }
    
}
