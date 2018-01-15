package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.awaitility.Duration;
import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.MapOptions;
import org.redisson.api.RFuture;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.map.event.EntryCreatedListener;
import org.redisson.api.map.event.EntryEvent;
import org.redisson.api.map.event.EntryExpiredListener;
import org.redisson.api.map.event.EntryRemovedListener;
import org.redisson.api.map.event.EntryUpdatedListener;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.codec.MsgPackJacksonCodec;

import static org.awaitility.Awaitility.*;

public class RedissonMapCacheTest extends BaseMapTest {

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
    
    @Override
    protected <K, V> RMap<K, V> getWriterTestMap(String name, Map<K, V> map) {
        MapOptions<K, V> options = MapOptions.<K, V>defaults().writer(createMapWriter(map));
        return redisson.getMapCache("test", options);        
    }
    
    @Override
    protected <K, V> RMap<K, V> getLoaderTestMap(String name, Map<K, V> map) {
        MapOptions<K, V> options = MapOptions.<K, V>defaults().loader(createMapLoader(map));
        return redisson.getMapCache("test", options);        
    }
    
    @Test
    public void testWriterPutIfAbsent() {
        Map<String, String> store = new HashMap<>();
        RMapCache<String, String> map = (RMapCache<String, String>) getWriterTestMap("test", store);

        map.putIfAbsent("1", "11", 10, TimeUnit.SECONDS);
        map.putIfAbsent("1", "00", 10, TimeUnit.SECONDS);
        map.putIfAbsent("2", "22", 10, TimeUnit.SECONDS);
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        assertThat(store).isEqualTo(expected);
    }
    
    @Test
    public void testWriterPutTTL() {
        Map<String, String> store = new HashMap<>();
        RMapCache<String, String> map = (RMapCache<String, String>) getWriterTestMap("test", store);
        
        map.put("1", "11", 10, TimeUnit.SECONDS);
        map.put("2", "22", 10, TimeUnit.SECONDS);
        map.put("3", "33", 10, TimeUnit.SECONDS);
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
    }
    
    @Test
    public void testWriterFastPutIfAbsentTTL() {
        Map<String, String> store = new HashMap<>();
        RMapCache<String, String> map = (RMapCache<String, String>) getWriterTestMap("test", store);

        map.fastPutIfAbsent("1", "11", 10, TimeUnit.SECONDS);
        map.fastPutIfAbsent("1", "00", 10, TimeUnit.SECONDS);
        map.fastPutIfAbsent("2", "22", 10, TimeUnit.SECONDS);
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        assertThat(store).isEqualTo(expected);
    }
    
    @Test
    public void testWriterFastPutTTL() {
        Map<String, String> store = new HashMap<>();
        RMapCache<String, String> map = (RMapCache<String, String>) getWriterTestMap("test", store);

        map.fastPut("1", "11", 10, TimeUnit.SECONDS);
        map.fastPut("2", "22", 10, TimeUnit.SECONDS);
        map.fastPut("3", "33", 10, TimeUnit.SECONDS);
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
    }

