package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.bucket.SetArgs;
import org.redisson.config.NameMapper;
import org.redisson.api.RBucket;
import org.redisson.api.RBuckets;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonBucketsTest extends RedisDockerTest {

    @Test
    public void testNameMapper() {
        Config config = redisson.getConfig();
        config.useSingleServer()
                .setNameMapper(new NameMapper() {
                    @Override
                    public String map(String name) {
                        return "test::" + name;
                    }

                    @Override
                    public String unmap(String name) {
                        return name.replace("test::", "");
                    }
                });

        RedissonClient redisson = Redisson.create(config);
        RBuckets buckets = redisson.getBuckets();
        Map<String, String> kvMap = new HashMap<>();
        kvMap.put("k1", "v1");
        kvMap.put("k2", "v2");
        buckets.set(kvMap);

        Map<String, Object> res = buckets.get("k1", "k2");
        assertThat(res.get("k1")).isEqualTo("v1");
        assertThat(res.get("k2")).isEqualTo("v2");
    }

    @Test
    public void testGetInClusterNameMapper() {
        testInCluster(client -> {
            Config config = client.getConfig();
            config.useClusterServers()
                    .setReadMode(ReadMode.MASTER)
                    .setNameMapper(new NameMapper() {
                        @Override
                        public String map(String name) {
                            return "test::" + name;
                        }

                        @Override
                        public String unmap(String name) {
                            return name.replace("test::", "");
                        }
                    });

            RedissonClient redisson = Redisson.create(config);

            int size = 10000;
            Map<String, Integer> map = new HashMap<>();
            for (int i = 0; i < 10; i++) {
                map.put("test" + i, i);
            }
            for (int i = 10; i < size; i++) {
                map.put("test" + i + "{" + (i % 100) + "}", i);
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
        });
    }

    @Test
    public void testGetInCluster() {
        testInCluster(client -> {
            Config config = client.getConfig();
            config.useClusterServers()
                    .setReadMode(ReadMode.MASTER);
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
        });
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

    @Test
    public void testSetIfAllKeysExist() {
        redisson.getBucket("12").set("341");
        redisson.getBucket("23").set("234");

        Map<String, Integer> buckets = new HashMap<>();
        buckets.put("12", 1);
        buckets.put("41", 2);
        assertThat(redisson.getBuckets().setIfAllKeysExist(SetArgs.entries(buckets))).isFalse();

        redisson.getBucket("41").set("123");
        Instant s = Instant.now().plusSeconds(10);
        assertThat(redisson.getBuckets().setIfAllKeysExist(SetArgs.entries(buckets)
                .expireAt(s))).isTrue();

        Map<String, Integer> buckets2 = new HashMap<>();
        buckets2.put("41", 3);
        buckets2.put("23", 4);
        assertThat(redisson.getBuckets().setIfAllKeysExist(SetArgs.entries(buckets2)
                .timeToLive(Duration.ofSeconds(2)))).isTrue();

        assertThat(redisson.getBucket("41").remainTimeToLive()).isLessThan(2010);
        assertThat(redisson.getBucket("12").getExpireTime()).isEqualTo(s.toEpochMilli());

        assertThat(redisson.getBucket("41").get()).isEqualTo(3);
        assertThat(redisson.getBucket("23").get()).isEqualTo(4);

        buckets2.put("12", 5);
        assertThat(redisson.getBuckets().setIfAllKeysExist(SetArgs.entries(buckets2)
                .keepTTL())).isTrue();
        assertThat(redisson.getBucket("12").get()).isEqualTo(5);

    }

    
}
