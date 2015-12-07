package org.redisson;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.MsgPackJacksonCodec;
import org.redisson.core.Predicate;
import org.redisson.core.RCache;
import org.redisson.core.RMap;

import io.netty.util.concurrent.Future;

public class RedissonCacheTest extends BaseTest {

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
    public void testAddAndGet() throws InterruptedException {
        RCache<Integer, Integer> map = redisson.getCache("getAll");
        map.put(1, 100);

        Integer res = map.addAndGet(1, 12);
        Assert.assertEquals(112, (int)res);
        res = map.get(1);
        Assert.assertEquals(112, (int)res);

        RCache<Integer, Double> map2 = redisson.getCache("getAll2");
        map2.put(1, new Double(100.2));

        Double res2 = map2.addAndGet(1, new Double(12.1));
        Assert.assertTrue(new Double(112.3).compareTo(res2) == 0);
        res2 = map2.get(1);
        Assert.assertTrue(new Double(112.3).compareTo(res2) == 0);
    }

    @Test
    public void testGetAll() throws InterruptedException {
        RCache<Integer, Integer> map = redisson.getCache("getAll");
        map.put(1, 100);
        map.put(2, 200, 1, TimeUnit.SECONDS);
        map.put(3, 300, 1, TimeUnit.SECONDS);
        map.put(4, 400);

        Map<Integer, Integer> filtered = map.getAll(new HashSet<Integer>(Arrays.asList(2, 3, 5)));

        Map<Integer, Integer> expectedMap = new HashMap<Integer, Integer>();
        expectedMap.put(2, 200);
        expectedMap.put(3, 300);
        Assert.assertEquals(expectedMap, filtered);

        Thread.sleep(1000);

        Map<Integer, Integer> filteredAgain = map.getAll(new HashSet<Integer>(Arrays.asList(2, 3, 5)));
        Assert.assertTrue(filteredAgain.isEmpty());
        Thread.sleep(100);
        Assert.assertEquals(2, map.size());
    }

    @Test
    public void testGetAllWithStringKeys() {
        RCache<String, Integer> map = redisson.getCache("getAllStrings");
        map.put("A", 100);
        map.put("B", 200);
        map.put("C", 300);
        map.put("D", 400);

        Map<String, Integer> filtered = map.getAll(new HashSet<String>(Arrays.asList("B", "C", "E")));

        Map<String, Integer> expectedMap = new HashMap<String, Integer>();
        expectedMap.put("B", 200);
        expectedMap.put("C", 300);
        Assert.assertEquals(expectedMap, filtered);
    }

//    @Test
//    public void testFilterKeys() {
//        RCache<Integer, Integer> map = redisson.getCache("filterKeys");
//        map.put(1, 100);
//        map.put(2, 200);
//        map.put(3, 300);
//        map.put(4, 400);
//
//        Map<Integer, Integer> filtered = map.filterKeys(new Predicate<Integer>() {
//            @Override
//            public boolean apply(Integer input) {
//                return input >= 2 && input <= 3;
//            }
//        });
//
//        Map<Integer, Integer> expectedMap = new HashMap<Integer, Integer>();
//        expectedMap.put(2, 200);
//        expectedMap.put(3, 300);
//        Assert.assertEquals(expectedMap, filtered);
//    }

    @Test
    public void testInteger() {
        Map<Integer, Integer> map = redisson.getCache("test_int");
        map.put(1, 2);
        map.put(3, 4);

        Assert.assertEquals(2, map.size());

        Integer val = map.get(1);
        Assert.assertEquals(2, val.intValue());
        Integer val2 = map.get(3);
        Assert.assertEquals(4, val2.intValue());
    }

    @Test
    public void testLong() {
        Map<Long, Long> map = redisson.getCache("test_long");
        map.put(1L, 2L);
        map.put(3L, 4L);

        Assert.assertEquals(2, map.size());

        Long val = map.get(1L);
        Assert.assertEquals(2L, val.longValue());
        Long val2 = map.get(3L);
        Assert.assertEquals(4L, val2.longValue());
    }

    @Test
    public void testNull() {
        Map<Integer, String> map = redisson.getCache("simple12");
        map.put(1, null);
        map.put(2, null);
        map.put(3, "43");

        Assert.assertEquals(3, map.size());

        String val = map.get(2);
        Assert.assertNull(val);
        String val2 = map.get(1);
        Assert.assertNull(val2);
        String val3 = map.get(3);
        Assert.assertEquals("43", val3);
    }