    @Test
    public void testMaxSize() {
        final AtomicInteger maxSize = new AtomicInteger(2);
        Map<String, String> store = new LinkedHashMap<String, String>() {
            @Override
            protected boolean removeEldestEntry(Entry<String, String> eldest) {
                return size() > maxSize.get();
            }
        };
        MapOptions<String, String> options = MapOptions.<String, String>defaults().writer(createMapWriter(store));
        RMapCache<String, String> map = redisson.getMapCache("test", options);
        assertThat(map.trySetMaxSize(maxSize.get())).isTrue();
        assertThat(map.trySetMaxSize(1)).isFalse();

        assertThat(map.fastPutIfAbsent("01", "00")).isTrue();
        assertThat(map.fastPutIfAbsent("02", "00")).isTrue();
        assertThat(map.put("03", "00")).isNull();
        assertThat(map.fastPutIfAbsent("04", "00", 10, TimeUnit.SECONDS)).isTrue();
        assertThat(map.fastPut("1", "11", 10, TimeUnit.SECONDS)).isTrue();
        assertThat(map.fastPut("2", "22", 10, TimeUnit.SECONDS)).isTrue();
        assertThat(map.fastPut("3", "33", 10, TimeUnit.SECONDS)).isTrue();

        assertThat(map.size()).isEqualTo(maxSize.get());

        Map<String, String> expected = new HashMap<>();
        expected.put("2", "22");
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
        
        assertThat(map.get("2")).isEqualTo("22");
        assertThat(map.get("0")).isNull();
        assertThat(map.putIfAbsent("2", "3")).isEqualTo("22");
        assertThat(map.putIfAbsent("3", "4", 10, TimeUnit.SECONDS, 10, TimeUnit.SECONDS)).isEqualTo("33");
        assertThat(map.containsKey("2")).isTrue();
        assertThat(map.containsKey("0")).isFalse();
        assertThat(map.containsValue("22")).isTrue();
        assertThat(map.containsValue("00")).isFalse();
        assertThat(map.getAll(new HashSet<String>(Arrays.asList("2", "3")))).isEqualTo(expected);
        assertThat(map.remove("2", "33")).isFalse();
        assertThat(map.remove("2", "22")).isTrue();
        assertThat(map.remove("0")).isNull();
        assertThat(map.remove("3")).isEqualTo("33");

        maxSize.set(6);
        map.setMaxSize(maxSize.get());
        assertThat(map.fastPut("01", "00")).isTrue();
        assertThat(map.fastPut("02", "00")).isTrue();
        assertThat(map.fastPut("03", "00")).isTrue();
        assertThat(map.fastPut("04", "00")).isTrue();
        assertThat(map.fastPut("05", "00")).isTrue();
        assertThat(map.fastPut("06", "00")).isTrue();
        assertThat(map.fastPut("07", "00")).isTrue();

        assertThat(map.size()).isEqualTo(maxSize.get());
        assertThat(map.keySet()).containsExactly("02", "03", "04", "05", "06", "07");
        
        map.put("08", "00");
        map.put("09", "00");
        map.put("10", "00");
        map.put("11", "00");
        map.put("12", "00");
        map.put("13", "00");
        map.put("14", "00");
        
        assertThat(map.size()).isEqualTo(maxSize.get());
        assertThat(map.keySet()).containsExactly("09", "10", "11", "12", "13", "14");
        
        map.putIfAbsent("15", "00", 1, TimeUnit.SECONDS);
        map.putIfAbsent("16", "00", 1, TimeUnit.SECONDS);
        map.putIfAbsent("17", "00", 1, TimeUnit.SECONDS);
        map.putIfAbsent("18", "00", 1, TimeUnit.SECONDS);
        map.putIfAbsent("19", "00", 1, TimeUnit.SECONDS);
        map.putIfAbsent("20", "00", 1, TimeUnit.SECONDS);
        map.putIfAbsent("21", "00", 1, TimeUnit.SECONDS);
        
        assertThat(map.size()).isEqualTo(maxSize.get());
        assertThat(map.keySet()).containsExactly("16", "17", "18", "19", "20", "21");

        map.putIfAbsent("22", "00");
        map.putIfAbsent("23", "00");
        map.putIfAbsent("24", "00");
        map.putIfAbsent("25", "00");
        map.putIfAbsent("26", "00");
        map.putIfAbsent("27", "00");
        map.putIfAbsent("28", "00");
        
        assertThat(map.size()).isEqualTo(maxSize.get());
        assertThat(map.keySet()).containsExactly("23", "24", "25", "26", "27", "28");

        map.fastPut("29", "00", 1, TimeUnit.SECONDS);
        map.fastPut("30", "00", 1, TimeUnit.SECONDS);
        map.fastPut("31", "00", 1, TimeUnit.SECONDS);
        map.fastPut("32", "00", 1, TimeUnit.SECONDS);
        map.fastPut("33", "00", 1, TimeUnit.SECONDS);
        map.fastPut("34", "00", 1, TimeUnit.SECONDS);
        map.fastPut("35", "00", 1, TimeUnit.SECONDS);
        
        assertThat(map.size()).isEqualTo(maxSize.get());
        assertThat(map.keySet()).containsExactly("30", "31", "32", "33", "34", "35");

        map.put("36", "00", 1, TimeUnit.SECONDS);
        map.put("37", "00", 1, TimeUnit.SECONDS);
        map.put("38", "00", 1, TimeUnit.SECONDS);
        map.put("39", "00", 1, TimeUnit.SECONDS);
        map.put("40", "00", 1, TimeUnit.SECONDS);
        map.put("41", "00", 1, TimeUnit.SECONDS);
        map.put("42", "00", 1, TimeUnit.SECONDS);
        
        assertThat(map.size()).isEqualTo(maxSize.get());
        assertThat(map.keySet()).containsExactly("37", "38", "39", "40", "41", "42");

        map.fastPutIfAbsent("43", "00");
        map.fastPutIfAbsent("44", "00");
        map.fastPutIfAbsent("45", "00");
        map.fastPutIfAbsent("46", "00");
        map.fastPutIfAbsent("47", "00");
        map.fastPutIfAbsent("48", "00");
        map.fastPutIfAbsent("49", "00");
        
        assertThat(map.size()).isEqualTo(maxSize.get());
        assertThat(map.keySet()).containsExactly("44", "45", "46", "47", "48", "49");

        map.fastPutIfAbsent("50", "00", 1, TimeUnit.SECONDS);
        map.fastPutIfAbsent("51", "00", 1, TimeUnit.SECONDS);
        map.fastPutIfAbsent("52", "00", 1, TimeUnit.SECONDS);
        map.fastPutIfAbsent("53", "00", 1, TimeUnit.SECONDS);
        map.fastPutIfAbsent("54", "00", 1, TimeUnit.SECONDS);
        map.fastPutIfAbsent("55", "00", 1, TimeUnit.SECONDS);
        map.fastPutIfAbsent("56", "00", 1, TimeUnit.SECONDS);
        
        assertThat(map.size()).isEqualTo(maxSize.get());
        assertThat(map.keySet()).containsExactly("51", "52", "53", "54", "55", "56");

        Map<String, String> newMap = new LinkedHashMap<>();
        newMap.put("57", "00");
        newMap.put("58", "00");
        newMap.put("59", "00");
        newMap.put("60", "00");
        newMap.put("61", "00");
        newMap.put("62", "00");
        newMap.put("63", "00");
        map.putAll(newMap);
        
        assertThat(map.size()).isEqualTo(maxSize.get());
        assertThat(map.keySet()).containsExactly("58", "59", "60", "61", "62", "63");

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

        RMap<String, String> rmap = redisson.getMapCache("123");
        rmap.putAll(map);

        assertThat(rmap.keySet()).containsExactlyElementsOf(map.keySet());
        assertThat(rmap.readAllKeySet()).containsExactlyElementsOf(map.keySet());
        
        assertThat(rmap.values()).containsExactlyElementsOf(map.values());
        assertThat(rmap.readAllValues()).containsExactlyElementsOf(map.values());
        
        assertThat(rmap.entrySet()).containsExactlyElementsOf(map.entrySet());
        assertThat(rmap.readAllEntrySet()).containsExactlyElementsOf(map.entrySet());
    }
    
