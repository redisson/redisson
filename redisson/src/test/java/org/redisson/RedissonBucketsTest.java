package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.redisson.ClusterRunner.ClusterProcesses;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.api.NameMapper;
import org.redisson.api.RBucket;
import org.redisson.api.RBuckets;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.connection.balancer.RandomLoadBalancer;

public class RedissonBucketsTest extends BaseTest {

    @Test
    public void testGetInClusterNameMapper() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().randomPort().randomDir().nosave();

        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterProcesses process = clusterRunner.run();

        Config config = new Config();
        config.useClusterServers()
                .setNameMapper(new NameMapper() {
                    @Override
                    public String map(String name) {
                        return "test::" + name;
                    }

                    @Override
                    public String unmap(String name) {
                        return name.replace("test::", "");
                    }
                })
                .setLoadBalancer(new RandomLoadBalancer())
                .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        int size = 10000;
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            map.put("test" + i, i);
        }
        for (int i = 10; i < size; i++) {
            map.put("test" + i + "{" + (i%100)+ "}", i);
        }

        redisson.getBuckets().set(map);

        Set<String> queryKeys = new HashSet<>(map.keySet());
        queryKeys.add("test_invalid");
        Map<String, Integer> buckets = redisson.getBuckets().get(queryKeys.toArray(new String[map.size()]));

        assertThat(buckets).isEqualTo(map);

        for (int i = 0; i < 10; i++) {
            assertThat(redisson.getBucket("test" + i).get()).isEqualTo(i);
        }

        redisson.shutdown();
        process.shutdown();
    }

    @Test
    public void testGetInCluster() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisRunner master1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner master3 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave1 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave2 = new RedisRunner().randomPort().randomDir().nosave();
        RedisRunner slave3 = new RedisRunner().randomPort().randomDir().nosave();

        ClusterRunner clusterRunner = new ClusterRunner()
                .addNode(master1, slave1)
                .addNode(master2, slave2)
                .addNode(master3, slave3);
        ClusterProcesses process = clusterRunner.run();
        
        Config config = new Config();
        config.useClusterServers()
        .setLoadBalancer(new RandomLoadBalancer())
        .addNodeAddress(process.getNodes().stream().findAny().get().getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);
        
        int size = 10000;
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            map.put("test" + i, i);
        }
        for (int i = 10; i < size; i++) {
            map.put("test" + i + "{" + (i%100)+ "}", i);
        }

        redisson.getBuckets().set(map);
        
        Set<String> queryKeys = new HashSet<>(map.keySet());
        queryKeys.add("test_invalid");
        Map<String, Integer> buckets = redisson.getBuckets().get(queryKeys.toArray(new String[map.size()]));
        
        assertThat(buckets).isEqualTo(map);
        
        redisson.shutdown();
        process.shutdown();
    }
    
    @Test
    public void testGet() {
        redisson.getBucket("test1").set("someValue1");
        redisson.getBucket("test2").delete();
        redisson.getBucket("test3").set("someValue3");
        redisson.getBucket("test4").delete();

        Map<String, String> result = redisson.getBuckets().get("test1", "test2", "test3", "test4");
        Map<String, String> expected = new HashMap<String, String>();
        expected.put("test1", "someValue1");
        expected.put("test3", "someValue3");

        assertThat(expected).isEqualTo(result);
    }

    @Test
    public void testCodec() {
        RBuckets buckets = redisson.getBuckets(StringCodec.INSTANCE);
        Map<String, String> items = buckets.get("buckets:A", "buckets:B", "buckets:C");

        items.put("buckets:A", "XYZ");
        items.put("buckets:B", "OPM");
        items.put("buckets:C", "123");

        buckets.set(items);
        items = buckets.get("buckets:A", "buckets:B", "buckets:C");
        assertThat(3).isEqualTo(items.size());
        assertThat(items.get("buckets:A")).isEqualTo("XYZ");
    }

    @Test
    public void testSet() {
        Map<String, Integer> buckets = new HashMap<String, Integer>();
        buckets.put("12", 1);
        buckets.put("41", 2);
        redisson.getBuckets().set(buckets);

        RBucket<Object> r1 = redisson.getBucket("12");
        assertThat(r1.get()).isEqualTo(1);

        RBucket<Object> r2 = redisson.getBucket("41");
        assertThat(r2.get()).isEqualTo(2);
    }

    @Test
    public void testTrySet() {
        redisson.getBucket("12").set("341");

        Map<String, Integer> buckets = new HashMap<String, Integer>();
        buckets.put("12", 1);
        buckets.put("41", 2);
        assertThat(redisson.getBuckets().trySet(buckets)).isFalse();

        RBucket<Object> r2 = redisson.getBucket("41");
        assertThat(r2.get()).isNull();
        
        Map<String, Integer> buckets2 = new HashMap<String, Integer>();
        buckets2.put("61", 1);
        buckets2.put("41", 2);
        assertThat(redisson.getBuckets().trySet(buckets2)).isTrue();

        RBucket<Object> r1 = redisson.getBucket("61");
        assertThat(r1.get()).isEqualTo(1);

        RBucket<Object> r3 = redisson.getBucket("41");
        assertThat(r3.get()).isEqualTo(2);
    }

    
}
