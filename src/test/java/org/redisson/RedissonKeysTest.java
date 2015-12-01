package org.redisson;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RBucket;
import org.redisson.core.RMap;

public class RedissonKeysTest extends BaseTest {

    @Test
    public void testKeysIterablePattern() {
        redisson.getBucket("test1").set("someValue");
        redisson.getBucket("test2").set("someValue");

        redisson.getBucket("test12").set("someValue");

        Iterator<String> iterator = redisson.getKeys().getKeysByPattern("test?").iterator();
        for (; iterator.hasNext();) {
            String key = iterator.next();
            MatcherAssert.assertThat(key, Matchers.isOneOf("test1", "test2"));
        }
    }

    @Test
    public void testKeysIterable() throws InterruptedException {
        Set<String> keys = new HashSet<String>();
        for (int i = 0; i < 115; i++) {
            String key = "key" + Math.random();
            RBucket<String> bucket = redisson.getBucket(key);
            bucket.set("someValue");
        }

        Iterator<String> iterator = redisson.getKeys().getKeys().iterator();
        for (; iterator.hasNext();) {
            String key = iterator.next();
            keys.remove(key);
            iterator.remove();
        }
        Assert.assertEquals(0, keys.size());
        Assert.assertFalse(redisson.getKeys().getKeys().iterator().hasNext());
    }

    @Test
    public void testRandomKey() {
        RBucket<String> bucket = redisson.getBucket("test1");
        bucket.set("someValue1");

        RBucket<String> bucket2 = redisson.getBucket("test2");
        bucket2.set("someValue2");

        MatcherAssert.assertThat(redisson.getKeys().randomKey(), Matchers.isOneOf("test1", "test2"));
        redisson.getKeys().delete("test1");
        Assert.assertEquals(redisson.getKeys().randomKey(), "test2");
        redisson.flushdb();
        Assert.assertNull(redisson.getKeys().randomKey());
    }

    @Test
    public void testDeleteByPattern() {
        RBucket<String> bucket = redisson.getBucket("test1");
        bucket.set("someValue");
        RMap<String, String> map = redisson.getMap("test2");
        map.fastPut("1", "2");

        Assert.assertEquals(2, redisson.getKeys().deleteByPattern("test?"));
    }

    @Test
    public void testFindKeys() {
        RBucket<String> bucket = redisson.getBucket("test1");
        bucket.set("someValue");
        RMap<String, String> map = redisson.getMap("test2");
        map.fastPut("1", "2");

        Collection<String> keys = redisson.getKeys().findKeysByPattern("test?");
        MatcherAssert.assertThat(keys, Matchers.containsInAnyOrder("test1", "test2"));

        Collection<String> keys2 = redisson.getKeys().findKeysByPattern("test");
        MatcherAssert.assertThat(keys2, Matchers.empty());
    }

    @Test
    public void testMassDelete() {
        RBucket<String> bucket = redisson.getBucket("test");
        bucket.set("someValue");
        RMap<String, String> map = redisson.getMap("map2");
        map.fastPut("1", "2");

        Assert.assertEquals(2, redisson.getKeys().delete("test", "map2"));
        Assert.assertEquals(0, redisson.getKeys().delete("test", "map2"));
    }

}
