package org.redisson;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.AfterClass;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisOutOfMemoryException;
import org.redisson.client.WriteRedisConnectionException;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.codec.SerializationCodec;
import org.redisson.connection.ConnectionListener;
import org.redisson.core.ClusterNode;
import org.redisson.core.Node;
import org.redisson.core.NodesGroup;
import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.redisson.BaseTest.createInstance;

public class RedissonTest {

    protected RedissonClient redisson;
    protected static RedissonClient defaultRedisson;

    @Test
    public void testIterator() {
        RedissonBaseIterator iter = new RedissonBaseIterator() {
            int i;
            @Override
            ListScanResult iterator(InetSocketAddress client, long nextIterPos) {
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

    @Test(expected = WriteRedisConnectionException.class)
    public void testSer() {
        Config config = new Config();
        config.useSingleServer().setAddress("127.0.0.1:6379");
        config.setCodec(new SerializationCodec());
        RedissonClient r = Redisson.create(config);
        r.getMap("test").put("1", new Dummy());
    }
    
    @Test(expected = RedisOutOfMemoryException.class)
    public void testMemoryScript() throws IOException, InterruptedException {
        RedisProcess p = redisTestSmallMemory();

        Config config = new Config();
        config.useSingleServer().setAddress("127.0.0.1:6319").setTimeout(100000);

        try {
            RedissonClient r = Redisson.create(config);
            r.getKeys().flushall();
            for (int i = 0; i < 10000; i++) {
                r.getMap("test").put("" + i, "" + i);
            }
        } finally {
            p.stop();
        }
    }

    @Test(expected = RedisOutOfMemoryException.class)
    public void testMemoryCommand() throws IOException, InterruptedException {
        RedisProcess p = redisTestSmallMemory();

        Config config = new Config();
        config.useSingleServer().setAddress("127.0.0.1:6319").setTimeout(100000);

        try {
            RedissonClient r = Redisson.create(config);
            r.getKeys().flushall();
            for (int i = 0; i < 10000; i++) {
                r.getMap("test").fastPut("" + i, "" + i);
            }
        } finally {
            p.stop();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfigValidation() {
        Config redissonConfig = new Config();
        redissonConfig.useSingleServer()
        .setAddress("127.0.0.1:6379")
        .setConnectionPoolSize(2);
        Redisson.create(redissonConfig);        
    }
    
    @Test
    public void testConnectionListener() throws IOException, InterruptedException, TimeoutException {

        RedisProcess p = redisTestConnection();

        final AtomicInteger connectCounter = new AtomicInteger();
        final AtomicInteger disconnectCounter = new AtomicInteger();

        Config config = new Config();
        config.useSingleServer().setAddress("127.0.0.1:6319").setFailedAttempts(1).setRetryAttempts(1)
        .setConnectionMinimumIdleSize(0);

        RedissonClient r = Redisson.create(config);

        int id = r.getNodesGroup().addConnectionListener(new ConnectionListener() {

            @Override
            public void onDisconnect(InetSocketAddress addr) {
                assertThat(addr).isEqualTo(new InetSocketAddress("127.0.0.1", 6319));
                disconnectCounter.incrementAndGet();
            }

            @Override
            public void onConnect(InetSocketAddress addr) {
                assertThat(addr).isEqualTo(new InetSocketAddress("127.0.0.1", 6319));
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

        p = redisTestConnection();

        r.getBucket("1").get();

        r.shutdown();

        Assert.assertEquals(0, p.stop());

        await().atMost(1, TimeUnit.SECONDS).until(() -> assertThat(connectCounter.get()).isEqualTo(2));
        await().until(() -> assertThat(disconnectCounter.get()).isEqualTo(1));
    }

    @Test
    public void testShutdown() {
        Config config = new Config();
        config.useSingleServer().setAddress("127.0.0.1:6379");

        RedissonClient r = Redisson.create(config);
        Assert.assertFalse(r.isShuttingDown());
        Assert.assertFalse(r.isShutdown());
        r.shutdown();
        Assert.assertTrue(r.isShuttingDown());
        Assert.assertTrue(r.isShutdown());
    }

//    @Test
    public void test() {
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
        originalConfig.useClusterServers().addNodeAddress("123.123.1.23:1902", "9.3.1.0:1902");
        String t = originalConfig.toJSON();
        Config c = Config.fromJSON(t);
        System.out.println(t);
        assertThat(c.toJSON()).isEqualTo(t);
    }

    @Test
    public void testSingleConfig() throws IOException {
        RedissonClient r = Redisson.create();
        String t = r.getConfig().toJSON();
        Config c = Config.fromJSON(t);
        assertThat(c.toJSON()).isEqualTo(t);
    }

    @Test
    public void testMasterSlaveConfig() throws IOException {
        Config c2 = new Config();
        c2.useMasterSlaveServers().setMasterAddress("123.1.1.1:1231").addSlaveAddress("82.12.47.12:1028");

        String t = c2.toJSON();
        Config c = Config.fromJSON(t);
        assertThat(c.toJSON()).isEqualTo(t);
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
        config.useSingleServer().setAddress("127.0.0.1:1111");
        Redisson.create(config);

        Thread.sleep(1500);
    }

    @Test(expected = RedisConnectionException.class)
    public void testClusterConnectionFail() throws InterruptedException {
        Config config = new Config();
        config.useClusterServers().addNodeAddress("127.0.0.1:1111");
        Redisson.create(config);

        Thread.sleep(1500);
    }

    @Test(expected = RedisConnectionException.class)
    public void testElasticacheConnectionFail() throws InterruptedException {
        Config config = new Config();
        config.useElasticacheServers().addNodeAddress("127.0.0.1:1111");
        Redisson.create(config);

        Thread.sleep(1500);
    }

    @Test(expected = RedisConnectionException.class)
    public void testMasterSlaveConnectionFail() throws InterruptedException {
        Config config = new Config();
        config.useMasterSlaveServers().setMasterAddress("127.0.0.1:1111");
        Redisson.create(config);

        Thread.sleep(1500);
    }

    @Test(expected = RedisConnectionException.class)
    public void testSentinelConnectionFail() throws InterruptedException {
        Config config = new Config();
        config.useSentinelServers().addSentinelAddress("127.0.0.1:1111");
        Redisson.create(config);

        Thread.sleep(1500);
    }

    @Test
    public void testManyConnections() {
        Assume.assumeFalse(Boolean.valueOf(System.getProperty("travisEnv")));
        Config redisConfig = new Config();
        redisConfig.useSingleServer()
        .setConnectionMinimumIdleSize(10000)
        .setConnectionPoolSize(10000)
        .setAddress("localhost:6379");
        RedissonClient r = Redisson.create(redisConfig);
        r.shutdown();
    }

    private RedisProcess redisTestSmallMemory() throws IOException, InterruptedException {
        return new RedisRunner()
                .maxmemory("1mb")
                .nosave()
                .randomDir()
                .port(6319)
                .run();
    }

    private RedisProcess redisTestConnection() throws IOException, InterruptedException {
        return new RedisRunner()
                .nosave()
                .randomDir()
                .port(6319)
                .run();
    }
    
}
