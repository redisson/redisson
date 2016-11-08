package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.api.RType;

public class RedissonKeysTest extends AbstractBaseTest {

    @Test
    public void testExists() {
        redissonRule.getSharedClient().getSet("test").add("1");
        redissonRule.getSharedClient().getSet("test10").add("1");
        
        assertThat(redissonRule.getSharedClient().getKeys().isExists("test")).isEqualTo(1);
        assertThat(redissonRule.getSharedClient().getKeys().isExists("test", "test2")).isEqualTo(1);
        assertThat(redissonRule.getSharedClient().getKeys().isExists("test3", "test2")).isEqualTo(0);
        assertThat(redissonRule.getSharedClient().getKeys().isExists("test3", "test10", "test")).isEqualTo(2);
    }
    
    @Test
    public void testType() {
        redissonRule.getSharedClient().getSet("test").add("1");
        
        assertThat(redissonRule.getSharedClient().getKeys().getType("test")).isEqualTo(RType.SET);
        assertThat(redissonRule.getSharedClient().getKeys().getType("test1")).isNull();
    }
    
    @Test
    public void testKeysIterablePattern() {
        redissonRule.getSharedClient().getBucket("test1").set("someValue");
        redissonRule.getSharedClient().getBucket("test2").set("someValue");

        redissonRule.getSharedClient().getBucket("test12").set("someValue");

        Iterator<String> iterator = redissonRule.getSharedClient().getKeys().getKeysByPattern("test?").iterator();
        for (; iterator.hasNext();) {
            String key = iterator.next();
            assertThat(key).isIn("test1", "test2");
        }
    }

    @Test
    public void testKeysIterable() throws InterruptedException {
        Set<String> keys = new HashSet<String>();
        for (int i = 0; i < 115; i++) {
            String key = "key" + Math.random();
            RBucket<String> bucket = redissonRule.getSharedClient().getBucket(key);
            bucket.set("someValue");
        }

        Iterator<String> iterator = redissonRule.getSharedClient().getKeys().getKeys().iterator();
        for (; iterator.hasNext();) {
            String key = iterator.next();
            keys.remove(key);
            iterator.remove();
        }
        Assert.assertEquals(0, keys.size());
        Assert.assertFalse(redissonRule.getSharedClient().getKeys().getKeys().iterator().hasNext());
    }

    @Test
    public void testRandomKey() {
        RBucket<String> bucket = redissonRule.getSharedClient().getBucket("test1");
        bucket.set("someValue1");

        RBucket<String> bucket2 = redissonRule.getSharedClient().getBucket("test2");
        bucket2.set("someValue2");

        assertThat(redissonRule.getSharedClient().getKeys().randomKey()).isIn("test1", "test2");
        redissonRule.getSharedClient().getKeys().delete("test1");
        Assert.assertEquals("test2", redissonRule.getSharedClient().getKeys().randomKey());
        redissonRule.getSharedClient().getKeys().flushdb();
        Assert.assertNull(redissonRule.getSharedClient().getKeys().randomKey());
    }

    @Test
    public void testDeleteByPattern() {
        RBucket<String> bucket = redissonRule.getSharedClient().getBucket("test0");
        bucket.set("someValue3");
        assertThat(bucket.isExists()).isTrue();

        RBucket<String> bucket2 = redissonRule.getSharedClient().getBucket("test9");
        bucket2.set("someValue4");
        assertThat(bucket.isExists()).isTrue();

        RMap<String, String> map = redissonRule.getSharedClient().getMap("test2");
        map.fastPut("1", "2");
        assertThat(map.isExists()).isTrue();

        RMap<String, String> map2 = redissonRule.getSharedClient().getMap("test3");
        map2.fastPut("1", "5");
        assertThat(map2.isExists()).isTrue();


        Assert.assertEquals(4, redissonRule.getSharedClient().getKeys().deleteByPattern("test?"));
        Assert.assertEquals(0, redissonRule.getSharedClient().getKeys().deleteByPattern("test?"));
    }

    @Test
    public void testFindKeys() {
        RBucket<String> bucket = redissonRule.getSharedClient().getBucket("test1");
        bucket.set("someValue");
        RMap<String, String> map = redissonRule.getSharedClient().getMap("test2");
        map.fastPut("1", "2");

        Collection<String> keys = redissonRule.getSharedClient().getKeys().findKeysByPattern("test?");
        assertThat(keys).containsOnly("test1", "test2");

        Collection<String> keys2 = redissonRule.getSharedClient().getKeys().findKeysByPattern("test");
        assertThat(keys2).isEmpty();
    }

    @Test
    public void testMassDelete() {
        RBucket<String> bucket0 = redissonRule.getSharedClient().getBucket("test0");
        bucket0.set("someValue");
        RBucket<String> bucket1 = redissonRule.getSharedClient().getBucket("test1");
        bucket1.set("someValue");
        RBucket<String> bucket2 = redissonRule.getSharedClient().getBucket("test2");
        bucket2.set("someValue");
        RBucket<String> bucket3 = redissonRule.getSharedClient().getBucket("test3");
        bucket3.set("someValue");
        RBucket<String> bucket10 = redissonRule.getSharedClient().getBucket("test10");
        bucket10.set("someValue");

        RBucket<String> bucket12 = redissonRule.getSharedClient().getBucket("test12");
        bucket12.set("someValue");
        RMap<String, String> map = redissonRule.getSharedClient().getMap("map2");
        map.fastPut("1", "2");

        Assert.assertEquals(7, redissonRule.getSharedClient().getKeys().delete("test0", "test1", "test2", "test3", "test10", "test12", "map2"));
        Assert.assertEquals(0, redissonRule.getSharedClient().getKeys().delete("test0", "test1", "test2", "test3", "test10", "test12", "map2"));
    }

    @Test
    public void testCount() {
        Long s = redissonRule.getSharedClient().getKeys().count();
        assertThat(s).isEqualTo(0);
        redissonRule.getSharedClient().getBucket("test1").set(23);
        s = redissonRule.getSharedClient().getKeys().count();
        assertThat(s).isEqualTo(1);
    }
}
