package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.RedisRunner.KEYSPACE_EVENTS_OPTIONS;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.DeletedObjectListener;
import org.redisson.api.ExpiredObjectListener;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.SetObjectListener;
import org.redisson.config.Config;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonBucketTest extends BaseTest {

    @Test
    public void testGetAndClearExpire() {
        Assumptions.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("6.2.0") > 0);

        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1, 1, TimeUnit.SECONDS);
        assertThat(al.getAndClearExpire()).isEqualTo(1);
        assertThat(al.remainTimeToLive()).isEqualTo(-1);
    }

    @Test
    public void testGetAndExpire() throws InterruptedException {
        Assumptions.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("6.2.0") > 0);

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
        Assumptions.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("7.0.0") > 0);

        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1);
        assertThat(al.getExpireTime()).isEqualTo(-1);
        Instant s = Instant.now().plusSeconds(10);
        al.expire(s);
        assertThat(al.getExpireTime()).isEqualTo(s.toEpochMilli());
    }

    @Test
    public void testKeepTTL() {
        Assumptions.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("6.0.0") > 0);

        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1234, 10, TimeUnit.SECONDS);
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
    public void testDeletedListener() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisProcess instance = new RedisRunner()
                .nosave()
                .randomPort()
                .randomDir()
                .notifyKeyspaceEvents( 
                                    KEYSPACE_EVENTS_OPTIONS.E,
                                    KEYSPACE_EVENTS_OPTIONS.g)
                .run();
        
        Config config = new Config();
        config.useSingleServer().setAddress(instance.getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);
        
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
        
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        
        redisson.shutdown();
        instance.stop();
    }
    
    @Test
    public void testSetListener() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisProcess instance = new RedisRunner()
                .nosave()
                .randomPort()
                .randomDir()
                .notifyKeyspaceEvents(
                                    KEYSPACE_EVENTS_OPTIONS.E,
                                    KEYSPACE_EVENTS_OPTIONS.$)
                .run();

        Config config = new Config();
        config.useSingleServer().setAddress(instance.getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);

        RBucket<Integer> al = redisson.getBucket("test");
        CountDownLatch latch = new CountDownLatch(1);
        al.addListener(new SetObjectListener() {
            @Override
            public void onSet(String name) {
                latch.countDown();
            }
        });
        al.set(1);

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();

        redisson.shutdown();
        instance.stop();    }

    
    @Test
    public void testExpiredListener() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisProcess instance = new RedisRunner()
                .nosave()
                .randomPort()
                .randomDir()
                .notifyKeyspaceEvents( 
                                    KEYSPACE_EVENTS_OPTIONS.E,
                                    KEYSPACE_EVENTS_OPTIONS.x)
                .run();
        
        Config config = new Config();
        config.useSingleServer().setAddress(instance.getRedisServerAddressAndPort());
        RedissonClient redisson = Redisson.create(config);
        
        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1, 3, TimeUnit.SECONDS);
        CountDownLatch latch = new CountDownLatch(1);
        al.addListener(new ExpiredObjectListener() {
            @Override
            public void onExpired(String name) {
                latch.countDown();
            }
        });
        
        assertThat(latch.await(4, TimeUnit.SECONDS)).isTrue();
        
        redisson.shutdown();
        instance.stop();
    }
    
    @Test
    public void testSizeInMemory() {
        Assumptions.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("4.0.0") > 0);
        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1234);
        assertThat(al.sizeInMemory()).isEqualTo(51);
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
        assertThat(r1.getAndSet("value2", 500, TimeUnit.MILLISECONDS)).isEqualTo("value1");
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
        assertThat(r2.setIfExists("2", 1, TimeUnit.SECONDS)).isTrue();
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
        bucket.set("someValue", 1, TimeUnit.SECONDS);

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
    
    @Test
    public void testMigrate() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisProcess runner = new RedisRunner()
                .appendonly(true)
                .randomDir()
                .randomPort()
                .run();
        
        RBucket<String> bucket = redisson.getBucket("test");
        bucket.set("someValue");
        
        bucket.migrate(runner.getRedisServerBindAddress(), runner.getRedisServerPort(), 0, 5000);
        
        Config config = new Config();
        config.useSingleServer().setAddress(runner.getRedisServerAddressAndPort());
        RedissonClient r = Redisson.create(config);
        
        RBucket<String> bucket2 = r.getBucket("test");
        assertThat(bucket2.get()).isEqualTo("someValue");
        assertThat(bucket.isExists()).isFalse();
        
        runner.stop();
    }

    @Test
    public void testCopy() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisProcess runner = new RedisRunner()
                .appendonly(true)
                .randomDir()
                .randomPort()
                .run();
        
        RBucket<String> bucket = redisson.getBucket("test");
        bucket.set("someValue");
        
        bucket.copy(runner.getRedisServerBindAddress(), runner.getRedisServerPort(), 0, 5000);
        
        Config config = new Config();
        config.useSingleServer().setAddress(runner.getRedisServerAddressAndPort());
        RedissonClient r = Redisson.create(config);
        
        RBucket<String> bucket2 = r.getBucket("test");
        assertThat(bucket2.get()).isEqualTo("someValue");
        assertThat(bucket.get()).isEqualTo("someValue");
        
        runner.stop();
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
        bucket.set(null, 1, TimeUnit.DAYS);
        
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

}
