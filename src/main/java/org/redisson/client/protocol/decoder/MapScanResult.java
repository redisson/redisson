package org.redisson.client.protocol.decoder;

import java.util.Map;

public class MapScanResult<K, V> {

    private final Long pos;
    private final Map<K, V> values;

    public MapScanResult(Long pos, Map<K, V> values) {
        super();
        this.pos = pos;
        this.values = values;
    }

    public Long getPos() {
        return pos;
    }

    public Map<K, V> getMap() {
        return values;
    }

}