    @Test
    public void testCacheValues() {
        final RMapCache<String, String> map = redisson.getMapCache("testRMapCacheValues");
        map.put("1234", "5678", 0, TimeUnit.MINUTES, 60, TimeUnit.MINUTES);
        assertThat(map.values()).containsOnly("5678");
    }    

    @Test
    public void testGetAllBig() {
        Map<Integer, String> joinMap = new HashMap<Integer, String>();
        for (int i = 0; i < 10000; i++) {
            joinMap.put(i, "" + i);
        }
        
        RMap<Integer, String> map = redisson.getMapCache("simple");
        map.putAll(joinMap);
        
        Map<Integer, String> s = map.getAll(joinMap.keySet());
        assertThat(s).isEqualTo(joinMap);
        
        assertThat(map.size()).isEqualTo(joinMap.size());
    }

    
    @Test
    public void testGetAll() throws InterruptedException {
        RMapCache<Integer, Integer> map = redisson.getMapCache("getAll");
        map.put(1, 100);
        map.put(2, 200, 1, TimeUnit.SECONDS);
        map.put(3, 300, 1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS);
        map.put(4, 400);

        Map<Integer, Integer> filtered = map.getAll(new HashSet<Integer>(Arrays.asList(2, 3, 5)));

        Map<Integer, Integer> expectedMap = new HashMap<Integer, Integer>();
        expectedMap.put(2, 200);
        expectedMap.put(3, 300);
        Assert.assertEquals(expectedMap, filtered);

        Thread.sleep(1000);

        Map<Integer, Integer> filteredAgain = map.getAll(new HashSet<Integer>(Arrays.asList(2, 3, 5)));
        Assert.assertTrue(filteredAgain.isEmpty());
    }

    @Test
    public void testGetAllWithStringKeys() {
        RMapCache<String, Integer> map = redisson.getMapCache("getAllStrings");
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

    @Test
    public void testExpiredIterator() throws InterruptedException {
        RMapCache<String, String> cache = redisson.getMapCache("simple");
        cache.put("0", "8");
        cache.put("1", "6", 1, TimeUnit.SECONDS);
        cache.put("2", "4", 3, TimeUnit.SECONDS);
        cache.put("3", "2", 4, TimeUnit.SECONDS);
        cache.put("4", "4", 1, TimeUnit.SECONDS);

        Thread.sleep(1000);

        assertThat(cache.keySet()).containsOnly("0", "2", "3");
    }

    @Test
    public void testExpire() throws InterruptedException {
        RMapCache<String, String> cache = redisson.getMapCache("simple");
        cache.put("0", "8", 1, TimeUnit.SECONDS);

        cache.expire(100, TimeUnit.MILLISECONDS);

        Thread.sleep(500);

        Assert.assertEquals(0, cache.size());
    }

    @Test
    public void testExpireAt() throws InterruptedException {
        RMapCache<String, String> cache = redisson.getMapCache("simple");
        cache.put("0", "8", 1, TimeUnit.SECONDS);

        cache.expireAt(System.currentTimeMillis() + 100);

        Thread.sleep(500);

        Assert.assertEquals(0, cache.size());
    }

    @Test
    public void testIteratorRemoveHighVolume() throws InterruptedException {
        RMapCache<Integer, Integer> map = redisson.getMapCache("simpleMap");
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
        Assert.assertEquals(10000, cnt);
        assertThat(map).isEmpty();
        Assert.assertEquals(0, map.size());
    }
    
    @Test
    public void testIteratorRandomRemoveFirst() throws InterruptedException {
        RMapCache<Integer, Integer> map = redisson.getMapCache("simpleMap");
        for (int i = 0; i < 1000; i++) {
            map.put(i, i*10);
        }
        
        int cnt = 0;
        int removed = 0;
        Iterator<Entry<Integer, Integer>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<Integer, Integer> entry = iterator.next();
            if (cnt < 20) {
                iterator.remove();
                removed++;
            }
            cnt++;
        }
        Assert.assertEquals(1000, cnt);
        assertThat(map.size()).isEqualTo(cnt - removed);
    }
    