    @Test
    public void testExpiredIterator() throws InterruptedException {
        RCache<String, String> cache = redisson.getCache("simple");
        cache.put("0", "8");
        cache.put("1", "6", 1, TimeUnit.SECONDS);
        cache.put("2", "4", 3, TimeUnit.SECONDS);
        cache.put("3", "2", 4, TimeUnit.SECONDS);
        cache.put("4", "4", 1, TimeUnit.SECONDS);

        Thread.sleep(1000);

        MatcherAssert.assertThat(cache.keySet(), Matchers.contains("0", "2", "3"));
    }

    @Test
    public void testEntrySet() throws InterruptedException {
        RCache<Integer, String> map = redisson.getCache("simple12");
        map.put(1, "12");
        map.put(2, "33", 1, TimeUnit.SECONDS);
        map.put(3, "43");

        Assert.assertEquals(3, map.entrySet().size());

        MatcherAssert.assertThat(map, Matchers.hasEntry(Matchers.equalTo(1), Matchers.equalTo("12")));
        MatcherAssert.assertThat(map, Matchers.hasEntry(Matchers.equalTo(3), Matchers.equalTo("43")));
    }

    @Test
    public void testSimpleTypes() {
        Map<Integer, String> map = redisson.getCache("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        String val = map.get(2);
        Assert.assertEquals("33", val);
    }

    @Test
    public void testRemove() {
        Map<SimpleKey, SimpleValue> map = redisson.getCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        map.remove(new SimpleKey("33"));
        map.remove(new SimpleKey("5"));

        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testPutAll() {
        Map<Integer, String> map = redisson.getCache("simple");
        map.put(1, "1");
        map.put(2, "2");
        map.put(3, "3");

        Map<Integer, String> joinMap = new HashMap<Integer, String>();
        joinMap.put(4, "4");
        joinMap.put(5, "5");
        joinMap.put(6, "6");
        map.putAll(joinMap);

        MatcherAssert.assertThat(map.keySet(), Matchers.containsInAnyOrder(1, 2, 3, 4, 5, 6));
    }

    @Test
    public void testKeySet() throws InterruptedException {
        RCache<SimpleKey, SimpleValue> map = redisson.getCache("simple");
        map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS);
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        Assert.assertTrue(map.keySet().contains(new SimpleKey("33")));
        Assert.assertFalse(map.keySet().contains(new SimpleKey("44")));
        Assert.assertTrue(map.keySet().contains(new SimpleKey("1")));

        Thread.sleep(1000);

        Assert.assertFalse(map.keySet().contains(new SimpleKey("33")));
        Assert.assertFalse(map.keySet().contains(new SimpleKey("44")));
        Assert.assertTrue(map.keySet().contains(new SimpleKey("1")));
        Thread.sleep(50);
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testValues() throws InterruptedException {
        RCache<SimpleKey, SimpleValue> map = redisson.getCache("simple");
        map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS);
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        Assert.assertTrue(map.values().contains(new SimpleValue("44")));
        Assert.assertFalse(map.values().contains(new SimpleValue("33")));
        Assert.assertTrue(map.values().contains(new SimpleValue("2")));

        Thread.sleep(1000);

        Assert.assertFalse(map.values().contains(new SimpleValue("44")));
        Assert.assertFalse(map.values().contains(new SimpleValue("33")));
        Assert.assertTrue(map.values().contains(new SimpleValue("2")));
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testContainsValue() throws InterruptedException {
        RCache<SimpleKey, SimpleValue> map = redisson.getCache("simple", new MsgPackJacksonCodec());
        Assert.assertFalse(map.containsValue(new SimpleValue("34")));
        map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS);

        Assert.assertTrue(map.containsValue(new SimpleValue("44")));
        Assert.assertFalse(map.containsValue(new SimpleValue("34")));

        Thread.sleep(1000);

        Assert.assertFalse(map.containsValue(new SimpleValue("44")));
        Thread.sleep(50);
        Assert.assertEquals(0, map.size());
    }

    @Test
    public void testContainsKey() throws InterruptedException {
        RCache<SimpleKey, SimpleValue> map = redisson.getCache("simple");
        map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS);

        Assert.assertTrue(map.containsKey(new SimpleKey("33")));
        Assert.assertFalse(map.containsKey(new SimpleKey("34")));

        Thread.sleep(1000);

        Assert.assertFalse(map.containsKey(new SimpleKey("33")));
        Thread.sleep(50);
        Assert.assertEquals(0, map.size());
    }

