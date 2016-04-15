package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RBucket;

public class RedissonBucketTest extends BaseTest {

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
    public void testRenamenx() {
        RBucket<String> bucket = redisson.getBucket("test");
        bucket.set("someValue");
        RBucket<String> bucket2 = redisson.getBucket("test2");
        bucket2.set("someValue2");
        Assert.assertTrue(bucket.renamenx("test1"));
        RBucket<String> oldBucket = redisson.getBucket("test");
        Assert.assertNull(oldBucket.get());
        RBucket<String> newBucket = redisson.getBucket("test1");
        Assert.assertEquals("someValue", newBucket.get());
        Assert.assertFalse(newBucket.renamenx("test2"));
    }

    @Test
    public void testRename() {
        RBucket<String> bucket = redisson.getBucket("test");
        bucket.set("someValue");
        bucket.rename("test1");
        RBucket<String> oldBucket = redisson.getBucket("test");
        Assert.assertNull(oldBucket.get());
        RBucket<String> newBucket = redisson.getBucket("test1");
        Assert.assertEquals("someValue", newBucket.get());
    }

    @Test
    public void testSetGet() {
        RBucket<String> bucket = redisson.getBucket("test");
        Assert.assertNull(bucket.get());
        String value = "somevalue";
        bucket.set(value);
        Assert.assertEquals(value, bucket.get());
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
