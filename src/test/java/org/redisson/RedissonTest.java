package org.redisson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.WriteRedisConnectionException;
import org.redisson.client.codec.Codec;
import org.redisson.codec.SerializationCodec;
import org.redisson.connection.ConnectionListener;
import org.redisson.connection.balancer.LoadBalancer;
import org.redisson.core.ClusterNode;
import org.redisson.core.Node;
import org.redisson.core.NodesGroup;
import org.redisson.misc.URIBuilder;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import static org.assertj.core.api.Assertions.*;

import net.jodah.concurrentunit.Waiter;

public class RedissonTest {

    private String redisFolder = "C:\\Devel\\projects\\redis\\Redis-x64-3.0.500\\";

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

        Process p = runRedis();

        final Waiter onConnectWaiter = new Waiter();
        final Waiter onDisconnectWaiter = new Waiter();

        Config config = new Config();
        config.useSingleServer().setAddress("127.0.0.1:6319").setFailedAttempts(1).setRetryAttempts(1)
        .setConnectionMinimumIdleSize(0);

        RedissonClient r = Redisson.create(config);

        int id = r.getNodesGroup().addConnectionListener(new ConnectionListener() {

            @Override
            public void onDisconnect(InetSocketAddress addr) {
                onDisconnectWaiter.assertEquals(new InetSocketAddress("127.0.0.1", 6319), addr);
                onDisconnectWaiter.resume();
            }

            @Override
            public void onConnect(InetSocketAddress addr) {
                onConnectWaiter.assertEquals(new InetSocketAddress("127.0.0.1", 6319), addr);
                onConnectWaiter.resume();
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

        p = runRedis();

        r.getBucket("1").get();

        r.shutdown();

        p.destroy();
        Assert.assertEquals(1, p.waitFor());

        onConnectWaiter.await(1, TimeUnit.SECONDS, 2);
        onDisconnectWaiter.await();
    }

    private Process runRedis() throws IOException, InterruptedException {
        URL resource = getClass().getResource("/redis_connectionListener_test.conf");

        ProcessBuilder master = new ProcessBuilder(redisFolder + "redis-server.exe", resource.getFile().substring(1));
        master.directory(new File(redisFolder));
        Process p = master.start();
        Thread.sleep(1000);
        return p;
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
