package org.redisson;

import net.bytebuddy.utility.RandomString;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.api.*;
import org.redisson.api.bucket.CompareAndDeleteArgs;
import org.redisson.api.listener.SetObjectListener;
import org.redisson.api.listener.TrackingListener;
import org.redisson.api.options.PlainOptions;
import org.redisson.client.RedisResponseTimeoutException;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonBucketTest extends RedisDockerTest {

    public static Iterable<Object[]> trackingData() {
        return Arrays.asList(new Object[][] {
                {Arrays.asList(ReadMode.MASTER, SubscriptionMode.MASTER)},
                {Arrays.asList(ReadMode.SLAVE, SubscriptionMode.MASTER)},
                {Arrays.asList(ReadMode.SLAVE, SubscriptionMode.SLAVE)},
                {Arrays.asList(ReadMode.MASTER_SLAVE, SubscriptionMode.SLAVE)},
                {Arrays.asList(ReadMode.MASTER_SLAVE, SubscriptionMode.MASTER)}
        });
    }

    @Test
    public void testTracking() {
        Config c = redisson.getConfig();
        c.setProtocol(Protocol.RESP3);

        RedissonClient rs = Redisson.create(c);
        RBucket<String> b = rs.getBucket("test");
        AtomicInteger ref = new AtomicInteger();
        int id = b.addListener(new TrackingListener() {
            @Override
            public void onChange(String name) {
                assertThat(name).isEqualTo(b.getName());
                ref.incrementAndGet();
            }
        });
        int id3 = b.addListener(new TrackingListener() {
            @Override
            public void onChange(String name) {
                assertThat(name).isEqualTo(b.getName());
                ref.incrementAndGet();
            }
        });
        String r = b.get();
        assertThat(ref.get()).isZero();

        RBucket<String> b2 = rs.getBucket("test1");
        AtomicReference<String> ref2 = new AtomicReference<>();
        int id2 = b2.addListener(new TrackingListener() {
            @Override
            public void onChange(String name) {
                System.out.println("name2 " + name);
                ref2.set(name);
            }
        });
        b2.get();
        assertThat(ref2.get()).isNull();


        RBucket<String> bb = rs.getBucket("test");
        bb.set("1234");
        RBucket<String> bb2 = rs.getBucket("test1");
        bb2.set("7584");

        Awaitility.waitAtMost(Duration.ofMillis(500)).untilAsserted(() -> {
            assertThat(ref.getAndSet(0)).isEqualTo(2);
            assertThat(ref2.getAndSet(null)).isEqualTo("test1");
        });

        b.removeListener(id);
        b.removeListener(id3);

        String r2 = b.get();
        bb.set("6345");

        Awaitility.waitAtMost(Duration.ofMillis(500)).untilAsserted(() -> {
            assertThat(ref.get()).isZero();
            assertThat(ref2.get()).isNull();
        });

        String r3 = b2.get();
        bb2.set("6345");

        Awaitility.waitAtMost(Duration.ofMillis(500)).untilAsserted(() -> {
            assertThat(ref.get()).isZero();
            assertThat(ref2.getAndSet(null)).isEqualTo("test1");
        });

        rs.shutdown();
    }

    @Test
    public void testRenameInCluster2() {
        testInCluster(rc -> {
            RBucket<String> b = rc.getBucket("{abc}alpha");
            b.set("123");

            RBatch rb = rc.createBatch(BatchOptions.defaults().sync(1, Duration.ofSeconds(1)));
            RBucketAsync<Object> b2 = rb.getBucket("{abc}alpha");
            b2.renameAsync("{abc}beta");
            rb.execute();

            RBucket<String> bs = rc.getBucket("{abc}beta");
            assertThat(bs.get()).isEqualTo("123");

            assertThat(rc.getKeys().count()).isEqualTo(1);
        });
    }

    @Test
    public void testRenameInCluster() {
        testInCluster(rc -> {
            RBucket<String> b = rc.getBucket("test1234");
            b.set("123");

            b.rename("test347834");
            assertThat(b.getName()).isEqualTo("test347834");

            RBucket<String> b2 = rc.getBucket("12324");
            b2.set("111");

            b.rename("12324");

            RBucket<String> bs = rc.getBucket(b.getName());
            assertThat(bs.get()).isEqualTo("123");

            assertThat(rc.getKeys().count()).isEqualTo(1);
        });
    }

    @ParameterizedTest
    @MethodSource("trackingData")
    public void testTrackingCluster(List<Object> params) {
        testInCluster(rc -> {
            Config c = rc.getConfig();
            c.setProtocol(Protocol.RESP3);
            c.useClusterServers()
                    .setReadMode((ReadMode) params.get(0))
                    .setSubscriptionMode((SubscriptionMode) params.get(1));

            RedissonClient redissonClient = Redisson.create(c);
            RBucket<String> b = redissonClient.getBucket("test");
            AtomicReference<String> ref = new AtomicReference<>();
            int id = b.addListener(new TrackingListener() {
                @Override
                public void onChange(String name) {
                    ref.set(name);
                }
            });
            String r = b.get();
            assertThat(ref.get()).isNull();

            RBucket<String> bb = redissonClient.getBucket("test");
            bb.set("1234");

            Awaitility.waitAtMost(Duration.ofMillis(2000)).untilAsserted(() -> {
                assertThat(ref.getAndSet(null)).isEqualTo("test");
            });
            b.removeListener(id);

            String r2 = b.get();
            bb.set("6345");

            Awaitility.waitAtMost(Duration.ofMillis(500)).untilAsserted(() -> {
                assertThat(ref.get()).isNull();
            });

            redissonClient.shutdown();
        });
    }

//    @Test
    public void testTrackingCluster() {
        for (int i = 0; i < 20; i++) {
        testInCluster(rc -> {
                Config c = rc.getConfig();
                c.setProtocol(Protocol.RESP3);
                c.useClusterServers()
                        .setPingConnectionInterval(0)
                        .setReadMode(ReadMode.SLAVE)
                        .setSubscriptionMode(SubscriptionMode.MASTER);

                RedissonClient redissonClient = Redisson.create(c);
                RBucket<String> b = redissonClient.getBucket("test");
                AtomicReference<String> ref = new AtomicReference<>();
                int id = b.addListener(new TrackingListener() {
                    @Override
                    public void onChange(String name) {
                        ref.set(name);
                    }
                });
                String r = b.get();
                assertThat(ref.get()).isNull();

                RBucket<String> bb = redissonClient.getBucket("test");
                bb.set("1234");

                Awaitility.waitAtMost(Duration.ofMillis(1000)).untilAsserted(() -> {
                    assertThat(ref.getAndSet(null)).isEqualTo("test");
                });
                b.removeListener(id);

                String r2 = b.get();
                bb.set("6345");

                Awaitility.waitAtMost(Duration.ofMillis(1000)).untilAsserted(() -> {
                    assertThat(ref.get()).isNull();
                });

                redissonClient.shutdown();
        });
            System.out.println("iteration " + i);
        }
    }

    @Test
    public void testOptions() {
        Config c = createConfig();
        c.useSingleServer().setTimeout(5);

        RedissonClient r = Redisson.create(c);

        String val = RandomString.make(1048 * 40000);
        Assertions.assertThrows(RedisResponseTimeoutException.class, () -> {
            RBucket<String> al = r.getBucket("test");
            al.set(val);
        });

        RBucket<String> al = r.getBucket(PlainOptions.name("test")
                                                    .timeout(Duration.ofSeconds(1)));
        al.set(val);

        r.shutdown();
    }

    @Test
    public void testGetAndClearExpire() {
        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1, Duration.ofSeconds(1));
        assertThat(al.getAndClearExpire()).isEqualTo(1);
        assertThat(al.remainTimeToLive()).isEqualTo(-1);
    }

    @Test
    public void testGetAndExpire() throws InterruptedException {
        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1);
        assertThat(al.getAndExpire(Duration.ofSeconds(1))).isEqualTo(1);
        Thread.sleep(500);
        assertThat(al.get()).isEqualTo(1);
        Thread.sleep(600);
        assertThat(al.get()).isNull();

        al.set(2);
        assertThat(al.getAndExpire(Instant.now().plusSeconds(1))).isEqualTo(2);
        Thread.sleep(500);
        assertThat(al.get()).isEqualTo(2);
        Thread.sleep(600);
        assertThat(al.get()).isNull();
    }

    @Test
    public void testExpireTime() {
        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1);
        assertThat(al.getExpireTime()).isEqualTo(-1);
        Instant s = Instant.now().plusSeconds(10);
        al.expire(s);
        assertThat(al.getExpireTime()).isEqualTo(s.toEpochMilli());
    }

    @Test
    public void testKeepTTL() {
        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1234, Duration.ofSeconds(10));
        al.setAndKeepTTL(222);
        assertThat(al.remainTimeToLive()).isGreaterThan(9900);
        assertThat(al.get()).isEqualTo(222);
    }

    @Test
    public void testIdleTime() throws InterruptedException {
        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1234);

        Thread.sleep(5000);

        assertThat(al.getIdleTime()).isBetween(4L, 6L);
    }

    @Test
    public void testReferenceCount() {
        RBucket<Integer> al = redisson.getBucket("test");
        assertThat(al.getReferenceCount()).isEqualTo(0);

        al.set(10000);
        assertThat(al.getReferenceCount()).isEqualTo(1);
    }

    @Test
    public void testAccessFrequency() {
        testWithParams(redisson -> {
            RBucket<Integer> al = redisson.getBucket("test");
            assertThat(al.getAccessFrequency()).isEqualTo(0);
            al.set(10000);
            al.get();
            assertThat(al.getAccessFrequency()).isGreaterThan(1);
        }, MAXMEMORY_POLICY, "allkeys-lfu");
    }

    @Test
    public void testInternalEncoding() {
        RBucket<Integer> al = redisson.getBucket("test");
        assertThat(al.getInternalEncoding()).isEqualTo(ObjectEncoding.NULL);
        al.set(123);
        assertThat(al.getInternalEncoding()).isEqualTo(ObjectEncoding.EMBSTR);

        RList<String> list=redisson.getList("list");
        list.addAll(Arrays.asList("a","b","c"));
        assertThat(list.getInternalEncoding()).isEqualTo(ObjectEncoding.LISTPACK);

        RMap<Integer, String> map = redisson.getMap("map");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");
        assertThat(map.getInternalEncoding()).isEqualTo(ObjectEncoding.LISTPACK);

        RSet<Integer> set = redisson.getSet("set", IntegerCodec.INSTANCE);
        set.add(1);
        set.add(2);
        set.add(3);
        assertThat(set.getInternalEncoding()).isEqualTo(ObjectEncoding.INTSET);

        RSortedSet<Long> sortedSet = redisson.getSortedSet("sortedSet", LongCodec.INSTANCE);
        sortedSet.add(2L);
        sortedSet.add(0L);
        sortedSet.add(1L);
        sortedSet.add(5L);
        assertThat(sortedSet.getInternalEncoding()).isEqualTo(ObjectEncoding.LISTPACK);

    }

    @Test
    public void testDeletedListener() {
        testWithParams(redisson -> {
            RBucket<Integer> al = redisson.getBucket("test");
            al.set(1);
            CountDownLatch latch = new CountDownLatch(1);
            al.addListener(new DeletedObjectListener() {
                @Override
                public void onDeleted(String name) {
                    latch.countDown();
                }
            });
            al.delete();

            try {
                assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, NOTIFY_KEYSPACE_EVENTS, "Eg");
    }

    @Test
    public void testRemoveListenerAsync() {
        testWithParams(redisson -> {
            RBucket<Integer> al = redisson.getBucket("test");
            int id = al.addListenerAsync(new SetObjectListener() {
                @Override
                public void onSet(String name) {
                }
            }).toCompletableFuture().join();
            al.removeListenerAsync(id).toCompletableFuture().join();
        }, NOTIFY_KEYSPACE_EVENTS, "E$");
    }

    @Test
    public void testSetListener() {
        testWithParams(redisson -> {
            RBucket<Integer> al = redisson.getBucket("test");
            CountDownLatch latch = new CountDownLatch(1);
            al.addListener(new SetObjectListener() {
                @Override
                public void onSet(String name) {
                    latch.countDown();
                }
            });
            al.set(1);

            try {
                assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, NOTIFY_KEYSPACE_EVENTS, "E$");
    }
    
    @Test
    public void testExpiredListener() {
        testWithParams(redisson -> {
            RBucket<Integer> al = redisson.getBucket("test");
            al.set(1, Duration.ofSeconds(3));
            CountDownLatch latch = new CountDownLatch(1);
            al.addListener(new ExpiredObjectListener() {
                @Override
                public void onExpired(String name) {
                    latch.countDown();
                }
            });

            try {
                assertThat(latch.await(4, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, NOTIFY_KEYSPACE_EVENTS, "Ex");
    }
    
    @Test
    public void testSizeInMemory() {
        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1234);
        assertThat(al.sizeInMemory()).isEqualTo(32);
    }
    
    @Test
    public void testDumpAndRestore() {
        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1234);
        
        byte[] state = al.dump();
        al.delete();
        
        al.restore(state);
        assertThat(al.get()).isEqualTo(1234);
        
        RBucket<Integer> bucket = redisson.getBucket("test2");
        bucket.set(300);
        bucket.restoreAndReplace(state);
        assertThat(bucket.get()).isEqualTo(1234);
    }
    
    @Test
    public void testDumpAndRestoreTTL() {
        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1234);
        
        byte[] state = al.dump();
        al.delete();
        
        al.restore(state, 10, TimeUnit.SECONDS);
        assertThat(al.get()).isEqualTo(1234);
        assertThat(al.remainTimeToLive()).isBetween(9500L, 10000L);
        
        RBucket<Integer> bucket = redisson.getBucket("test2");
        bucket.set(300);
        bucket.restoreAndReplace(state, 10, TimeUnit.SECONDS);
        assertThat(bucket.get()).isEqualTo(1234);
    }
    
    @Test
    public void testGetAndDelete() {
        RBucket<Integer> al = redisson.getBucket("test");
        al.set(10);
        assertThat(al.getAndDelete()).isEqualTo(10);
        assertThat(al.isExists()).isFalse();
        assertThat(al.getAndDelete()).isNull();
    }
    
    @Test
    public void testSize() {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSet");
        assertThat(bucket.size()).isZero();
        bucket.set("1234");
        // json adds quotes
        assertThat(bucket.size()).isEqualTo(5);
    }
    
    @Test
    public void testCompareAndSet() {
        RBucket<List<String>> r1 = redisson.getBucket("testCompareAndSet");
        assertThat(r1.compareAndSet(null, Arrays.asList("81"))).isTrue();
        assertThat(r1.compareAndSet(null, Arrays.asList("12"))).isFalse();

        assertThat(r1.compareAndSet(Arrays.asList("81"), Arrays.asList("0"))).isTrue();
        assertThat(r1.get()).isEqualTo(Arrays.asList("0"));

        assertThat(r1.compareAndSet(Arrays.asList("1"), Arrays.asList("2"))).isFalse();
        assertThat(r1.get()).isEqualTo(Arrays.asList("0"));

        assertThat(r1.compareAndSet(Arrays.asList("0"), null)).isTrue();
        assertThat(r1.get()).isNull();
        assertThat(r1.isExists()).isFalse();
    }

    @Test
    public void testGetAndSetTTL() throws InterruptedException {
        RBucket<String> r1 = redisson.getBucket("getAndSetTTL");
        r1.set("value1");
        assertThat(r1.getAndSet("value2", Duration.ofMillis(500))).isEqualTo("value1");
        assertThat(r1.get()).isEqualTo("value2");

        Thread.sleep(1000);

        assertThat(r1.get()).isNull();
    }
    
    @Test
    public void testGetAndSet() {
        RBucket<List<String>> r1 = redisson.getBucket("testGetAndSet");
        assertThat(r1.getAndSet(Arrays.asList("81"))).isNull();
        assertThat(r1.getAndSet(Arrays.asList("1"))).isEqualTo(Arrays.asList("81"));
        assertThat(r1.get()).isEqualTo(Arrays.asList("1"));

        assertThat(r1.getAndSet(null)).isEqualTo(Arrays.asList("1"));
        assertThat(r1.get()).isNull();
        assertThat(r1.isExists()).isFalse();
    }

    @Test
    public void testSetIfExists() throws InterruptedException {
        RBucket<String> r1 = redisson.getBucket("test1");
        assertThat(r1.setIfExists("0")).isFalse();
        assertThat(r1.isExists()).isFalse();
        r1.set("1");
        assertThat(r1.setIfExists("2")).isTrue();
        assertThat(r1.get()).isEqualTo("2");

        RBucket<String> r2 = redisson.getBucket("test2");
        r2.set("1");
        assertThat(r2.setIfExists("2", Duration.ofSeconds(1))).isTrue();
        assertThat(r2.get()).isEqualTo("2");
        Thread.sleep(1000);
        assertThat(r2.isExists()).isFalse();
    }

    @Test
    public void testTrySet() {
        RBucket<String> r1 = redisson.getBucket("testTrySet");
        assertThat(r1.setIfAbsent("3")).isTrue();
        assertThat(r1.setIfAbsent("4")).isFalse();
        assertThat(r1.get()).isEqualTo("3");
    }

    @Test
    public void testTrySetTTL() throws InterruptedException {
        RBucket<String> r1 = redisson.getBucket("testTrySetTTL");
        assertThat(r1.setIfAbsent("3", Duration.ofMillis(500))).isTrue();
        assertThat(r1.setIfAbsent("4", Duration.ofMillis(500))).isFalse();
        assertThat(r1.get()).isEqualTo("3");

        Thread.sleep(1000);

        assertThat(r1.get()).isNull();
    }

    @Test
    public void testExpire() throws InterruptedException {
        RBucket<String> bucket = redisson.getBucket("test1");
        bucket.set("someValue", Duration.ofSeconds(1));

        Thread.sleep(1100);

        Assertions.assertNull(bucket.get());
    }

    @Test
    public void testTouch() {
        RBucket<String> bucket = redisson.getBucket("test");
        bucket.set("someValue");
        assertThat(bucket.touch()).isTrue();
        
        RBucket<String> bucket2 = redisson.getBucket("test2");
        assertThat(bucket2.touch()).isFalse();
    }
    
    @Test
    public void testRenamenx() {
        RBucket<String> bucket = redisson.getBucket("test");
        bucket.set("someValue");
        RBucket<String> bucket2 = redisson.getBucket("test2");
        bucket2.set("someValue2");
        Assertions.assertTrue(bucket.renamenx("test1"));
        bucket.set("value1");
        RBucket<String> oldBucket = redisson.getBucket("test");
        Assertions.assertNull(oldBucket.get());
        RBucket<String> newBucket = redisson.getBucket("test1");
        Assertions.assertEquals("value1", newBucket.get());
        Assertions.assertFalse(newBucket.renamenx("test2"));
    }

    private void testTwoInstances(BiConsumer<RedissonClient, RedissonClient> consumer) {
        Network network = Network.newNetwork();

        GenericContainer<?> redis = createRedis()
                                        .withNetwork(network)
                                        .withNetworkAliases("foo1");
        redis.start();

        GenericContainer<?> redis2 = createRedis()
                                        .withNetwork(network)
                                        .withNetworkAliases("foo2");
        redis2.start();

        Config config2 = createConfig(redis);
        RedissonClient r2 = Redisson.create(config2);

        Config config = createConfig(redis2);
        RedissonClient r = Redisson.create(config);

        consumer.accept(r2, r);

        r.shutdown();
        r2.shutdown();
        redis.stop();
        redis2.stop();
        network.close();
    }

    @Test
    public void testFailoverTimeout() {
        GenericContainer<?> redis = createRedis();
        redis.start();

        Config config = createConfig(redis);
        config.useSingleServer()
                .setRetryAttempts(3)
                .setRetryDelay(new ConstantDelay(Duration.ZERO));
        RedissonClient rc = Redisson.create(config);

        List<String> args = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            args.add("" + i);
        }

        redis.stop();

        Assertions.assertTimeout(Duration.ofMillis(100), () -> {
            Assertions.assertThrows(Exception.class, () -> {
                rc.getBuckets().get(args.toArray(new String[0]));
            });
        });

        Assertions.assertTimeout(Duration.ofMillis(100), () -> {
            Assertions.assertThrows(Exception.class, () -> {
                rc.getBucket("test").get();
            });
        });

        rc.shutdown();
    }

    @Test
    public void testMigrate() {
        testTwoInstances((r2, r) -> {
            RBucket<String> bucket = r2.getBucket("test");
            bucket.set("someValue");
            bucket.migrate("foo2", 6379, 0, 5000);

            RBucket<String> bucket2 = r.getBucket("test");
            assertThat(bucket2.get()).isEqualTo("someValue");
            assertThat(bucket.isExists()).isFalse();
        });
    }

    @Test
    public void testCopy()  {
        testTwoInstances((r2, r) -> {
            RBucket<String> bucket = r2.getBucket("test");
            bucket.set("someValue");
            bucket.copy("foo2", 6379, 0, 5000);

            RBucket<String> bucket2 = r.getBucket("test");
            assertThat(bucket2.get()).isEqualTo("someValue");
            assertThat(bucket.get()).isEqualTo("someValue");
        });
    }

    @Test
    public void testCopy2()  {
        RBucket<String> bucket = redisson.getBucket("test");
        bucket.set("someValue");
        bucket.copy("test2");

        RBucket<String> bucket2 = redisson.getBucket("test2");
        assertThat(bucket2.get()).isEqualTo("someValue");
    }
    
    @Test
    public void testCopy3() {
        testTwoDatabase((r1, r2) -> {
            // normal test
            RBucket<String> bucket1 = r1.getBucket("test");
            bucket1.set("someValue");
            bucket1.copy("test", 1);
            
            RBucket<String> bucket2 = r2.getBucket("test");
            assertThat(bucket2.get()).isEqualTo("someValue");
        });
    }
    
    @Test
    public void testCopy4() {
        testTwoDatabase((r1, r2) -> {
            // database1 copy to database0
            // this will cause a RedisException
            RBucket<String> bucket2 = r2.getBucket("test");
            bucket2.set("database1");
            bucket2.copy("test", 0);
            
            RBucket<String> bucket1 = r1.getBucket("test");
            assertThat(bucket1.get()).isEqualTo("database1");
        });
    }

    @Test
    public void testRename() {
        RBucket<String> bucket = redisson.getBucket("test");
        bucket.set("someValue");
        bucket.rename("test1");
        bucket.set("value1");
        RBucket<String> oldBucket = redisson.getBucket("test");
        Assertions.assertNull(oldBucket.get());
        RBucket<String> newBucket = redisson.getBucket("test1");
        Assertions.assertEquals("value1", newBucket.get());
    }

    @Test
    public void testSetGet() {
        RBucket<String> bucket = redisson.getBucket("test");
        Assertions.assertNull(bucket.get());
        String value = "somevalue";
        bucket.set(value);
        Assertions.assertEquals(value, bucket.get());
        
        bucket.set(null);
        bucket.set(null, Duration.ofDays(1));
        
        assertThat(bucket.isExists()).isFalse();
    }

    @Test
    public void testSetDelete() {
        RBucket<String> bucket = redisson.getBucket("test");
        String value = "somevalue";
        bucket.set(value);
        Assertions.assertEquals(value, bucket.get());
        Assertions.assertTrue(bucket.delete());
        Assertions.assertNull(bucket.get());
        Assertions.assertFalse(bucket.delete());
    }


    @Test
    public void testSetExist() {
        RBucket<String> bucket = redisson.getBucket("test");
        Assertions.assertNull(bucket.get());
        String value = "somevalue";
        bucket.set(value);
        Assertions.assertEquals(value, bucket.get());

        Assertions.assertTrue(bucket.isExists());
    }

    @Test
    public void testSetDeleteNotExist() {
        RBucket<String> bucket = redisson.getBucket("test");
        Assertions.assertNull(bucket.get());
        String value = "somevalue";
        bucket.set(value);
        Assertions.assertEquals(value, bucket.get());

        Assertions.assertTrue(bucket.isExists());

        bucket.delete();

        Assertions.assertFalse(bucket.isExists());
    }

    @Test
    public void testCommon() {
        RBucket<String> bucket1 = redisson.getBucket("test1", StringCodec.INSTANCE);
        bucket1.set("123243411");
        RBucket<String> bucket2 = redisson.getBucket("test2", StringCodec.INSTANCE);
        bucket2.set("aaa232fhdjal11");

        assertThat(bucket1.findCommon("test2")).isEqualTo("23211");
        assertThat(bucket1.findCommonLength("test2")).isEqualTo(5);

        bucket1.set("tteq");
        bucket2.set("dfdafsdf");

        assertThat(bucket1.findCommon("test2")).isEmpty();
        assertThat(bucket1.findCommonLength("test2")).isEqualTo(0);
    }

    @Test
    public void testGetDigest() {
        RBucket<String> bucket = redisson.getBucket("testDigest");
        bucket.set("Hello world");

        String digest = bucket.getDigest();

        assertThat(digest).isNotNull();
        assertThat(digest).matches("[0-9a-f]+").hasSize(16);

        bucket.delete();

        String emptyDigest = bucket.getDigest();
        assertThat(emptyDigest).isNull();
    }

    @Test
    public void testCompareAndDeleteExpected() {
        RBucket<String> bucket = redisson.getBucket("testCompareAndDeleteExpected");

        bucket.set("value1");
        assertThat(bucket.compareAndDelete(CompareAndDeleteArgs.expected("value1"))).isTrue();
        assertThat(bucket.isExists()).isFalse();

        bucket.set("value2");
        assertThat(bucket.compareAndDelete(CompareAndDeleteArgs.expected("wrongValue"))).isFalse();
        assertThat(bucket.isExists()).isTrue();
        assertThat(bucket.get()).isEqualTo("value2");

        bucket.delete();
        assertThat(bucket.compareAndDelete(CompareAndDeleteArgs.expected("anyValue"))).isFalse();
    }

    @Test
    public void testCompareAndDeleteUnexpected() {
        RBucket<String> bucket = redisson.getBucket("testCompareAndDeleteUnexpected");

        bucket.set("value1");
        assertThat(bucket.compareAndDelete(CompareAndDeleteArgs.unexpected("differentValue"))).isTrue();
        assertThat(bucket.isExists()).isFalse();

        bucket.set("value2");
        assertThat(bucket.compareAndDelete(CompareAndDeleteArgs.unexpected("value2"))).isFalse();
        assertThat(bucket.isExists()).isTrue();
        assertThat(bucket.get()).isEqualTo("value2");

        bucket.delete();
        assertThat(bucket.compareAndDelete(CompareAndDeleteArgs.unexpected("anyValue"))).isFalse();
    }

    @Test
    public void testCompareAndDeleteAsync() throws Exception {
        RBucket<String> bucket = redisson.getBucket("testCompareAndDeleteAsync");

        bucket.set("value1");
        assertThat(bucket.compareAndDeleteAsync(CompareAndDeleteArgs.expected("value1")).get()).isTrue();
        assertThat(bucket.isExists()).isFalse();

        bucket.set("value2");
        assertThat(bucket.compareAndDeleteAsync(CompareAndDeleteArgs.unexpected("differentValue")).get()).isTrue();
        assertThat(bucket.isExists()).isFalse();
    }

    @Test
    public void testCompareAndDeleteArgsValidation() {
        RBucket<String> bucket = redisson.getBucket("testCompareAndDeleteValidation");

        assertThatThrownBy(() -> bucket.compareAndDelete(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> CompareAndDeleteArgs.expectedDigest(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> CompareAndDeleteArgs.unexpectedDigest(null))
                .isInstanceOf(NullPointerException.class);
    }

}
