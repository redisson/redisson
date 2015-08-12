package org.redisson;

import java.util.Iterator;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.client.WriteRedisConnectionException;
import org.redisson.codec.SerializationCodec;
import org.redisson.core.ClusterNode;
import org.redisson.core.Node;
import org.redisson.core.NodesGroup;

public class RedissonTest extends BaseTest {

    public static class Dummy {
        private String field;
    }

    @Test(expected = WriteRedisConnectionException.class)
    public void testSer() {
        Config config = new Config();
        config.useSingleServer().setAddress("127.0.0.1:6379");
        config.setCodec(new SerializationCodec());
        Redisson r = Redisson.create(config);
        r.getMap("test").put("1", new Dummy());
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
