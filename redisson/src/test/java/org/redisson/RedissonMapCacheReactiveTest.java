package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLockReactive;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RMapReactive;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonMapCacheReactiveTest extends BaseReactiveTest {

    public static class SimpleKey implements Serializable {

        private String key;

        public SimpleKey() {
        }

        public SimpleKey(String field) {
            this.key = field;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return "key: " + key;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SimpleKey other = (SimpleKey) obj;
            if (key == null) {
                if (other.key != null)
                    return false;
            } else if (!key.equals(other.key))
                return false;
            return true;
        }

    }

    public static class SimpleValue implements Serializable {

        private String value;

        public SimpleValue() {
        }

        public SimpleValue(String field) {
            this.value = field;
        }

        public void setValue(String field) {
            this.value = field;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "value: " + value;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SimpleValue other = (SimpleValue) obj;
            if (value == null) {
                if (other.value != null)
                    return false;
            } else if (!value.equals(other.value))
                return false;
            return true;
        }

    }

    @Test
    public void testLock() {
        RMapCacheReactive<Integer, Integer> map = redisson.getMapCache("getAll");
        RLockReactive lock = map.getLock(123);
        assertThat(sync(lock.isLocked())).isFalse();
        sync(lock.lock());
        assertThat(sync(lock.isLocked())).isTrue();
        sync(lock.unlock());
        assertThat(sync(lock.isLocked())).isFalse();
    }

    @Test
    public void testGetAll() throws InterruptedException {
        RMapCacheReactive<Integer, Integer> map = redisson.getMapCache("getAll");
        sync(map.put(1, 100));
        sync(map.put(2, 200, 1, TimeUnit.SECONDS));
        sync(map.put(3, 300, 1, TimeUnit.SECONDS));
        sync(map.put(4, 400));

        Map<Integer, Integer> filtered = sync(map.getAll(new HashSet<Integer>(Arrays.asList(2, 3, 5))));

        Map<Integer, Integer> expectedMap = new HashMap<Integer, Integer>();
        expectedMap.put(2, 200);
        expectedMap.put(3, 300);
        Assertions.assertEquals(expectedMap, filtered);

        Thread.sleep(1000);

        Map<Integer, Integer> filteredAgain = sync(map.getAll(new HashSet<Integer>(Arrays.asList(2, 3, 5))));
        Assertions.assertTrue(filteredAgain.isEmpty());
    }

    @Test
    public void testGetAllWithStringKeys() {
        RMapCacheReactive<String, Integer> map = redisson.getMapCache("getAllStrings");
        sync(map.put("A", 100));
        sync(map.put("B", 200));
        sync(map.put("C", 300));
        sync(map.put("D", 400));

        Map<String, Integer> filtered = sync(map.getAll(new HashSet<String>(Arrays.asList("B", "C", "E"))));

        Map<String, Integer> expectedMap = new HashMap<String, Integer>();
        expectedMap.put("B", 200);
        expectedMap.put("C", 300);
        Assertions.assertEquals(expectedMap, filtered);
    }

    @Test
    public void testExpiredIterator() throws InterruptedException {
        RMapCacheReactive<String, String> cache = redisson.getMapCache("simple");
        sync(cache.put("0", "8"));
        sync(cache.put("1", "6", 1, TimeUnit.SECONDS));
        sync(cache.put("2", "4", 3, TimeUnit.SECONDS));
        sync(cache.put("3", "2", 4, TimeUnit.SECONDS));
        sync(cache.put("4", "4", 1, TimeUnit.SECONDS));

        Thread.sleep(1000);

        assertThat(toIterator(cache.keyIterator())).toIterable().containsOnly("0", "2", "3");
    }

    @Test
    public void testExpire() throws InterruptedException {
        RMapCacheReactive<String, String> cache = redisson.getMapCache("simple");
        sync(cache.put("0", "8", 1, TimeUnit.SECONDS));

        sync(cache.expire(100, TimeUnit.MILLISECONDS));

        Thread.sleep(500);

        Assertions.assertEquals(0, sync(cache.size()).intValue());
    }

    @Test
    public void testExpireAt() throws InterruptedException {
        RMapCacheReactive<String, String> cache = redisson.getMapCache("simple");
        sync(cache.put("0", "8", 1, TimeUnit.SECONDS));

        sync(cache.expireAt(System.currentTimeMillis() + 100));

        Thread.sleep(500);

        Assertions.assertEquals(0, sync(cache.size()).intValue());
    }

    @Test
    public void testClearExpire() throws InterruptedException {
        RMapCacheReactive<String, String> cache = redisson.getMapCache("simple");
        sync(cache.put("0", "8", 1, TimeUnit.SECONDS));

        sync(cache.expireAt(System.currentTimeMillis() + 100));

        sync(cache.clearExpire());

        Thread.sleep(500);

        Assertions.assertEquals(1, sync(cache.size()).intValue());
    }

    @Test
    public void testRemove() {
        RMapCacheReactive<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        sync(map.put(new SimpleKey("1"), new SimpleValue("2")));
        sync(map.put(new SimpleKey("33"), new SimpleValue("44")));
        sync(map.put(new SimpleKey("5"), new SimpleValue("6")));

        sync(map.remove(new SimpleKey("33")));
        sync(map.remove(new SimpleKey("5")));

        Assertions.assertEquals(1, sync(map.size()).intValue());
    }

    @Test
    public void testPutAll() {
        RMapCacheReactive<Integer, String> map = redisson.getMapCache("simple");
        sync(map.put(1, "1"));
        sync(map.put(2, "2"));
        sync(map.put(3, "3"));

        Map<Integer, String> joinMap = new HashMap<Integer, String>();
        joinMap.put(4, "4");
        joinMap.put(5, "5");
        joinMap.put(6, "6");
        sync(map.putAll(joinMap));

        assertThat(toIterable(map.keyIterator())).containsOnly(1, 2, 3, 4, 5, 6);
    }

    @Test
    public void testComputeIfAbsent() {
        RMapCacheReactive<Integer, String> map = redisson.getMapCache("simple");

        map.computeIfAbsent(7, Duration.ofSeconds(1), new Function<Integer, String>() {
            @Override
            public String apply(Integer integer) {
                return "1234";
            }
        }).block();

        assertThat(map.get(7).block()).isEqualTo("1234");
    }

    @Test
    public void testContainsValue() throws InterruptedException {
        RMapCacheReactive<SimpleKey, SimpleValue> map = redisson.getMapCache("simple31");
        Assertions.assertFalse(sync(map.containsValue(new SimpleValue("34"))));
        sync(map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS));

        Assertions.assertTrue(sync(map.containsValue(new SimpleValue("44"))));
        Assertions.assertFalse(sync(map.containsValue(new SimpleValue("34"))));

        Thread.sleep(1000);

        Assertions.assertFalse(sync(map.containsValue(new SimpleValue("44"))));
    }

    @Test
    public void testContainsKey() throws InterruptedException {
        RMapCacheReactive<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        sync(map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS));

        Assertions.assertTrue(sync(map.containsKey(new SimpleKey("33"))));
        Assertions.assertFalse(sync(map.containsKey(new SimpleKey("34"))));

        Thread.sleep(1000);

        Assertions.assertFalse(sync(map.containsKey(new SimpleKey("33"))));
    }

    @Test
    public void testRemoveValue() {
        RMapCacheReactive<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        sync(map.put(new SimpleKey("1"), new SimpleValue("2"), 1, TimeUnit.SECONDS));

        boolean res = sync(map.remove(new SimpleKey("1"), new SimpleValue("2")));
        Assertions.assertTrue(res);

        SimpleValue val1 = sync(map.get(new SimpleKey("1")));
        Assertions.assertNull(val1);

        Assertions.assertEquals(0, sync(map.size()).intValue());
    }

    @Test
    public void testScheduler() throws InterruptedException {
        RMapCacheReactive<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        Assertions.assertNull(sync(map.get(new SimpleKey("33"))));

        sync(map.put(new SimpleKey("33"), new SimpleValue("44"), 5, TimeUnit.SECONDS));

        Thread.sleep(11000);

        Assertions.assertEquals(0, sync(map.size()).intValue());

    }

    @Test
    public void testPutGet() throws InterruptedException {
        RMapCacheReactive<SimpleKey, SimpleValue> map = redisson.getMapCache("simple01");
        Assertions.assertNull(sync(map.get(new SimpleKey("33"))));

        sync(map.put(new SimpleKey("33"), new SimpleValue("44"), 2, TimeUnit.SECONDS));

        SimpleValue val1 = sync(map.get(new SimpleKey("33")));
        Assertions.assertEquals("44", val1.getValue());

        Thread.sleep(1000);

        Assertions.assertEquals(1, sync(map.size()).intValue());
        SimpleValue val2 = sync(map.get(new SimpleKey("33")));
        Assertions.assertEquals("44", val2.getValue());
        Assertions.assertEquals(1, sync(map.size()).intValue());

        Thread.sleep(1000);

        Assertions.assertNull(sync(map.get(new SimpleKey("33"))));
    }

    @Test
    public void testPutIfAbsent() throws Exception {
        RMapCacheReactive<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        sync(map.put(key, value));
        Assertions.assertEquals(value, sync(map.putIfAbsent(key, new SimpleValue("3"), 1, TimeUnit.SECONDS)));
        Assertions.assertEquals(value, sync(map.get(key)));

        sync(map.putIfAbsent(new SimpleKey("4"), new SimpleValue("4"), 1, TimeUnit.SECONDS));
        Assertions.assertEquals(new SimpleValue("4"), sync(map.get(new SimpleKey("4"))));

        Thread.sleep(1000);

        Assertions.assertNull(sync(map.get(new SimpleKey("4"))));

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        Assertions.assertNull(sync(map.putIfAbsent(key1, value1, 2, TimeUnit.SECONDS)));
        Assertions.assertEquals(value1, sync(map.get(key1)));
    }

    @Test
    public void testSize() {
        RMapCacheReactive<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");

        sync(map.put(new SimpleKey("1"), new SimpleValue("2")));
        sync(map.put(new SimpleKey("3"), new SimpleValue("4")));
        sync(map.put(new SimpleKey("5"), new SimpleValue("6")));
        Assertions.assertEquals(3, sync(map.size()).intValue());

        sync(map.put(new SimpleKey("1"), new SimpleValue("2")));
        sync(map.put(new SimpleKey("3"), new SimpleValue("4")));
        Assertions.assertEquals(3, sync(map.size()).intValue());

        sync(map.put(new SimpleKey("1"), new SimpleValue("21")));
        sync(map.put(new SimpleKey("3"), new SimpleValue("41")));
        Assertions.assertEquals(3, sync(map.size()).intValue());

        sync(map.put(new SimpleKey("51"), new SimpleValue("6")));
        Assertions.assertEquals(4, sync(map.size()).intValue());

        sync(map.remove(new SimpleKey("3")));
        Assertions.assertEquals(3, sync(map.size()).intValue());
    }

    @Test
    public void testEmptyRemove() {
        RMapCacheReactive<Integer, Integer> map = redisson.getMapCache("simple");
        assertThat(sync(map.remove(1, 3))).isEqualTo(Boolean.FALSE);
        sync(map.put(4, 5));
        assertThat(sync(map.remove(4, 5))).isEqualTo(Boolean.TRUE);
    }

    @Test
    public void testKeyIterator() {
        RMapReactive<Integer, Integer> map = redisson.getMapCache("simple");
        sync(map.put(1, 0));
        sync(map.put(3, 5));
        sync(map.put(4, 6));
        sync(map.put(7, 8));

        List<Integer> keys = new ArrayList<>(Arrays.asList(1, 3, 4, 7));
        for (Iterator<Integer> iterator = toIterator(map.keyIterator()); iterator.hasNext();) {
            Integer value = iterator.next();
            if (!keys.remove(value)) {
                Assertions.fail();
            }
        }

        Assertions.assertEquals(0, keys.size());
    }

    @Test
    public void testValueIterator() {
        RMapReactive<Integer, Integer> map = redisson.getMapCache("simple");
        sync(map.put(1, 0));
        sync(map.put(3, 5));
        sync(map.put(4, 6));
        sync(map.put(7, 8));

        List<Integer> values = new ArrayList<Integer>(Arrays.asList(0, 5, 6, 8));
        for (Iterator<Integer> iterator = toIterator(map.valueIterator()); iterator.hasNext();) {
            Integer value = iterator.next();
            if (!values.remove(value)) {
                Assertions.fail();
            }
        }

        Assertions.assertEquals(0, values.size());
    }

}
