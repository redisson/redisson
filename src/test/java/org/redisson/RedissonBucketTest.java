package org.redisson;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RBucket;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class RedissonBucketTest extends BaseTest {

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
        bucket.delete();
        Assert.assertNull(bucket.get());
    }


    @Test
    public void testSetExist() {
        RBucket<String> bucket = redisson.getBucket("test");
        Assert.assertNull(bucket.get());
        String value = "somevalue";
        bucket.set(value);
        Assert.assertEquals(value, bucket.get());

        Assert.assertTrue(bucket.exists());
    }

    @Test
    public void testSetDeleteNotExist() {
        RBucket<String> bucket = redisson.getBucket("test");
        Assert.assertNull(bucket.get());
        String value = "somevalue";
        bucket.set(value);
        Assert.assertEquals(value, bucket.get());

        Assert.assertTrue(bucket.exists());

        bucket.delete();

        Assert.assertFalse(bucket.exists());
    }

    @Test
    public void testGetPattern() {
        Collection<String> names = Arrays.asList("test:testGetPattern:one", "test:testGetPattern:two");
        Collection<String> vals = Arrays.asList("one-val", "two-val");
        redisson.getBucket("test:testGetPattern:one").set("one-val");
        redisson.getBucket("test:testGetPattern:two").set("two-val");
        List<RBucket<String>> buckets = redisson.getBuckets("test:testGetPattern:*");
        Assert.assertEquals(2, buckets.size());
        Assert.assertTrue(names.contains(buckets.get(0).getName()));
        Assert.assertTrue(names.contains(buckets.get(1).getName()));
        Assert.assertTrue(vals.contains(buckets.get(0).get()));
        Assert.assertTrue(vals.contains(buckets.get(1).get()));
        for (RBucket<String> bucket : buckets) {
            bucket.delete();
        }
    }
}
