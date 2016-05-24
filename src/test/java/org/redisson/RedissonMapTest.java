package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.core.Predicate;
import org.redisson.core.RMap;

import io.netty.util.concurrent.Future;

public class RedissonMapTest extends BaseTest {

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
        RMap<Integer, Integer> map = redisson.getMap("getAll");
        map.put(1, 100);

        Integer res = map.addAndGet(1, 12);
        assertThat(res).isEqualTo(112);
        res = map.get(1);
        assertThat(res).isEqualTo(112);

        RMap<Integer, Double> map2 = redisson.getMap("getAll2");
        map2.put(1, new Double(100.2));

        Double res2 = map2.addAndGet(1, new Double(12.1));
        assertThat(res2).isEqualTo(112.3);
        res2 = map2.get(1);
        assertThat(res2).isEqualTo(112.3);

        RMap<String, Integer> mapStr = redisson.getMap("mapStr");
        assertThat(mapStr.put("1", 100)).isNull();

        assertThat(mapStr.addAndGet("1", 12)).isEqualTo(112);
        assertThat(mapStr.get("1")).isEqualTo(112);
    }

    @Test
    public void testGetAll() {
        RMap<Integer, Integer> map = redisson.getMap("getAll");
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);
        map.put(4, 400);

        Map<Integer, Integer> filtered = map.getAll(new HashSet<Integer>(Arrays.asList(2, 3, 5)));

        Map<Integer, Integer> expectedMap = new HashMap<Integer, Integer>();
        expectedMap.put(2, 200);
        expectedMap.put(3, 300);
        assertThat(filtered).isEqualTo(expectedMap);
    }

    @Test
    public void testGetAllWithStringKeys() {
        RMap<String, Integer> map = redisson.getMap("getAllStrings");
        map.put("A", 100);
        map.put("B", 200);
        map.put("C", 300);
        map.put("D", 400);

        Map<String, Integer> filtered = map.getAll(new HashSet<String>(Arrays.asList("B", "C", "E")));

        Map<String, Integer> expectedMap = new HashMap<String, Integer>();
        expectedMap.put("B", 200);
        expectedMap.put("C", 300);
        assertThat(filtered).isEqualTo(expectedMap);
    }

    @Test
    public void testFilterKeys() {
        RMap<Integer, Integer> map = redisson.getMap("filterKeys");
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);
        map.put(4, 400);

        Map<Integer, Integer> filtered = map.filterKeys(input -> input >= 2 && input <= 3);

        Map<Integer, Integer> expectedMap = new HashMap<Integer, Integer>();
        expectedMap.put(2, 200);
        expectedMap.put(3, 300);
        assertThat(filtered).isEqualTo(expectedMap);
    }

    @Test
    public void testStringCodec() {
        Config config = createConfig();
        config.setCodec(StringCodec.INSTANCE);
        RedissonClient redisson = Redisson.create(config);

        RMap<String, String> rmap = redisson.getMap("TestRMap01");
        rmap.put("A", "1");
        rmap.put("B", "2");

        Iterator<Map.Entry<String, String>> iterator = rmap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> next = iterator.next();
            assertThat(next).isIn(new AbstractMap.SimpleEntry("A", "1"), new AbstractMap.SimpleEntry("B", "2"));
        }

        redisson.shutdown();
    }

    @Test
    public void testInteger() {
        Map<Integer, Integer> map = redisson.getMap("test_int");
        map.put(1, 2);
        map.put(3, 4);

        assertThat(map.size()).isEqualTo(2);

        Integer val = map.get(1);
        assertThat(val).isEqualTo(2);

        Integer val2 = map.get(3);
        assertThat(val2).isEqualTo(4);
    }

    @Test
    public void testLong() {
        Map<Long, Long> map = redisson.getMap("test_long");
        map.put(1L, 2L);
        map.put(3L, 4L);

        assertThat(map.size()).isEqualTo(2);

        Long val = map.get(1L);
        assertThat(val).isEqualTo(2);

        Long val2 = map.get(3L);
        assertThat(val2).isEqualTo(4);
    }

    @Test
    public void testIteratorRemoveHighVolume() throws InterruptedException {
        RMap<Integer, Integer> map = redisson.getMap("simpleMap");
        for (int i = 0; i < 10000; i++) {
            map.put(i, i*10);
        }
        
        int cnt = 0;
        Iterator<Integer> iterator = map.keySet().iterator();
        while (iterator.hasNext()) {
            Integer integer = iterator.next();
            iterator.remove();
            cnt++;
        }
        Assert.assertEquals(10000, cnt);
        assertThat(map).isEmpty();
        Assert.assertEquals(0, map.size());
    }

    
    @Test
    public void testIterator() {
        RMap<Integer, Integer> rMap = redisson.getMap("123");

        int size = 1000;
        for (int i = 0; i < size; i++) {
            rMap.put(i,i);
        }
        assertThat(rMap.size()).isEqualTo(1000);

        int counter = 0;
        for (Integer key : rMap.keySet()) {
            counter++;
        }
        assertThat(counter).isEqualTo(size);
        counter = 0;
        for (Integer value : rMap.values()) {
            counter++;
        }
        assertThat(counter).isEqualTo(size);
        counter = 0;
        for (Entry<Integer, Integer> entry : rMap.entrySet()) {
            counter++;
        }
        assertThat(counter).isEqualTo(size);
   }

    @Test
    public void testNull() {
        Map<Integer, String> map = redisson.getMap("simple12");
        map.put(1, null);
        map.put(2, null);
        map.put(3, "43");

        assertThat(map.size()).isEqualTo(3);

        String val = map.get(2);
        assertThat(val).isNull();
        String val2 = map.get(1);
        assertThat(val2).isNull();
        String val3 = map.get(3);
        assertThat(val3).isEqualTo("43");
    }

    @Test
    public void testEntrySet() {
        Map<Integer, String> map = redisson.getMap("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        assertThat(map.entrySet().size()).isEqualTo(3);
        Map<Integer, String> testMap = new HashMap<Integer, String>(map);
        assertThat(map.entrySet()).containsOnlyElementsOf(testMap.entrySet());
    }

    @Test
    public void testReadAllEntrySet() {
        RMap<Integer, String> map = redisson.getMap("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        assertThat(map.readAllEntrySet().size()).isEqualTo(3);
        Map<Integer, String> testMap = new HashMap<Integer, String>(map);
        assertThat(map.readAllEntrySet()).containsOnlyElementsOf(testMap.entrySet());
    }

    @Test
    public void testSimpleTypes() {
        Map<Integer, String> map = redisson.getMap("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        String val = map.get(2);
        assertThat(val).isEqualTo("33");
    }

    @Test
    public void testRemove() {
        Map<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        map.remove(new SimpleKey("33"));
        map.remove(new SimpleKey("5"));

        assertThat(map.size()).isEqualTo(1);
    }

    @Test
    public void testPutAll() {
        Map<Integer, String> map = redisson.getMap("simple");
        map.put(1, "1");
        map.put(2, "2");
        map.put(3, "3");

        Map<Integer, String> joinMap = new HashMap<Integer, String>();
        joinMap.put(4, "4");
        joinMap.put(5, "5");
        joinMap.put(6, "6");
        map.putAll(joinMap);

        assertThat(map.keySet()).containsOnly(1, 2, 3, 4, 5, 6);
    }

    @Test
    public void testKeySet() {
        Map<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        Assert.assertTrue(map.keySet().contains(new SimpleKey("33")));
        Assert.assertFalse(map.keySet().contains(new SimpleKey("44")));
    }

    @Test
    public void testReadAllKeySet() {
        RMap<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        assertThat(map.readAllKeySet().size()).isEqualTo(3);
        Map<SimpleKey, SimpleValue> testMap = new HashMap<>(map);
        assertThat(map.readAllKeySet()).containsOnlyElementsOf(testMap.keySet());
    }
    
    @Test
    public void testReadAllKeySetHighAmount() {
        RMap<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        for (int i = 0; i < 1000; i++) {
            map.put(new SimpleKey("" + i), new SimpleValue("" + i));
        }

        assertThat(map.readAllKeySet().size()).isEqualTo(1000);
        Map<SimpleKey, SimpleValue> testMap = new HashMap<>(map);
        assertThat(map.readAllKeySet()).containsOnlyElementsOf(testMap.keySet());
    }

    @Test
    public void testReadAllValues() {
        RMap<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        assertThat(map.readAllValues().size()).isEqualTo(3);
        Map<SimpleKey, SimpleValue> testMap = new HashMap<>(map);
        assertThat(map.readAllValues()).containsOnlyElementsOf(testMap.values());
    }

    @Test
    public void testContainsValue() {
        Map<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        Assert.assertTrue(map.containsValue(new SimpleValue("2")));
        Assert.assertFalse(map.containsValue(new SimpleValue("441")));
        Assert.assertFalse(map.containsValue(new SimpleKey("5")));
    }

    @Test
    public void testContainsKey() {
        Map<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        Assert.assertTrue(map.containsKey(new SimpleKey("33")));
        Assert.assertFalse(map.containsKey(new SimpleKey("34")));
    }

    @Test
    public void testRemoveValue() {
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.remove(new SimpleKey("1"), new SimpleValue("2"));
        Assert.assertTrue(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertNull(val1);

        Assert.assertEquals(0, map.size());
    }

    @Test
    public void testRemoveValueFail() {
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getMap("simple");
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
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("43"), new SimpleValue("31"));
        Assert.assertFalse(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertEquals("2", val1.getValue());
    }

    @Test
    public void testReplaceOldValueSuccess() {
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getMap("simple");
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
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        SimpleValue res = map.replace(new SimpleKey("1"), new SimpleValue("3"));
        Assert.assertEquals("2", res.getValue());

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertEquals("3", val1.getValue());
    }


    @Test
    public void testReplace() {
        Map<SimpleKey, SimpleValue> map = redisson.getMap("simple");
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
    public void testPutGet() {
        Map<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        SimpleValue val1 = map.get(new SimpleKey("33"));
        Assert.assertEquals("44", val1.getValue());

        SimpleValue val2 = map.get(new SimpleKey("5"));
        Assert.assertEquals("6", val2.getValue());
    }

    @Test
    public void testPutIfAbsent() throws Exception {
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getMap("simple");
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
    public void testFastPutIfAbsent() throws Exception {
        RMap<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.put(key, value);
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("3"))).isFalse();
        assertThat(map.get(key)).isEqualTo(value);

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        assertThat(map.fastPutIfAbsent(key1, value1)).isTrue();
        assertThat(map.get(key1)).isEqualTo(value1);
    }

    @Test
    public void testSize() {
        Map<SimpleKey, SimpleValue> map = redisson.getMap("simple");

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
        RMap<Integer, Integer> map = redisson.getMap("simple");
        Assert.assertFalse(map.remove(1, 3));
        map.put(4, 5);
        Assert.assertTrue(map.remove(4, 5));
    }

    @Test
    public void testPutAsync() throws InterruptedException, ExecutionException {
        RMap<Integer, Integer> map = redisson.getMap("simple");
        Future<Integer> future = map.putAsync(2, 3);
        Assert.assertNull(future.get());

        Assert.assertEquals((Integer) 3, map.get(2));

        Future<Integer> future1 = map.putAsync(2, 4);
        Assert.assertEquals((Integer) 3, future1.get());

        Assert.assertEquals((Integer) 4, map.get(2));
    }

    @Test
    public void testRemoveAsync() throws InterruptedException, ExecutionException {
        RMap<Integer, Integer> map = redisson.getMap("simple");
        map.put(1, 3);
        map.put(3, 5);
        map.put(7, 8);

        assertThat(map.removeAsync(1).get()).isEqualTo(3);
        assertThat(map.removeAsync(3).get()).isEqualTo(5);
        assertThat(map.removeAsync(10).get()).isNull();
        assertThat(map.removeAsync(7).get()).isEqualTo(8);
    }

    @Test
    public void testFastRemoveAsync() throws InterruptedException, ExecutionException {
        RMap<Integer, Integer> map = redisson.getMap("simple");
        map.put(1, 3);
        map.put(3, 5);
        map.put(4, 6);
        map.put(7, 8);

        assertThat(map.fastRemoveAsync(1, 3, 7).get()).isEqualTo(3);
        Thread.sleep(1);
        assertThat(map.size()).isEqualTo(1);
    }

    @Test
    public void testKeyIterator() {
        RMap<Integer, Integer> map = redisson.getMap("simple");
        map.put(1, 0);
        map.put(3, 5);
        map.put(4, 6);
        map.put(7, 8);

        Collection<Integer> keys = map.keySet();
        assertThat(keys).containsOnly(1, 3, 4, 7);
        for (Iterator<Integer> iterator = map.keyIterator(); iterator.hasNext();) {
            Integer value = iterator.next();
            if (!keys.remove(value)) {
                Assert.fail();
            }
        }

        assertThat(keys.size()).isEqualTo(0);
    }

    @Test
    public void testValueIterator() {
        RMap<Integer, Integer> map = redisson.getMap("simple");
        map.put(1, 0);
        map.put(3, 5);
        map.put(4, 6);
        map.put(7, 8);

        Collection<Integer> values = map.values();
        assertThat(values).containsOnly(0, 5, 6, 8);
        for (Iterator<Integer> iterator = map.valueIterator(); iterator.hasNext();) {
            Integer value = iterator.next();
            if (!values.remove(value)) {
                Assert.fail();
            }
        }

        assertThat(values.size()).isEqualTo(0);
    }

    @Test
    public void testFastPut() throws Exception {
        RMap<Integer, Integer> map = redisson.getMap("simple");
        Assert.assertTrue(map.fastPut(1, 2));
        Assert.assertFalse(map.fastPut(1, 3));
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testEquals() {
        RMap<String, String> map = redisson.getMap("simple");
        map.put("1", "7");
        map.put("2", "4");
        map.put("3", "5");

        Map<String, String> testMap = new HashMap<String, String>();
        testMap.put("1", "7");
        testMap.put("2", "4");
        testMap.put("3", "5");

        assertThat(map).isEqualTo(testMap);
        assertThat(testMap.hashCode()).isEqualTo(map.hashCode());
    }

    @Test
    public void testFastRemoveEmpty() throws Exception {
        RMap<Integer, Integer> map = redisson.getMap("simple");
        map.put(1, 3);

        assertThat(map.fastRemove()).isZero();
        assertThat(map.size()).isEqualTo(1);
    }

    @Test(timeout = 5000)
    public void testDeserializationErrorReturnsErrorImmediately() throws Exception {
        redisson.getConfig().setCodec(new JsonJacksonCodec());

        RMap<String, SimpleObjectWithoutDefaultConstructor> map = redisson.getMap("deserializationFailure");
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
