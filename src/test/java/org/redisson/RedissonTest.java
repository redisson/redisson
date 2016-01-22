package org.redisson;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.client.WriteRedisConnectionException;
import org.redisson.codec.SerializationCodec;
import org.redisson.connection.ConnectionListener;
import org.redisson.core.ClusterNode;
import org.redisson.core.Node;
import org.redisson.core.NodesGroup;
import static com.jayway.awaitility.Awaitility.*;

public class RedissonTest {

    RedissonClient redisson;

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

    @Test
    public void testConnectionListener() throws IOException, InterruptedException, TimeoutException {

        Process p = RedisRunner.runRedis("/redis_connectionListener_test.conf");

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
        p.destroy();
        Assert.assertEquals(1, p.waitFor());

        try {
            r.getBucket("1").get();
        } catch (Exception e) {
        }

        p = RedisRunner.runRedis("/redis_connectionListener_test.conf");

        r.getBucket("1").get();

        r.shutdown();

        p.destroy();
        Assert.assertEquals(1, p.waitFor());

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

        for (Node node : nodes.getNodes()) {
            Assert.assertTrue(node.ping());
        }

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
        System.out.println(t);
        assertThat(c.toJSON()).isEqualTo(t);
    }

    @Test
    public void testMasterSlaveConfig() throws IOException {
        Config c2 = new Config();
        c2.useMasterSlaveServers().setMasterAddress("123.1.1.1:1231").addSlaveAddress("82.12.47.12:1028");

        String t = c2.toJSON();
        Config c = Config.fromJSON(t);
        System.out.println(t);
        assertThat(c.toJSON()).isEqualTo(t);
    }

    @Test
    public void testCluster() {
        NodesGroup<ClusterNode> nodes = redisson.getClusterNodesGroup();
        Assert.assertEquals(2, nodes.getNodes().size());

        for (ClusterNode node : nodes.getNodes()) {
            Map<String, String> params = node.info();
            Assert.assertNotNull(params);
            Assert.assertTrue(node.ping());
        }

        Assert.assertTrue(nodes.pingAll());
    }

}
