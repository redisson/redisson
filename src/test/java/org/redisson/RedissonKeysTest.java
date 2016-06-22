package org.redisson;

import static org.assertj.core.api.Assertions.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.core.RBucket;
import org.redisson.core.RMap;
import org.redisson.core.RType;

public class RedissonKeysTest extends BaseTest {

    @Test
    public void testType() {
        redisson.getSet("test").add("1");
        
        assertThat(redisson.getKeys().getType("test")).isEqualTo(RType.SET);
        assertThat(redisson.getKeys().getType("test1")).isNull();
    }
    
    @Test
    public void testKeysIterablePattern() {
        redisson.getBucket("test1").set("someValue");
        redisson.getBucket("test2").set("someValue");

        redisson.getBucket("test12").set("someValue");

        Iterator<String> iterator = redisson.getKeys().getKeysByPattern("test?").iterator();
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

        assertThat(redisson.getKeys().randomKey()).isIn("test1", "test2");
        redisson.getKeys().delete("test1");
        Assert.assertEquals("test2", redisson.getKeys().randomKey());
        redisson.getKeys().flushdb();
        Assert.assertNull(redisson.getKeys().randomKey());
    }

    @Test
    public void testDeleteByPattern() {
        RBucket<String> bucket = redisson.getBucket("test0");
        bucket.set("someValue3");
        assertThat(bucket.isExists()).isTrue();

        RBucket<String> bucket2 = redisson.getBucket("test9");
        bucket2.set("someValue4");
        assertThat(bucket.isExists()).isTrue();

        RMap<String, String> map = redisson.getMap("test2");
        map.fastPut("1", "2");
        assertThat(map.isExists()).isTrue();

        RMap<String, String> map2 = redisson.getMap("test3");
        map2.fastPut("1", "5");
        assertThat(map2.isExists()).isTrue();


        Assert.assertEquals(4, redisson.getKeys().deleteByPattern("test?"));
        Assert.assertEquals(0, redisson.getKeys().deleteByPattern("test?"));
    }

    @Test
    public void testFindKeys() {
        RBucket<String> bucket = redisson.getBucket("test1");
        bucket.set("someValue");
        RMap<String, String> map = redisson.getMap("test2");
        map.fastPut("1", "2");

        Collection<String> keys = redisson.getKeys().findKeysByPattern("test?");
        assertThat(keys).containsOnly("test1", "test2");

        Collection<String> keys2 = redisson.getKeys().findKeysByPattern("test");
        assertThat(keys2).isEmpty();
    }

    @Test
    public void testMassDelete() {
        RBucket<String> bucket0 = redisson.getBucket("test0");
        bucket0.set("someValue");
        RBucket<String> bucket1 = redisson.getBucket("test1");
        bucket1.set("someValue");
        RBucket<String> bucket2 = redisson.getBucket("test2");
        bucket2.set("someValue");
        RBucket<String> bucket3 = redisson.getBucket("test3");
        bucket3.set("someValue");
        RBucket<String> bucket10 = redisson.getBucket("test10");
        bucket10.set("someValue");

        RBucket<String> bucket12 = redisson.getBucket("test12");
        bucket12.set("someValue");
        RMap<String, String> map = redisson.getMap("map2");
        map.fastPut("1", "2");

        Assert.assertEquals(7, redisson.getKeys().delete("test0", "test1", "test2", "test3", "test10", "test12", "map2"));
        Assert.assertEquals(0, redisson.getKeys().delete("test0", "test1", "test2", "test3", "test10", "test12", "map2"));
    }

    @Test
    public void testCount() {
        Long s = redisson.getKeys().count();
        assertThat(s).isEqualTo(0);
        redisson.getBucket("test1").set(23);
        s = redisson.getKeys().count();
        assertThat(s).isEqualTo(1);
    }

}
