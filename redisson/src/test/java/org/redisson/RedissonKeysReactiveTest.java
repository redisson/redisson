package org.redisson;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.*;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RKeysReactive;
import org.redisson.api.RKeysRx;
import org.redisson.api.RMapReactive;
import reactor.core.publisher.Flux;

public class RedissonKeysReactiveTest extends BaseReactiveTest {

    @Test
    public void testGetKeys() {
        RKeysReactive keys = redisson.getKeys();
        sync(redisson.getBucket("test1").set(1));
        sync(redisson.getBucket("test2").set(1));
        Flux<String> k = keys.getKeys();
        assertThat(k.toIterable()).contains("test1", "test2");
    }

    @Test
    public void testKeysIterablePattern() {
        sync(redisson.getBucket("test1").set("someValue"));
        sync(redisson.getBucket("test2").set("someValue"));

        sync(redisson.getBucket("test12").set("someValue"));

        Iterator<String> iterator = toIterator(redisson.getKeys().getKeysByPattern("test?"));
        for (; iterator.hasNext();) {
            String key = iterator.next();
            assertThat(key).isIn("test1", "test2");
        }
    }

    @Test
    public void testRandomKey() {
        RBucketReactive<String> bucket = redisson.getBucket("test1");
        sync(bucket.set("someValue1"));

        RBucketReactive<String> bucket2 = redisson.getBucket("test2");
        sync(bucket2.set("someValue2"));

        assertThat(sync(redisson.getKeys().randomKey())).isIn("test1", "test2");
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
