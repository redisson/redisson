package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.LocalCachedMapOptions.EvictionPolicy;
import org.redisson.api.LocalCachedMapOptions.ReconnectionStrategy;
import org.redisson.api.LocalCachedMapOptions.SyncStrategy;
import org.redisson.api.MapOptions.WriteMode;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RMap;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;

public class RedissonLocalCachedMapTest extends BaseMapTest {

    public abstract class UpdateTest {

        RLocalCachedMap<String, Integer> map1;
        RLocalCachedMap<String, Integer> map2;
        Map<String, Integer> cache1;
        Map<String, Integer> cache2;
        
        public void execute() throws InterruptedException {
            LocalCachedMapOptions<String, Integer> options = LocalCachedMapOptions.<String, Integer>defaults().evictionPolicy(EvictionPolicy.LFU)
                    .syncStrategy(SyncStrategy.UPDATE)
                    .reconnectionStrategy(ReconnectionStrategy.CLEAR)
                    .cacheSize(5);
            map1 = redisson.getLocalCachedMap("test2", options);
            cache1 = map1.getCachedMap();
            
            map2 = redisson.getLocalCachedMap("test2", options);
            cache2 = map2.getCachedMap();
            
            map1.put("1", 1);
            map1.put("2", 2);
            
            Thread.sleep(50);
            
            assertThat(cache1.size()).isEqualTo(2);
            assertThat(cache2.size()).isEqualTo(2);
            
            test();
        }
        
        public abstract void test() throws InterruptedException;
        
    }
    
    public abstract class InvalidationTest {

        RLocalCachedMap<String, Integer> map1;
        RLocalCachedMap<String, Integer> map2;
        Map<String, Integer> cache1;
        Map<String, Integer> cache2;
        
        public void execute() throws InterruptedException {
            LocalCachedMapOptions<String, Integer> options = LocalCachedMapOptions.<String, Integer>defaults().evictionPolicy(EvictionPolicy.LFU).cacheSize(5);
            map1 = redisson.getLocalCachedMap("test", options);
            cache1 = map1.getCachedMap();
            
            map2 = redisson.getLocalCachedMap("test", options);
            cache2 = map2.getCachedMap();
            
            map1.put("1", 1);
            map1.put("2", 2);
            
            assertThat(map2.get("1")).isEqualTo(1);
            assertThat(map2.get("2")).isEqualTo(2);
            
            assertThat(cache1.size()).isEqualTo(2);
            assertThat(cache2.size()).isEqualTo(2);
            
            test();
        }
        
        public abstract void test() throws InterruptedException;
        
    }

    @Override
    protected <K, V> RMap<K, V> getMap(String name) {
        return redisson.getLocalCachedMap(name, LocalCachedMapOptions.<K, V>defaults());
    }
    
    @Override
    protected <K, V> RMap<K, V> getMap(String name, Codec codec) {
        return redisson.getLocalCachedMap(name, codec, LocalCachedMapOptions.<K, V>defaults());
    }
    
    @Override
    protected <K, V> RMap<K, V> getWriterTestMap(String name, Map<K, V> map) {
        LocalCachedMapOptions<K, V> options = LocalCachedMapOptions.<K, V>defaults().writer(createMapWriter(map));
        return redisson.getLocalCachedMap(name, options);        
    }
    
    @Override
    protected <K, V> RMap<K, V> getWriteBehindTestMap(String name, Map<K, V> map) {
        LocalCachedMapOptions<K, V> options = LocalCachedMapOptions.<K, V>defaults()
                                    .writer(createMapWriter(map))
                                    .writeMode(WriteMode.WRITE_BEHIND);
        return redisson.getLocalCachedMap("test", options);        
    }
        
    @Override
    protected <K, V> RMap<K, V> getLoaderTestMap(String name, Map<K, V> map) {
        LocalCachedMapOptions<K, V> options = LocalCachedMapOptions.<K, V>defaults().loader(createMapLoader(map));
        return redisson.getLocalCachedMap(name, options);        
    }
        
