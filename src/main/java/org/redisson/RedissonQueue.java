package org.redisson;

import java.util.NoSuchElementException;
import java.util.Queue;

import com.lambdaworks.redis.RedisConnection;

public class RedissonQueue<V> extends RedissonList<V> implements Queue<V> {

    RedissonQueue(Redisson redisson, RedisConnection<Object, Object> connection, String name) {
        super(redisson, connection, name);
    }

    @Override
    public boolean offer(V e) {
        return add(e);
    }

    public V getFirst() {
        V value = (V) getConnection().lindex(getName(), 0);
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public V removeFirst() {
        V value = (V) getConnection().lpop(getName());
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public V remove() {
        return removeFirst();
    }

    @Override
    public V poll() {
        return (V) getConnection().lpop(getName());
    }

    @Override
    public V element() {
        return getFirst();
    }

    @Override
    public V peek() {
        return (V) getConnection().lindex(getName(), 0);
    }

}
