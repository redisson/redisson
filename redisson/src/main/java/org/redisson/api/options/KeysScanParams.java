package org.redisson.api.options;

import org.redisson.api.RType;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class KeysScanParams implements KeysScanOptions {

    private int limit;
    private String pattern;
    private int chunkSize;
    private RType type;

    @Override
    public KeysScanOptions limit(int value) {
        this.limit = value;
        return this;
    }

    @Override
    public KeysScanOptions pattern(String value) {
        this.pattern = value;
        return this;
    }

    @Override
    public KeysScanOptions chunkSize(int value) {
        this.chunkSize = value;
        return this;
    }

    @Override
    public KeysScanOptions type(RType value) {
        this.type = value;
        return this;
    }

    public int getLimit() {
        return limit;
    }

    public String getPattern() {
        return pattern;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public RType getType() {
        return type;
    }
}