    @Test
    public void testIteratorRandomRemoveHighVolume() throws InterruptedException {
        RMapCache<Integer, Integer> map = redisson.getMapCache("simpleMap");
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
        Assert.assertEquals(10000, cnt);
        assertThat(map.size()).isEqualTo(cnt - removed);
    }

    @Test
    public void testClearExpire() throws InterruptedException {
        RMapCache<String, String> cache = redisson.getMapCache("simple");
        cache.put("0", "8", 1, TimeUnit.SECONDS);

        cache.expireAt(System.currentTimeMillis() + 100);

        cache.clearExpire();

        Thread.sleep(500);

        Assert.assertEquals(1, cache.size());
    }

    @Test
    public void testEntrySet() throws InterruptedException {
        RMapCache<Integer, String> map = redisson.getMapCache("simple12");
        map.put(1, "12");
        map.put(2, "33", 1, TimeUnit.SECONDS);
        map.put(3, "43");

        Map<Integer, String> expected = new HashMap<>();
        map.put(1, "12");
        map.put(3, "43");
        
        assertThat(map.entrySet()).containsAll(expected.entrySet());
        assertThat(map).hasSize(3);
    }

    @Test
    public void testRemove() {
        Map<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        map.remove(new SimpleKey("33"));
        map.remove(new SimpleKey("5"));

        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testPutAllBig() {
        Map<Integer, String> joinMap = new HashMap<Integer, String>();
        for (int i = 0; i < 100000; i++) {
            joinMap.put(i, "" + i);
        }
        
        Map<Integer, String> map = redisson.getMapCache("simple");
        map.putAll(joinMap);
        
        assertThat(map.size()).isEqualTo(joinMap.size());
    }
    
    @Test
    public void testPutAll() {
        Map<Integer, String> map = redisson.getMapCache("simple");
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
    public void testKeySet() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple03");
        map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS);
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        Assert.assertTrue(map.keySet().contains(new SimpleKey("33")));
        Assert.assertFalse(map.keySet().contains(new SimpleKey("44")));
        Assert.assertTrue(map.keySet().contains(new SimpleKey("1")));

        Thread.sleep(1000);

        Assert.assertFalse(map.keySet().contains(new SimpleKey("33")));
        Assert.assertFalse(map.keySet().contains(new SimpleKey("44")));
        Assert.assertTrue(map.keySet().contains(new SimpleKey("1")));
    }

