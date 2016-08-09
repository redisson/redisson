package org.redisson;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RBucketReactive;

public class RedissonBucketReactiveTest extends BaseReactiveTest {

    @Test
    public void testExpire() throws InterruptedException {
        RBucketReactive<String> bucket = redisson.getBucket("test1");
        sync(bucket.set("someValue", 1, TimeUnit.SECONDS));
        Assert.assertNotNull(sync(bucket.get()));

        Thread.sleep(1100);

        Assert.assertNull(sync(bucket.get()));
    }

    @Test
    public void testRenamenx() {
        RBucketReactive<String> bucket = redisson.getBucket("test");
        sync(bucket.set("someValue"));
        RBucketReactive<String> bucket2 = redisson.getBucket("test2");
        sync(bucket2.set("someValue2"));
        Assert.assertTrue(sync(bucket.renamenx("test1")));
        RBucketReactive<String> oldBucket = redisson.getBucket("test");
        Assert.assertNull(sync(oldBucket.get()));
        RBucketReactive<String> newBucket = redisson.getBucket("test1");
        Assert.assertEquals("someValue", sync(newBucket.get()));
        Assert.assertFalse(sync(newBucket.renamenx("test2")));
    }

    @Test
    public void testRename() {
        RBucketReactive<String> bucket = redisson.getBucket("test");
        sync(bucket.set("someValue"));
        sync(bucket.rename("test1"));
        RBucketReactive<String> oldBucket = redisson.getBucket("test");
        Assert.assertNull(sync(oldBucket.get()));
        RBucketReactive<String> newBucket = redisson.getBucket("test1");
        Assert.assertEquals("someValue", sync(newBucket.get()));
    }

    @Test
    public void testSetGet() {
        RBucketReactive<String> bucket = redisson.getBucket("test");
        Assert.assertNull(sync(bucket.get()));
        String value = "somevalue";
        sync(bucket.set(value));
        Assert.assertEquals(value, sync(bucket.get()));
    }

    @Test
    public void testSetDelete() {
        RBucketReactive<String> bucket = redisson.getBucket("test");
        String value = "somevalue";
        sync(bucket.set(value));
        Assert.assertEquals(value, sync(bucket.get()));
        Assert.assertTrue(sync(bucket.delete()));
        Assert.assertNull(sync(bucket.get()));
        Assert.assertFalse(sync(bucket.delete()));
    }


    @Test
    public void testSetExist() {
        RBucketReactive<String> bucket = redisson.getBucket("test");
        Assert.assertNull(sync(bucket.get()));
        String value = "somevalue";
        sync(bucket.set(value));
        Assert.assertEquals(value, sync(bucket.get()));

        Assert.assertTrue(sync(bucket.isExists()));
    }

    @Test
    public void testSetDeleteNotExist() {
        RBucketReactive<String> bucket = redisson.getBucket("test");
        Assert.assertNull(sync(bucket.get()));
        String value = "somevalue";
        sync(bucket.set(value));
        Assert.assertEquals(value, sync(bucket.get()));

        Assert.assertTrue(sync(bucket.isExists()));

        sync(bucket.delete());

        Assert.assertFalse(sync(bucket.isExists()));
    }

    @Test
    public void testFindPattern() {
        Collection<String> names = Arrays.asList("test:testGetPattern:one", "test:testGetPattern:two");
        Collection<String> vals = Arrays.asList("one-val", "two-val");
        sync(redisson.getBucket("test:three").set("three-val"));
        sync(redisson.getBucket("test:testGetPattern:one").set("one-val"));
        sync(redisson.getBucket("test:testGetPattern:two").set("two-val"));

        List<RBucketReactive<String>> buckets = redisson.findBuckets("test:testGetPattern:*");
        Assert.assertEquals(2, buckets.size());
        Assert.assertTrue(names.contains(buckets.get(0).getName()));
        Assert.assertTrue(names.contains(buckets.get(1).getName()));
        Assert.assertTrue(vals.contains(sync(buckets.get(0).get())));
        Assert.assertTrue(vals.contains(sync(buckets.get(1).get())));

        for (RBucketReactive<String> bucket : buckets) {
            bucket.delete();
        }
    }
}
