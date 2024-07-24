package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMap;
import org.redisson.api.RMapCacheNative;
import org.redisson.api.listener.MapExpiredListener;
import org.redisson.api.listener.MapRemoveListener;
import org.redisson.api.map.WriteMode;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonMapCacheNativeTest extends BaseMapTest {

    @Test
    public void testExpireEntry() {
        RMapCacheNative<String, String> testMap = redisson.getMapCacheNative("map");
        testMap.put("key", "value");
        testMap.expireEntry("key", Duration.ofMillis(20000));
        assertThat(testMap.remainTimeToLive("key")).isBetween(19800L, 20000L);
    }

    @Test
    public void testExpireEntryIfNotSet() {
        RMapCacheNative<String, String> testMap = redisson.getMapCacheNative("map");
        testMap.put("key", "value");
        testMap.expireEntryIfNotSet("key", Duration.ofMillis(20000));
        assertThat(testMap.remainTimeToLive("key")).isBetween(19800L, 20000L);
    }

    @Test
    public void testExpireEntries() {
        RMapCacheNative<String, String> testMap = redisson.getMapCacheNative("map");
        testMap.put("key1", "value");
        testMap.put("key2", "value");
        testMap.expireEntries(new HashSet<>(Arrays.asList("key1", "key2")), Duration.ofMillis(20000));
        assertThat(testMap.remainTimeToLive("key1")).isBetween(19800L, 20000L);
    }

    @Test
    public void testExpireEntriesIfNotSet() {
        RMapCacheNative<String, String> testMap = redisson.getMapCacheNative("map");
        testMap.put("key1", "value");
        testMap.put("key2", "value");
        testMap.expireEntriesIfNotSet(new HashSet<>(Arrays.asList("key1", "key2")), Duration.ofMillis(20000));
        assertThat(testMap.remainTimeToLive("key1")).isBetween(19800L, 20000L);
    }

    @Test
    public void testFastPutExpiration() throws Exception {
        RMapCacheNative<String, Object> mapCache = redisson.getMapCacheNative("testFastPutExpiration");
        mapCache.fastPut("k1", "v1", Duration.ofSeconds(1));
        Thread.sleep(1000);
        mapCache.fastPut("k1", "v2");
        assertThat(mapCache.get("k1")).isEqualTo("v2");
    }

    @Test
    public void testEntryEntryIfNotSet() throws InterruptedException {
        RMapCacheNative<Integer, Integer> cache = redisson.getMapCacheNative("testUpdateEntryExpiration");
        cache.put(1, 10);
        cache.put(2, 20);
        cache.put(3, 30, Duration.ofSeconds(1));

        assertThat(cache.expireEntryIfNotSet(2, Duration.ofSeconds(2))).isTrue();
        assertThat(cache.expireEntryIfNotSet(3, Duration.ofSeconds(4))).isFalse();
        long ttl2 = cache.remainTimeToLive(2);
        assertThat(ttl2).isBetween(1900L, 2000L);
        Thread.sleep(1200);
        assertThat(cache.containsKey(2)).isTrue();
        assertThat(cache.containsKey(3)).isFalse();
        Thread.sleep(1300);
        assertThat(cache.containsKey(2)).isFalse();
    }

    @Test
    public void testEntryEntriesIfNotSet() throws InterruptedException {
        RMapCacheNative<Integer, Integer> cache = redisson.getMapCacheNative("testUpdateEntryExpiration");
        cache.put(1, 10);
        cache.put(2, 20);
        cache.put(3, 30, Duration.ofSeconds(1));

        assertThat(cache.expireEntriesIfNotSet(new HashSet<>(Arrays.asList(2, 3)), Duration.ofSeconds(2))).isEqualTo(1);
        long ttl2 = cache.remainTimeToLive(2);
        assertThat(ttl2).isBetween(1900L, 2000L);
        Thread.sleep(2200);
        assertThat(cache.expireEntriesIfNotSet(new HashSet<>(Arrays.asList(2, 3)), Duration.ofSeconds(2))).isZero();
        assertThat(cache.containsKey(2)).isFalse();
        assertThat(cache.containsKey(3)).isFalse();
    }

    @Test
    public void testEntryEntries() throws InterruptedException {
        RMapCacheNative<Integer, Integer> cache = redisson.getMapCacheNative("testUpdateEntryExpiration");
        cache.put(1, 10, Duration.ofSeconds(3));
        cache.put(2, 20, Duration.ofSeconds(3));
        cache.put(3, 30, Duration.ofSeconds(3));

        Thread.sleep(2000);
        long ttl = cache.remainTimeToLive(1);
        assertThat(ttl).isBetween(900L, 1000L);
        assertThat(cache.expireEntries(new HashSet<>(Arrays.asList(2, 3)), Duration.ofSeconds(2))).isEqualTo(2);
        long ttl2 = cache.remainTimeToLive(2);
        assertThat(ttl2).isBetween(1900L, 2000L);
        Thread.sleep(2200);
        assertThat(cache.expireEntries(new HashSet<>(Arrays.asList(2, 3)), Duration.ofSeconds(2))).isZero();
    }

    @Test
    public void testUpdateEntryExpiration() throws InterruptedException {
        RMapCacheNative<Integer, Integer> cache = redisson.getMapCacheNative("testUpdateEntryExpiration");
        cache.put(1, 2, Duration.ofSeconds(3));
        Thread.sleep(2000);
        long ttl = cache.remainTimeToLive(1);
        assertThat(ttl).isBetween(900L, 1000L);
        assertThat(cache.expireEntry(1, Duration.ofSeconds(2))).isTrue();
        long ttl2 = cache.remainTimeToLive(1);
        assertThat(ttl2).isBetween(1900L, 2000L);
        Thread.sleep(2200);
        assertThat(cache.expireEntry(1, Duration.ofSeconds(2))).isFalse();
    }

    @Test
    public void testRemoveListener() {
        testWithParams(redisson -> {
            RMapCacheNative<Long, String> rMapCache = redisson.getMapCacheNative("test");
            AtomicBoolean removed = new AtomicBoolean();
            rMapCache.addListener(new MapRemoveListener() {
                @Override
                public void onRemove(String name) {
                    removed.set(true);
                }
            });

            rMapCache.put(1L, "1");
            rMapCache.remove(1L);

            Awaitility.await().atMost(Duration.ofSeconds(5)).untilTrue(removed);
        }, NOTIFY_KEYSPACE_EVENTS, "Eh");

    }

    @Override
    protected <K, V> RMap<K, V> getMap(String name) {
        return redisson.getMapCacheNative(name);        
    }
    
    @Override
    protected <K, V> RMap<K, V> getMap(String name, Codec codec) {
        return redisson.getMapCacheNative(name, codec);
    }
    
    @Override
    protected <K, V> RMap<K, V> getWriterTestMap(String name, Map<K, V> map) {
        org.redisson.api.options.MapOptions<K, V> options = org.redisson.api.options.MapOptions.<K, V>name("test").writer(createMapWriter(map));
        return redisson.getMapCacheNative(options);
    }
    
    @Override
    protected <K, V> RMap<K, V> getWriteBehindTestMap(String name, Map<K, V> map) {
        org.redisson.api.options.MapOptions<K, V> options = org.redisson.api.options.MapOptions.<K, V>name("test")
                                    .writer(createMapWriter(map))
                                    .writeMode(org.redisson.api.map.WriteMode.WRITE_BEHIND);
        return redisson.getMapCacheNative(options);
    }

    @Override
    protected <K, V> RMap<K, V> getWriteBehindAsyncTestMap(String name, Map<K, V> map) {
        org.redisson.api.options.MapOptions<K, V> options = org.redisson.api.options.MapOptions.<K, V>name("test")
                .writerAsync(createMapWriterAsync(map))
                .writeMode(WriteMode.WRITE_BEHIND);
        return redisson.getMapCacheNative(options);
    }

    @Override
    protected <K, V, M extends RMap<K, V>> M getLoaderTestMap(String name, Map<K, V> map) {
        org.redisson.api.options.MapOptions<K, V> options = org.redisson.api.options.MapOptions.<K, V>name("test").loader(createMapLoader(map));
        return (M) redisson.getMapCacheNative(options);
    }

    @Override
    protected <K, V> RMap<K, V> getLoaderAsyncTestMap(String name, Map<K, V> map) {
        org.redisson.api.options.MapOptions<K, V> options = org.redisson.api.options.MapOptions.<K, V>name("test").loaderAsync(createMapLoaderAsync(map));
        return redisson.getMapCacheNative(options);
    }

    @Test
    public void testSizeInMemory() {
        RMapCacheNative<Integer, Integer> map = redisson.getMapCacheNative("test");
        for (int i = 0; i < 10; i++) {
            map.put(i, i, Duration.ofSeconds(5));
        }
        
        assertThat(map.sizeInMemory()).isGreaterThanOrEqualTo(272);
    }
    
    @Test
    public void testRemainTimeToLive() {
        RMapCacheNative<String, String> map = redisson.getMapCacheNative("test");
        map.put("1", "2", Duration.ofSeconds(2));
        assertThat(map.remainTimeToLive("1")).isBetween(1900L, 2000L);
        map.put("3", "4");
        assertThat(map.remainTimeToLive("3")).isEqualTo(-1);
        assertThat(map.remainTimeToLive("0")).isEqualTo(-2);

        map.put("5", "6", Duration.ofSeconds(20));
        assertThat(map.remainTimeToLive("1")).isLessThan(9900);

        Map<String, Long> r = map.remainTimeToLive(Set.of("0", "1", "3", "5", "6"));
        assertThat(r.get("0")).isEqualTo(-2);
        assertThat(r.get("1")).isGreaterThan(1);
        assertThat(r.get("3")).isEqualTo(-1);
        assertThat(r.get("5")).isGreaterThan(1);
        assertThat(r.get("6")).isEqualTo(-2);
    }
    
    @Test
    public void testFastPutTTL() throws InterruptedException {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("getAll");
        map.fastPut(new SimpleKey("1"), new SimpleValue("3"), Duration.ofSeconds(5));
        Thread.sleep(5000);
        assertThat(map.get(new SimpleKey("1"))).isNull();

        map.fastPut(new SimpleKey("1"), new SimpleValue("4"), Duration.ofSeconds(5));
        Thread.sleep(10000);
        assertThat(map.get(new SimpleKey("1"))).isNull();
    }
    
    @Test
    public void testWriterPutIfAbsentTTL() {
        Map<String, String> store = new HashMap<>();
        RMapCacheNative<String, String> map = (RMapCacheNative<String, String>) getWriterTestMap("test", store);

        map.putIfAbsent("1", "11", Duration.ofSeconds(10));
        map.putIfAbsent("1", "00", Duration.ofSeconds(10));
        map.putIfAbsent("2", "22", Duration.ofSeconds(10));
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        assertThat(store).isEqualTo(expected);
        map.destroy();
    }
    
    @Test
    public void testWriterPutTTL() {
        Map<String, String> store = new HashMap<>();
        RMapCacheNative<String, String> map = (RMapCacheNative<String, String>) getWriterTestMap("test", store);
        
        map.put("1", "11", Duration.ofSeconds(10));
        map.put("2", "22", Duration.ofSeconds(10));
        map.put("3", "33", Duration.ofSeconds(10));
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
        map.destroy();
    }
    
    @Test
    public void testWriterFastPutIfAbsentTTL() {
        Map<String, String> store = new HashMap<>();
        RMapCacheNative<String, String> map = (RMapCacheNative<String, String>) getWriterTestMap("test", store);

        map.fastPutIfAbsent("1", "11", Duration.ofSeconds(10));
        map.fastPutIfAbsent("1", "00", Duration.ofSeconds(10));
        map.fastPutIfAbsent("2", "22", Duration.ofSeconds(10));
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        assertThat(store).isEqualTo(expected);
        map.destroy();
    }
    
    @Test
    public void testWriterFastPutTTL() {
        Map<String, String> store = new HashMap<>();
        RMapCacheNative<String, String> map = (RMapCacheNative<String, String>) getWriterTestMap("test", store);

        map.fastPut("1", "11", Duration.ofSeconds(10));
        map.fastPut("2", "22", Duration.ofSeconds(10));
        map.fastPut("3", "33", Duration.ofSeconds(10));
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
        map.destroy();
    }

    @Test
    public void testCacheValues() {
        final RMapCacheNative<String, String> map = redisson.getMapCacheNative("testRMapCacheValues");
        map.put("1234", "5678", Duration.ofSeconds(60));
        assertThat(map.values()).containsOnly("5678");
        map.destroy();
    }    

    @Test
    public void testGetAllTTL() throws InterruptedException {
        RMapCacheNative<Integer, Integer> map = redisson.getMapCacheNative("getAll");
        map.put(1, 100);
        map.put(2, 200, Duration.ofSeconds(1));
        map.put(3, 300, Duration.ofSeconds(1));
        map.put(4, 400);

        Map<Integer, Integer> filtered = map.getAll(new HashSet<Integer>(Arrays.asList(2, 3, 5)));

        Map<Integer, Integer> expectedMap = new HashMap<Integer, Integer>();
        expectedMap.put(2, 200);
        expectedMap.put(3, 300);
        Assertions.assertEquals(expectedMap, filtered);

        Thread.sleep(1000);

        Map<Integer, Integer> filteredAgain = map.getAll(new HashSet<Integer>(Arrays.asList(2, 3, 5)));
        Assertions.assertTrue(filteredAgain.isEmpty());
        map.destroy();
    }

    @Test
    public void testGetAllWithStringKeys() {
        RMapCacheNative<String, Integer> map = redisson.getMapCacheNative("getAllStrings");
        map.put("A", 100);
        map.put("B", 200);
        map.put("C", 300);
        map.put("D", 400);

        Map<String, Integer> filtered = map.getAll(new HashSet<String>(Arrays.asList("B", "C", "E")));

        Map<String, Integer> expectedMap = new HashMap<String, Integer>();
        expectedMap.put("B", 200);
        expectedMap.put("C", 300);
        Assertions.assertEquals(expectedMap, filtered);
        map.destroy();
    }

    @Test
    public void testExpiredIterator() throws InterruptedException {
        RMapCacheNative<String, String> cache = redisson.getMapCacheNative("simple");
        cache.put("0", "8");
        cache.put("1", "6", Duration.ofSeconds(1));
        cache.put("2", "4", Duration.ofSeconds(3));
        cache.put("3", "2", Duration.ofSeconds(4));
        cache.put("4", "4", Duration.ofSeconds(1));

        Thread.sleep(1000);

        assertThat(cache.keySet()).containsOnly("0", "2", "3");
        cache.destroy();
    }

    @Test
    public void testExpire() throws InterruptedException {
        RMapCacheNative<String, String> cache = redisson.getMapCacheNative("simple");
        cache.put("0", "8", Duration.ofSeconds(1));

        cache.expire(Duration.ofMillis(100));

        Thread.sleep(500);

        Assertions.assertEquals(0, cache.size());
        cache.destroy();
    }

    @Test
    public void testExpireAt() throws InterruptedException {
        RMapCacheNative<String, String> cache = redisson.getMapCacheNative("simple");
        cache.put("0", "8", Duration.ofSeconds(1));

        cache.expireAt(System.currentTimeMillis() + 100);

        Thread.sleep(500);

        Assertions.assertEquals(0, cache.size());
        cache.destroy();
    }

    @Test
    public void testClear() {
        RMapCacheNative<String, String> cache = redisson.getMapCacheNative("simple");
        cache.put("0", "8", Duration.ofSeconds(1));
        cache.put("02", "18", Duration.ofSeconds(1));
        cache.put("03", "38", Duration.ofSeconds(1));

        assertThat(cache.clearExpire("0")).isTrue();
        assertThat(cache.clearExpire("01")).isNull();

        Map<String, Boolean> r = cache.clearExpire(Set.of("0", "02", "03", "04"));
        assertThat(r.get("0")).isFalse();
        assertThat(r.get("02")).isTrue();
        assertThat(r.get("03")).isTrue();
        assertThat(r.get("04")).isNull();
    }
    
    @Test
    public void testClearExpire() throws InterruptedException {
        RMapCacheNative<String, String> cache = redisson.getMapCacheNative("simple");
        cache.put("0", "8", Duration.ofSeconds(1));

        cache.expireAt(System.currentTimeMillis() + 100);

        cache.clearExpire();

        Thread.sleep(500);

        Assertions.assertEquals(1, cache.size());
        cache.destroy();
    }

    @Test
    public void testEntrySet() throws InterruptedException {
        RMapCacheNative<Integer, String> map = redisson.getMapCacheNative("simple12");
        map.put(1, "12");
        map.put(2, "33", Duration.ofSeconds(1));
        map.put(3, "43");

        Map<Integer, String> expected = new HashMap<>();
        map.put(1, "12");
        map.put(3, "43");
        
        assertThat(map.entrySet()).containsAll(expected.entrySet());
        assertThat(map).hasSize(3);
        map.destroy();
    }
    
    @Test
    public void testKeySet() throws InterruptedException {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simple03");
        map.put(new SimpleKey("33"), new SimpleValue("44"), Duration.ofSeconds(1));
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        Assertions.assertTrue(map.keySet().contains(new SimpleKey("33")));
        Assertions.assertFalse(map.keySet().contains(new SimpleKey("44")));
        Assertions.assertTrue(map.keySet().contains(new SimpleKey("1")));

        Thread.sleep(1000);

        Assertions.assertFalse(map.keySet().contains(new SimpleKey("33")));
        Assertions.assertFalse(map.keySet().contains(new SimpleKey("44")));
        Assertions.assertTrue(map.keySet().contains(new SimpleKey("1")));
        map.destroy();
    }

    @Test
    public void testValues() throws InterruptedException {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simple05");
        map.put(new SimpleKey("33"), new SimpleValue("44"), Duration.ofSeconds(1));
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        Assertions.assertTrue(map.values().contains(new SimpleValue("44")));
        Assertions.assertFalse(map.values().contains(new SimpleValue("33")));
        Assertions.assertTrue(map.values().contains(new SimpleValue("2")));

        Thread.sleep(1000);

        Assertions.assertFalse(map.values().contains(new SimpleValue("44")));
        Assertions.assertFalse(map.values().contains(new SimpleValue("33")));
        Assertions.assertTrue(map.values().contains(new SimpleValue("2")));
        map.destroy();
    }

    @Test
    public void testKeySetByPatternTTL() {
        RMapCacheNative<String, String> map = redisson.getMapCacheNative("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200", Duration.ofMinutes(1));
        map.put("30", "300");

        assertThat(map.keySet("?0")).containsExactlyInAnyOrder("10", "20", "30");
        assertThat(map.keySet("1")).isEmpty();
        assertThat(map.keySet("10")).containsExactlyInAnyOrder("10");
        map.destroy();
    }

    @Test
    public void testValuesByPatternTTL() {
        RMapCacheNative<String, String> map = redisson.getMapCacheNative("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200", Duration.ofMinutes(1));
        map.put("30", "300");

        assertThat(map.values("?0")).containsExactlyInAnyOrder("100", "200", "300");
        assertThat(map.values("1")).isEmpty();
        assertThat(map.values("10")).containsExactlyInAnyOrder("100");
        map.destroy();
    }

    @Test
    public void testEntrySetByPatternTTL() {
        RMapCacheNative<String, String> map = redisson.getMapCacheNative("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200", Duration.ofMinutes(1));
        map.put("30", "300");

        assertThat(map.entrySet("?0")).containsExactlyInAnyOrder(new AbstractMap.SimpleEntry("10", "100"), new AbstractMap.SimpleEntry("20", "200"), new AbstractMap.SimpleEntry("30", "300"));
        assertThat(map.entrySet("1")).isEmpty();
        assertThat(map.entrySet("10")).containsExactlyInAnyOrder(new AbstractMap.SimpleEntry("10", "100"));
        map.destroy();
    }

    
    @Test
    public void testContainsValueTTL() throws InterruptedException {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simple01");
        Assertions.assertFalse(map.containsValue(new SimpleValue("34")));
        map.put(new SimpleKey("33"), new SimpleValue("44"), Duration.ofSeconds(1));

        Assertions.assertTrue(map.containsValue(new SimpleValue("44")));
        Assertions.assertFalse(map.containsValue(new SimpleValue("34")));

        Thread.sleep(1000);

        Assertions.assertFalse(map.containsValue(new SimpleValue("44")));
        map.destroy();
    }

    @Test
    public void testContainsKeyTTL() throws InterruptedException {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simple30");
        map.put(new SimpleKey("33"), new SimpleValue("44"), Duration.ofSeconds(1));

        Assertions.assertTrue(map.containsKey(new SimpleKey("33")));
        Assertions.assertFalse(map.containsKey(new SimpleKey("34")));

        Thread.sleep(1000);

        Assertions.assertFalse(map.containsKey(new SimpleKey("33")));
        map.destroy();
    }

    @Test
    public void testRemoveValueTTL() throws InterruptedException {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"), Duration.ofSeconds(1));

        boolean res = map.remove(new SimpleKey("1"), new SimpleValue("2"));
        Assertions.assertTrue(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assertions.assertNull(val1);

        Assertions.assertEquals(0, map.size());
        
        map.put(new SimpleKey("3"), new SimpleValue("4"), Duration.ofSeconds(1));

        Thread.sleep(1000);
        
        assertThat(map.remove(new SimpleKey("3"), new SimpleValue("4"))).isFalse();

        assertThat(map.get(new SimpleKey("3"))).isNull();
        map.destroy();
    }
    
    @Test
    public void testRemoveValueFail() {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.remove(new SimpleKey("2"), new SimpleValue("1"));
        Assertions.assertFalse(res);

        boolean res1 = map.remove(new SimpleKey("1"), new SimpleValue("3"));
        Assertions.assertFalse(res1);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assertions.assertEquals("2", val1.getValue());
        map.destroy();
    }


    @Test
    public void testReplaceOldValueFail() {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("43"), new SimpleValue("31"));
        Assertions.assertFalse(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assertions.assertEquals("2", val1.getValue());
        map.destroy();
    }

    @Test
    public void testReplaceOldValueSuccess() {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3"));
        Assertions.assertTrue(res);

        boolean res1 = map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3"));
        Assertions.assertFalse(res1);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assertions.assertEquals("3", val1.getValue());
        map.destroy();
    }

    @Test
    public void testExpiration() {
        testWithParams(redisson -> {
            AtomicBoolean executed = new AtomicBoolean();
            RMapCacheNative<String, String> map = redisson.getMapCacheNative("simple");
            map.addListener(new MapExpiredListener() {
                @Override
                public void onExpired(String name) {
                    executed.set(true);
                }
            });
            map.put("1", "2", Duration.ofSeconds(1));

            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(executed.get()).isTrue());

            redisson.shutdown();
        }, NOTIFY_KEYSPACE_EVENTS, "Eh");
    }

    @Test
    public void testReplaceValueTTL() throws InterruptedException {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"), Duration.ofSeconds(1));

        Thread.sleep(1000);
        
        SimpleValue res = map.replace(new SimpleKey("1"), new SimpleValue("3"));
        assertThat(res).isNull();

        SimpleValue val1 = map.get(new SimpleKey("1"));
        assertThat(val1).isNull();
        map.destroy();
    }

    @Test
    public void testPutGetTTL() throws InterruptedException {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simple04");
        Assertions.assertNull(map.get(new SimpleKey("33")));

        map.put(new SimpleKey("33"), new SimpleValue("44"), Duration.ofSeconds(2));

        SimpleValue val1 = map.get(new SimpleKey("33"));
        Assertions.assertEquals("44", val1.getValue());

        Thread.sleep(1000);

        Assertions.assertEquals(1, map.size());
        SimpleValue val2 = map.get(new SimpleKey("33"));
        Assertions.assertEquals("44", val2.getValue());
        Assertions.assertEquals(1, map.size());

        Thread.sleep(1000);

        Assertions.assertNull(map.get(new SimpleKey("33")));
        map.destroy();
    }

    @Test
    public void testPutAllGetTTL() throws InterruptedException {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simple06");
        Assertions.assertNull(map.get(new SimpleKey("33")));
        Assertions.assertNull(map.get(new SimpleKey("55")));

        Map<SimpleKey, SimpleValue> entries = new HashMap<>();
        entries.put(new SimpleKey("33"), new SimpleValue("44"));
        entries.put(new SimpleKey("55"), new SimpleValue("66"));
        map.putAll(entries, Duration.ofSeconds(2));

        SimpleValue val1 = map.get(new SimpleKey("33"));
        Assertions.assertEquals("44", val1.getValue());
        SimpleValue val2 = map.get(new SimpleKey("55"));
        Assertions.assertEquals("66", val2.getValue());

        Thread.sleep(1000);

        Assertions.assertEquals(2, map.size());
        SimpleValue val3 = map.get(new SimpleKey("33"));
        Assertions.assertEquals("44", val3.getValue());
        SimpleValue val4 = map.get(new SimpleKey("55"));
        Assertions.assertEquals("66", val4.getValue());
        Assertions.assertEquals(2, map.size());

        Thread.sleep(1000);

        Assertions.assertNull(map.get(new SimpleKey("33")));
        Assertions.assertNull(map.get(new SimpleKey("55")));
        map.destroy();
    }
    
    @Test
    public void testPutIfAbsentTTL() throws Exception {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simple");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.put(key, value);
        Assertions.assertEquals(value, map.putIfAbsent(key, new SimpleValue("3"), Duration.ofSeconds(1)));
        Assertions.assertEquals(value, map.get(key));

        map.putIfAbsent(new SimpleKey("4"), new SimpleValue("4"), Duration.ofSeconds(1));
        Assertions.assertEquals(new SimpleValue("4"), map.get(new SimpleKey("4")));
        
        Thread.sleep(1000);

        Assertions.assertNull(map.get(new SimpleKey("4")));
        
        // this should be passed
        map.putIfAbsent(new SimpleKey("4"), new SimpleValue("4"), Duration.ofSeconds(1));
        Assertions.assertEquals(new SimpleValue("4"), map.get(new SimpleKey("4")));
        

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        Assertions.assertNull(map.putIfAbsent(key1, value1, Duration.ofSeconds(2)));
        Assertions.assertEquals(value1, map.get(key1));
        map.destroy();
    }

    @Test
    public void testFastPutIfAbsentTTL() throws Exception {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simple");
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
        map.put(key2, new SimpleValue("31"), Duration.ofMillis(500));
        assertThat(map.fastPutIfAbsent(key2, new SimpleValue("32"))).isFalse();
        
        Thread.sleep(500);
        assertThat(map.fastPutIfAbsent(key2, new SimpleValue("32"))).isTrue();
        assertThat(map.get(key2)).isEqualTo(new SimpleValue("32"));
        map.destroy();

    }

    @Test
    public void testEntryUpdate() throws InterruptedException {
        RMapCacheNative<Integer, Integer> map = redisson.getMapCacheNative("simple");
        map.put(1, 1, Duration.ofSeconds(1));
        assertThat(map.get(1)).isEqualTo(1);

        Thread.sleep(1000);

        assertThat(map.put(1, 1, Duration.ofSeconds(0))).isNull();
        assertThat(map.get(1)).isEqualTo(1);
    }

    @Test
    public void testTTL() throws InterruptedException {
        testTTLExpiration(map -> {
            map.put("12", 1, Duration.ofSeconds(1));
            map.put("14", 2, Duration.ofSeconds(2));
            map.put("15", 3, Duration.ofSeconds(3));
        });

        testTTLExpiration(map -> {
            map.fastPut("12", 1, Duration.ofSeconds(1));
            map.fastPut("14", 2, Duration.ofSeconds(2));
            map.fastPut("15", 3, Duration.ofSeconds(3));
        });

        testTTLExpiration(map -> {
            map.putIfAbsent("12", 1, Duration.ofSeconds(1));
            map.putIfAbsent("14", 2, Duration.ofSeconds(2));
            map.putIfAbsent("15", 3, Duration.ofSeconds(3));
        });

        testTTLExpiration(map -> {
            map.fastPutIfAbsent("12", 1, Duration.ofSeconds(1));
            map.fastPutIfAbsent("14", 2, Duration.ofSeconds(2));
            map.fastPutIfAbsent("15", 3, Duration.ofSeconds(3));
        });
    }

    protected void testTTLExpiration(Consumer<RMapCacheNative<String, Integer>> callback) throws InterruptedException {
        RMapCacheNative<String, Integer> map = redisson.getMapCacheNative("simple");

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
        map.destroy();
    }

    @Test
    public void testExpireOverwrite() throws InterruptedException, ExecutionException {
        RMapCacheNative<String, Integer> map = redisson.getMapCacheNative("simple");
        map.put("123", 3, Duration.ofSeconds(1));

        Thread.sleep(800);

        map.put("123", 3, Duration.ofSeconds(1));

        Thread.sleep(800);
        Assertions.assertEquals(3, (int)map.get("123"));

        Thread.sleep(200);

        Assertions.assertFalse(map.containsKey("123"));
        map.destroy();
    }

    @Test
    public void testRMapCacheValues() {
        final RMapCacheNative<String, String> map = redisson.getMapCacheNative("testRMapCacheValues");
        map.put("1234", "5678", Duration.ofMinutes(1));
        assertThat(map.values()).containsOnly("5678");
        map.destroy();
    }

    @Test
    public void testReadAllEntrySet() throws InterruptedException {
        RMapCacheNative<Integer, String> map = redisson.getMapCacheNative("simple12");
        map.put(1, "12");
        map.put(2, "33", Duration.ofMinutes(10));
        map.put(3, "43");
        
        assertThat(map.readAllEntrySet()).isEqualTo(map.entrySet());
        map.destroy();
    }

    @Test
    public void testReadAllValuesTTL() {
        final RMapCacheNative<String, String> map = redisson.getMapCacheNative("testRMapCacheAllValues");
        map.put("1234", "5678", Duration.ofMinutes(1));
        assertThat(map.readAllValues()).containsOnly("5678");
        map.destroy();
    }

    @Test
    public void testAddAndGetTTL() {
        RMapCacheNative<String, Object> mapCache = redisson.getMapCacheNative("test_put_if_absent", LongCodec.INSTANCE);
        assertThat(mapCache.putIfAbsent("4", 0L, Duration.ofSeconds(10000))).isNull();
        assertThat(mapCache.addAndGet("4", 1L)).isEqualTo(1L);
        assertThat(mapCache.putIfAbsent("4", 0L)).isEqualTo(1L);
        assertThat(mapCache.addAndGet("key", Long.MAX_VALUE-10)).isEqualTo(Long.MAX_VALUE-10);
        assertThat(mapCache.addAndGet("key", 10L)).isEqualTo(Long.MAX_VALUE);
        Assertions.assertEquals(1L, mapCache.get("4"));
        mapCache.destroy();
        mapCache = redisson.getMapCacheNative("test_put_if_absent_1", LongCodec.INSTANCE);
        mapCache.putIfAbsent("4", 0L);
        mapCache.addAndGet("4", 1L);
        mapCache.putIfAbsent("4", 0L);
        Assertions.assertEquals(1L, mapCache.get("4"));
        RMap map = redisson.getMap("test_put_if_absent_2", LongCodec.INSTANCE);
        map.putIfAbsent("4", 0L);
        map.addAndGet("4", 1L);
        map.putIfAbsent("4", 0L);
        Assertions.assertEquals(1L, map.get("4"));
        RMapCacheNative<String, Object> mapCache1 = redisson.getMapCacheNative("test_put_if_absent_3", DoubleCodec.INSTANCE);
        mapCache1.putIfAbsent("4", 1.23, Duration.ofSeconds(10000));
        mapCache1.addAndGet("4", 1D);
        Assertions.assertEquals(2.23, mapCache1.get("4"));
        mapCache.destroy();
        mapCache1.destroy();
    }

    
    @Test
    public void testFastPutIfAbsentWithTTL() throws Exception {
        RMapCacheNative<SimpleKey, SimpleValue> map = redisson.getMapCacheNative("simpleTTL");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.fastPutIfAbsent(key, value, Duration.ofSeconds(1));
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("3"), Duration.ofSeconds(1))).isFalse();
        assertThat(map.get(key)).isEqualTo(value);
        
        Thread.sleep(1100);
        
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("3"), Duration.ofSeconds(1))).isTrue();
        assertThat(map.get(key)).isEqualTo(new SimpleValue("3"));
        
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("4"), Duration.ofSeconds(1))).isFalse();
        assertThat(map.get(key)).isEqualTo(new SimpleValue("3"));
        
        Thread.sleep(1100);
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("4"), Duration.ofSeconds(1))).isTrue();
        map.destroy();
    }
}

