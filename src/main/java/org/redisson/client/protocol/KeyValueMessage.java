package org.redisson.client.protocol;

public class KeyValueMessage<K, V> {

    private K key;
    private V value;

    public KeyValueMessage(K key, V value) {
        super();
        this.key = key;
        this.value = value;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

}
