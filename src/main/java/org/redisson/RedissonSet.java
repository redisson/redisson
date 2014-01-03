package org.redisson;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import com.lambdaworks.redis.RedisConnection;

public class RedissonSet<V> implements Set<V> {

    private final Redisson redisson;
    private final RedisConnection<Object, Object> connection;
    private final String name;

    RedissonSet(Redisson redisson, RedisConnection<Object, Object> connection, String name) {
        this.connection = connection;
        this.name = name;
        this.redisson = redisson;
    }

    @Override
    public int size() {
        return connection.scard(name).intValue();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return connection.sismember(name, o);
    }

    @Override
    public Iterator<V> iterator() {
        return (Iterator<V>) connection.smembers(name).iterator();
    }

    @Override
    public Object[] toArray() {
        return connection.smembers(name).toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return connection.smembers(name).toArray(a);
    }

    @Override
    public boolean add(V e) {
        return connection.sadd(name, e) > 0;
    }

    @Override
    public boolean remove(Object o) {
        return connection.srem(name, o) > 0;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object object : c) {
            if (!contains(object)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        return connection.sadd(name, c.toArray()) > 0;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        RedisConnection<Object, Object> conn = redisson.connect();
        try {
            while (true) {
                int changed = 0;
                conn.watch(name);
                Iterator<V> iterator = (Iterator<V>) conn.smembers(name).iterator();
                conn.multi();
                while (iterator.hasNext()) {
                    V object = iterator.next();
                    if (!c.contains(object)) {
                        iterator.remove();
                        connection.srem(name, object);
                        changed++;
                    }
                }
                if (changed == 0) {
                    conn.discard();
                    return false;
                } else if (conn.exec().size() == changed) {
                    return true;
                }
            }
        } finally {
            conn.close();
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return connection.srem(name, c.toArray()) > 0;
    }

    @Override
    public void clear() {
        connection.del(name);
    }

}
