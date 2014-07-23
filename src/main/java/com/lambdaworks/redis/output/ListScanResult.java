package com.lambdaworks.redis.output;

import java.util.ArrayList;
import java.util.List;

public class ListScanResult<V> {

    private Long pos;
    private List<V> values = new ArrayList<V>();
    
    public void setPos(Long pos) {
        this.pos = pos;
    }
    public Long getPos() {
        return pos;
    }
    
    public void addValue(V value) {
        values.add(value);
    }
    public List<V> getValues() {
        return values;
    }
    
}
