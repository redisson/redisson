package org.redisson;

import java.util.Iterator;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RMapReactive;

public class RedissonKeysReactiveTest extends BaseReactiveTest {

    @Test
    public void testKeysIterablePattern() {
        redisson.getBucket("test1").set("someValue");
        redisson.getBucket("test2").set("someValue");

        redisson.getBucket("test12").set("someValue");

        Iterator<String> iterator = toIterator(redisson.getKeys().getKeysByPattern("test?"));
        for (; iterator.hasNext();) {
            String key = iterator.next();
            MatcherAssert.assertThat(key, Matchers.isOneOf("test1", "test2"));
        }
    }

    @Test
    public void testRandomKey() {
        RBucketReactive<String> bucket = redisson.getBucket("test1");
        sync(bucket.set("someValue1"));

        RBucketReactive<String> bucket2 = redisson.getBucket("test2");
        sync(bucket2.set("someValue2"));

        MatcherAssert.assertThat(sync(redisson.getKeys().randomKey()), Matchers.isOneOf("test1", "test2"));
        sync(redisson.getKeys().delete("test1"));
        Assert.assertEquals("test2", sync(redisson.getKeys().randomKey()));
        sync(redisson.getKeys().flushdb());
        Assert.assertNull(sync(redisson.getKeys().randomKey()));
    }

    @Test
    public void testDeleteByPattern() {
        RBucketReactive<String> bucket = redisson.getBucket("test1");
        sync(bucket.set("someValue"));
        RMapReactive<String, String> map = redisson.getMap("test2");
        sync(map.fastPut("1", "2"));

        Assert.assertEquals(2, sync(redisson.getKeys().deleteByPattern("test?")).intValue());
    }

    @Test
    public void testMassDelete() {
        RBucketReactive<String> bucket = redisson.getBucket("test");
        sync(bucket.set("someValue"));
        RMapReactive<String, String> map = redisson.getMap("map2");
        sync(map.fastPut("1", "2"));

        Assert.assertEquals(2, sync(redisson.getKeys().delete("test", "map2")).intValue());
        Assert.assertEquals(0, sync(redisson.getKeys().delete("test", "map2")).intValue());
    }

}