    @Test
    public void testValues() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple05");
        map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS);
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        Assert.assertTrue(map.values().contains(new SimpleValue("44")));
        Assert.assertFalse(map.values().contains(new SimpleValue("33")));
        Assert.assertTrue(map.values().contains(new SimpleValue("2")));

        Thread.sleep(1000);

        Assert.assertFalse(map.values().contains(new SimpleValue("44")));
        Assert.assertFalse(map.values().contains(new SimpleValue("33")));
        Assert.assertTrue(map.values().contains(new SimpleValue("2")));
    }

    @Test
    public void testKeySetByPattern() {
        RMapCache<String, String> map = redisson.getMapCache("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200", 1, TimeUnit.MINUTES);
        map.put("30", "300");

        assertThat(map.keySet("?0")).containsExactly("10", "20", "30");
        assertThat(map.keySet("1")).isEmpty();
        assertThat(map.keySet("10")).containsExactly("10");
    }

    @Test
    public void testValuesByPattern() {
        RMapCache<String, String> map = redisson.getMapCache("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200", 1, TimeUnit.MINUTES);
        map.put("30", "300");

        assertThat(map.values("?0")).containsExactly("100", "200", "300");
        assertThat(map.values("1")).isEmpty();
        assertThat(map.values("10")).containsExactly("100");
    }

    @Test
    public void testEntrySetByPattern() {
        RMapCache<String, String> map = redisson.getMapCache("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200", 1, TimeUnit.MINUTES);
        map.put("30", "300");

        assertThat(map.entrySet("?0")).containsExactly(new AbstractMap.SimpleEntry("10", "100"), new AbstractMap.SimpleEntry("20", "200"), new AbstractMap.SimpleEntry("30", "300"));
        assertThat(map.entrySet("1")).isEmpty();
        assertThat(map.entrySet("10")).containsExactly(new AbstractMap.SimpleEntry("10", "100"));
    }

    
    @Test
    public void testContainsValue() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple01", new MsgPackJacksonCodec());
        Assert.assertFalse(map.containsValue(new SimpleValue("34")));
        map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS);

        Assert.assertTrue(map.containsValue(new SimpleValue("44")));
        Assert.assertFalse(map.containsValue(new SimpleValue("34")));

        Thread.sleep(1000);

        Assert.assertFalse(map.containsValue(new SimpleValue("44")));
    }

    @Test
    public void testContainsKey() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple30");
        map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS);

        Assert.assertTrue(map.containsKey(new SimpleKey("33")));
        Assert.assertFalse(map.containsKey(new SimpleKey("34")));

        Thread.sleep(1000);

        Assert.assertFalse(map.containsKey(new SimpleKey("33")));
    }

    @Test
    public void testRemoveValue() {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"), 1, TimeUnit.SECONDS);

        boolean res = map.remove(new SimpleKey("1"), new SimpleValue("2"));
        Assert.assertTrue(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertNull(val1);

        Assert.assertEquals(0, map.size());
    }
    
    @Test
    public void testRemoveValueTTL() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"), 1, TimeUnit.SECONDS);

        Thread.sleep(1000);
        
        boolean res = map.remove(new SimpleKey("1"), new SimpleValue("2"));
        Assert.assertFalse(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertNull(val1);
    }


    @Test
    public void testRemoveValueFail() {
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
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
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("43"), new SimpleValue("31"));
        Assert.assertFalse(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertEquals("2", val1.getValue());
    }

    @Test
    public void testReplaceOldValueSuccess() {
        ConcurrentMap<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3"));
        Assert.assertTrue(res);

        boolean res1 = map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3"));
        Assert.assertFalse(res1);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertEquals("3", val1.getValue());
    }

    @Test
    public void testReplaceValue() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        SimpleValue res = map.replace(new SimpleKey("1"), new SimpleValue("3"));
        Assert.assertEquals("2", res.getValue());

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertEquals("3", val1.getValue());
    }

    @Test
    public void testReplaceValueTTL() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"), 1, TimeUnit.SECONDS);

        Thread.sleep(1000);
        
        SimpleValue res = map.replace(new SimpleKey("1"), new SimpleValue("3"));
        assertThat(res).isNull();

        SimpleValue val1 = map.get(new SimpleKey("1"));
        assertThat(val1).isNull();
    }

    @Test
    public void testReplace() {
        Map<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
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
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple3", new MsgPackJacksonCodec());
        Assert.assertNull(map.get(new SimpleKey("33")));

        map.put(new SimpleKey("33"), new SimpleValue("44"), 5, TimeUnit.SECONDS);
        map.put(new SimpleKey("10"), new SimpleValue("32"), 5, TimeUnit.SECONDS, 2, TimeUnit.SECONDS);
        map.put(new SimpleKey("01"), new SimpleValue("92"), 0, null, 2, TimeUnit.SECONDS);

        Assert.assertEquals(3, map.size());

        Thread.sleep(11000);

        Assert.assertEquals(0, map.size());

    }

    @Test
    public void testPutGet() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple04", new MsgPackJacksonCodec());
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
    }

    @Test
    public void testPutIfAbsent() throws Exception {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.put(key, value);
        Assert.assertEquals(value, map.putIfAbsent(key, new SimpleValue("3"), 1, TimeUnit.SECONDS));
        Assert.assertEquals(value, map.get(key));

        map.putIfAbsent(new SimpleKey("4"), new SimpleValue("4"), 1, TimeUnit.SECONDS);
        Assert.assertEquals(new SimpleValue("4"), map.get(new SimpleKey("4")));
        
        Thread.sleep(1000);

        Assert.assertNull(map.get(new SimpleKey("4")));
        
        // this should be passed
        map.putIfAbsent(new SimpleKey("4"), new SimpleValue("4"), 1, TimeUnit.SECONDS);
        Assert.assertEquals(new SimpleValue("4"), map.get(new SimpleKey("4")));
        

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        Assert.assertNull(map.putIfAbsent(key1, value1, 2, TimeUnit.SECONDS));
        Assert.assertEquals(value1, map.get(key1));
    }

    @Test
    public void testSize() {
        Map<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");

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
        RMapCache<Integer, Integer> map = redisson.getMapCache("simple");
        Assert.assertFalse(map.remove(1, 3));
        map.put(4, 5);
        Assert.assertTrue(map.remove(4, 5));
    }

    @Test
    public void testPutAsync() throws InterruptedException, ExecutionException {
        RMapCache<Integer, Integer> map = redisson.getMapCache("simple");
        RFuture<Integer> future = map.putAsync(2, 3);
        Assert.assertNull(future.get());

        Assert.assertEquals((Integer) 3, map.get(2));

        RFuture<Integer> future1 = map.putAsync(2, 4);
        Assert.assertEquals((Integer) 3, future1.get());

        Assert.assertEquals((Integer) 4, map.get(2));
    }

    @Test
    public void testRemoveAsync() throws InterruptedException, ExecutionException {
        RMapCache<Integer, Integer> map = redisson.getMapCache("simple");
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
        RMapCache<Integer, Integer> map = redisson.getMapCache("simple");
        map.put(1, 3);
        map.put(3, 5);
        map.put(4, 6);
        map.put(7, 8);

        Assert.assertEquals((Long) 3L, map.fastRemoveAsync(1, 3, 7).get());
        Thread.sleep(1);
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testFastPutIfAbsent() throws Exception {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.put(key, value);
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("3"))).isFalse();
        assertThat(map.get(key)).isEqualTo(value);

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        assertThat(map.fastPutIfAbsent(key1, value1)).isTrue();
        assertThat(map.get(key1)).isEqualTo(value1);
        
        SimpleKey key2 = new SimpleKey("3");
        map.put(key2, new SimpleValue("31"), 500, TimeUnit.MILLISECONDS);
        assertThat(map.fastPutIfAbsent(key2, new SimpleValue("32"))).isFalse();
        
        Thread.sleep(500);
        assertThat(map.fastPutIfAbsent(key2, new SimpleValue("32"))).isTrue();
        assertThat(map.get(key2)).isEqualTo(new SimpleValue("32"));

    }

    @Test
    public void testCreatedListener() {
        RMapCache<Integer, Integer> map = redisson.getMapCache("simple");
        
        checkCreatedListener(map, 1, 2, () -> map.put(1, 2));
        checkCreatedListener(map, 10, 2, () -> map.put(10, 2, 2, TimeUnit.SECONDS));
        checkCreatedListener(map, 2, 5, () -> map.fastPut(2, 5));
        checkCreatedListener(map, 13, 2, () -> map.fastPut(13, 2, 2, TimeUnit.SECONDS));
        checkCreatedListener(map, 3, 2, () -> map.putIfAbsent(3, 2));
        checkCreatedListener(map, 14, 2, () -> map.putIfAbsent(14, 2, 2, TimeUnit.SECONDS));
        checkCreatedListener(map, 4, 1, () -> map.fastPutIfAbsent(4, 1));
        checkCreatedListener(map, 15, 2, () -> map.fastPutIfAbsent(15, 2, 2, TimeUnit.SECONDS));
        checkCreatedListener(map, 5, 0, () -> map.addAndGet(5, 0));
    }

    private void checkCreatedListener(RMapCache<Integer, Integer> map, Integer key, Integer value, Runnable runnable) {
        AtomicBoolean ref = new AtomicBoolean();
        int createListener1 = map.addListener(new EntryCreatedListener<Integer, Integer>() {

            @Override
            public void onCreated(EntryEvent<Integer, Integer> event) {
                assertThat(event.getKey()).isEqualTo(key);
                assertThat(event.getValue()).isEqualTo(value);
                
                if (!ref.compareAndSet(false, true)) {
                    Assert.fail();
                }
            }
            
        });
        runnable.run();

        await().atMost(Duration.ONE_SECOND).untilTrue(ref);
        map.removeListener(createListener1);
    }
    
    @Test
    public void testUpdatedListener() {
        RMapCache<Integer, Integer> map = redisson.getMapCache("simple");

        map.put(1, 1);
        checkUpdatedListener(map, 1, 3, 1, () -> map.put(1, 3));
        
        map.put(10, 1);
        checkUpdatedListener(map, 10, 2, 1, () -> map.put(10, 2, 2, TimeUnit.SECONDS));
        
        map.put(2, 1);
        checkUpdatedListener(map, 2, 5, 1, () -> map.fastPut(2, 5));
        
        map.put(13, 1);
        checkUpdatedListener(map, 13, 2, 1, () -> map.fastPut(13, 2, 2, TimeUnit.SECONDS));
        
        map.put(14, 1);
        checkUpdatedListener(map, 14, 2, 1, () -> map.replace(14, 2));
        checkUpdatedListener(map, 14, 3, 2, () -> map.replace(14, 2, 3));
        
        map.put(5, 1);
        checkUpdatedListener(map, 5, 4, 1, () -> map.addAndGet(5, 3));

    }
    
    @Test
    public void testExpiredListener() {
        RMapCache<Integer, Integer> map = redisson.getMapCache("simple");

        checkExpiredListener(map, 10, 2, () -> map.put(10, 2, 2, TimeUnit.SECONDS));
        checkExpiredListener(map, 13, 2, () -> map.fastPut(13, 2, 2, TimeUnit.SECONDS));
        checkExpiredListener(map, 14, 2, () -> map.putIfAbsent(14, 2, 2, TimeUnit.SECONDS));
        checkExpiredListener(map, 15, 2, () -> map.fastPutIfAbsent(15, 2, 2, TimeUnit.SECONDS));
    }

    private void checkExpiredListener(RMapCache<Integer, Integer> map, Integer key, Integer value, Runnable runnable) {
        AtomicBoolean ref = new AtomicBoolean();
        int createListener1 = map.addListener(new EntryExpiredListener<Integer, Integer>() {

            @Override
            public void onExpired(EntryEvent<Integer, Integer> event) {
                assertThat(event.getKey()).isEqualTo(key);
                assertThat(event.getValue()).isEqualTo(value);
                
                if (!ref.compareAndSet(false, true)) {
                    Assert.fail();
                }
            }
            
        });
        runnable.run();

        await().atMost(Duration.ONE_MINUTE).untilTrue(ref);
        map.removeListener(createListener1);
    }

    
    private void checkUpdatedListener(RMapCache<Integer, Integer> map, Integer key, Integer value, Integer oldValue, Runnable runnable) {
        AtomicBoolean ref = new AtomicBoolean();
        int createListener1 = map.addListener(new EntryUpdatedListener<Integer, Integer>() {

            @Override
            public void onUpdated(EntryEvent<Integer, Integer> event) {
                assertThat(event.getKey()).isEqualTo(key);
                assertThat(event.getValue()).isEqualTo(value);
                assertThat(event.getOldValue()).isEqualTo(oldValue);
                
                if (!ref.compareAndSet(false, true)) {
                    Assert.fail();
                }
            }
            
        });
        runnable.run();

        await().atMost(Duration.ONE_SECOND).untilTrue(ref);
        map.removeListener(createListener1);
    }

    @Test
    public void testRemovedListener() {
        RMapCache<Integer, Integer> map = redisson.getMapCache("simple");

        map.put(1, 1);
        checkRemovedListener(map, 1, 1, () -> map.remove(1, 1));
        
        map.put(10, 1);
        checkRemovedListener(map, 10, 1, () -> map.remove(10));
        
        map.put(2, 1);
        checkRemovedListener(map, 2, 1, () -> map.fastRemove(2));
    }
    
    private void checkRemovedListener(RMapCache<Integer, Integer> map, Integer key, Integer value, Runnable runnable) {
        AtomicBoolean ref = new AtomicBoolean();
        int createListener1 = map.addListener(new EntryRemovedListener<Integer, Integer>() {

            @Override
            public void onRemoved(EntryEvent<Integer, Integer> event) {
                assertThat(event.getKey()).isEqualTo(key);
                assertThat(event.getValue()).isEqualTo(value);
                
                if (!ref.compareAndSet(false, true)) {
                    Assert.fail();
                }
            }
            
        });
        runnable.run();

        await().atMost(Duration.ONE_SECOND).untilTrue(ref);
        map.removeListener(createListener1);
    }

    
    @Test
    public void testFastPut() throws Exception {
        RMapCache<Integer, Integer> map = redisson.getMapCache("simple");
        Assert.assertTrue(map.fastPut(1, 2));
        assertThat(map.get(1)).isEqualTo(2);
        Assert.assertFalse(map.fastPut(1, 3));
        assertThat(map.get(1)).isEqualTo(3);
        Assert.assertEquals(1, map.size());
    }

    @Test
    public void testIdle() throws InterruptedException {
        testIdleExpiration(map -> {
            map.put("12", 1, 0, null, 1, TimeUnit.SECONDS);
            map.put("14", 2, 0, null, 2, TimeUnit.SECONDS);
            map.put("15", 3, 0, null, 3, TimeUnit.SECONDS);
        });

        testIdleExpiration(map -> {
            map.fastPut("12", 1, 0, null, 1, TimeUnit.SECONDS);
            map.fastPut("14", 2, 0, null, 2, TimeUnit.SECONDS);
            map.fastPut("15", 3, 0, null, 3, TimeUnit.SECONDS);
        });

        testIdleExpiration(map -> {
            map.putIfAbsent("12", 1, 0, null, 1, TimeUnit.SECONDS);
            map.putIfAbsent("14", 2, 0, null, 2, TimeUnit.SECONDS);
            map.putIfAbsent("15", 3, 0, null, 3, TimeUnit.SECONDS);
        });

        testIdleExpiration(map -> {
            map.fastPutIfAbsent("12", 1, 0, null, 1, TimeUnit.SECONDS);
            map.fastPutIfAbsent("14", 2, 0, null, 2, TimeUnit.SECONDS);
            map.fastPutIfAbsent("15", 3, 0, null, 3, TimeUnit.SECONDS);
        });
    }
    
    @Test
    public void testTTL() throws InterruptedException {
        testTTLExpiration(map -> {
            map.put("12", 1, 1, TimeUnit.SECONDS);
            map.put("14", 2, 2, TimeUnit.SECONDS);
            map.put("15", 3, 3, TimeUnit.SECONDS);
        });

        testTTLExpiration(map -> {
            map.fastPut("12", 1, 1, TimeUnit.SECONDS);
            map.fastPut("14", 2, 2, TimeUnit.SECONDS);
            map.fastPut("15", 3, 3, TimeUnit.SECONDS);
        });

        testTTLExpiration(map -> {
            map.putIfAbsent("12", 1, 1, TimeUnit.SECONDS);
            map.putIfAbsent("14", 2, 2, TimeUnit.SECONDS);
            map.putIfAbsent("15", 3, 3, TimeUnit.SECONDS);
        });

        testTTLExpiration(map -> {
            map.fastPutIfAbsent("12", 1, 1, TimeUnit.SECONDS);
            map.fastPutIfAbsent("14", 2, 2, TimeUnit.SECONDS);
            map.fastPutIfAbsent("15", 3, 3, TimeUnit.SECONDS);
        });
    }

    protected void testIdleExpiration(Consumer<RMapCache<String, Integer>> callback) throws InterruptedException {
        RMapCache<String, Integer> map = redisson.getMapCache("simple");

        callback.accept(map);

        Thread.sleep(1000);
        
        assertThat(map.get("12")).isNull();
        assertThat(map.get("14")).isEqualTo(2);
        assertThat(map.get("15")).isEqualTo(3);
        
        Thread.sleep(2000);
        
        assertThat(map.get("12")).isNull();
        assertThat(map.get("14")).isNull();
        assertThat(map.get("15")).isEqualTo(3);
        
        Thread.sleep(3000);

        assertThat(map.get("12")).isNull();
        assertThat(map.get("14")).isNull();
        assertThat(map.get("15")).isNull();

        
        map.clear();
    }
    
    protected void testTTLExpiration(Consumer<RMapCache<String, Integer>> callback) throws InterruptedException {
        RMapCache<String, Integer> map = redisson.getMapCache("simple");

        callback.accept(map);

        Thread.sleep(1000);
        
        assertThat(map.get("12")).isNull();
        assertThat(map.get("14")).isEqualTo(2);
        assertThat(map.get("15")).isEqualTo(3);
        
        Thread.sleep(1000);
        
        assertThat(map.get("12")).isNull();
        assertThat(map.get("14")).isNull();
        assertThat(map.get("15")).isEqualTo(3);
        
        Thread.sleep(1000);

        assertThat(map.get("12")).isNull();
        assertThat(map.get("14")).isNull();
        assertThat(map.get("15")).isNull();

        
        map.clear();
    }

    
    
    @Test
    public void testEquals() {
        RMapCache<String, String> map = redisson.getMapCache("simple");
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
    public void testExpireOverwrite() throws InterruptedException, ExecutionException {
        RMapCache<String, Integer> set = redisson.getMapCache("simple");
        set.put("123", 3, 1, TimeUnit.SECONDS);

        Thread.sleep(800);

        set.put("123", 3, 1, TimeUnit.SECONDS);

        Thread.sleep(800);
        Assert.assertEquals(3, (int)set.get("123"));

        Thread.sleep(200);

        Assert.assertFalse(set.containsKey("123"));
    }

    @Test
    public void testFastRemoveEmpty() throws Exception {
        RMapCache<Integer, Integer> map = redisson.getMapCache("simple");
        map.put(1, 3);
        Assert.assertEquals(0, map.fastRemove());
        Assert.assertEquals(1, map.size());
    }

    @Test(timeout = 5000)
    public void testDeserializationErrorReturnsErrorImmediately() throws Exception {
        redisson.getConfig().setCodec(new JsonJacksonCodec());

        RMapCache<String, SimpleObjectWithoutDefaultConstructor> map = redisson.getMapCache("deserializationFailure");
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

    
    
    @Test
    public void testRMapCacheValues() {
        final RMapCache<String, String> map = redisson.getMapCache("testRMapCacheValues");
        map.put("1234", "5678", 1, TimeUnit.MINUTES, 60, TimeUnit.MINUTES);
        assertThat(map.values()).containsOnly("5678");
    }

    @Test
    public void testReadAllEntrySet() throws InterruptedException {
        RMapCache<Integer, String> map = redisson.getMapCache("simple12");
        map.put(1, "12");
        map.put(2, "33", 10, TimeUnit.MINUTES, 60, TimeUnit.MINUTES);
        map.put(3, "43");
        
        assertThat(map.readAllEntrySet()).isEqualTo(map.entrySet());
    }

    
    @Test
    public void testReadAllValues() {
        final RMapCache<String, String> map = redisson.getMapCache("testRMapCacheAllValues");
        map.put("1234", "5678", 1, TimeUnit.MINUTES, 60, TimeUnit.MINUTES);
        assertThat(map.readAllValues()).containsOnly("5678");
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
    public void testAddAndGet() {
        RMapCache<String, Object> mapCache = redisson.getMapCache("test_put_if_absent", LongCodec.INSTANCE);
        assertThat(mapCache.putIfAbsent("4", 0L, 10000L, TimeUnit.SECONDS)).isNull();
        assertThat(mapCache.addAndGet("4", 1L)).isEqualTo(1L);
        assertThat(mapCache.putIfAbsent("4", 0L)).isEqualTo(1L);
        Assert.assertEquals(1L, mapCache.get("4"));
        mapCache = redisson.getMapCache("test_put_if_absent_1", LongCodec.INSTANCE);
        mapCache.putIfAbsent("4", 0L);
        mapCache.addAndGet("4", 1L);
        mapCache.putIfAbsent("4", 0L);
        Assert.assertEquals(1L, mapCache.get("4"));
        RMap map = redisson.getMap("test_put_if_absent_2", LongCodec.INSTANCE);
        map.putIfAbsent("4", 0L);
        map.addAndGet("4", 1L);
        map.putIfAbsent("4", 0L);
        Assert.assertEquals(1L, map.get("4"));
        RMapCache<String, Object> mapCache1 = redisson.getMapCache("test_put_if_absent_3", DoubleCodec.INSTANCE);
        mapCache1.putIfAbsent("4", 1.23, 10000L, TimeUnit.SECONDS);
        mapCache1.addAndGet("4", 1D);
        Assert.assertEquals(2.23, mapCache1.get("4"));
    }

    
    @Test
    public void testFastPutIfAbsentWithTTL() throws Exception {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simpleTTL");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.fastPutIfAbsent(key, value, 1, TimeUnit.SECONDS);
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("3"), 1, TimeUnit.SECONDS)).isFalse();
        assertThat(map.get(key)).isEqualTo(value);
        
        Thread.sleep(1100);
        
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("3"), 1, TimeUnit.SECONDS)).isTrue();
        assertThat(map.get(key)).isEqualTo(new SimpleValue("3"));
        
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("4"), 1, TimeUnit.SECONDS)).isFalse();
        assertThat(map.get(key)).isEqualTo(new SimpleValue("3"));
        
        Thread.sleep(1100);
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("4"), 1, TimeUnit.SECONDS, 500, TimeUnit.MILLISECONDS)).isTrue();
        
        Thread.sleep(550);
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("5"), 1, TimeUnit.SECONDS, 500, TimeUnit.MILLISECONDS)).isTrue();

    }
}

