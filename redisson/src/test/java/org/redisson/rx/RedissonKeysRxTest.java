package org.redisson.rx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RBucketRx;
import org.redisson.api.RMapRx;

public class RedissonKeysRxTest extends BaseRxTest {

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
        RBucketRx<String> bucket = redisson.getBucket("test1");
        sync(bucket.set("someValue1"));

        RBucketRx<String> bucket2 = redisson.getBucket("test2");
        sync(bucket2.set("someValue2"));

        assertThat(sync(redisson.getKeys().randomKey())).isIn("test1", "test2");
        sync(redisson.getKeys().delete("test1"));
        Assert.assertEquals("test2", sync(redisson.getKeys().randomKey()));
        sync(redisson.getKeys().flushdb());
        Assert.assertNull(sync(redisson.getKeys().randomKey()));
    }

    @Test
    public void testDeleteByPattern() {
        RBucketRx<String> bucket = redisson.getBucket("test1");
        sync(bucket.set("someValue"));
        RMapRx<String, String> map = redisson.getMap("test2");
        sync(map.fastPut("1", "2"));

        Assert.assertEquals(2, sync(redisson.getKeys().deleteByPattern("test?")).intValue());
    }

    @Test
    public void testMassDelete() {
        RBucketRx<String> bucket = redisson.getBucket("test");
        sync(bucket.set("someValue"));
        RMapRx<String, String> map = redisson.getMap("map2");
        sync(map.fastPut("1", "2"));

        Assert.assertEquals(2, sync(redisson.getKeys().delete("test", "map2")).intValue());
        Assert.assertEquals(0, sync(redisson.getKeys().delete("test", "map2")).intValue());
    }

}
