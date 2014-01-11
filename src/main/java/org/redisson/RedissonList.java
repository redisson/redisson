/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RList;

import com.lambdaworks.redis.RedisConnection;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonList<V> extends RedissonObject implements RList<V> {

    private int batchSize = 50;

    private final ConnectionManager connectionManager;

    RedissonList(ConnectionManager connectionManager, String name) {
        super(name);
        this.connectionManager = connectionManager;
    }

    protected ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    @Override
    public int size() {
        RedisConnection<String, Object> connection = connectionManager.connection();
        try {
            return connection.llen(getName()).intValue();
        } finally {
            connectionManager.release(connection);
        }
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
        return addAll(Collections.singleton(e));
    }

    @Override
    public boolean remove(Object o) {
        RedisConnection<String, Object> connection = connectionManager.connection();
        try {
            return connection.lrem(getName(), 1, o) > 0;
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        if (isEmpty()) {
            return false;
        }

        RedisConnection<String, Object> connection = connectionManager.connection();
        try {
            Collection<Object> copy = new ArrayList<Object>(c);
            int to = div(size(), batchSize);
            for (int i = 0; i < to; i++) {
                List<Object> range = connection.lrange(getName(), i*batchSize, i*batchSize + batchSize - 1);
                for (Iterator<Object> iterator = copy.iterator(); iterator.hasNext();) {
                    Object obj = iterator.next();
                    int index = range.indexOf(obj);
                    if (index != -1) {
                        iterator.remove();
                    }
                }
            }

            return copy.isEmpty();
        } finally {
            connectionManager.release(connection);
        }

    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            conn.rpush(getName(), c.toArray());
            return true;
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public boolean addAll(int index, Collection<? extends V> coll) {
        checkPosition(index);
        if (index < size()) {
            RedisConnection<String, Object> conn = connectionManager.connection();
            try {
                while (true) {
                    conn.watch(getName());
                    List<Object> tail = conn.lrange(getName(), index, size());

                    conn.multi();
                    conn.ltrim(getName(), 0, index - 1);
                    conn.rpush(getName(), coll.toArray());
                    conn.rpush(getName(), tail.toArray());
                    if (conn.exec().size() == 3) {
                        return true;
                    }
                }
            } finally {
                connectionManager.release(conn);
            }
        } else {
            return addAll(coll);
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            boolean result = false;
            for (Object object : c) {
                boolean res = conn.lrem(getName(), 0, object) > 0;
                if (!result) {
                    result = res;
                }
            }
            return result;
        } finally {
            connectionManager.release(conn);
        }

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
        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            conn.del(getName());
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public V get(int index) {
        checkIndex(index);
        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            return (V) conn.lindex(getName(), index);
        } finally {
            connectionManager.release(conn);
        }
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
        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            V prev = (V) conn.lindex(getName(), index);
            conn.lset(getName(), index, element);
            return prev;
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public void add(int index, V element) {
        addAll(index, Collections.singleton(element));
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

        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            if (index == 0) {
                return (V) conn.lpop(getName());
            }
            while (true) {
                conn.watch(getName());
                V prev = (V) conn.lindex(getName(), index);
                List<Object> tail = conn.lrange(getName(), index + 1, size());

                conn.multi();
                conn.ltrim(getName(), 0, index - 1);
                conn.rpush(getName(), tail.toArray());
                if (conn.exec().size() == 2) {
                    return prev;
                }
            }
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public int indexOf(Object o) {
        if (isEmpty()) {
            return -1;
        }

        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            int to = div(size(), batchSize);
            for (int i = 0; i < to; i++) {
                List<Object> range = conn.lrange(getName(), i*batchSize, i*batchSize + batchSize - 1);
                int index = range.indexOf(o);
                if (index != -1) {
                    return index + i*batchSize;
                }
            }

            return -1;
        } finally {
            connectionManager.release(conn);
        }
    }

    @Override
    public int lastIndexOf(Object o) {
        if (isEmpty()) {
            return -1;
        }

        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            int size = size();
            int to = div(size, batchSize);
            for (int i = 1; i <= to; i++) {
                int startIndex = -i*batchSize;
                List<Object> range = conn.lrange(getName(), startIndex, size - (i-1)*batchSize);
                int index = range.lastIndexOf(o);
                if (index != -1) {
                    return Math.max(size + startIndex, 0) + index;
                }
            }

            return -1;
        } finally {
            connectionManager.release(conn);
        }
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

        RedisConnection<String, Object> conn = connectionManager.connection();
        try {
            return (List<V>) conn.lrange(getName(), fromIndex, toIndex - 1);
        } finally {
            connectionManager.release(conn);
        }
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
