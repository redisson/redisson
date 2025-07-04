package org.redisson;

import org.awaitility.Awaitility;
import org.joor.Reflect;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.api.MapOptions.WriteMode;
import org.redisson.api.map.event.*;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.*;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.codec.CompositeCodec;
import org.redisson.config.Config;
import org.redisson.eviction.EvictionScheduler;

import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class RedissonMapCacheTest extends BaseMapTest {

    public static class SimpleEntryCreatedListener implements EntryCreatedListener {

        private Map<Object, Object> map = new HashMap<>();

        @Override
        public void onCreated(EntryEvent event) {
            map.put(event.getKey(), event.getValue());
        }

        public Map<Object, Object> getCreatedEntries() {
            return new HashMap<>(map);
        }
    }

    @Test
    public void testExpireEntry() {
        RMapCache<String, String> testMap = redisson.getMapCache("map");
        testMap.put("key", "value");
        testMap.expireEntry("key", Duration.ofMillis(0), Duration.ofMillis(20000));
        assertThat(testMap.remainTimeToLive("key")).isBetween(19800L, 20000L);
    }

    @Test
    public void testExpireEntryIfNotSet() {
        RMapCache<String, String> testMap = redisson.getMapCache("map");
        testMap.put("key", "value");
        testMap.expireEntryIfNotSet("key", Duration.ofMillis(0), Duration.ofMillis(20000));
        assertThat(testMap.remainTimeToLive("key")).isBetween(19800L, 20000L);
    }

    @Test
    public void testExpireEntries() {
        RMapCache<String, String> testMap = redisson.getMapCache("map");
        testMap.put("key1", "value");
        testMap.put("key2", "value");
        testMap.expireEntries(new HashSet<>(Arrays.asList("key1", "key2")), Duration.ofMillis(0), Duration.ofMillis(20000));
        assertThat(testMap.remainTimeToLive("key1")).isBetween(19800L, 20000L);
    }

    @Test
    public void testExpireEntriesIfNotSet() {
        RMapCache<String, String> testMap = redisson.getMapCache("map");
        testMap.put("key1", "value");
        testMap.put("key2", "value");
        testMap.expireEntriesIfNotSet(new HashSet<>(Arrays.asList("key1", "key2")), Duration.ofMillis(0), Duration.ofMillis(20000));
        assertThat(testMap.remainTimeToLive("key1")).isBetween(19800L, 20000L);
    }

    @Test
    public void testRemoveEmptyEvictionTask() throws InterruptedException {
        Config config = createConfig();
        config.setMaxCleanUpDelay(2);
        config.setMinCleanUpDelay(1);
        RedissonClient redisson = Redisson.create(config);

        assertThat(redisson.getKeys().count()).isZero();
        RMapCache<Integer, Integer> map = redisson.getMapCache("simple", MapCacheOptions.<Integer, Integer>defaults().removeEmptyEvictionTask());
        map.fastPut(1, 1, 1, TimeUnit.SECONDS);

        EvictionScheduler evictionScheduler = ((Redisson) redisson).getEvictionScheduler();
        Map<?, ?> tasks = Reflect.on(evictionScheduler).get("tasks");
        assertThat(tasks.isEmpty()).isFalse();

        Thread.sleep(6000);

        assertThat(tasks.isEmpty()).isTrue();

        redisson.shutdown();
    }

    @Test
    public void testFastPutExpiration() throws Exception {
        RMapCache<String, Object> mapCache = redisson.getMapCache("testFastPutExpiration");
        mapCache.fastPut("k1", "v1", 1, TimeUnit.SECONDS);
        Thread.sleep(1000);
        mapCache.fastPut("k1", "v2");
        assertThat(mapCache.get("k1")).isEqualTo("v2");
    }

    @Test
    public void testGetAllWithTTLOnly() throws InterruptedException {
        RMapCache<Integer, Integer> cache = redisson.getMapCache("testGetAllWithTTLOnly");
        cache.put(1, 2, 3, TimeUnit.SECONDS);
        cache.put(3, 4, 1, TimeUnit.SECONDS);
        cache.put(5, 6, 1, TimeUnit.SECONDS);

        Map<Integer, Integer> map = cache.getAllWithTTLOnly(new HashSet<>(Arrays.asList(1, 3, 5)));
        assertThat(map).containsOnlyKeys(1, 3, 5);
        assertThat(map).containsValues(2, 4, 6);

        Thread.sleep(1500);

        map = cache.getAllWithTTLOnly(new HashSet<>(Arrays.asList(1, 3, 5)));
        assertThat(map).containsOnlyKeys(1);
        assertThat(map).containsValues(2);
    }

    @Test
    public void testGetWithTTLOnly() throws InterruptedException {
        RMapCache<Integer, Integer> cache = redisson.getMapCache("testUpdateEntryExpiration");
        cache.put(1, 2, 3, TimeUnit.SECONDS);
        Thread.sleep(2000);
        assertThat(cache.getWithTTLOnly(1)).isEqualTo(2);
        Thread.sleep(2000);
        assertThat(cache.getWithTTLOnly(1)).isNull();
    }

    @Test
    public void testEntryEntryIfNotSet() throws InterruptedException {
        RMapCache<Integer, Integer> cache = redisson.getMapCache("testUpdateEntryExpiration");
        cache.put(1, 10);
        cache.put(2, 20);
        cache.put(3, 30, 1, TimeUnit.SECONDS);

        assertThat(cache.expireEntryIfNotSet(2, Duration.ofSeconds(2), null)).isTrue();
        assertThat(cache.expireEntryIfNotSet(3, Duration.ofSeconds(4), null)).isFalse();
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
        RMapCache<Integer, Integer> cache = redisson.getMapCache("testUpdateEntryExpiration");
        cache.put(1, 10);
        cache.put(2, 20);
        cache.put(3, 30, 1, TimeUnit.SECONDS);

        assertThat(cache.expireEntriesIfNotSet(new HashSet<>(Arrays.asList(2, 3)), Duration.ofSeconds(2), null)).isEqualTo(1);
        long ttl2 = cache.remainTimeToLive(2);
        assertThat(ttl2).isBetween(1900L, 2000L);
        Thread.sleep(2200);
        assertThat(cache.expireEntriesIfNotSet(new HashSet<>(Arrays.asList(2, 3)), Duration.ofSeconds(2), null)).isZero();
        assertThat(cache.containsKey(2)).isFalse();
        assertThat(cache.containsKey(3)).isFalse();
    }

    @Test
    public void testEntryEntries() throws InterruptedException {
        RMapCache<Integer, Integer> cache = redisson.getMapCache("testUpdateEntryExpiration");
        cache.put(1, 10, 3, TimeUnit.SECONDS);
        cache.put(2, 20, 3, TimeUnit.SECONDS);
        cache.put(3, 30, 3, TimeUnit.SECONDS);

        Thread.sleep(2000);
        long ttl = cache.remainTimeToLive(1);
        assertThat(ttl).isBetween(900L, 1000L);
        assertThat(cache.expireEntries(new HashSet<>(Arrays.asList(2, 3)), Duration.ofSeconds(2), null)).isEqualTo(2);
        long ttl2 = cache.remainTimeToLive(2);
        assertThat(ttl2).isBetween(1900L, 2000L);
        Thread.sleep(2000);
        assertThat(cache.expireEntries(new HashSet<>(Arrays.asList(2, 3)), Duration.ofSeconds(2), null)).isZero();
    }

    @Test
    public void testUpdateEntryExpiration() throws InterruptedException {
        RMapCache<Integer, Integer> cache = redisson.getMapCache("testUpdateEntryExpiration");
        cache.put(1, 2, 3, TimeUnit.SECONDS);
        Thread.sleep(2000);
        long ttl = cache.remainTimeToLive(1);
        assertThat(ttl).isBetween(900L, 1000L);
        assertThat(cache.updateEntryExpiration(1, 2, TimeUnit.SECONDS, -1, TimeUnit.SECONDS)).isTrue();
        long ttl2 = cache.remainTimeToLive(1);
        assertThat(ttl2).isBetween(1900L, 2000L);
        Thread.sleep(2000);
        assertThat(cache.updateEntryExpiration(1, 2, TimeUnit.SECONDS, -1, TimeUnit.SECONDS)).isFalse();
    }

    @Test
    public void testRemoveListener() {
        RMapCache<Long, String> rMapCache = redisson.getMapCache("test");
        rMapCache.trySetMaxSize(5);
        AtomicBoolean removed = new AtomicBoolean();
        rMapCache.addListener(new EntryRemovedListener() {
            @Override
            public void onRemoved(EntryEvent event) {
                removed.set(true);
            }
        });

        rMapCache.put(1L, "1");
        rMapCache.put(2L, "2");
        rMapCache.put(3L, "3");
        rMapCache.put(4L, "4");
        rMapCache.put(5L, "5");

        rMapCache.put(6L, "6");

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilTrue(removed);
    }

    @Test
    public void testDestroy() {
        RMapCache<String, String> cache = redisson.getMapCache("test");
        AtomicInteger counter = new AtomicInteger();
        cache.addListener(new EntryCreatedListener<>() {
            @Override
            public void onCreated(EntryEvent<Object, Object> event) {
                counter.incrementAndGet();
            }
        });

        cache.fastPut("1", "2");

        Awaitility.await().atMost(Duration.ofSeconds(1))
                            .untilAsserted(() -> assertThat(counter.get()).isEqualTo(1));

        EvictionScheduler evictionScheduler = ((Redisson)redisson).getEvictionScheduler();
        Map<?, ?> map = Reflect.on(evictionScheduler).get("tasks");
        assertThat(map.isEmpty()).isFalse();
        cache.destroy();
        assertThat(map.isEmpty()).isTrue();

        RMapCache<String, String> cache2 = redisson.getMapCache("test");
        cache2.fastPut("3", "4");

        Awaitility.await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(counter.get()).isEqualTo(1));

    }
    
    @Override
    protected <K, V> RMap<K, V> getMap(String name) {
        return redisson.getMapCache(name);        
    }
    
    @Override
    protected <K, V> RMap<K, V> getMap(String name, Codec codec) {
        return redisson.getMapCache(name, codec);
    }
    
    @Override
    protected <K, V> RMap<K, V> getWriterTestMap(String name, Map<K, V> map) {
        MapCacheOptions<K, V> options = MapCacheOptions.<K, V>defaults().writer(createMapWriter(map));
        return redisson.getMapCache("test", options);        
    }
    
    @Override
    protected <K, V> RMap<K, V> getWriteBehindTestMap(String name, Map<K, V> map) {
        MapCacheOptions<K, V> options = MapCacheOptions.<K, V>defaults()
                                    .writer(createMapWriter(map))
                                    .writeMode(WriteMode.WRITE_BEHIND);
        return redisson.getMapCache("test", options);        
    }

    @Override
    protected <K, V> RMap<K, V> getWriteBehindAsyncTestMap(String name, Map<K, V> map) {
        MapCacheOptions<K, V> options = MapCacheOptions.<K, V>defaults()
                .writerAsync(createMapWriterAsync(map))
                .writeMode(WriteMode.WRITE_BEHIND);
        return redisson.getMapCache("test", options);
    }

    @Override
    protected <K, V, M extends RMap<K, V>> M getLoaderTestMap(String name, Map<K, V> map) {
        MapCacheOptions<K, V> options = MapCacheOptions.<K, V>defaults().loader(createMapLoader(map));
        return (M) redisson.getMapCache("test", options);
    }

    @Override
    protected <K, V> RMap<K, V> getLoaderAsyncTestMap(String name, Map<K, V> map) {
        MapCacheOptions<K, V> options = MapCacheOptions.<K, V>defaults().loaderAsync(createMapLoaderAsync(map));
        return redisson.getMapCache("test", options);
    }

    @Test
    public void testSizeInMemory() {
        RMapCache<Integer, Integer> map = redisson.getMapCache("test");
        for (int i = 0; i < 10; i++) {
            map.put(i, i, 5, TimeUnit.SECONDS);
        }
        
        assertThat(map.sizeInMemory()).isGreaterThanOrEqualTo(466);
    }
    
    @Test
    public void testRemainTimeToLive() {
        RMapCache<String, String> map = redisson.getMapCache("test");
        map.put("1", "2", 2, TimeUnit.SECONDS);
        assertThat(map.remainTimeToLive("1")).isBetween(1900L, 2000L);
        map.put("3", "4");
        assertThat(map.remainTimeToLive("3")).isEqualTo(-1);
        assertThat(map.remainTimeToLive("0")).isEqualTo(-2);

        map.put("5", "6", 20, TimeUnit.SECONDS, 10, TimeUnit.SECONDS);
        assertThat(map.remainTimeToLive("1")).isLessThan(9900);
        map.destroy();
    }
    
    @Test
    public void testFastPutTTL() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("getAll");
        map.trySetMaxSize(1);
        map.fastPut(new SimpleKey("1"), new SimpleValue("3"), 5, TimeUnit.SECONDS, 0, TimeUnit.SECONDS);
        Thread.sleep(5000);
        assertThat(map.get(new SimpleKey("1"))).isNull();

        map.fastPut(new SimpleKey("1"), new SimpleValue("4"), 5, TimeUnit.SECONDS, 0, TimeUnit.SECONDS);
        Thread.sleep(10000);
        assertThat(map.get(new SimpleKey("1"))).isNull();
    }
    
    @Test
    public void testWriterPutIfAbsentTTL() {
        Map<String, String> store = new HashMap<>();
        RMapCache<String, String> map = (RMapCache<String, String>) getWriterTestMap("test", store);

        map.putIfAbsent("1", "11", 10, TimeUnit.SECONDS);
        map.putIfAbsent("1", "00", 10, TimeUnit.SECONDS);
        map.putIfAbsent("2", "22", 10, TimeUnit.SECONDS);
        
        Map<String, String> expected = new HashMap<>();
        expected.put("1", "11");
        expected.put("2", "22");
        assertThat(store).isEqualTo(expected);
        map.destroy();
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
        map.destroy();
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
        map.destroy();
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
        map.destroy();
    }

    @Test
    public void testExpirationWithMaxSize() throws InterruptedException {
        Config config = new Config();
        config.useSingleServer().setAddress(redisson.getConfig().useSingleServer().getAddress());
        config.setMaxCleanUpDelay(2);
        config.setMinCleanUpDelay(1);
        RedissonClient redisson = Redisson.create(config);

        RMapCache<String, String> map = redisson.getMapCache("test", StringCodec.INSTANCE);
        assertThat(map.trySetMaxSize(2)).isTrue();

        map.put("1", "1", 3, TimeUnit.SECONDS);
        map.put("2", "2", 0, TimeUnit.SECONDS, 3, TimeUnit.SECONDS);
        map.put("3", "3", 3, TimeUnit.SECONDS);
        map.put("4", "4", 0, TimeUnit.SECONDS, 3, TimeUnit.SECONDS);

        Thread.sleep(8000);

        assertThat(map.size()).isZero();

        assertThat(redisson.getKeys().count()).isEqualTo(2);
        redisson.shutdown();
    }

    @Test
    public void testMaxSizeLFU() {
        RMapCache<String, String> map = redisson.getMapCache("test");
        map.setMaxSize(3, EvictionMode.LFU);
        map.put("1", "2");
        map.put("2", "4");
        map.put("3", "5");

        assertThat(map.get("1")).isEqualTo("2");
        assertThat(map.get("2")).isEqualTo("4");
        assertThat(map.get("3")).isEqualTo("5");

        map.get("1");
        map.get("3");
        map.get("1");
        map.get("3");
        map.get("2");
        map.put("4", "7");

        assertThat(map.get("1")).isEqualTo("2");
        assertThat(map.get("3")).isEqualTo("5");
        assertThat(map.get("2")).isNull();

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
        MapCacheOptions<String, String> options = MapCacheOptions.<String, String>defaults().writer(createMapWriter(store));
        RMapCache<String, String> map = redisson.getMapCache("test", options);
        assertThat(map.trySetMaxSize(maxSize.get())).isTrue();
        assertThat(map.trySetMaxSize(1)).isFalse();

        assertThat(map.fastPutIfAbsent("01", "00")).isTrue();
        assertThat(map.fastPutIfAbsent("02", "00")).isTrue();
        assertThat(map.put("03", "00")).isNull();
        assertThat(map.fastPutIfAbsent("04", "00", 60, TimeUnit.SECONDS)).isTrue();
        assertThat(map.fastPut("1", "11", 60, TimeUnit.SECONDS)).isTrue();
        assertThat(map.size()).isEqualTo(2);
        assertThat(map.fastPut("2", "22", 60, TimeUnit.SECONDS)).isTrue();
        assertThat(map.fastPut("3", "33", 60, TimeUnit.SECONDS)).isTrue();

        assertThat(map.size()).isEqualTo(maxSize.get());

        Map<String, String> expected = new HashMap<>();
        expected.put("2", "22");
        expected.put("3", "33");
        assertThat(store).isEqualTo(expected);
        
        assertThat(map.get("2")).isEqualTo("22");
        assertThat(map.get("0")).isNull();
        assertThat(map.putIfAbsent("2", "3")).isEqualTo("22");
        assertThat(map.putIfAbsent("3", "4", 60, TimeUnit.SECONDS, 60, TimeUnit.SECONDS)).isEqualTo("33");
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
        map.destroy();

    }    
    
    @Test
    public void testCacheValues() {
        final RMapCache<String, String> map = redisson.getMapCache("testRMapCacheValues");
        map.put("1234", "5678", 0, TimeUnit.MINUTES, 60, TimeUnit.MINUTES);
        assertThat(map.values()).containsOnly("5678");
        map.destroy();
    }    

    @Test
    public void testGetAllTTL() throws InterruptedException {
        RMapCache<Integer, Integer> map = redisson.getMapCache("getAll");
        map.put(1, 100);
        map.put(2, 200, 1, TimeUnit.SECONDS);
        map.put(3, 300, 1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS);
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
        RMapCache<String, Integer> map = redisson.getMapCache("getAllStrings");
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
        RMapCache<String, String> cache = redisson.getMapCache("simple");
        cache.put("0", "8");
        cache.put("1", "6", 1, TimeUnit.SECONDS);
        cache.put("2", "4", 3, TimeUnit.SECONDS);
        cache.put("3", "2", 4, TimeUnit.SECONDS);
        cache.put("4", "4", 1, TimeUnit.SECONDS);

        Thread.sleep(1000);

        assertThat(cache.keySet()).containsOnly("0", "2", "3");
        cache.destroy();
    }

    @Test
    public void testExpire() throws InterruptedException {
        RMapCache<String, String> cache = redisson.getMapCache("simple");
        cache.put("0", "8", 1, TimeUnit.SECONDS);

        cache.expire(Duration.ofMillis(100));

        Thread.sleep(500);

        Assertions.assertEquals(0, cache.size());
        cache.destroy();
    }

    @Test
    public void testExpireAt() throws InterruptedException {
        RMapCache<String, String> cache = redisson.getMapCache("simple");
        cache.put("0", "8", 1, TimeUnit.SECONDS);

        cache.expireAt(System.currentTimeMillis() + 100);

        Thread.sleep(500);

        Assertions.assertEquals(0, cache.size());
        cache.destroy();
    }
    
    @Test
    public void testClearExpire() throws InterruptedException {
        RMapCache<String, String> cache = redisson.getMapCache("simple");
        cache.put("0", "8", 1, TimeUnit.SECONDS);

        cache.expireAt(System.currentTimeMillis() + 100);

        cache.clearExpire();

        Thread.sleep(500);

        Assertions.assertEquals(1, cache.size());
        cache.destroy();
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
        map.destroy();
    }
    
    @Test
    public void testKeySet() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple03");
        map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS);
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
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple05");
        map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS);
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
        RMapCache<String, String> map = redisson.getMapCache("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200", 1, TimeUnit.MINUTES);
        map.put("30", "300");

        assertThat(map.keySet("?0")).containsExactly("10", "20", "30");
        assertThat(map.keySet("1")).isEmpty();
        assertThat(map.keySet("10")).containsExactly("10");
        map.destroy();
    }

    @Test
    public void testValuesByPatternTTL() {
        RMapCache<String, String> map = redisson.getMapCache("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200", 1, TimeUnit.MINUTES);
        map.put("30", "300");

        assertThat(map.values("?0")).containsExactly("100", "200", "300");
        assertThat(map.values("1")).isEmpty();
        assertThat(map.values("10")).containsExactly("100");
        map.destroy();
    }

    @Test
    public void testEntrySetByPatternTTL() {
        RMapCache<String, String> map = redisson.getMapCache("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200", 1, TimeUnit.MINUTES);
        map.put("30", "300");

        assertThat(map.entrySet("?0")).containsExactly(new AbstractMap.SimpleEntry("10", "100"), new AbstractMap.SimpleEntry("20", "200"), new AbstractMap.SimpleEntry("30", "300"));
        assertThat(map.entrySet("1")).isEmpty();
        assertThat(map.entrySet("10")).containsExactly(new AbstractMap.SimpleEntry("10", "100"));
        map.destroy();
    }

    
    @Test
    public void testContainsValueTTL() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple01");
        Assertions.assertFalse(map.containsValue(new SimpleValue("34")));
        map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS);

        Assertions.assertTrue(map.containsValue(new SimpleValue("44")));
        Assertions.assertFalse(map.containsValue(new SimpleValue("34")));

        Thread.sleep(1000);

        Assertions.assertFalse(map.containsValue(new SimpleValue("44")));
        map.destroy();
    }

    @Test
    public void testContainsKeyTTL() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple30");
        map.put(new SimpleKey("33"), new SimpleValue("44"), 1, TimeUnit.SECONDS);

        Assertions.assertTrue(map.containsKey(new SimpleKey("33")));
        Assertions.assertFalse(map.containsKey(new SimpleKey("34")));

        Thread.sleep(1000);

        Assertions.assertFalse(map.containsKey(new SimpleKey("33")));
        map.destroy();
    }

    @Test
    public void testRemoveValueTTL() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"), 1, TimeUnit.SECONDS);

        boolean res = map.remove(new SimpleKey("1"), new SimpleValue("2"));
        Assertions.assertTrue(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assertions.assertNull(val1);

        Assertions.assertEquals(0, map.size());
        
        map.put(new SimpleKey("3"), new SimpleValue("4"), 1, TimeUnit.SECONDS);

        Thread.sleep(1000);
        
        assertThat(map.remove(new SimpleKey("3"), new SimpleValue("4"))).isFalse();

        assertThat(map.get(new SimpleKey("3"))).isNull();
        map.destroy();
    }
    
    @Test
    public void testRemoveValueFail() {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
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
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("43"), new SimpleValue("31"));
        Assertions.assertFalse(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assertions.assertEquals("2", val1.getValue());
        map.destroy();
    }

    @Test
    public void testReplaceOldValueSuccess() {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
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
    public void testExpirationInCluster() {
        testInCluster(client -> {
            Config config = client.getConfig();
            config.useClusterServers()
                    .setNameMapper(new NameMapper() {
                        @Override
                        public String map(String name) {
                            return name + "-namemapper-";
                        }

                        @Override
                        public String unmap(String name) {
                            return name.replace("-namemapper-", "");
                        }
                    });
            RedissonClient redisson = Redisson.create(config);

            AtomicBoolean executed = new AtomicBoolean();
            RMapCache<String, String> map = redisson.getMapCache("simple");
            map.addListener(new EntryExpiredListener() {
                @Override
                public void onExpired(EntryEvent event) {
                    executed.set(true);
                }
            });
            map.put("1", "2", 1, TimeUnit.SECONDS);

            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(executed.get()).isTrue());

            redisson.shutdown();
        });
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
        map.destroy();
    }

    @Test
    public void testReplaceValueTTLIdleUpdate() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = null;
        SimpleValue val1;
        try {
            map = redisson.getMapCache("simple");
            map.put(new SimpleKey("1"), new SimpleValue("2"), 2, TimeUnit.SECONDS, 1, TimeUnit.SECONDS);

            Thread.sleep(750);

            // update value, would like idle timeout to be refreshed
            SimpleValue res = map.replace(new SimpleKey("1"), new SimpleValue("3"));
            assertThat(res).isNotNull();

            Thread.sleep(750);

            // if idle timeout has been updated val1 will be not be null, else it will be null
            val1 = map.get(new SimpleKey("1"));
            assertThat(val1).isNotNull();

            Thread.sleep(750);

            // val1 will have expired due to TTL
            val1 = map.get(new SimpleKey("1"));
            assertThat(val1).isNull();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            map.remove(new SimpleKey("1"));
        }
    }

    @Test
    public void testScheduler() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple3");
        Assertions.assertNull(map.get(new SimpleKey("33")));

        map.put(new SimpleKey("33"), new SimpleValue("44"), 5, TimeUnit.SECONDS);
        map.put(new SimpleKey("10"), new SimpleValue("32"), 5, TimeUnit.SECONDS, 2, TimeUnit.SECONDS);
        map.put(new SimpleKey("01"), new SimpleValue("92"), 0, null, 2, TimeUnit.SECONDS);

        Assertions.assertEquals(3, map.size());

        Thread.sleep(11000);

        Assertions.assertEquals(0, map.size());
        map.destroy();

    }

    @Test
    public void testPutGetTTL() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple04");
        Assertions.assertNull(map.get(new SimpleKey("33")));

        map.put(new SimpleKey("33"), new SimpleValue("44"), 2, TimeUnit.SECONDS);

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
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple06");
        Assertions.assertNull(map.get(new SimpleKey("33")));
        Assertions.assertNull(map.get(new SimpleKey("55")));

        Map<SimpleKey, SimpleValue> entries = new HashMap<>();
        entries.put(new SimpleKey("33"), new SimpleValue("44"));
        entries.put(new SimpleKey("55"), new SimpleValue("66"));
        map.putAll(entries, 2, TimeUnit.SECONDS);

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
    public void testPutAllWithListener() throws InterruptedException {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        SimpleEntryCreatedListener listener = new SimpleEntryCreatedListener();
        map.addListener(listener);

        Assertions.assertNull(map.get(new SimpleKey("33")));
        Assertions.assertNull(map.get(new SimpleKey("55")));

        Map<SimpleKey, SimpleValue> entries = new HashMap<>();
        entries.put(new SimpleKey("33"), new SimpleValue("44"));
        entries.put(new SimpleKey("55"), new SimpleValue("66"));
        map.putAll(entries);

        SimpleValue val1 = map.get(new SimpleKey("33"));
        Assertions.assertEquals("44", val1.getValue());
        SimpleValue val2 = map.get(new SimpleKey("55"));
        Assertions.assertEquals("66", val2.getValue());
        Assertions.assertEquals(entries, listener.getCreatedEntries());

        map.destroy();
    }

    @Test
    public void testPutIfAbsentTTL() throws Exception {
        RMapCache<SimpleKey, SimpleValue> map = redisson.getMapCache("simple");
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.put(key, value);
        Assertions.assertEquals(value, map.putIfAbsent(key, new SimpleValue("3"), 1, TimeUnit.SECONDS));
        Assertions.assertEquals(value, map.get(key));

        map.putIfAbsent(new SimpleKey("4"), new SimpleValue("4"), 1, TimeUnit.SECONDS);
        Assertions.assertEquals(new SimpleValue("4"), map.get(new SimpleKey("4")));
        
        Thread.sleep(1000);

        Assertions.assertNull(map.get(new SimpleKey("4")));
        
        // this should be passed
        map.putIfAbsent(new SimpleKey("4"), new SimpleValue("4"), 1, TimeUnit.SECONDS);
        Assertions.assertEquals(new SimpleValue("4"), map.get(new SimpleKey("4")));
        

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        Assertions.assertNull(map.putIfAbsent(key1, value1, 2, TimeUnit.SECONDS));
        Assertions.assertEquals(value1, map.get(key1));
        map.destroy();
    }

    @Test
    public void testFastPutIfAbsentTTL() throws Exception {
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
        map.destroy();

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
        map.destroy();
        
        RMapCache<Integer, Integer> map2 = redisson.getMapCache("simple3", new CompositeCodec(redisson.getConfig().getCodec(), IntegerCodec.INSTANCE));
        checkCreatedListener(map2, 5, 10, () -> map2.addAndGet(5, 10));
        map2.destroy();
    }

    private void checkCreatedListener(RMapCache<Integer, Integer> map, Integer key, Integer value, Runnable runnable) {
        AtomicBoolean ref = new AtomicBoolean();
        int createListener1 = map.addListener(new EntryCreatedListener<Integer, Integer>() {

            @Override
            public void onCreated(EntryEvent<Integer, Integer> event) {
                try {
                    assertThat(event.getKey()).isEqualTo(key);
                    assertThat(event.getValue()).isEqualTo(value);
                    
                    if (!ref.compareAndSet(false, true)) {
                        Assertions.fail();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
        });
        runnable.run();

        await().atMost(Duration.ofSeconds(1)).untilTrue(ref);
        map.removeListener(createListener1);
        map.destroy();
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
        map.destroy();
        
        RMapCache<Integer, Integer> map2 = redisson.getMapCache("simple2", new CompositeCodec(redisson.getConfig().getCodec(), IntegerCodec.INSTANCE));
        map2.put(5, 1);
        checkUpdatedListener(map2, 5, 4, 1, () -> map2.addAndGet(5, 3));
        map2.destroy();

    }
    
    @Test
    public void testExpiredListener() {
        RMapCache<Integer, Integer> map = redisson.getMapCache("simple");

        checkExpiredListener(map, 10, 2, () -> map.put(10, 2, 2, TimeUnit.SECONDS));
        checkExpiredListener(map, 13, 2, () -> map.fastPut(13, 2, 2, TimeUnit.SECONDS));
        checkExpiredListener(map, 14, 2, () -> map.putIfAbsent(14, 2, 2, TimeUnit.SECONDS));
        checkExpiredListener(map, 15, 2, () -> map.fastPutIfAbsent(15, 2, 2, TimeUnit.SECONDS));
        map.destroy();
    }

    private void checkExpiredListener(RMapCache<Integer, Integer> map, Integer key, Integer value, Runnable runnable) {
        AtomicBoolean ref = new AtomicBoolean();
        int createListener1 = map.addListener(new EntryExpiredListener<Integer, Integer>() {

            @Override
            public void onExpired(EntryEvent<Integer, Integer> event) {
                assertThat(event.getKey()).isEqualTo(key);
                assertThat(event.getValue()).isEqualTo(value);
                
                if (!ref.compareAndSet(false, true)) {
                    Assertions.fail();
                }
            }
            
        });
        runnable.run();

        await().atMost(Duration.ofMinutes(1)).untilTrue(ref);
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
                    Assertions.fail();
                }
            }
            
        });
        runnable.run();

        await().atMost(Duration.ofSeconds(1)).untilTrue(ref);
        map.removeListener(createListener1);
    }

    @Test
    public void testEntryUpdate() throws InterruptedException {
        RMapCache<Integer, Integer> map = redisson.getMapCache("simple");
        map.put(1, 1, 1, TimeUnit.SECONDS);
        assertThat(map.get(1)).isEqualTo(1);

        Thread.sleep(1000);

        assertThat(map.put(1, 1, 0, TimeUnit.SECONDS)).isNull();
        assertThat(map.get(1)).isEqualTo(1);
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
        map.destroy();
    }
    
    private void checkRemovedListener(RMapCache<Integer, Integer> map, Integer key, Integer value, Runnable runnable) {
        AtomicBoolean ref = new AtomicBoolean();
        int createListener1 = map.addListener(new EntryRemovedListener<Integer, Integer>() {

            @Override
            public void onRemoved(EntryEvent<Integer, Integer> event) {
                assertThat(event.getKey()).isEqualTo(key);
                assertThat(event.getValue()).isEqualTo(value);
                
                if (!ref.compareAndSet(false, true)) {
                    Assertions.fail();
                }
            }
            
        });
        runnable.run();

        await().atMost(Duration.ofSeconds(1)).untilTrue(ref);
        map.removeListener(createListener1);
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
        map.destroy();
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
        map.destroy();
    }

    @Test
    public void testExpireOverwrite() throws InterruptedException, ExecutionException {
        RMapCache<String, Integer> map = redisson.getMapCache("simple");
        map.put("123", 3, 1, TimeUnit.SECONDS);

        Thread.sleep(800);

        map.put("123", 3, 1, TimeUnit.SECONDS);

        Thread.sleep(800);
        Assertions.assertEquals(3, (int)map.get("123"));

        Thread.sleep(200);

        Assertions.assertFalse(map.containsKey("123"));
        map.destroy();
    }

    @Test
    public void testRMapCacheValues() {
        final RMapCache<String, String> map = redisson.getMapCache("testRMapCacheValues");
        map.put("1234", "5678", 1, TimeUnit.MINUTES, 60, TimeUnit.MINUTES);
        assertThat(map.values()).containsOnly("5678");
        map.destroy();
    }

    @Test
    public void testReadAllEntrySet() throws InterruptedException {
        RMapCache<Integer, String> map = redisson.getMapCache("simple12");
        map.put(1, "12");
        map.put(2, "33", 10, TimeUnit.MINUTES, 60, TimeUnit.MINUTES);
        map.put(3, "43");
        
        assertThat(map.readAllEntrySet()).isEqualTo(map.entrySet());
        map.destroy();
    }

    @Test
    public void testReadAllValuesTTL() {
        final RMapCache<String, String> map = redisson.getMapCache("testRMapCacheAllValues");
        map.put("1234", "5678", 1, TimeUnit.MINUTES, 60, TimeUnit.MINUTES);
        assertThat(map.readAllValues()).containsOnly("5678");
        map.destroy();
    }

    @Test
    public void testReadAllEntrySetWithPattern() throws ExecutionException, InterruptedException {
        RMapCache<String, String> map = redisson.getMapCache("simple12", StringCodec.INSTANCE);
        map.put("10", "12");
        map.put("12", "33", 5, TimeUnit.SECONDS, 60, TimeUnit.SECONDS);
        map.put("21", "43");
        assertThat((map.readAllEntrySetAsync("1?")).get().size()).isEqualTo(2);

        Thread.sleep(6000);

        assertThat((map.readAllEntrySetAsync("1?")).get().size()).isEqualTo(1);
    }

    @Test
    public void testReadAllKeySetWithPattern() throws ExecutionException, InterruptedException {
        RMapCache<String, String> map = redisson.getMapCache("simple12", StringCodec.INSTANCE);
        map.put("10", "12");
        map.put("12", "33", 5, TimeUnit.SECONDS, 60, TimeUnit.SECONDS);
        map.put("21", "43");
        assertThat((map.readAllKeySetAsync("1?")).get()).containsOnly("10", "12");

        Thread.sleep(6000);

        assertThat((map.readAllKeySetAsync("1?")).get()).containsOnly("10");
    }

    @Test
    public void testReadAllValuesWithPattern() throws ExecutionException, InterruptedException {
        RMapCache<String, String> map = redisson.getMapCache("simple12", StringCodec.INSTANCE);
        map.put("10", "12");
        map.put("12", "33", 5, TimeUnit.SECONDS, 60, TimeUnit.SECONDS);
        map.put("21", "43");
        assertThat((map.readAllValuesAsync("1?")).get()).containsOnly("12", "33");

        Thread.sleep(6000);

        assertThat((map.readAllValuesAsync("1?")).get()).containsOnly("12");
    }

    @Test
    public void testAddAndGetTTL() {
        RMapCache<String, Object> mapCache = redisson.getMapCache("test_put_if_absent", LongCodec.INSTANCE);
        assertThat(mapCache.putIfAbsent("4", 0L, 10000L, TimeUnit.SECONDS)).isNull();
        assertThat(mapCache.addAndGet("4", 1L)).isEqualTo(1L);
        assertThat(mapCache.putIfAbsent("4", 0L)).isEqualTo(1L);
        assertThat(mapCache.addAndGet("key", Long.MAX_VALUE-10)).isEqualTo(Long.MAX_VALUE-10);
        assertThat(mapCache.addAndGet("key", 10L)).isEqualTo(Long.MAX_VALUE);
        Assertions.assertEquals(1L, mapCache.get("4"));
        mapCache.destroy();
        mapCache = redisson.getMapCache("test_put_if_absent_1", LongCodec.INSTANCE);
        mapCache.putIfAbsent("4", 0L);
        mapCache.addAndGet("4", 1L);
        mapCache.putIfAbsent("4", 0L);
        Assertions.assertEquals(1L, mapCache.get("4"));
        RMap map = redisson.getMap("test_put_if_absent_2", LongCodec.INSTANCE);
        map.putIfAbsent("4", 0L);
        map.addAndGet("4", 1L);
        map.putIfAbsent("4", 0L);
        Assertions.assertEquals(1L, map.get("4"));
        RMapCache<String, Object> mapCache1 = redisson.getMapCache("test_put_if_absent_3", DoubleCodec.INSTANCE);
        mapCache1.putIfAbsent("4", 1.23, 10000L, TimeUnit.SECONDS);
        mapCache1.addAndGet("4", 1D);
        Assertions.assertEquals(2.23, mapCache1.get("4"));
        mapCache.destroy();
        mapCache1.destroy();
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
        map.destroy();

    }

    @Test
    public void testComputeIfAbsentWithTTL() throws Exception{
        RMapCache<String, String> map = redisson.getMapCache("testMap");
        map.delete();
        Duration duration = Duration.ofSeconds(1);
        String value = map.computeIfAbsent("key1",duration, (t1) -> "value1");
        assertThat("value1".equals(value)).isTrue();
        value = map.computeIfAbsent("key1", duration, (t1) -> "value2");
        assertThat("value2".equals(value)).isFalse();
        Thread.sleep(1100);
        value = map.computeIfAbsent("key1", duration, (t1) -> "value3");
        assertThat("value3".equals(value)).isTrue();
        map.destroy();

    }

    @Test
    public void testNameMapper() throws InterruptedException, ExecutionException {
        Config config = new Config();
        config.useSingleServer()
                .setNameMapper(new NameMapper() {
                    @Override
                    public String map(String name) {
                        return name + ":suffix:";
                    }

                    @Override
                    public String unmap(String name) {
                        return name.replace(":suffix:", "");
                    }
                })
                .setConnectionMinimumIdleSize(3)
                .setConnectionPoolSize(3)
                .setAddress(redisson.getConfig().useSingleServer().getAddress());

        RedissonClient redisson = Redisson.create(config);

        AtomicBoolean executed = new AtomicBoolean();
        RMapCache<String, String> map = redisson.getMapCache("test");
        map.addListener(new EntryExpiredListener() {
            @Override
            public void onExpired(EntryEvent event) {
                executed.set(true);
            }
        });
        map.put("1", "2", 1, TimeUnit.SECONDS);

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(executed.get()).isTrue());

        RedisClientConfig destinationCfg = new RedisClientConfig();
        destinationCfg.setAddress(redisson.getConfig().useSingleServer().getAddress());
        RedisClient client = RedisClient.create(destinationCfg);
        RedisConnection destinationConnection = client.connect();
        List<String> channels = destinationConnection.sync(RedisCommands.PUBSUB_CHANNELS);
        assertThat(channels).contains("redisson_map_cache_expired:{test:suffix:}");
        client.shutdown();

        redisson.shutdown();
    }
}

