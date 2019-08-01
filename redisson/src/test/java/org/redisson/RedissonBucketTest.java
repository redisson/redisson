package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.redisson.RedisRunner.FailedToStartRedisException;
import org.redisson.RedisRunner.KEYSPACE_EVENTS_OPTIONS;
import org.redisson.RedisRunner.RedisProcess;
import org.redisson.api.DeletedObjectListener;
import org.redisson.api.ExpiredObjectListener;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public class RedissonBucketTest extends BaseTest {

    @Test
    public void testDeletedListener() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisProcess instance = new RedisRunner()
                .nosave()
                .port(6379)
                .randomDir()
                .notifyKeyspaceEvents( 
                                    KEYSPACE_EVENTS_OPTIONS.E,
                                    KEYSPACE_EVENTS_OPTIONS.g)
                .run();
        
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
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
    public void testExpiredListener() throws FailedToStartRedisException, IOException, InterruptedException {
        RedisProcess instance = new RedisRunner()
                .nosave()
                .port(6379)
                .randomDir()
                .notifyKeyspaceEvents( 
                                    KEYSPACE_EVENTS_OPTIONS.E,
                                    KEYSPACE_EVENTS_OPTIONS.x)
                .run();
        
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
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
        Assume.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("4.0.0") > 0);
        RBucket<Integer> al = redisson.getBucket("test");
        al.set(1234);
        assertThat(al.sizeInMemory()).isEqualTo(55);
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
        assertThat(bucket.size()).isEqualTo(6);
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
    public void testTrySet() {
        RBucket<String> r1 = redisson.getBucket("testTrySet");
        assertThat(r1.trySet("3")).isTrue();
        assertThat(r1.trySet("4")).isFalse();
        assertThat(r1.get()).isEqualTo("3");
    }

    @Test
    public void testTrySetTTL() throws InterruptedException {
        RBucket<String> r1 = redisson.getBucket("testTrySetTTL");
        assertThat(r1.trySet("3", 500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(r1.trySet("4", 500, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(r1.get()).isEqualTo("3");

        Thread.sleep(1000);

        assertThat(r1.get()).isNull();
    }

    @Test
    public void testExpire() throws InterruptedException {
        RBucket<String> bucket = redisson.getBucket("test1");
        bucket.set("someValue", 1, TimeUnit.SECONDS);

        Thread.sleep(1100);

        Assert.assertNull(bucket.get());
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
        Assert.assertTrue(bucket.renamenx("test1"));
        bucket.set("value1");
        RBucket<String> oldBucket = redisson.getBucket("test");
        Assert.assertNull(oldBucket.get());
        RBucket<String> newBucket = redisson.getBucket("test1");
        Assert.assertEquals("value1", newBucket.get());
        Assert.assertFalse(newBucket.renamenx("test2"));
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
        Assert.assertNull(oldBucket.get());
        RBucket<String> newBucket = redisson.getBucket("test1");
        Assert.assertEquals("value1", newBucket.get());
    }

    @Test
    public void testSetGet() {
        RBucket<String> bucket = redisson.getBucket("test");
        Assert.assertNull(bucket.get());
        String value = "somevalue";
        bucket.set(value);
        Assert.assertEquals(value, bucket.get());
        
        bucket.set(null);
        bucket.set(null, 1, TimeUnit.DAYS);
        
        assertThat(bucket.isExists()).isFalse();
    }

    @Test
    public void testSetDelete() {
        RBucket<String> bucket = redisson.getBucket("test");
        String value = "somevalue";
        bucket.set(value);
        Assert.assertEquals(value, bucket.get());
        Assert.assertTrue(bucket.delete());
        Assert.assertNull(bucket.get());
        Assert.assertFalse(bucket.delete());
    }


    @Test
    public void testSetExist() {
        RBucket<String> bucket = redisson.getBucket("test");
        Assert.assertNull(bucket.get());
        String value = "somevalue";
        bucket.set(value);
        Assert.assertEquals(value, bucket.get());

        Assert.assertTrue(bucket.isExists());
    }

    @Test
    public void testSetDeleteNotExist() {
        RBucket<String> bucket = redisson.getBucket("test");
        Assert.assertNull(bucket.get());
        String value = "somevalue";
        bucket.set(value);
        Assert.assertEquals(value, bucket.get());

        Assert.assertTrue(bucket.isExists());

        bucket.delete();

        Assert.assertFalse(bucket.isExists());
    }

}
