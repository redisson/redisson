package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Test;
import org.redisson.api.RMap;
import org.redisson.api.map.MapLoader;

public abstract class BaseMapTest extends BaseTest {

    protected abstract <K, V> RMap<K, V> getLoaderTestMap(String name, Map<K, V> map);
    
    @Test
    public void testLoadAllReplaceValues() {
        Map<String, String> cache = new HashMap<String, String>();
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
