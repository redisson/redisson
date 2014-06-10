package org.redisson;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RBucket;

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

}
