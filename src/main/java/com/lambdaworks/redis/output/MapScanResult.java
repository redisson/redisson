package com.lambdaworks.redis.output;

import java.util.LinkedHashMap;
import java.util.Map;

public class MapScanResult<K, V> {

    private Long pos;
    private K lastKey;
    private Map<K, V> values = new LinkedHashMap<K, V>();
    
    public void setPos(Long pos) {
        this.pos = pos;
    }
    public Long getPos() {
        return pos;
    }
    
    public void addKey(K key) {
        lastKey = key;
    }
    
    public void addValue(V value) {
        values.put(lastKey, value);
    }
    public Map<K, V> getMap() {
        return values;
    }
    
}
