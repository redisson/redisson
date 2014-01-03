package org.redisson;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import com.lambdaworks.redis.RedisConnection;

public class RedissonList<V> implements List<V> {

    private int batchSize = 50;

    private final Redisson redisson;
    private final RedisConnection<Object, Object> connection;
    private final String name;

    RedissonList(Redisson redisson, RedisConnection<Object, Object> connection, String name) {
        this.connection = connection;
        this.name = name;
        this.redisson = redisson;
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
        connection.rpush(name, e);
        return true;
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
        connection.rpush(name, c.toArray());
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
        checkIndex(index);
        return (V) connection.lindex(name, index);
    }

    private void checkIndex(int index) {
        int size = size();
        if (!isInRange(index, size))
            throw new IndexOutOfBoundsException("index: " + index + " but current size: "+ size);
    }

    private boolean isInRange(int index, int size) {
        return index >= 0 && index < size;
    }

    private void checkPosition(int index) {
        int size = size();
        if (!isPositionInRange(index, size))
            throw new IndexOutOfBoundsException("index: " + index + " but current size: "+ size);
    }

    private boolean isPositionInRange(int index, int size) {
        return index >= 0 && index <= size;
    }


    @Override
    public V set(int index, V element) {
        checkIndex(index);
        V prev = get(index);
        connection.lset(name, index, element);
        return prev;
    }

    @Override
    public void add(int index, V element) {
        checkPosition(index);
        if (index < size()) {
            RedisConnection<Object, Object> c = redisson.connect();
            c.watch(name);
            List<Object> tail = c.lrange(name, index, size());
            c.multi();
            c.ltrim(name, 0, index - 1);
            c.rpush(name, element);
            c.rpush(name, tail.toArray());
            c.exec();
        } else {
            add(element);
        }
    }

    private int div(int p, int q) {
        int div = p / q;
        int rem = p - q * div; // equal to p % q

        if (rem == 0) {
          return div;
        }

        return div + 1;
    }

    @Override
    public V remove(int index) {
        checkIndex(index);
        V value = get(index);
        connection.lrem(name, 1, value);
        return value;
    }

    @Override
    public int indexOf(Object o) {
        if (isEmpty()) {
            return -1;
        }

        int to = div(size(), batchSize);
        for (int i = 0; i < to; i++) {
            List<Object> range = connection.lrange(name, i*batchSize, i*batchSize + batchSize - 1);
            int index = range.indexOf(o);
            if (index != -1) {
                return index + i*batchSize;
            }
        }

        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        if (isEmpty()) {
            return -1;
        }

        int size = size();
        int to = div(size, batchSize);
        for (int i = 1; i <= to; i++) {
            int startIndex = -i*batchSize;
            List<Object> range = connection.lrange(name, startIndex, size - (i-1)*batchSize);
            int index = range.lastIndexOf(o);
            if (index != -1) {
                return Math.max(size + startIndex, 0) + index;
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
                currentIndex--;
                removeExecuted = true;
            }

            @Override
            public boolean hasPrevious() {
                int size = size();
                return currentIndex-1 < size && size > 0 && currentIndex >= 0;
            }

            @Override
            public V previous() {
                if (!hasPrevious()) {
                    throw new NoSuchElementException("No such element at index " + currentIndex);
                }
                removeExecuted = false;
                V res = RedissonList.this.get(currentIndex);
                currentIndex--;
                return res;
            }

            @Override
            public int nextIndex() {
                return currentIndex + 1;
            }

            @Override
            public int previousIndex() {
                return currentIndex;
            }

            @Override
            public void set(V e) {
                if (currentIndex >= size()-1) {
                    throw new IllegalStateException();
                }
                RedissonList.this.set(currentIndex, e);
            }

            @Override
            public void add(V e) {
                RedissonList.this.add(currentIndex+1, e);
                currentIndex++;
            }
        };
    }

    @Override
    public List<V> subList(int fromIndex, int toIndex) {
        int size = size();
        if (fromIndex < 0 || toIndex > size) {
            throw new IndexOutOfBoundsException("fromIndex: " + fromIndex + " toIndex: " + toIndex + " size: " + size);
        }
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex: " + fromIndex + " toIndex: " + toIndex);
        }
        return (List<V>) connection.lrange(name, fromIndex, toIndex - 1);
    }

    public String toString() {
        Iterator<V> it = iterator();
        if (! it.hasNext())
            return "[]";

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (;;) {
            V e = it.next();
            sb.append(e == this ? "(this Collection)" : e);
            if (! it.hasNext())
                return sb.append(']').toString();
            sb.append(',').append(' ');
        }
    }

}
