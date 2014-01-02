package org.redisson;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import com.lambdaworks.redis.RedisConnection;

public class RedissonList<V> implements List<V> {

    private RedisConnection<Object, Object> connection;
    private String name;

    RedissonList(RedisConnection<Object, Object> connection, String name) {
        this.connection = connection;
        this.name = name;
    }

    @Override
    public int size() {
        return connection.llen(name).intValue();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) != -1;
    }

    @Override
    public Iterator<V> iterator() {
        return listIterator();
    }

    @Override
    public Object[] toArray() {
        List<V> list = subList(0, size());
        return list.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        List<V> list = subList(0, size());
        return list.toArray(a);
    }

    @Override
    public boolean add(V e) {
        return connection.rpush(name, e) > 1;
    }

    @Override
    public boolean remove(Object o) {
        return connection.lrem(name, 1, o) > 0;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object object : c) {
            // TODO optimize - search bulk values at once in range
            if (!contains(object)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        for (V v : c) {
            add(v);
        }
        return true;
    }

    @Override
    public boolean addAll(int index, Collection<? extends V> c) {
        for (V v : c) {
            add(index++, v);
        }
        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean result = false;
        for (Object object : c) {
            boolean res = connection.lrem(name, 0, object) > 0;
            if (!result) {
                result = res;
            }
        }
        return result;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = false;
        for (Iterator<V> iterator = iterator(); iterator.hasNext();) {
            V object = iterator.next();
            if (!c.contains(object)) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void clear() {
        connection.del(name);
    }

    @Override
    public V get(int index) {
        return (V) connection.lindex(name, index);
    }

    @Override
    public V set(int index, V element) {
        V prev = get(index);
        connection.lset(name, index, element);
        return prev;
    }

    @Override
    public void add(int index, V element) {
        V value = get(index);
        connection.linsert(name, true, value, element);
    }

    @Override
    public V remove(int index) {
        V value = get(index);
        connection.lrem(name, 1, value);
        return value;
    }

    @Override
    public int indexOf(Object o) {
        if (isEmpty()) {
            return -1;
        }

        int to = Math.max(size()/100, 1);
        for (int i = 0; i < to; i++) {
            List<Object> range = connection.lrange(name, i*100, i*100 + 100);
            int index = range.indexOf(o);
            if (index != -1) {
                return index + i*100;
            }
        }

        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        if (isEmpty()) {
            return -1;
        }

        int to = Math.max(size()/100, 1);
        for (int i = 1; i <= to; i++) {
            List<Object> range = connection.lrange(name, -i*100, 100);
            int index = range.indexOf(o);
            if (index != -1) {
                return index + i*100;
            }
        }

        return -1;
    }

    @Override
    public ListIterator<V> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<V> listIterator(final int ind) {
        return new ListIterator<V>() {

            private int currentIndex = ind - 1;
            private boolean removeExecuted;

            @Override
            public boolean hasNext() {
                int size = size();
                return currentIndex+1 < size && size > 0;
            }

            @Override
            public V next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No such element at index " + currentIndex);
                }
                currentIndex++;
                removeExecuted = false;
                return RedissonList.this.get(currentIndex);
            }

            @Override
            public void remove() {
                if (removeExecuted) {
                    throw new IllegalStateException("Element been already deleted");
                }
                RedissonList.this.remove(currentIndex);
                removeExecuted = true;
            }

            @Override
            public boolean hasPrevious() {
                int size = size();
                return currentIndex-1 < size && size > 0;
            }

            @Override
            public V previous() {
                if (!hasPrevious()) {
                    throw new NoSuchElementException("No such element at index " + currentIndex);
                }
                currentIndex--;
                removeExecuted = false;
                return RedissonList.this.get(currentIndex);
            }

            @Override
            public int nextIndex() {
                return currentIndex + 1;
            }

            @Override
            public int previousIndex() {
                return currentIndex - 1;
            }

            @Override
            public void set(V e) {
                RedissonList.this.set(currentIndex, e);
            }

            @Override
            public void add(V e) {
                RedissonList.this.add(currentIndex, e);
            }
        };
    }

    @Override
    public List<V> subList(int fromIndex, int toIndex) {
        return (List<V>) connection.lrange(name, fromIndex, toIndex);
    }


}