    @Test
    public void testBigPutAll() throws InterruptedException {
        RLocalCachedMap<Object, Object> m = redisson.getLocalCachedMap("testValuesWithNearCache2",
                LocalCachedMapOptions.defaults().evictionPolicy(EvictionPolicy.LFU).syncStrategy(SyncStrategy.INVALIDATE));
        
        Map<Object, Object> map = new HashMap<>();
        for (int k = 0; k < 10000; k++) {
            map.put("" + k, "" + k);
        }
        m.putAll(map);
        
        assertThat(m.size()).isEqualTo(10000);
    }
    
    
    @Test
    public void testReadValuesAndEntries() {
        RLocalCachedMap<Object, Object> m = redisson.getLocalCachedMap("testValuesWithNearCache2",
                LocalCachedMapOptions.defaults());
        m.clear();
        m.put("a", 1);
        m.put("b", 2);
        m.put("c", 3);

        Set<Integer> expectedValuesSet = new HashSet<>();
        expectedValuesSet.add(1);
        expectedValuesSet.add(2);
        expectedValuesSet.add(3);
        Set<Object> actualValuesSet = new HashSet<>(m.readAllValues());
        Assert.assertEquals(expectedValuesSet, actualValuesSet);
        Map<String, Integer> expectedMap = new HashMap<>();
        expectedMap.put("a", 1);
        expectedMap.put("b", 2);
        expectedMap.put("c", 3);
        Assert.assertEquals(expectedMap.entrySet(), m.readAllEntrySet());
    }
    
    @Test
    public void testClearEmpty() {
        RLocalCachedMap<Object, Object> localCachedMap = redisson.getLocalCachedMap("udi-test",
                        LocalCachedMapOptions.defaults());

        localCachedMap.clear();
    }
    
    @Test
    public void testDelete() {
        RLocalCachedMap<String, String> localCachedMap = redisson.getLocalCachedMap("udi-test",
                        LocalCachedMapOptions.defaults());

        assertThat(localCachedMap.delete()).isFalse();
        localCachedMap.put("1", "2");
        assertThat(localCachedMap.delete()).isTrue();
    }

    @Test
    public void testInvalidationOnClear() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.clear();

                Thread.sleep(50);
                assertThat(cache1.size()).isZero();
                assertThat(cache2.size()).isZero();
                
