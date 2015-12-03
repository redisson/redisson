package org.redisson.client.protocol.decoder;

public class ListIteratorResult<V> {

    private final V element;
    private final long size;

    public ListIteratorResult(V element, long size) {
        super();
        this.element = element;
        this.size = size;
    }

    public V getElement() {
        return element;
    }

    public long getSize() {
        return size;
    }

}
