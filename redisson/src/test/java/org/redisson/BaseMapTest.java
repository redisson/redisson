package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Test;
import org.redisson.api.RMap;
import org.redisson.api.map.MapLoader;
import org.redisson.api.map.MapWriter;

public abstract class BaseMapTest extends BaseTest {

    protected abstract <K, V> RMap<K, V> getWriterTestMap(String name, Map<K, V> map);
    
    protected abstract <K, V> RMap<K, V> getLoaderTestMap(String name, Map<K, V> map);

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
    }
    
    @Test
    public void testWriterAddAndGet() {
        Map<String, Integer> store = new HashMap<>();
        RMap<String, Integer> map = getWriterTestMap("test", store);

        assertThat(map.addAndGet("1", 11)).isEqualTo(11);
        assertThat(map.addAndGet("1", 7)).isEqualTo(18);
        
        Map<String, Integer> expected = new HashMap<>();
        expected.put("1", 18);
        assertThat(store).isEqualTo(expected);
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
    }
    
    protected <K, V> MapWriter<K, V> createMapWriter(Map<K, V> map) {
        return new MapWriter<K, V>() {

            @Override
            public void write(K key, V value) {
                map.put(key, value);
            }

            @Override
            public void writeAll(Map<K, V> values) {
                map.putAll(values);
            }

            @Override
            public void delete(K key) {
                map.remove(key);
            }

            @Override
            public void deleteAll(Collection<K> keys) {
                for (K key : keys) {
                    map.remove(key);
                }
            }
            
        };
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
        
        Map<String, String> s = map.getAll(new HashSet<>(Arrays.asList("1", "2", "9", "3")));
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put("1", "11");
        expectedMap.put("2", "22");
        expectedMap.put("3", "33");
        assertThat(s).isEqualTo(expectedMap);
        assertThat(map.size()).isEqualTo(4);
    }

}
