package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RBucket;

public class RedissonBucketTest extends AbstractBaseTest {
    
    @Test
    public void testSize() {
        RBucket<String> bucket = redissonRule.getSharedClient().getBucket("testCompareAndSet");
        assertThat(bucket.size()).isZero();
        bucket.set("1234");
        // json adds quotes
        assertThat(bucket.size()).isEqualTo(6);
    }
    
    @Test
    public void testCompareAndSet() {
        RBucket<List<String>> r1 = redissonRule.getSharedClient().getBucket("testCompareAndSet");
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
    public void testGetAndSet() {
        RBucket<List<String>> r1 = redissonRule.getSharedClient().getBucket("testGetAndSet");
        assertThat(r1.getAndSet(Arrays.asList("81"))).isNull();
        assertThat(r1.getAndSet(Arrays.asList("1"))).isEqualTo(Arrays.asList("81"));
        assertThat(r1.get()).isEqualTo(Arrays.asList("1"));

        assertThat(r1.getAndSet(null)).isEqualTo(Arrays.asList("1"));
        assertThat(r1.get()).isNull();
        assertThat(r1.isExists()).isFalse();
    }

    @Test
    public void testTrySet() {
        RBucket<String> r1 = redissonRule.getSharedClient().getBucket("testTrySet");
        assertThat(r1.trySet("3")).isTrue();
        assertThat(r1.trySet("4")).isFalse();
        assertThat(r1.get()).isEqualTo("3");
    }

    @Test
    public void testTrySetTTL() throws InterruptedException {
        RBucket<String> r1 = redissonRule.getSharedClient().getBucket("testTrySetTTL");
        assertThat(r1.trySet("3", 500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(r1.trySet("4", 500, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(r1.get()).isEqualTo("3");

        Thread.sleep(1000);

        assertThat(r1.get()).isNull();
    }

    @Test
    public void testExpire() throws InterruptedException {
        RBucket<String> bucket = redissonRule.getSharedClient().getBucket("test1");
        bucket.set("someValue", 1, TimeUnit.SECONDS);

        Thread.sleep(1100);

        Assert.assertNull(bucket.get());
    }

    @Test
    public void testRenamenx() {
        RBucket<String> bucket = redissonRule.getSharedClient().getBucket("test");
        bucket.set("someValue");
        RBucket<String> bucket2 = redissonRule.getSharedClient().getBucket("test2");
        bucket2.set("someValue2");
        Assert.assertTrue(bucket.renamenx("test1"));
        RBucket<String> oldBucket = redissonRule.getSharedClient().getBucket("test");
        Assert.assertNull(oldBucket.get());
        RBucket<String> newBucket = redissonRule.getSharedClient().getBucket("test1");
        Assert.assertEquals("someValue", newBucket.get());
        Assert.assertFalse(newBucket.renamenx("test2"));
    }

    @Test
    public void testRename() {
        RBucket<String> bucket = redissonRule.getSharedClient().getBucket("test");
        bucket.set("someValue");
        bucket.rename("test1");
        RBucket<String> oldBucket = redissonRule.getSharedClient().getBucket("test");
        Assert.assertNull(oldBucket.get());
        RBucket<String> newBucket = redissonRule.getSharedClient().getBucket("test1");
        Assert.assertEquals("someValue", newBucket.get());
    }

    @Test
    public void testSetGet() {
        RBucket<String> bucket = redissonRule.getSharedClient().getBucket("test");
        Assert.assertNull(bucket.get());
        String value = "somevalue";
        bucket.set(value);
        Assert.assertEquals(value, bucket.get());
    }

    @Test
    public void testSetDelete() {
        RBucket<String> bucket = redissonRule.getSharedClient().getBucket("test");
        String value = "somevalue";
        bucket.set(value);
        Assert.assertEquals(value, bucket.get());
        Assert.assertTrue(bucket.delete());
        Assert.assertNull(bucket.get());
        Assert.assertFalse(bucket.delete());
    }


    @Test
    public void testSetExist() {
        RBucket<String> bucket = redissonRule.getSharedClient().getBucket("test");
        Assert.assertNull(bucket.get());
        String value = "somevalue";
        bucket.set(value);
        Assert.assertEquals(value, bucket.get());

        Assert.assertTrue(bucket.isExists());
    }

    @Test
    public void testSetDeleteNotExist() {
        RBucket<String> bucket = redissonRule.getSharedClient().getBucket("test");
        Assert.assertNull(bucket.get());
        String value = "somevalue";
        bucket.set(value);
        Assert.assertEquals(value, bucket.get());

        Assert.assertTrue(bucket.isExists());

        bucket.delete();

        Assert.assertFalse(bucket.isExists());
    }

}