                assertThat(map1.size()).isZero();
                assertThat(map2.size()).isZero();
            }
        }.execute();
    }

    @Test
    public void testInvalidationOnUpdateNonBinaryCodec() throws InterruptedException {
        LocalCachedMapOptions<String, String> options = LocalCachedMapOptions.<String, String>defaults().evictionPolicy(EvictionPolicy.LFU).cacheSize(5);
        RLocalCachedMap<String, String> map1 = redisson.getLocalCachedMap("test", new StringCodec(), options);
        Map<String, String> cache1 = map1.getCachedMap();
        
        RLocalCachedMap<String, String> map2 = redisson.getLocalCachedMap("test", new StringCodec(), options);
        Map<String, String> cache2 = map2.getCachedMap();
        
        map1.put("1", "1");
        map1.put("2", "2");
        
        assertThat(map2.get("1")).isEqualTo("1");
        assertThat(map2.get("2")).isEqualTo("2");
        
        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);

        map1.put("1", "3");
        map2.put("2", "4");
        Thread.sleep(50);
        
        assertThat(cache1.size()).isEqualTo(1);
        assertThat(cache2.size()).isEqualTo(1);
    }
    
    @Test
    public void testSyncOnUpdate() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.put("1", 3);
                map2.put("2", 4);
                Thread.sleep(50);
                
                assertThat(cache1.size()).isEqualTo(1);
                assertThat(cache2.size()).isEqualTo(1);
            }
        }.execute();
        
        new UpdateTest() {
            @Override
            public void test() throws InterruptedException {
                map1.put("1", 3);
                map2.put("2", 4);
                Thread.sleep(50);
                
                assertThat(cache1.size()).isEqualTo(2);
                assertThat(cache2.size()).isEqualTo(2);
            }
        }.execute();
    }
    
    @Test
    public void testNoInvalidationOnUpdate() throws InterruptedException {
        LocalCachedMapOptions<String, Integer> options = LocalCachedMapOptions.<String, Integer>defaults()
                .evictionPolicy(EvictionPolicy.LFU)
                .cacheSize(5)
                .syncStrategy(SyncStrategy.NONE);
        
        RLocalCachedMap<String, Integer> map1 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache1 = map1.getCachedMap();
        
        RLocalCachedMap<String, Integer> map2 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache2 = map2.getCachedMap();
        
        map1.put("1", 1);
        map1.put("2", 2);
        
        assertThat(map2.get("1")).isEqualTo(1);
        assertThat(map2.get("2")).isEqualTo(2);
        
        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);

        map1.put("1", 3);
        map2.put("2", 4);
        Thread.sleep(50);

        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);
    }

    @Test
    public void testLocalCacheState() throws InterruptedException {
        LocalCachedMapOptions<String, String> options = LocalCachedMapOptions.<String, String>defaults()
                .evictionPolicy(EvictionPolicy.LFU)
                .cacheSize(5)
                .syncStrategy(SyncStrategy.INVALIDATE);
        
        RLocalCachedMap<String, String> map = redisson.getLocalCachedMap("test", options);
        map.put("1", "11");
        map.put("2", "22");
        assertThat(map.cachedKeySet()).containsExactlyInAnyOrder("1", "2");
        assertThat(map.cachedValues()).containsExactlyInAnyOrder("11", "22");
        assertThat(map.getCachedMap().keySet()).containsExactlyInAnyOrder("1", "2");
        assertThat(map.getCachedMap().values()).containsExactlyInAnyOrder("11", "22");
    }

    
    @Test
    public void testLocalCacheClear() throws InterruptedException {
        LocalCachedMapOptions<String, Integer> options = LocalCachedMapOptions.<String, Integer>defaults()
                .evictionPolicy(EvictionPolicy.LFU)
                .cacheSize(5)
                .syncStrategy(SyncStrategy.INVALIDATE);
        
        RLocalCachedMap<String, Integer> map1 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache1 = map1.getCachedMap();
        
        RLocalCachedMap<String, Integer> map2 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache2 = map2.getCachedMap();
        
        map1.put("1", 1);
        map1.put("2", 2);
        
        assertThat(map2.get("1")).isEqualTo(1);
        assertThat(map2.get("2")).isEqualTo(2);
        
        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);

        map1.clearLocalCache();

        assertThat(cache1.size()).isZero();
        assertThat(cache2.size()).isZero();
    }

    
    @Test
    public void testNoInvalidationOnRemove() throws InterruptedException {
        LocalCachedMapOptions<String, Integer> options = LocalCachedMapOptions.<String, Integer>defaults()
                .evictionPolicy(EvictionPolicy.LFU)
                .cacheSize(5)
                .syncStrategy(SyncStrategy.NONE);
        
        RLocalCachedMap<String, Integer> map1 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache1 = map1.getCachedMap();
        
        RLocalCachedMap<String, Integer> map2 = redisson.getLocalCachedMap("test", options);
        Map<String, Integer> cache2 = map2.getCachedMap();
        
        map1.put("1", 1);
        map1.put("2", 2);
        
        assertThat(map2.get("1")).isEqualTo(1);
        assertThat(map2.get("2")).isEqualTo(2);
        
        assertThat(cache1.size()).isEqualTo(2);
        assertThat(cache2.size()).isEqualTo(2);

        map1.remove("1");
        map2.remove("2");
        Thread.sleep(50);
        
        assertThat(cache1.size()).isEqualTo(1);
        assertThat(cache2.size()).isEqualTo(1);
    }
    
    @Test
    public void testSyncOnRemove() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.remove("1");
                map2.remove("2");
                Thread.sleep(50);
                
                assertThat(cache1.size()).isEqualTo(0);
                assertThat(cache2.size()).isEqualTo(0);
            }
        }.execute();
        
        new UpdateTest() {
            @Override
            public void test() throws InterruptedException {
                map1.remove("1");
                map2.remove("2");
                Thread.sleep(50);
                
                assertThat(cache1.size()).isEqualTo(0);
                assertThat(cache2.size()).isEqualTo(0);
            }
        }.execute();
    }
    
    @Test
    public void testLFU() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.<String, Integer>defaults().evictionPolicy(EvictionPolicy.LFU).cacheSize(5));
        Map<String, Integer> cache = map.getCachedMap();

        map.put("12", 1);
        map.put("14", 2);
        map.put("15", 3);
        map.put("16", 4);
        map.put("17", 5);
        map.put("18", 6);
        
        assertThat(cache.size()).isEqualTo(5);
        assertThat(map.size()).isEqualTo(6);
        assertThat(map.keySet()).containsOnly("12", "14", "15", "16", "17", "18");
        assertThat(map.values()).containsOnly(1, 2, 3, 4, 5, 6);
    }
    
    @Test
    public void testLRU() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.<String, Integer>defaults().evictionPolicy(EvictionPolicy.LRU).cacheSize(5));
        Map<String, Integer> cache = map.getCachedMap();

        map.put("12", 1);
        map.put("14", 2);
        map.put("15", 3);
        map.put("16", 4);
        map.put("17", 5);
        map.put("18", 6);
        
        assertThat(cache.size()).isEqualTo(5);
        assertThat(map.size()).isEqualTo(6);
        assertThat(map.keySet()).containsOnly("12", "14", "15", "16", "17", "18");
        assertThat(map.values()).containsOnly(1, 2, 3, 4, 5, 6);
    }

    @Test
    public void testSizeCache() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        Map<String, Integer> cache = map.getCachedMap();

        map.put("12", 1);
        map.put("14", 2);
        map.put("15", 3);
        
        assertThat(cache.size()).isEqualTo(3);
        assertThat(map.size()).isEqualTo(3);
    }

    @Test
    public void testInvalidationOnPut() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.put("1", 10);
                map1.put("2", 20);

                Thread.sleep(50);
                
                assertThat(cache1).hasSize(2);
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }
    
    @Test
    public void testPutGetCache() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        Map<String, Integer> cache = map.getCachedMap();

        map.put("12", 1);
        map.put("14", 2);
        map.put("15", 3);

        assertThat(cache).containsValues(1, 2, 3);
        assertThat(map.get("12")).isEqualTo(1);
        assertThat(map.get("14")).isEqualTo(2);
        assertThat(map.get("15")).isEqualTo(3);
        
        RLocalCachedMap<String, Integer> map1 = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        
        assertThat(map1.get("12")).isEqualTo(1);
        assertThat(map1.get("14")).isEqualTo(2);
        assertThat(map1.get("15")).isEqualTo(3);
    }
    
    @Test
    public void testGetAllCache() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("getAll", LocalCachedMapOptions.defaults());
        Map<String, Integer> cache = map.getCachedMap();
        map.put("1", 100);
        map.put("2", 200);
        map.put("3", 300);
        map.put("4", 400);

        assertThat(cache.size()).isEqualTo(4);
        Map<String, Integer> filtered = map.getAll(new HashSet<String>(Arrays.asList("2", "3", "5")));

        Map<String, Integer> expectedMap = new HashMap<String, Integer>();
        expectedMap.put("2", 200);
        expectedMap.put("3", 300);
        assertThat(filtered).isEqualTo(expectedMap);
        
        RMap<String, Integer> map1 = redisson.getLocalCachedMap("getAll", LocalCachedMapOptions.defaults());
        
        Map<String, Integer> filtered1 = map1.getAll(new HashSet<String>(Arrays.asList("2", "3", "5")));

        assertThat(filtered1).isEqualTo(expectedMap);
    }

    @Test
    public void testInvalidationOnPutAll() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                Map<String, Integer> entries = new HashMap<>();
                entries.put("1", 10);
                entries.put("2", 20);
                map1.putAll(entries);

                Thread.sleep(50);
                
                assertThat(cache1).hasSize(2);
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }

    
    @Test
    public void testPutAllCache() throws InterruptedException {
        RLocalCachedMap<Integer, String> map = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        RLocalCachedMap<Integer, String> map1 = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        Map<Integer, String> cache = map.getCachedMap();
        Map<Integer, String> cache1 = map1.getCachedMap();
        map.put(1, "1");
        map.put(2, "2");
        map.put(3, "3");

        Map<Integer, String> joinMap = new HashMap<Integer, String>();
        joinMap.put(4, "4");
        joinMap.put(5, "5");
        joinMap.put(6, "6");
        map.putAll(joinMap);

        assertThat(cache.size()).isEqualTo(6);
        assertThat(cache1.size()).isEqualTo(0);
        assertThat(map.keySet()).containsOnly(1, 2, 3, 4, 5, 6);
        
        map1.putAll(joinMap);
        
        // waiting for cache cleanup listeners triggering 
        Thread.sleep(500);
        
        assertThat(cache.size()).isEqualTo(3);
        assertThat(cache1.size()).isEqualTo(3);
    }
    
    @Test
    public void testAddAndGet() throws InterruptedException {
        RLocalCachedMap<Integer, Integer> map = redisson.getLocalCachedMap("getAll", new CompositeCodec(redisson.getConfig().getCodec(), IntegerCodec.INSTANCE), LocalCachedMapOptions.defaults());
        Map<Integer, Integer> cache = map.getCachedMap();
        map.put(1, 100);

        Integer res = map.addAndGet(1, 12);
        assertThat(cache.size()).isEqualTo(1);
        assertThat(res).isEqualTo(112);
        res = map.get(1);
        assertThat(res).isEqualTo(112);

        RMap<Integer, Double> map2 = redisson.getLocalCachedMap("getAll2", new CompositeCodec(redisson.getConfig().getCodec(), DoubleCodec.INSTANCE), LocalCachedMapOptions.defaults());
        map2.put(1, new Double(100.2));

        Double res2 = map2.addAndGet(1, new Double(12.1));
        assertThat(res2).isEqualTo(112.3);
        res2 = map2.get(1);
        assertThat(res2).isEqualTo(112.3);

        RMap<String, Integer> mapStr = redisson.getLocalCachedMap("mapStr", new CompositeCodec(redisson.getConfig().getCodec(), IntegerCodec.INSTANCE), LocalCachedMapOptions.defaults());
        assertThat(mapStr.put("1", 100)).isNull();

        assertThat(mapStr.addAndGet("1", 12)).isEqualTo(112);
        assertThat(mapStr.get("1")).isEqualTo(112);
        assertThat(cache.size()).isEqualTo(1);
    }
    
    @Test
    public void testFastPutIfAbsent() throws Exception {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        
        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.put(key, value);
        assertThat(map.fastPutIfAbsent(key, new SimpleValue("3"))).isFalse();
        assertThat(cache.size()).isEqualTo(1);
        assertThat(map.get(key)).isEqualTo(value);

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        assertThat(map.fastPutIfAbsent(key1, value1)).isTrue();
        assertThat(cache.size()).isEqualTo(2);
        assertThat(map.get(key1)).isEqualTo(value1);
    }
    
    @Test
    public void testReadAllEntrySet() {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple12", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        assertThat(map.readAllEntrySet().size()).isEqualTo(3);
        assertThat(cache.size()).isEqualTo(3);
        Map<SimpleKey, SimpleValue> testMap = new HashMap<>(map);
        assertThat(map.readAllEntrySet()).containsOnlyElementsOf(testMap.entrySet());
        
        RMap<SimpleKey, SimpleValue> map2 = redisson.getLocalCachedMap("simple12", LocalCachedMapOptions.defaults());
        assertThat(map2.readAllEntrySet()).containsOnlyElementsOf(testMap.entrySet());
    }
    
    @Test
    public void testPutIfAbsent() throws Exception {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple12", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();

        SimpleKey key = new SimpleKey("1");
        SimpleValue value = new SimpleValue("2");
        map.put(key, value);
        Assert.assertEquals(value, map.putIfAbsent(key, new SimpleValue("3")));
        Assert.assertEquals(value, map.get(key));

        SimpleKey key1 = new SimpleKey("2");
        SimpleValue value1 = new SimpleValue("4");
        Assert.assertNull(map.putIfAbsent(key1, value1));
        Assert.assertEquals(value1, map.get(key1));
        assertThat(cache.size()).isEqualTo(2);
    }
    
    @Test
    public void testInvalidationOnRemoveValue() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.remove("1", 1);
                map1.remove("2", 2);

                Thread.sleep(50);
                
                assertThat(cache1).isEmpty();
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }

    
    @Test
    public void testRemoveValue() {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple12", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.remove(new SimpleKey("1"), new SimpleValue("2"));
        Assert.assertTrue(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertNull(val1);

        Assert.assertEquals(0, map.size());
        assertThat(cache.size()).isEqualTo(0);
    }

    @Test
    public void testRemoveValueFail() {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple12", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.remove(new SimpleKey("2"), new SimpleValue("1"));
        Assert.assertFalse(res);

        boolean res1 = map.remove(new SimpleKey("1"), new SimpleValue("3"));
        Assert.assertFalse(res1);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertEquals("2", val1.getValue());
        assertThat(cache.size()).isEqualTo(1);
    }
    
    @Test
    public void testInvalidationOnReplaceOldValue() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.replace("1", 1, 10);
                map1.replace("2", 2, 20);

                Thread.sleep(50);
                
                assertThat(cache1).hasSize(2);
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }

    
    @Test
    public void testReplaceOldValueFail() {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("43"), new SimpleValue("31"));
        Assert.assertFalse(res);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertEquals("2", val1.getValue());
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    public void testReplaceOldValueSuccess() {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        boolean res = map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3"));
        Assert.assertTrue(res);

        boolean res1 = map.replace(new SimpleKey("1"), new SimpleValue("2"), new SimpleValue("3"));
        Assert.assertFalse(res1);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertEquals("3", val1.getValue());
        assertThat(cache.size()).isEqualTo(1);
    }
    
    @Test
    public void testInvalidationOnReplaceValue() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.replace("1", 10);
                map1.replace("2", 20);

                Thread.sleep(50);
                
                assertThat(cache1).hasSize(2);
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }
    
    @Test
    public void testReplaceValue() {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        map.put(new SimpleKey("1"), new SimpleValue("2"));

        SimpleValue res = map.replace(new SimpleKey("1"), new SimpleValue("3"));
        Assert.assertEquals("2", res.getValue());
        assertThat(cache.size()).isEqualTo(1);

        SimpleValue val1 = map.get(new SimpleKey("1"));
        Assert.assertEquals("3", val1.getValue());
    }
    
    @Test
    public void testReadAllValues() {
        RLocalCachedMap<SimpleKey, SimpleValue> map = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        Map<SimpleKey, SimpleValue> cache = map.getCachedMap();
        
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));
        assertThat(cache.size()).isEqualTo(3);

        assertThat(map.readAllValues().size()).isEqualTo(3);
        Map<SimpleKey, SimpleValue> testMap = new HashMap<>(map);
        assertThat(map.readAllValues()).containsOnlyElementsOf(testMap.values());
        
        RMap<SimpleKey, SimpleValue> map2 = redisson.getLocalCachedMap("simple", LocalCachedMapOptions.defaults());
        assertThat(map2.readAllValues()).containsOnlyElementsOf(testMap.values());
    }

    
    @Test
    public void testInvalidationOnFastRemove() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.fastRemove("1", "2", "3");

                Thread.sleep(50);
                
                assertThat(cache1).isEmpty();
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }

    @Test
    public void testRemove() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        Map<String, Integer> cache = map.getCachedMap();
        map.put("12", 1);

        assertThat(cache.size()).isEqualTo(1);
        
        assertThat(map.remove("12")).isEqualTo(1);
        
        assertThat(cache.size()).isEqualTo(0);
        
        assertThat(map.remove("14")).isNull();
    }

    @Test
    public void testFastRemove() throws InterruptedException, ExecutionException {
        RLocalCachedMap<Integer, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        map.put(1, 3);
        map.put(2, 4);
        map.put(7, 8);

        assertThat(map.fastRemove(1, 2)).isEqualTo(2);
        assertThat(map.fastRemove(2)).isEqualTo(0);
        assertThat(map.size()).isEqualTo(1);
    }
    
    @Test
    public void testFastRemoveEmpty() throws InterruptedException, ExecutionException {
        LocalCachedMapOptions options = LocalCachedMapOptions.defaults()
                .evictionPolicy(EvictionPolicy.NONE)
                .cacheSize(3)
                .syncStrategy(SyncStrategy.NONE);
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", options);
        assertThat(map.fastRemove("test")).isZero();
    }

    @Test
    public void testInvalidationOnFastPut() throws InterruptedException {
        new InvalidationTest() {
            @Override
            public void test() throws InterruptedException {
                map1.fastPut("1", 10);
                map1.fastPut("2", 20);

                Thread.sleep(50);
                
                assertThat(cache1).hasSize(2);
                assertThat(cache2).isEmpty();
            }
        }.execute();
    }


    @Test
    public void testFastPut() {
        RLocalCachedMap<String, Integer> map = redisson.getLocalCachedMap("test", LocalCachedMapOptions.defaults());
        Assert.assertTrue(map.fastPut("1", 2));
        assertThat(map.get("1")).isEqualTo(2);
        Assert.assertFalse(map.fastPut("1", 3));
        assertThat(map.get("1")).isEqualTo(3);
        Assert.assertEquals(1, map.size());
    }

    
}
