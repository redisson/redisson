package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMapReactive;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.codec.CompositeCodec;

public class RedissonMapReactiveTest extends BaseReactiveTest {

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
    public void testIteratorSequence() throws InterruptedException {
        RMapReactive<Long, Long> map = redisson.getMap("map");
        for (int i = 0; i < 1000; i++) {
            sync(map.put(Long.valueOf(i), Long.valueOf(i)));
        }

        Map<Long, Long> setCopy = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            setCopy.put(Long.valueOf(i), Long.valueOf(i));
        }

        checkIterator(map, setCopy);
    }

    private <K, V> void checkIterator(RMapReactive<K, V> set, Map<K, V> setCopy) {
        for (Iterator<Entry<K, V>> iterator = toIterator(set.entryIterator()); iterator.hasNext();) {
            Entry<K, V> entry = iterator.next();
            if (!setCopy.remove(entry.getKey(), entry.getValue())) {
                Assertions.fail();
            }
        }

        Assertions.assertEquals(0, setCopy.size());
    }
    
    @Test
    public void testAddAndGet() throws InterruptedException {
        RMapReactive<Integer, Integer> map = redisson.getMap("getAll", new CompositeCodec(redisson.getConfig().getCodec(), IntegerCodec.INSTANCE));
        sync(map.put(1, 100));

        Integer res = sync(map.addAndGet(1, 12));
        Assertions.assertEquals(112, (int)res);
        res = sync(map.get(1));
        Assertions.assertEquals(112, (int)res);

        RMapReactive<Integer, Double> map2 = redisson.getMap("getAll2", new CompositeCodec(redisson.getConfig().getCodec(), DoubleCodec.INSTANCE));
        sync(map2.put(1, new Double(100.2)));

        Double res2 = sync(map2.addAndGet(1, new Double(12.1)));
        Assertions.assertTrue(new Double(112.3).compareTo(res2) == 0);
        res2 = sync(map2.get(1));
        Assertions.assertTrue(new Double(112.3).compareTo(res2) == 0);

        RMapReactive<String, Integer> mapStr = redisson.getMap("mapStr", new CompositeCodec(redisson.getConfig().getCodec(), IntegerCodec.INSTANCE));
        assertThat(sync(mapStr.put("1", 100))).isNull();

        assertThat(sync(mapStr.addAndGet("1", 12))).isEqualTo(112);
        assertThat(sync(mapStr.get("1"))).isEqualTo(112);
    }

    @Test
    public void testGetAll() {
        RMapReactive<Integer, Integer> map = redisson.getMap("getAll");
        sync(map.put(1, 100));
        sync(map.put(2, 200));
        sync(map.put(3, 300));
        sync(map.put(4, 400));

        Map<Integer, Integer> filtered = sync(map.getAll(new HashSet<Integer>(Arrays.asList(2, 3, 5))));

        Map<Integer, Integer> expectedMap = new HashMap<Integer, Integer>();
        expectedMap.put(2, 200);
        expectedMap.put(3, 300);
        Assertions.assertEquals(expectedMap, filtered);
    }

    @Test
    public void testGetAllWithStringKeys() {
        RMapReactive<String, Integer> map = redisson.getMap("getAllStrings");
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
    public void testInteger() {
        RMapReactive<Integer, Integer> map = redisson.getMap("test_int");
        sync(map.put(1, 2));
        sync(map.put(3, 4));

        Assertions.assertEquals(2, sync(map.size()).intValue());

        Integer val = sync(map.get(1));
        Assertions.assertEquals(2, val.intValue());
        Integer val2 = sync(map.get(3));
        Assertions.assertEquals(4, val2.intValue());
    }

    @Test
    public void testLong() {
        RMapReactive<Long, Long> map = redisson.getMap("test_long");
        sync(map.put(1L, 2L));
        sync(map.put(3L, 4L));

        Assertions.assertEquals(2, sync(map.size()).intValue());

        Long val = sync(map.get(1L));
        Assertions.assertEquals(2L, val.longValue());
        Long val2 = sync(map.get(3L));
        Assertions.assertEquals(4L, val2.longValue());
    }

    @Test
    public void testSimpleTypes() {
        RMapReactive<Integer, String> map = redisson.getMap("simple12");
        sync(map.put(1, "12"));
        sync(map.put(2, "33"));
        sync(map.put(3, "43"));

        String val = sync(map.get(2));
        Assertions.assertEquals("33", val);
    }

    @Test
    public void testRemove() {
        RMapReactive<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        sync(map.put(new SimpleKey("1"), new SimpleValue("2")));
        sync(map.put(new SimpleKey("33"), new SimpleValue("44")));
        sync(map.put(new SimpleKey("5"), new SimpleValue("6")));

        sync(map.remove(new SimpleKey("33")));
        sync(map.remove(new SimpleKey("5")));

        Assertions.assertEquals(1, sync(map.size()).intValue());
    }

    @Test
    public void testPutAll() {
        RMapReactive<Integer, String> map = redisson.getMap("simple");
        sync(map.put(1, "1"));
        sync(map.put(2, "2"));
        sync(map.put(3, "3"));

        Map<Integer, String> joinMap = new HashMap<Integer, String>();
        joinMap.put(4, "4");
        joinMap.put(5, "5");
        joinMap.put(6, "6");
        sync(map.putAll(joinMap));

        assertThat(toIterable(map.keyIterator())).contains(1, 2, 3, 4, 5, 6);
    }

    @Test
    public void testContainsValue() {
        RMapReactive<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        sync(map.put(new SimpleKey("1"), new SimpleValue("2")));
        sync(map.put(new SimpleKey("33"), new SimpleValue("44")));
        sync(map.put(new SimpleKey("5"), new SimpleValue("6")));

        Assertions.assertTrue(sync(map.containsValue(new SimpleValue("2"))));
        Assertions.assertFalse(sync(map.containsValue(new SimpleValue("441"))));
        Assertions.assertFalse(sync(map.containsValue(new SimpleKey("5"))));
    }

    @Test
    public void testContainsKey() {
        RMapReactive<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        sync(map.put(new SimpleKey("1"), new SimpleValue("2")));
        sync(map.put(new SimpleKey("33"), new SimpleValue("44")));
        sync(map.put(new SimpleKey("5"), new SimpleValue("6")));

        Assertions.assertTrue(sync(map.containsKey(new SimpleKey("33"))));
        Assertions.assertFalse(sync(map.containsKey(new SimpleKey("34"))));
    }

    @Test
    public void testRemoveValue() {
        RMapReactive<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        sync(map.put(new SimpleKey("1"), new SimpleValue("2")));

        boolean size = sync(map.remove(new SimpleKey("1"), new SimpleValue("2")));
        Assertions.assertTrue(size);

        SimpleValue val1 = sync(map.get(new SimpleKey("1")));
        Assertions.assertNull(val1);

        Assertions.assertEquals(0, sync(map.size()).intValue());
    }

    @Test
    public void testRemoveValueFail() {
        RMapReactive<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        sync(map.put(new SimpleKey("1"), new SimpleValue("2")));

        boolean removed = sync(map.remove(new SimpleKey("2"), new SimpleValue("1")));
        Assertions.assertFalse(removed);

        boolean size2 = sync(map.remove(new SimpleKey("1"), new SimpleValue("3")));
        Assertions.assertFalse(size2);

        SimpleValue val1 = sync(map.get(new SimpleKey("1")));
        Assertions.assertEquals("2", val1.getValue());
    }


    @Test
    public void testReplaceOldValueFail() {
        RMapReactive<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        sync(map.put(new SimpleKey("1"), new SimpleValue("2")));

        boolean res = sync(map.replace(new SimpleKey("1"), new SimpleValue("43"), new SimpleValue("31")));
        Assertions.assertFalse(res);

        SimpleValue val1 = sync(map.get(new SimpleKey("1")));
        Assertions.assertEquals("2", val1.getValue());
    }

    @Test
    public void testReplaceOldValueSuccess() {
        RMapReactive<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        sync(map.put(new SimpleKey("1"), new SimpleValue("2")));

        boolean res = sync(map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3")));
        Assertions.assertTrue(res);

        boolean res1 = sync(map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3")));
        Assertions.assertFalse(res1);

        SimpleValue val1 = sync(map.get(new SimpleKey("1")));
        Assertions.assertEquals("3", val1.getValue());
    }

    @Test
    public void testReplaceValue() {
        RMapReactive<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        sync(map.put(new SimpleKey("1"), new SimpleValue("2")));

        SimpleValue res = sync(map.replace(new SimpleKey("1"), new SimpleValue("3")));
        Assertions.assertEquals("2", res.getValue());

        SimpleValue val1 = sync(map.get(new SimpleKey("1")));
        Assertions.assertEquals("3", val1.getValue());
    }


    @Test
    public void testReplace() {
        RMapReactive<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        sync(map.put(new SimpleKey("1"), new SimpleValue("2")));
        sync(map.put(new SimpleKey("33"), new SimpleValue("44")));
        sync(map.put(new SimpleKey("5"), new SimpleValue("6")));

        SimpleValue val1 = sync(map.get(new SimpleKey("33")));
        Assertions.assertEquals("44", val1.getValue());

        sync(map.put(new SimpleKey("33"), new SimpleValue("abc")));
        SimpleValue val2 = sync(map.get(new SimpleKey("33")));
        Assertions.assertEquals("abc", val2.getValue());
    }

    @Test
    public void testPutGet() {
        RMapReactive<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        sync(map.put(new SimpleKey("1"), new SimpleValue("2")));
        sync(map.put(new SimpleKey("33"), new SimpleValue("44")));
        sync(map.put(new SimpleKey("5"), new SimpleValue("6")));

        SimpleValue val1 = sync(map.get(new SimpleKey("33")));
        Assertions.assertEquals("44", val1.getValue());

        SimpleValue val2 = sync(map.get(new SimpleKey("5")));
        Assertions.assertEquals("6", val2.getValue());
    }

    @Test
    public void testPutIfAbsent() throws Exception {
        RMapReactive<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        sync(map.put(key, value));
        Assertions.assertEquals(value, sync(map.putIfAbsent(key, new SimpleValue("3"))));
        Assertions.assertEquals(value, sync(map.get(key)));

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        Assertions.assertNull(sync(map.putIfAbsent(key1, value1)));
        Assertions.assertEquals(value1, sync(map.get(key1)));

    }

    @Test
    public void testSize() {
        RMapReactive<SimpleKey, SimpleValue> map = redisson.getMap("simple");

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
        RMapReactive<Integer, Integer> map = redisson.getMap("simple");
        assertThat(sync(map.remove(1, 3))).isFalse();
        sync(map.put(4, 5));
        assertThat(sync(map.remove(4, 5))).isTrue();
    }

    @Test
    public void testFastRemoveAsync() throws InterruptedException, ExecutionException {
        RMapReactive<Integer, Integer> map = redisson.getMap("simple");
        sync(map.put(1, 3));
        sync(map.put(3, 5));
        sync(map.put(4, 6));
        sync(map.put(7, 8));

        Assertions.assertEquals((Long) 3L, sync(map.fastRemove(1, 3, 7)));
        Assertions.assertEquals(1, sync(map.size()).intValue());
    }

    @Test
    public void testKeyIterator() {
        RMapReactive<Integer, Integer> map = redisson.getMap("simple");
        sync(map.put(1, 0));
        sync(map.put(3, 5));
        sync(map.put(4, 6));
        sync(map.put(7, 8));

        List<Integer> keys = new ArrayList<Integer>(Arrays.asList(1, 3, 4, 7));
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
        RMapReactive<Integer, Integer> map = redisson.getMap("simple");
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

    @Test
    public void testFastPut() throws Exception {
        RMapReactive<Integer, Integer> map = redisson.getMap("simple");
        Assertions.assertTrue(sync(map.fastPut(1, 2)));
        Assertions.assertFalse(sync(map.fastPut(1, 3)));
        Assertions.assertEquals(1, sync(map.size()).intValue());
    }

    @Test
    public void testFastRemoveEmpty() throws Exception {
        RMapReactive<Integer, Integer> map = redisson.getMap("simple");
        sync(map.put(1, 3));
        Assertions.assertEquals(0, sync(map.fastRemove()).intValue());
        Assertions.assertEquals(1, sync(map.size()).intValue());
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