    @Test
    public void testRemoveValue() {
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.remove(new SimpleKey("1"), new SimpleValue("2"));
        Assert.assertTrue(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertNull(val1);

        Assert.assertEquals(0, map.size());
    }

    @Test
    public void testRemoveValueFail() {
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.remove(new SimpleKey("2"), new SimpleValue("1"));
        Assert.assertFalse(res);

        boolean res1 = map.remove(new SimpleKey("1"), new SimpleValue("3"));
        Assert.assertFalse(res1);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertEquals("2", val1.getValue());
    }


    @Test
    public void testReplaceOldValueFail() {
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("43"), new SimpleValue("31"));
        Assert.assertFalse(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertEquals("2", val1.getValue());
    }

    @Test
    public void testReplaceOldValueSuccess() {
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3"));
        Assert.assertTrue(res);

        boolean res1 = map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3"));
        Assert.assertFalse(res1);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertEquals("3", val1.getValue());
    }

    @Test
    public void testReplaceValue() {
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        SimpleValue res = map.replace(new SimpleKey("1"), new SimpleValue("3"));
        Assert.assertEquals("2", res.getValue());

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertEquals("3", val1.getValue());
    }


    @Test
    public void testReplace() {
        Map<SimpleKey, SimpleValue> map = redisson.getCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        SimpleValue val1 = map.get(new SimpleKey("33"));
        Assert.assertEquals("44", val1.getValue());

        map.put(new SimpleKey("33"), new SimpleValue("abc"));
        SimpleValue val2 = map.get(new SimpleKey("33"));
        Assert.assertEquals("abc", val2.getValue());
    }

    @Test
    public void testScheduler() throws InterruptedException {
        RCache<SimpleKey, SimpleValue> map = redisson.getCache("simple", new MsgPackJacksonCodec());
        Assert.assertNull(map.get(new SimpleKey("33")));

        map.put(new SimpleKey("33"), new SimpleValue("44"), 5, TimeUnit.SECONDS);

        Thread.sleep(11000);

        Assert.assertEquals(0, map.size());

    }

    @Test
    public void testPutGet() throws InterruptedException {
        RCache<SimpleKey, SimpleValue> map = redisson.getCache("simple", new MsgPackJacksonCodec());
        Assert.assertNull(map.get(new SimpleKey("33")));

        map.put(new SimpleKey("33"), new SimpleValue("44"), 2, TimeUnit.SECONDS);

        SimpleValue val1 = map.get(new SimpleKey("33"));
        Assert.assertEquals("44", val1.getValue());

        Thread.sleep(1000);

        Assert.assertEquals(1, map.size());
        SimpleValue val2 = map.get(new SimpleKey("33"));
        Assert.assertEquals("44", val2.getValue());
        Assert.assertEquals(1, map.size());

        Thread.sleep(1000);

        Assert.assertNull(map.get(new SimpleKey("33")));
        Thread.sleep(50);
        Assert.assertEquals(0, map.size());
    }

    @Test
    public void testPutIfAbsent() throws Exception {
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getCache("simple");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.put(key, value);
        Assert.assertEquals(value, map.putIfAbsent(key, new SimpleValue("3")));
        Assert.assertEquals(value, map.get(key));

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        Assert.assertNull(map.putIfAbsent(key1, value1));
        Assert.assertEquals(value1, map.get(key1));

    }

    @Test
    public void testSize() {
        Map<SimpleKey, SimpleValue> map = redisson.getCache("simple");

        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));
        Assert.assertEquals(3, map.size());

        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));
        Assert.assertEquals(3, map.size());

        map.put(new SimpleKey("1"), new SimpleValue("21"));
        map.put(new SimpleKey("3"), new SimpleValue("41"));
        Assert.assertEquals(3, map.size());

        map.put(new SimpleKey("51"), new SimpleValue("6"));
        Assert.assertEquals(4, map.size());

        map.remove(new SimpleKey("3"));
        Assert.assertEquals(3, map.size());
    }

    @Test
    public void testEmptyRemove() {
        RCache<Integer, Integer> map = redisson.getCache("simple");
        Assert.assertFalse(map.remove(1, 3));
        map.put(4, 5);
        Assert.assertTrue(map.remove(4, 5));
    }

    @Test
    public void testPutAsync() throws InterruptedException, ExecutionException {
        RCache<Integer, Integer> map = redisson.getCache("simple");
        Future<Integer> future = map.putAsync(2, 3);
        Assert.assertNull(future.get());

        Assert.assertEquals((Integer) 3, map.get(2));

        Future<Integer> future1 = map.putAsync(2, 4);
        Assert.assertEquals((Integer) 3, future1.get());

        Assert.assertEquals((Integer) 4, map.get(2));
    }

    @Test
    public void testRemoveAsync() throws InterruptedException, ExecutionException {
        RCache<Integer, Integer> map = redisson.getCache("simple");
        map.put(1, 3);
        map.put(3, 5);
        map.put(7, 8);

        Assert.assertEquals((Integer) 3, map.removeAsync(1).get());
        Assert.assertEquals((Integer) 5, map.removeAsync(3).get());
        Assert.assertNull(map.removeAsync(10).get());
        Assert.assertEquals((Integer) 8, map.removeAsync(7).get());
    }

    @Test
    public void testFastRemoveAsync() throws InterruptedException, ExecutionException {
        RCache<Integer, Integer> map = redisson.getCache("simple");
        map.put(1, 3);
        map.put(3, 5);
        map.put(4, 6);
        map.put(7, 8);

        Assert.assertEquals((Long) 3L, map.fastRemoveAsync(1, 3, 7).get());
        Thread.sleep(1);
        Assert.assertEquals(1, map.size());
    }

