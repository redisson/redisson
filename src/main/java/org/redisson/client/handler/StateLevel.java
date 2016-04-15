package org.redisson.client.handler;

import java.util.List;

public class StateLevel {

    private long size;
    private List<Object> parts;

    public StateLevel(long size, List<Object> parts) {
        super();
        this.size = size;
        this.parts = parts;
    }

    public long getSize() {
        return size;
    }

    public List<Object> getParts() {
        return parts;
    }

    @Override
    public String toString() {
        return "StateLevel [size=" + size + ", parts=" + parts + "]";
    }
    
}
