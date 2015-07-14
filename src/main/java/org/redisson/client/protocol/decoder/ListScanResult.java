package org.redisson.client.protocol.decoder;

import java.util.List;

public class ListScanResult<V> {

    private final Long pos;
    private final List<V> values;

    public ListScanResult(Long pos, List<V> values) {
        this.pos = pos;
        this.values = values;
    }

    public Long getPos() {
        return pos;
    }

    public List<V> getValues() {
        return values;
    }

}
