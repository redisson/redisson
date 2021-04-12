package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucketReactive;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RedissonBucketReactiveTest extends BaseReactiveTest {

    @Test
    public void testExpire() throws InterruptedException {
        RBucketReactive<String> bucket = redisson.getBucket("test1");
        sync(bucket.set("someValue", 1, TimeUnit.SECONDS));
        Assertions.assertNotNull(sync(bucket.get()));

        Thread.sleep(1100);

        Assertions.assertNull(sync(bucket.get()));
    }

    @Test
    public void testRenamenx() {
        RBucketReactive<String> bucket = redisson.getBucket("test");
        sync(bucket.set("someValue"));
        RBucketReactive<String> bucket2 = redisson.getBucket("test2");
        sync(bucket2.set("someValue2"));
        Assertions.assertTrue(sync(bucket.renamenx("test1")));
        RBucketReactive<String> oldBucket = redisson.getBucket("test");
        Assertions.assertNull(sync(oldBucket.get()));
        RBucketReactive<String> newBucket = redisson.getBucket("test1");
        Assertions.assertEquals("someValue", sync(newBucket.get()));
        Assertions.assertFalse(sync(newBucket.renamenx("test2")));
    }

    @Test
    public void testRename() {
        RBucketReactive<String> bucket = redisson.getBucket("test");
        sync(bucket.set("someValue"));
        sync(bucket.rename("test1"));
        RBucketReactive<String> oldBucket = redisson.getBucket("test");
        Assertions.assertNull(sync(oldBucket.get()));
        RBucketReactive<String> newBucket = redisson.getBucket("test1");
        Assertions.assertEquals("someValue", sync(newBucket.get()));
    }

    @Test
    public void testSetGet() {
        RBucketReactive<String> bucket = redisson.getBucket("test");
        Assertions.assertNull(sync(bucket.get()));
        String value = "somevalue";
        sync(bucket.set(value));
        Assertions.assertEquals(value, sync(bucket.get()));
    }

    @Test
    public void testSetDelete() {
        RBucketReactive<String> bucket = redisson.getBucket("test");
        String value = "somevalue";
        sync(bucket.set(value));
        Assertions.assertEquals(value, sync(bucket.get()));
        Assertions.assertTrue(sync(bucket.delete()));
        Assertions.assertNull(sync(bucket.get()));
        Assertions.assertFalse(sync(bucket.delete()));
    }


    @Test
    public void testSetExist() {
        RBucketReactive<String> bucket = redisson.getBucket("test");
        Assertions.assertNull(sync(bucket.get()));
        String value = "somevalue";
        sync(bucket.set(value));
        Assertions.assertEquals(value, sync(bucket.get()));

        Assertions.assertTrue(sync(bucket.isExists()));
    }

    @Test
    public void testSetDeleteNotExist() {
        RBucketReactive<String> bucket = redisson.getBucket("test");
        Assertions.assertNull(sync(bucket.get()));
        String value = "somevalue";
        sync(bucket.set(value));
        Assertions.assertEquals(value, sync(bucket.get()));

        Assertions.assertTrue(sync(bucket.isExists()));

        sync(bucket.delete());

        Assertions.assertFalse(sync(bucket.isExists()));
    }

    @Test
    public void testFindPattern() {
        Collection<String> names = Arrays.asList("test:testGetPattern:one", "test:testGetPattern:two");
        Collection<String> vals = Arrays.asList("one-val", "two-val");
        sync(redisson.getBucket("test:three").set("three-val"));
        sync(redisson.getBucket("test:testGetPattern:one").set("one-val"));
        sync(redisson.getBucket("test:testGetPattern:two").set("two-val"));

        List<RBucketReactive<String>> buckets = redisson.findBuckets("test:testGetPattern:*");
        Assertions.assertEquals(2, buckets.size());
        Assertions.assertTrue(names.contains(buckets.get(0).getName()));
        Assertions.assertTrue(names.contains(buckets.get(1).getName()));
        Assertions.assertTrue(vals.contains(sync(buckets.get(0).get())));
        Assertions.assertTrue(vals.contains(sync(buckets.get(1).get())));

        for (RBucketReactive<String> bucket : buckets) {
            bucket.delete();
        }
    }
}
