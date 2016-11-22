package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.redisson.rule.TestUtil.sync;
import static org.redisson.rule.TestUtil.toIterator;

import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RMapReactive;

public class RedissonKeysReactiveTest extends AbstractBaseTest {

    @Test
    public void testKeysIterablePattern() {
        redissonRule.getSharedReactiveClient().getBucket("test1").set("someValue");
        redissonRule.getSharedReactiveClient().getBucket("test2").set("someValue");

        redissonRule.getSharedReactiveClient().getBucket("test12").set("someValue");

        Iterator<String> iterator = toIterator(redissonRule.getSharedReactiveClient().getKeys().getKeysByPattern("test?"));
        for (; iterator.hasNext();) {
            String key = iterator.next();
            assertThat(key).isIn("test1", "test2");
        }
    }

    @Test
    public void testRandomKey() {
        RBucketReactive<String> bucket = redissonRule.getSharedReactiveClient().getBucket("test1");
        sync(bucket.set("someValue1"));

        RBucketReactive<String> bucket2 = redissonRule.getSharedReactiveClient().getBucket("test2");
        sync(bucket2.set("someValue2"));

        assertThat(sync(redissonRule.getSharedReactiveClient().getKeys().randomKey())).isIn("test1", "test2");
        sync(redissonRule.getSharedReactiveClient().getKeys().delete("test1"));
        Assert.assertEquals("test2", sync(redissonRule.getSharedReactiveClient().getKeys().randomKey()));
        sync(redissonRule.getSharedReactiveClient().getKeys().flushdb());
        Assert.assertNull(sync(redissonRule.getSharedReactiveClient().getKeys().randomKey()));
    }

    @Test
    public void testDeleteByPattern() {
        RBucketReactive<String> bucket = redissonRule.getSharedReactiveClient().getBucket("test1");
        sync(bucket.set("someValue"));
        RMapReactive<String, String> map = redissonRule.getSharedReactiveClient().getMap("test2");
        sync(map.fastPut("1", "2"));

        Assert.assertEquals(2, sync(redissonRule.getSharedReactiveClient().getKeys().deleteByPattern("test?")).intValue());
    }

    @Test
    public void testMassDelete() {
        RBucketReactive<String> bucket = redissonRule.getSharedReactiveClient().getBucket("test");
        sync(bucket.set("someValue"));
        RMapReactive<String, String> map = redissonRule.getSharedReactiveClient().getMap("map2");
        sync(map.fastPut("1", "2"));

        Assert.assertEquals(2, sync(redissonRule.getSharedReactiveClient().getKeys().delete("test", "map2")).intValue());
        Assert.assertEquals(0, sync(redissonRule.getSharedReactiveClient().getKeys().delete("test", "map2")).intValue());
    }
}
