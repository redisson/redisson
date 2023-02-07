package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.api.map.MapLoader;
import org.redisson.api.map.MapLoaderAsync;
import org.redisson.api.map.MapWriter;
 import org.redisson.api.map.MapWriterAsync;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class BaseMapTest extends BaseTest {

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
    
    protected void destroy(RMap<?, ?> map) {
        map.destroy();
    }

    @Test
    public void testRandomKeys() {
        Assumptions.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("6.2.0") > 0);

        RMap<Integer, Integer> map = getMap("map");
        Set<Integer> e1 = map.randomKeys(1);
        assertThat(e1).isEmpty();
        map.put(1, 11);
        map.put(2, 21);
        map.put(3, 31);
        map.put(4, 41);
        Set<Integer> e = map.randomKeys(2);
        assertThat(e).containsAnyOf(1, 2, 3, 4).hasSize(2);
    }

    @Test
    public void testRandomEntries() {
        Assumptions.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("6.2.0") > 0);

        RMap<Integer, Integer> map = getMap("map");
        Map<Integer, Integer> e1 = map.randomEntries(1);
        assertThat(e1).isEmpty();
        map.put(1, 11);
        map.put(2, 21);
        map.put(3, 31);
        map.put(4, 41);
        Map<Integer, Integer> e = map.randomEntries(2);
        assertThat(e.keySet()).containsAnyOf(1, 2, 3, 4).hasSize(2);
    }

    @Test
    public void testComputeIfPresent() {
        RMap<String, String> map = getMap("map");
        map.computeIfPresent("1", (key, value) -> {
            return "12";
        });
        assertThat(map.get("1")).isNull();

        map.put("1", "10");
        map.computeIfPresent("1", (key, value) -> {
            assertThat(value).isEqualTo("10");
            return "12";
        });
        assertThat(map.get("1")).isEqualTo("12");
    }

    public static class MyClass implements Serializable {

        private String name;

        public MyClass(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MyClass myClass = (MyClass) o;
            return Objects.equals(name, myClass.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return "MyClass{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }

    @Test
    public void testComputeIfPresentMutable() {
        RMap<String, MyClass> map = getMap("map");

        map.put("1", new MyClass("value1"));
        map.computeIfPresent("1", (key, value) -> {
            assertThat(value).isEqualTo(new MyClass("value1"));
            value.setName("value2");
            return value;
        });
        assertThat(map.get("1")).isEqualTo(new MyClass("value2"));
    }


    @Test
    public void testComputeIfAbsent() {
        RMap<String, String> map = getMap("map");
        map.computeIfAbsent("1", (key) -> {
            assertThat(key).isEqualTo("1");
            return "12";
        });
        assertThat(map.get("1")).isEqualTo("12");

        map.computeIfAbsent("1", (key) -> {
            assertThat(key).isEqualTo("1");
            return "13";
        });
        assertThat(map.get("1")).isEqualTo("12");
    }

    @Test
    public void testMerge() {
        RMap<String, String> map = getMap("map");
        map.merge("1", "2", (key, oldValue) -> {
            return "12";
        });
        assertThat(map.get("1")).isEqualTo("2");

        map.merge("1", "2", (key, oldValue) -> {
            return "12";
        });
        assertThat(map.get("1")).isEqualTo("12");

        map.merge("1", "2", (key, oldValue) -> {
            return null;
        });
        assertThat(map.get("1")).isNull();
    }

    @Test
    public void testCompute() {
        RMap<String, String> map = getMap("map");
        map.compute("1", (key, oldValue) -> {
            return "12";
        });
        assertThat(map.get("1")).isEqualTo("12");

        map.compute("1", (key, oldValue) -> {
            return null;
        });
        assertThat(map.get("1")).isNull();
    }

    @Test
    public void testGetAllWithStringKeys() {
        RMap<String, Integer> map = getMap("getAllStrings");
        map.put("A", 100);
        map.put("B", 200);
        map.put("C", 300);
        map.put("D", 400);

        Map<String, Integer> filtered = map.getAll(new HashSet<String>(Arrays.asList("B", "C", "E")));

        Map<String, Integer> expectedMap = new HashMap<String, Integer>();
        expectedMap.put("B", 200);
        expectedMap.put("C", 300);
        assertThat(filtered).isEqualTo(expectedMap);
        
        destroy(map);
    }

    @Test
    public void testStringCodec() {
        Config config = createConfig();
        config.setCodec(StringCodec.INSTANCE);
        RedissonClient redisson = Redisson.create(config);

        RMap<String, String> rmap = redisson.getMap("TestRMap01");
        rmap.put("A", "1");
        rmap.put("B", "2");

        for (Entry<String, String> next : rmap.entrySet()) {
            assertThat(next).isIn(new AbstractMap.SimpleEntry("A", "1"), new AbstractMap.SimpleEntry("B", "2"));
        }

        destroy(rmap);
        redisson.shutdown();
    }

    @Test
    public void testInteger() {
        RMap<Integer, Integer> map = getMap("test_int");
        map.put(1, 2);
        map.put(3, 4);

        assertThat(map.size()).isEqualTo(2);

        Integer val = map.get(1);
        assertThat(val).isEqualTo(2);

        Integer val2 = map.get(3);
        assertThat(val2).isEqualTo(4);
        destroy(map);
    }

    @Test
    public void testLong() {
        RMap<Long, Long> map = getMap("test_long");
        map.put(1L, 2L);
        map.put(3L, 4L);

        assertThat(map.size()).isEqualTo(2);

        Long val = map.get(1L);
        assertThat(val).isEqualTo(2);

        Long val2 = map.get(3L);
        assertThat(val2).isEqualTo(4);
        destroy(map);
    }

    @Test
    public void testIterator() {
        RMap<Integer, Integer> rMap = getMap("123");

        int size = 1000;
        for (int i = 0; i < size; i++) {
            rMap.put(i, i);
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
        destroy(rMap);
    }
    
    @Test
    public void testOrdering() {
        Map<String, String> map = new LinkedHashMap<String, String>();

        // General player data
        map.put("name", "123");
        map.put("ip", "4124");
        map.put("rank", "none");
        map.put("tokens", "0");
        map.put("coins", "0");

        // Arsenal player statistics
        map.put("ar_score", "0");
        map.put("ar_gameswon", "0");
        map.put("ar_gameslost", "0");
        map.put("ar_kills", "0");
        map.put("ar_deaths", "0");

        RMap<String, String> rmap = getMap("123");
        Assumptions.assumeTrue(!(rmap instanceof RLocalCachedMap));
        
        rmap.putAll(map);

        assertThat(rmap.keySet()).containsExactlyElementsOf(map.keySet());
        assertThat(rmap.readAllKeySet()).containsExactlyElementsOf(map.keySet());
        
        assertThat(rmap.values()).containsExactlyElementsOf(map.values());
        assertThat(rmap.readAllValues()).containsExactlyElementsOf(map.values());
        
        assertThat(rmap.entrySet()).containsExactlyElementsOf(map.entrySet());
        assertThat(rmap.readAllEntrySet()).containsExactlyElementsOf(map.entrySet());
        destroy(rmap);
    }
    
    @Test
    public void testNullValue() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            RMap<Integer, String> map = getMap("simple12");
            destroy(map);
            map.put(1, null);
        });
    }
    
    @Test
    public void testNullKey() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            RMap<Integer, String> map = getMap("simple12");
            destroy(map);
            map.put(null, "1");
        });
    }

    @Test
    public void testSize() {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");

        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));
        assertThat(map.size()).isEqualTo(3);

        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("3"), new SimpleValue("4"));
        assertThat(map.size()).isEqualTo(3);

        map.put(new SimpleKey("1"), new SimpleValue("21"));
        map.put(new SimpleKey("3"), new SimpleValue("41"));
        assertThat(map.size()).isEqualTo(3);

        map.put(new SimpleKey("51"), new SimpleValue("6"));
        assertThat(map.size()).isEqualTo(4);

        map.remove(new SimpleKey("3"));
        assertThat(map.size()).isEqualTo(3);
        destroy(map);
    }

    @Test
    public void testEmptyRemove() {
        RMap<Integer, Integer> map = getMap("simple");
        assertThat(map.remove(1, 3)).isFalse();
        map.put(4, 5);
        assertThat(map.remove(4, 5)).isTrue();
        destroy(map);
    }

    @Test
    public void testFastPutIfAbsent() throws Exception {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.put(key, value);
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("3"))).isFalse();
        assertThat(map.get(key)).isEqualTo(value);

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        assertThat(map.fastPutIfAbsent(key1, value1)).isTrue();
        assertThat(map.get(key1)).isEqualTo(value1);
        destroy(map);
    }
    
    @Test
    public void testPutAll() {
        RMap<Integer, String> map = getMap("simple");
        map.put(1, "1");
        map.put(2, "2");
        map.put(3, "3");

        Map<Integer, String> joinMap = new HashMap<Integer, String>();
        joinMap.put(4, "4");
        joinMap.put(5, "5");
        joinMap.put(6, "6");
        map.putAll(joinMap);

        assertThat(map.keySet()).containsOnly(1, 2, 3, 4, 5, 6);
        destroy(map);
    }
    
    @Test
    public void testPutAllBatched() {
        RMap<Integer, String> map = getMap("simple");
        map.put(1, "1");
        map.put(2, "2");
        map.put(3, "3");

        Map<Integer, String> joinMap = new HashMap<Integer, String>();
        joinMap.put(4, "4");
        joinMap.put(5, "5");
        joinMap.put(6, "6");
        map.putAll(joinMap, 5);

        assertThat(map.keySet()).containsOnly(1, 2, 3, 4, 5, 6);
        
        Map<Integer, String> joinMap2 = new HashMap<Integer, String>();
        joinMap2.put(7, "7");
        joinMap2.put(8, "8");
        joinMap2.put(9, "9");
        joinMap2.put(10, "10");
        joinMap2.put(11, "11");
        map.putAll(joinMap2, 5);
        
        assertThat(map.keySet()).containsOnly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);

        Map<Integer, String> joinMap3 = new HashMap<Integer, String>();
        joinMap3.put(12, "12");
        joinMap3.put(13, "13");
        joinMap3.put(14, "14");
        joinMap3.put(15, "15");
        joinMap3.put(16, "16");
        joinMap3.put(17, "17");
        joinMap3.put(18, "18");
        map.putAll(joinMap3, 5);
        
        assertThat(map.keySet()).containsOnly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18);
        
        destroy(map);
    }
    
    @Test
    public void testPutAllBig() {
        Map<Integer, String> joinMap = new HashMap<Integer, String>();
        for (int i = 0; i < 100000; i++) {
            joinMap.put(i, "" + i);
        }
        
        RMap<Integer, String> map = getMap("simple");
        map.putAll(joinMap);
        
        assertThat(map.size()).isEqualTo(joinMap.size());
        destroy(map);
    }
    
    @Test
    public void testPutGet() {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        SimpleValue val1 = map.get(new SimpleKey("33"));
        assertThat(val1.getValue()).isEqualTo("44");

        SimpleValue val2 = map.get(new SimpleKey("5"));
        assertThat(val2.getValue()).isEqualTo("6");
        destroy(map);
    }

    @Test
    public void testPutIfAbsent() throws Exception {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.put(key, value);
        assertThat(map.putIfAbsent(key, new SimpleValue("3"))).isEqualTo(value);
        assertThat(map.get(key)).isEqualTo(value);

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        assertThat(map.putIfAbsent(key1, value1)).isNull();
        assertThat(map.get(key1)).isEqualTo(value1);
        destroy(map);
    }

    @Test
    public void testFastPutIfExists() throws Exception {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");

        assertThat(map.fastPutIfExists(key, new SimpleValue("3"))).isFalse();
        assertThat(map.get(key)).isNull();

        map.put(key, value);
        assertThat(map.fastPutIfExists(key, new SimpleValue("3"))).isTrue();
        Thread.sleep(50);
        assertThat(map.get(key)).isEqualTo(new SimpleValue("3"));
        destroy(map);
    }

    @Test
    public void testPutIfExists() throws Exception {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");

        assertThat(map.putIfExists(key, new SimpleValue("3"))).isNull();
        assertThat(map.get(key)).isNull();

        map.put(key, value);
        assertThat(map.putIfExists(key, new SimpleValue("3"))).isEqualTo(value);
        assertThat(map.get(key)).isEqualTo(new SimpleValue("3"));
        destroy(map);
    }

    @Test
    public void testDeserializationErrorReturnsErrorImmediately() {
        Assertions.assertTimeout(Duration.ofSeconds(5), () -> {
            RMap<String, SimpleObjectWithoutDefaultConstructor> map = getMap("deserializationFailure", new JsonJacksonCodec());
            Assumptions.assumeTrue(!(map instanceof RLocalCachedMap));
            SimpleObjectWithoutDefaultConstructor object = new SimpleObjectWithoutDefaultConstructor("test-val");

            assertThat(object.getTestField()).isEqualTo("test-val");
            map.put("test-key", object);

            try {
                map.get("test-key");
                Assertions.fail("Expected exception from map.get() call");
            } catch (Exception e) {
                e.printStackTrace();
            }
            destroy(map);
        });
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
    
    @Test
    public void testReplaceOldValueFail() {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("43"), new SimpleValue("31"));
        assertThat(res).isFalse();

        SimpleValue val1 = map.get(new SimpleKey("1"));
        assertThat(val1.getValue()).isEqualTo("2");
        destroy(map);
    }

    @Test
    public void testReplaceOldValueSuccess() {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3"));
        assertThat(res).isTrue();

        boolean res1 = map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3"));
        assertThat(res1).isFalse();

        SimpleValue val1 = map.get(new SimpleKey("1"));
        assertThat(val1.getValue()).isEqualTo("3");
        destroy(map);
    }

    @Test
    public void testReplaceValue() {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        SimpleValue res = map.replace(new SimpleKey("1"), new SimpleValue("3"));
        assertThat(res.getValue()).isEqualTo("2");

        SimpleValue val1 = map.get(new SimpleKey("1"));
        assertThat(val1.getValue()).isEqualTo("3");
        destroy(map);
    }


    @Test
    public void testReplace() {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        SimpleValue val1 = map.get(new SimpleKey("33"));
        assertThat(val1.getValue()).isEqualTo("44");

        map.put(new SimpleKey("33"), new SimpleValue("abc"));
        SimpleValue val2 = map.get(new SimpleKey("33"));
        assertThat(val2.getValue()).isEqualTo("abc");
        destroy(map);
    }
    
    @Test
    public void testContainsValue() {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        assertThat(map.containsValue(new SimpleValue("2"))).isTrue();
        assertThat(map.containsValue(new SimpleValue("441"))).isFalse();
        assertThat(map.containsValue(new SimpleKey("5"))).isFalse();
        destroy(map);
    }

    @Test
    public void testContainsKey() {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        assertThat(map.containsKey(new SimpleKey("33"))).isTrue();
        assertThat(map.containsKey(new SimpleKey("34"))).isFalse();
        destroy(map);
    }

    @Test
    public void testRemoveValueFail() {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.remove(new SimpleKey("2"), new SimpleValue("1"));
        assertThat(res).isFalse();

        boolean res1 = map.remove(new SimpleKey("1"), new SimpleValue("3"));
        assertThat(res1).isFalse();

        SimpleValue val1 = map.get(new SimpleKey("1"));
        assertThat(val1.getValue()).isEqualTo("2");
        destroy(map);
    }
    
    @Test
    public void testRemoveValue() throws InterruptedException {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.remove(new SimpleKey("1"), new SimpleValue("2"));
        assertThat(res).isTrue();

        SimpleValue val1 = map.get(new SimpleKey("1"));
        assertThat(val1).isNull();

        assertThat(map.size()).isZero();
        destroy(map);
    }
    
    @Test
    public void testRemoveObject() {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        assertThat(map.remove(new SimpleKey("33"))).isEqualTo(new SimpleValue("44"));
        assertThat(map.remove(new SimpleKey("5"))).isEqualTo(new SimpleValue("6"));
        assertThat(map.remove(new SimpleKey("11"))).isNull();

        assertThat(map.size()).isEqualTo(1);
        destroy(map);
    }
    
    @Test
    public void testRemove() {
        RMap<Integer, Integer> map = getMap("simple");
        map.put(1, 3);
        map.put(3, 5);
        map.put(7, 8);

        assertThat(map.remove(1)).isEqualTo(3);
        assertThat(map.remove(3)).isEqualTo(5);
        assertThat(map.remove(10)).isNull();
        assertThat(map.remove(7)).isEqualTo(8);
        destroy(map);
    }

    @Test
    public void testFastRemove() throws InterruptedException, ExecutionException {
        RMap<Integer, Integer> map = getMap("simple");
        map.put(1, 3);
        map.put(3, 5);
        map.put(4, 6);
        map.put(7, 8);

        assertThat(map.fastRemove(1, 3, 7)).isEqualTo(3);
        Thread.sleep(1);
        assertThat(map.size()).isEqualTo(1);
        destroy(map);
    }
    
    @Test
    public void testValueIterator() {
        RMap<Integer, Integer> map = getMap("simple");
        map.put(1, 0);
        map.put(3, 5);
        map.put(4, 6);
        map.put(7, 8);

        Collection<Integer> values = map.values();
        assertThat(values).containsOnly(0, 5, 6, 8);
        for (Iterator<Integer> iterator = map.values().iterator(); iterator.hasNext();) {
            Integer value = iterator.next();
            if (!values.remove(value)) {
                Assertions.fail("unable to remove value " + value);
            }
        }

        assertThat(values.size()).isEqualTo(0);
        destroy(map);
    }

    @Test
    public void testFastPut() throws Exception {
        RMap<Integer, Integer> map = getMap("simple");
        assertThat(map.fastPut(1, 2)).isTrue();
        assertThat(map.get(1)).isEqualTo(2);
        assertThat(map.fastPut(1, 3)).isFalse();
        assertThat(map.get(1)).isEqualTo(3);
        assertThat(map.size()).isEqualTo(1);
        destroy(map);
    }
    
    @Test
    public void testFastReplace() throws Exception {
        RMap<Integer, Integer> map = getMap("simple");
        map.put(1, 2);
        
        assertThat(map.fastReplace(1, 3)).isTrue();
        assertThat(map.fastReplace(2, 0)).isFalse();

        assertThat(map.size()).isEqualTo(1);
        assertThat(map.get(1)).isEqualTo(3);
        destroy(map);
    }

    @Test
    public void testEquals() {
        RMap<String, String> map = getMap("simple");
        map.put("1", "7");
        map.put("2", "4");
        map.put("3", "5");

        Map<String, String> testMap = new HashMap<String, String>();
        testMap.put("1", "7");
        testMap.put("2", "4");
        testMap.put("3", "5");

        assertThat(map).isEqualTo(testMap);
        assertThat(testMap.hashCode()).isEqualTo(map.hashCode());
        destroy(map);
    }

    @Test
    public void testFastRemoveEmpty() throws Exception {
        RMap<Integer, Integer> map = getMap("simple");
        map.put(1, 3);

        assertThat(map.fastRemove()).isZero();
        assertThat(map.size()).isEqualTo(1);
        destroy(map);
    }
    
    @Test
    public void testKeySetByPattern() {
        RMap<String, String> map = getMap("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200");
        map.put("30", "300");

        assertThat(map.keySet("?0")).containsExactly("10", "20", "30");
        assertThat(map.keySet("1")).isEmpty();
        assertThat(map.keySet("10")).containsExactly("10");
        destroy(map);
    }

    @Test
    public void testValuesByPattern() {
        RMap<String, String> map = getMap("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200");
        map.put("30", "300");

        assertThat(map.values("?0")).containsExactly("100", "200", "300");
        assertThat(map.values("1")).isEmpty();
        assertThat(map.values("10")).containsExactly("100");
        destroy(map);
    }

    @Test
    public void testEntrySetByPattern() {
        RMap<String, String> map = getMap("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200");
        map.put("30", "300");

        assertThat(map.entrySet("?0")).containsExactly(new AbstractMap.SimpleEntry("10", "100"), new AbstractMap.SimpleEntry("20", "200"), new AbstractMap.SimpleEntry("30", "300"));
        assertThat(map.entrySet("1")).isEmpty();
        assertThat(map.entrySet("10")).containsExactly(new AbstractMap.SimpleEntry("10", "100"));
        destroy(map);
    }
    
    @Test
    public void testReadAllKeySet() {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        assertThat(map.readAllKeySet().size()).isEqualTo(3);
        Map<SimpleKey, SimpleValue> testMap = new HashMap<>(map);
        assertThat(map.readAllKeySet()).isSubsetOf(testMap.keySet());
        destroy(map);
    }
    
    @Test
    public void testEntrySetIteratorRemoveHighVolume() throws InterruptedException {
        RMap<Integer, Integer> map = getMap("simpleMap");
        for (int i = 0; i < 10000; i++) {
            map.put(i, i*10);
        }
        
        int cnt = 0;
        Iterator<Entry<Integer, Integer>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<Integer, Integer> entry = iterator.next();
            iterator.remove();
            cnt++;
        }
        assertThat(cnt).isEqualTo(10000);
        assertThat(map).isEmpty();
        assertThat(map.size()).isEqualTo(0);
        destroy(map);
    }
    
    @Test
    public void testEntrySetIteratorRandomRemoveHighVolume() throws InterruptedException {
        RMap<Integer, Integer> map = getMap("simpleMap");
        for (int i = 0; i < 10000; i++) {
            map.put(i, i*10);
        }
        
        int cnt = 0;
        int removed = 0;
        Iterator<Entry<Integer, Integer>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<Integer, Integer> entry = iterator.next();
            if (ThreadLocalRandom.current().nextBoolean()) {
                iterator.remove();
                removed++;
            }
            cnt++;
        }
        assertThat(cnt).isEqualTo(10000);
        assertThat(map.size()).isEqualTo(cnt - removed);
        destroy(map);
    }
    
    @Test
    public void testKeySetIteratorRemoveHighVolume() throws InterruptedException {
        RMap<Integer, Integer> map = getMap("simpleMap");
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
        assertThat(cnt).isEqualTo(10000);
        assertThat(map).isEmpty();
        assertThat(map.size()).isEqualTo(0);
        destroy(map);
    }

    @Test
    public void testReadAllKeySetHighAmount() {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        for (int i = 0; i < 1000; i++) {
            map.put(new SimpleKey("" + i), new SimpleValue("" + i));
        }

        assertThat(map.readAllKeySet().size()).isEqualTo(1000);
        Map<SimpleKey, SimpleValue> testMap = new HashMap<>(map);
        assertThat(map.readAllKeySet()).isSubsetOf(testMap.keySet());
        destroy(map);
    }

    @Test
    public void testReadAllValues() {
        RMap<SimpleKey, SimpleValue> map = getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        assertThat(map.readAllValues().size()).isEqualTo(3);
        Map<SimpleKey, SimpleValue> testMap = new HashMap<>(map);
        assertThat(map.readAllValues()).isSubsetOf(testMap.values());
        destroy(map);
    }
    
    @Test
    public void testGetAllBig() {
        Map<Integer, String> joinMap = new HashMap<Integer, String>();
        for (int i = 0; i < 10000; i++) {
            joinMap.put(i, "" + i);
        }
        
        RMap<Integer, String> map = getMap("simple");
        map.putAll(joinMap);
        
        Map<Integer, String> s = map.getAll(joinMap.keySet());
        assertThat(s).isEqualTo(joinMap);
        
        assertThat(map.size()).isEqualTo(joinMap.size());
        destroy(map);
    }
    
    @Test
    public void testGetAll() {
        RMap<Integer, Integer> map = getMap("getAll");
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);
        map.put(4, 400);

        Map<Integer, Integer> filtered = map.getAll(new HashSet<Integer>(Arrays.asList(2, 3, 5)));

        Map<Integer, Integer> expectedMap = new HashMap<Integer, Integer>();
        expectedMap.put(2, 200);
        expectedMap.put(3, 300);
        assertThat(filtered).isEqualTo(expectedMap);
        destroy(map);
    }
    
    @Test
    public void testValueSize() {
        Assumptions.assumeTrue(RedisRunner.getDefaultRedisServerInstance().getRedisVersion().compareTo("3.2.0") > 0);
        RMap<String, String> map = getMap("getAll");
        Assumptions.assumeTrue(!(map instanceof RMapCache));
        map.put("1", "1234");
        assertThat(map.valueSize("4")).isZero();
        assertThat(map.valueSize("1")).isEqualTo(7);
        destroy(map);
    }
    
    @Test
    public void testGetAllOrder() {
        RMap<Integer, Integer> map = getMap("getAll");
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);
        map.put(4, 400);
        map.put(5, 500);
        map.put(6, 600);
        map.put(7, 700);
        map.put(8, 800);

        Map<Integer, Integer> filtered = map.getAll(new HashSet<Integer>(Arrays.asList(2, 3, 5, 1, 7, 8)));

        Map<Integer, Integer> expectedMap = new LinkedHashMap<Integer, Integer>();
        expectedMap.put(1, 100);
        expectedMap.put(2, 200);
        expectedMap.put(3, 300);
        expectedMap.put(5, 500);
        expectedMap.put(7, 700);
        expectedMap.put(8, 800);
        
        assertThat(filtered.entrySet()).containsExactlyElementsOf(expectedMap.entrySet());
        destroy(map);
    }
    
    @Test
    public void testGetAllOrderPartially() {
        RMap<Integer, Integer> map = getMap("getAll");
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);
        map.put(4, 400);
        RMap<Integer, Integer> map2 = getMap("getAll");
        map2.put(5, 500);
        map2.put(6, 600);
        map2.put(7, 700);
        map2.put(8, 800);

        Map<Integer, Integer> filtered = map.getAll(new HashSet<Integer>(Arrays.asList(2, 3, 5, 1, 7, 8)));

        Map<Integer, Integer> expectedMap = new LinkedHashMap<Integer, Integer>();
        expectedMap.put(1, 100);
        expectedMap.put(2, 200);
        expectedMap.put(3, 300);
        expectedMap.put(5, 500);
        expectedMap.put(7, 700);
        expectedMap.put(8, 800);
        
        assertThat(filtered.entrySet()).containsExactlyElementsOf(expectedMap.entrySet());
        destroy(map);
    }

    
    @Test
    public void testAddAndGet() throws InterruptedException {
        RMap<Integer, Integer> map = getMap("getAll", new CompositeCodec(redisson.getConfig().getCodec(), IntegerCodec.INSTANCE));
        map.put(1, 100);

        Integer res = map.addAndGet(1, 12);
        assertThat(res).isEqualTo(112);
        res = map.get(1);
        assertThat(res).isEqualTo(112);

        RMap<Integer, Double> map2 = getMap("getAll2", new CompositeCodec(redisson.getConfig().getCodec(), DoubleCodec.INSTANCE));
        map2.put(1, new Double(100.2));

        Double res2 = map2.addAndGet(1, new Double(12.1));
        assertThat(res2).isEqualTo(112.3);
        res2 = map2.get(1);
        assertThat(res2).isEqualTo(112.3);

        RMap<String, Integer> mapStr = getMap("mapStr", new CompositeCodec(redisson.getConfig().getCodec(), IntegerCodec.INSTANCE));
        assertThat(mapStr.put("1", 100)).isNull();

        assertThat(mapStr.addAndGet("1", 12)).isEqualTo(112);
        assertThat(mapStr.get("1")).isEqualTo(112);
        destroy(map);
    }
    
    protected abstract <K, V> RMap<K, V> getMap(String name);
    
    protected abstract <K, V> RMap<K, V> getMap(String name, Codec codec);
    
    protected abstract <K, V> RMap<K, V> getWriterTestMap(String name, Map<K, V> map);
    
    protected abstract <K, V> RMap<K, V> getWriteBehindTestMap(String name, Map<K, V> map);

    protected abstract <K, V> RMap<K, V> getWriteBehindAsyncTestMap(String name, Map<K, V> map);
    
    protected abstract <K, V> RMap<K, V> getLoaderTestMap(String name, Map<K, V> map);

    protected abstract <K, V> RMap<K, V> getLoaderAsyncTestMap(String name, Map<K, V> map);

    @Test
    public void testMapLoaderGetMulipleNulls() {
        Map<String, String> cache = new HashMap<String, String>();
        cache.put("1", "11");
        cache.put("2", "22");
        cache.put("3", "33");
        
        RMap<String, String> map = getLoaderTestMap("test", cache);
        assertThat(map.get("0")).isNull();
        assertThat(map.get("1")).isEqualTo("11");
        assertThat(map.get("0")).isNull(); // This line will never return anything and the test will hang
        destroy(map);
    }
    
    @Test
    public void testWriterAddAndGet() throws InterruptedException {
        Map<String, Integer> store = new HashMap<>();
        RMap<String, Integer> map = getWriterTestMap("test", store);

        assertThat(map.addAndGet("1", 11)).isEqualTo(11);
        assertThat(map.addAndGet("1", 7)).isEqualTo(18);
        
        Map<String, Integer> expected = new HashMap<>();
        expected.put("1", 18);
        assertThat(store).isEqualTo(expected);
        destroy(map);
    }

    @Test
    public void testWriteBehindFastRemove() throws InterruptedException {
        Map<String, String> store = new HashMap<>();
        RMap<String, String> map = getWriteBehindTestMap("test", store);

        map.put("1", "11");
        map.put("2", "22");
        map.put("3", "33");
        
        Thread.sleep(1400);
        
        map.fastRemove("1", "2", "4");
        
        Map<String, String> expected = new HashMap<>();
        expected.put("3", "33");
        Thread.sleep(1400);
        assertThat(store).isEqualTo(expected);
        destroy(map);
    }

    @Test
    public void testLoaderGetAsync() {
        Map<String, String> cache = new HashMap<String, String>();
        cache.put("1", "11");
        cache.put("2", "22");
        cache.put("3", "33");

        RMap<String, String> map = getLoaderAsyncTestMap("test", cache);

        assertThat(map.size()).isEqualTo(0);
        assertThat(map.get("1")).isEqualTo("11");
        assertThat(map.size()).isEqualTo(1);
        assertThat(map.get("0")).isNull();
        map.put("0", "00");
        assertThat(map.get("0")).isEqualTo("00");
        assertThat(map.size()).isEqualTo(2);

        assertThat(map.containsKey("2")).isTrue();
        assertThat(map.size()).isEqualTo(3);

        Map<String, String> s = map.getAll(new HashSet<>(Arrays.asList("1", "2", "9", "3")));
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put("1", "11");
        expectedMap.put("2", "22");
        expectedMap.put("3", "33");
        assertThat(s).isEqualTo(expectedMap);
        assertThat(map.size()).isEqualTo(4);
        destroy(map);

    }

    @Test
    public void testWriteBehindAsyncFastRemove() throws InterruptedException {
        Map<String, String> store = new HashMap<>();
        RMap<String, String> map = getWriteBehindAsyncTestMap("test", store);

        map.put("1", "11");
        map.put("2", "22");
        map.put("3", "33");

        Thread.sleep(1400);

        map.fastRemove("1", "2", "4");

        Map<String, String> expected = new HashMap<>();
        expected.put("3", "33");
        Thread.sleep(1400);
        assertThat(store).isEqualTo(expected);
        destroy(map);
    }

    @Test
    public void testWriterFastRemove() {
        Map<String, String> store = new HashMap<>();
        RMap<String, String> map = getWriterTestMap("test", store);

        map.put("1", "11");
        map.put("2", "22");
        map.put("3", "33");
        
        map.fastRemove("1", "2", "4");
        
        Map<String, String> expected = new HashMap<>();
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
        destroy(map);
    }
    
    @Test
    public void testWriterFastPut() {
        Map<String, String> store = new HashMap<>();
        RMap<String, String> map = getWriterTestMap("test", store);

        map.fastPut("1", "11");
        map.fastPut("2", "22");
        map.fastPut("3", "33");
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
        destroy(map);
    }
    
    @Test
    public void testWriterRemove() {
        Map<String, String> store = new HashMap<>();
        RMap<String, String> map = getWriterTestMap("test", store);

        map.put("1", "11");
        map.remove("1");
        map.put("3", "33");
        
        Map<String, String> expected = new HashMap<>();
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
    }
    
    @Test
    public void testWriterReplaceKeyValue() {
        Map<String, String> store = new HashMap<>();
        RMap<String, String> map = getWriterTestMap("test", store);

        map.put("1", "11");
        map.replace("1", "00");
        map.replace("2", "22");
        map.put("3", "33");
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "00");
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
    }
    
    @Test
    public void testWriterReplaceKeyOldNewValue() {
        Map<String, String> store = new HashMap<>();
        RMap<String, String> map = getWriterTestMap("test", store);

        map.put("1", "11");
        map.replace("1", "11", "00");
        map.put("3", "33");
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "00");
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
        destroy(map);
    }
    
    @Test
    public void testWriterRemoveKeyValue() {
        Map<String, String> store = new HashMap<>();
        RMap<String, String> map = getWriterTestMap("test", store);

        map.put("1", "11");
        map.put("2", "22");
        map.put("3", "33");
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
        
        map.remove("1", "11");
        
        Map<String, String> expected2 = new HashMap<>();
        expected2.put("2", "22");
        expected2.put("3", "33");
        assertThat(store).isEqualTo(expected2);
        destroy(map);
    }
    
    @Test
    public void testWriterFastPutIfAbsent() {
        Map<String, String> store = new HashMap<>();
        RMap<String, String> map = getWriterTestMap("test", store);

        map.fastPutIfAbsent("1", "11");
        map.fastPutIfAbsent("1", "00");
        map.fastPutIfAbsent("2", "22");
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        assertThat(store).isEqualTo(expected);
        destroy(map);
    }
    
    @Test
    public void testWriterPutIfAbsent() {
        Map<String, String> store = new HashMap<>();
        RMap<String, String> map = getWriterTestMap("test", store);

        map.putIfAbsent("1", "11");
        map.putIfAbsent("1", "00");
        map.putIfAbsent("2", "22");
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        assertThat(store).isEqualTo(expected);
        destroy(map);
    }
    
    @Test
    public void testWriterPutAll() {
        Map<String, String> store = new HashMap<>();
        RMap<String, String> map = getWriterTestMap("test", store);

        Map<String, String> newMap = new HashMap<>();
        newMap.put("1", "11");
        newMap.put("2", "22");
        newMap.put("3", "33");
        map.putAll(newMap);
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
        destroy(map);
    }

    
    @Test
    public void testWriterPut() {
        Map<String, String> store = new HashMap<>();
        RMap<String, String> map = getWriterTestMap("test", store);
        
        map.put("1", "11");
        map.put("2", "22");
        map.put("3", "33");
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
        destroy(map);
    }
    
    @Test
    public void testLoadAllReplaceValues() {
        Map<String, String> cache = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            cache.put("" + i, "" + i + "" + i);
        }
        
        RMap<String, String> map = getLoaderTestMap("test", cache);
        
        map.put("0", "010");
        map.put("5", "555");
        map.loadAll(false, 2);
        assertThat(map.size()).isEqualTo(10);
        assertThat(map.get("0")).isEqualTo("010");
        assertThat(map.get("5")).isEqualTo("555");
        map.clear();
        
        map.put("0", "010");
        map.put("5", "555");
        map.loadAll(true, 2);
        assertThat(map.size()).isEqualTo(10);
        assertThat(map.get("0")).isEqualTo("00");
        assertThat(map.get("5")).isEqualTo("55");
        destroy(map);
    }
    
    @Test
    public void testLoadAll() {
        Map<String, String> cache = new HashMap<String, String>();
        for (int i = 0; i < 100; i++) {
            cache.put("" + i, "" + (i*10 + i));
        }
        
        RMap<String, String> map = getLoaderTestMap("test", cache);
        
        assertThat(map.size()).isEqualTo(0);
        map.loadAll(false, 2);
        assertThat(map.size()).isEqualTo(100);
        
        for (int i = 0; i < 100; i++) {
            assertThat(map.containsKey("" + i)).isTrue();
        }
        destroy(map);
    }

    @Test
    public void testLoadAllAsync() {
        Map<String, String> cache = new HashMap<String, String>();
        for (int i = 0; i < 100; i++) {
            cache.put("" + i, "" + (i*10 + i));
        }

        RMap<String, String> map = getLoaderAsyncTestMap("test", cache);

        assertThat(map.size()).isEqualTo(0);
        map.loadAll(false, 2);
        assertThat(map.size()).isEqualTo(100);

        for (int i = 0; i < 100; i++) {
            assertThat(map.containsKey("" + i)).isTrue();
        }
        destroy(map);
    }

    protected <K, V> MapWriterAsync<K, V> createMapWriterAsync(Map<K, V> map) {
        return new MapWriterAsync<K, V>() {

            @Override
            public CompletionStage<Void> write(Map<K, V> values) {
                map.putAll(values);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> delete(Collection<K> keys) {
                for (K key : keys) {
                    map.remove(key);
                }
                return CompletableFuture.completedFuture(null);
            }

        };
    }

    protected <K, V> MapWriter<K, V> createMapWriter(Map<K, V> map) {
        return new MapWriter<K, V>() {

            @Override
            public void write(Map<K, V> values) {
                map.putAll(values);
                System.out.println("map " + map);
            }

            @Override
            public void delete(Collection<K> keys) {
                for (K key : keys) {
                    map.remove(key);
                }
                System.out.println("delete " + keys + " map " + map);
            }
            
        };
    }

    protected <K, V> MapLoaderAsync<K, V> createMapLoaderAsync(Map<K, V> map) {
        MapLoaderAsync<K, V> loaderAsync = new MapLoaderAsync<K, V>() {
            @Override
            public CompletionStage<V> load(Object key) {
                return CompletableFuture.completedFuture(map.get(key));
            }

            @Override
            public AsyncIterator<K> loadAllKeys() {
                Iterator<K> iter = map.keySet().iterator();
                return new AsyncIterator<K>() {

                    @Override
                    public CompletionStage<Boolean> hasNext() {
                        return CompletableFuture.completedFuture(iter.hasNext());
                    }

                    @Override
                    public CompletionStage<K> next() {
                        return CompletableFuture.completedFuture(iter.next());
                    }
                };
            }
        };
        return loaderAsync;
    }

    protected <K, V> MapLoader<K, V> createMapLoader(Map<K, V> map) {
        return new MapLoader<K, V>() {
            @Override
            public V load(K key) {
                return map.get(key);
            }

            @Override
            public Iterable<K> loadAllKeys() {
                return map.keySet();
            }
        };
    }

    @Test
    public void testMapLoaderGetWithException() {
        Map<String, String> cache = new HashMap<String, String>() {
            @Override
            public String get(Object key) {
                throw new RuntimeException();
            };
        };
        
        RMap<String, String> map = getLoaderTestMap("test", cache);
        assertThat(map.get("1")).isNull();
    }
    
    @Test
    public void testMapLoaderGet() {
        Map<String, String> cache = new HashMap<String, String>();
        cache.put("1", "11");
        cache.put("2", "22");
        cache.put("3", "33");
        
        RMap<String, String> map = getLoaderTestMap("test", cache);
        
        assertThat(map.size()).isEqualTo(0);
        assertThat(map.get("1")).isEqualTo("11");
        assertThat(map.size()).isEqualTo(1);
        assertThat(map.get("0")).isNull();
        map.put("0", "00");
        assertThat(map.get("0")).isEqualTo("00");
        assertThat(map.size()).isEqualTo(2);

        assertThat(map.containsKey("2")).isTrue();
        assertThat(map.size()).isEqualTo(3);
        
        Map<String, String> s = map.getAll(new HashSet<>(Arrays.asList("1", "2", "9", "3")));
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put("1", "11");
        expectedMap.put("2", "22");
        expectedMap.put("3", "33");
        assertThat(s).isEqualTo(expectedMap);
        assertThat(map.size()).isEqualTo(4);
        destroy(map);
    }

    @Test
    public void testWriterEvict() {
        Map<String, String> store = new HashMap<>();
        RMap<String, String> map = getWriterTestMap("test", store);

        map.put("1", "11");
        map.evict("1");
        map.put("3", "33");

        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
    }

}