//    @Test
//    public void testKeyIterator() {
//        RMap<Integer, Integer> map = redisson.getCache("simple");
//        map.put(1, 0);
//        map.put(3, 5);
//        map.put(4, 6);
//        map.put(7, 8);
//
//        Collection<Integer> keys = map.keySet();
//        MatcherAssert.assertThat(keys, Matchers.containsInAnyOrder(1, 3, 4, 7));
//        for (Iterator<Integer> iterator = map.keyIterator(); iterator.hasNext();) {
//            Integer value = iterator.next();
//            if (!keys.remove(value)) {
//                Assert.fail();
//            }
//        }
//
//        Assert.assertEquals(0, keys.size());
//    }

//    @Test
//    public void testValueIterator() {
//        RCache<Integer, Integer> map = redisson.getCache("simple");
//        map.put(1, 0);
//        map.put(3, 5);
//        map.put(4, 6);
//        map.put(7, 8);
//
//        Collection<Integer> values = map.values();
//        MatcherAssert.assertThat(values, Matchers.containsInAnyOrder(0, 5, 6, 8));
//        for (Iterator<Integer> iterator = map.valueIterator(); iterator.hasNext();) {
//            Integer value = iterator.next();
//            if (!values.remove(value)) {
//                Assert.fail();
//            }
//        }
//
//        Assert.assertEquals(0, values.size());
//    }

    @Test
    public void testFastPut() throws Exception {
        RCache<Integer, Integer> map = redisson.getCache("simple");
        Assert.assertTrue(map.fastPut(1, 2));
        Assert.assertFalse(map.fastPut(1, 3));
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testEquals() {
        RCache<String, String> map = redisson.getCache("simple");
        map.put("1", "7");
        map.put("2", "4");
        map.put("3", "5");

        Map<String, String> testMap = new HashMap<String, String>();
        testMap.put("1", "7");
        testMap.put("2", "4");
        testMap.put("3", "5");

        Assert.assertEquals(testMap, map);
        Assert.assertEquals(testMap.hashCode(), map.hashCode());
    }

    @Test
    public void testFastRemoveEmpty() throws Exception {
        RCache<Integer, Integer> map = redisson.getCache("simple");
        map.put(1, 3);
        Assert.assertEquals(0, map.fastRemove());
        Assert.assertEquals(1, map.size());
    }

    @Test(timeout = 5000)
    public void testDeserializationErrorReturnsErrorImmediately() throws Exception {
        redisson.getConfig().setCodec(new JsonJacksonCodec());

        RCache<String, SimpleObjectWithoutDefaultConstructor> map = redisson.getCache("deserializationFailure");
        SimpleObjectWithoutDefaultConstructor object = new SimpleObjectWithoutDefaultConstructor("test-val");

        Assert.assertEquals("test-val", object.getTestField());
        map.put("test-key", object);

        try {
            map.get("test-key");
            Assert.fail("Expected exception from map.get() call");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class SimpleObjectWithoutDefaultConstructor {

        private String testField;

        SimpleObjectWithoutDefaultConstructor(String testField) {
            this.testField = testField;
        }

        public String getTestField() {
            return testField;
        }

        public void setTestField(String testField) {
            this.testField = testField;
        }
    }
}
