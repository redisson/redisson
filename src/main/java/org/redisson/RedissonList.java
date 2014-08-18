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

import io.netty.util.concurrent.Future;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.redisson.async.ResultOperation;
import org.redisson.async.SyncOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RList;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisConnection;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonList<V> extends RedissonExpirable implements RList<V> {

    private int batchSize = 50;

    RedissonList(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public int size() {
        return connectionManager.read(new ResultOperation<Long, V>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, V> async) {
                return async.llen(getName());
            }
        }).intValue();
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
        return remove(o, 1);
    }

    protected boolean remove(final Object o, final int count) {
        return connectionManager.write(new ResultOperation<Long, Object>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.lrem(getName(), count, o);
            }
        }) > 0;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        if (isEmpty()) {
            return false;
        }

        Collection<Object> copy = new ArrayList<Object>(c);
            int to = div(size(), batchSize);
            for (int i = 0; i < to; i++) {
                final int j = i;
                List<Object> range = connectionManager.read(new ResultOperation<List<Object>, Object>() {
                    @Override
                    protected Future<List<Object>> execute(RedisAsyncConnection<Object, Object> async) {
                        return async.lrange(getName(), j*batchSize, j*batchSize + batchSize - 1);
                    }
                });
                for (Iterator<Object> iterator = copy.iterator(); iterator.hasNext();) {
                    Object obj = iterator.next();
                    int index = range.indexOf(obj);
                    if (index != -1) {
                        iterator.remove();
                    }
                }
            }
        return copy.isEmpty();

    }

    @Override
    public boolean addAll(final Collection<? extends V> c) {
        connectionManager.write(new ResultOperation<Long, Object>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.rpush(getName(), c.toArray());
            }
        });
        return true;
    }

    @Override
    public boolean addAll(final int index, final Collection<? extends V> coll) {
        checkPosition(index);
        if (index < size()) {
            return connectionManager.write(new SyncOperation<Object, Boolean>() {
                @Override
                public Boolean execute(RedisConnection<Object, Object> conn) {
                    while (true) {
                        conn.watch(getName());
                        List<Object> tail = conn.lrange(getName(), index, size());
                        int first = 0;
                        int last = 0;
                        if (index == 0){
                        	first = size();//truncate the list
                        	last = 0;
                        }else{
                        	first = 0;
                        	last = index - 1;
                        }
                        conn.multi();
                        conn.ltrim(getName(), first, last);
                        conn.rpush(getName(), coll.toArray());
                        conn.rpush(getName(), tail.toArray());
                        if (conn.exec().size() == 3) {
                            return true;
                        }
                    }
                }
            });
        } else {
            return addAll(coll);
        }
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        return connectionManager.write(new SyncOperation<Object, Boolean>() {
            @Override
            public Boolean execute(RedisConnection<Object, Object> conn) {
                boolean result = false;
                for (Object object : c) {
                    boolean res = conn.lrem(getName(), 0, object) > 0;
                    if (!result) {
                        result = res;
                    }
                }
                return result;
            }
            
        });
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
        connectionManager.write(new ResultOperation<Long, V>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, V> async) {
                return async.del(getName());
            }
        });
    }

    @Override
    public V get(final int index) {
        checkIndex(index);
        return connectionManager.read(new ResultOperation<V, V>() {
            @Override
            protected Future<V> execute(RedisAsyncConnection<Object, V> async) {
                return async.lindex(getName(), index);
            }
        });
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
    public V set(final int index, final V element) {
        checkIndex(index);
        
        return connectionManager.write(new SyncOperation<V, V>() {
            @Override
            public V execute(RedisConnection<Object, V> conn) {
                while (true) {
                    conn.watch(getName());
                    V prev = (V) conn.lindex(getName(), index);
                    
                    conn.multi();
                    conn.lset(getName(), index, element);
                    if (conn.exec().size() == 1) {
                        return prev;
                    }
                }
            }
        });
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
    public V remove(final int index) {
        checkIndex(index);

        return connectionManager.write(new SyncOperation<Object, V>() {
            @Override
            public V execute(RedisConnection<Object, Object> conn) {
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
            }
        });
    }

    @Override
    public int indexOf(Object o) {
        if (isEmpty()) {
            return -1;
        }

        int to = div(size(), batchSize);
        for (int i = 0; i < to; i++) {
            final int j = i;
            List<Object> range = connectionManager.read(new ResultOperation<List<Object>, Object>() {
                @Override
                protected Future<List<Object>> execute(RedisAsyncConnection<Object, Object> async) {
                    return async.lrange(getName(), j*batchSize, j*batchSize + batchSize - 1);
                }
            });
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

        final int size = size();
        int to = div(size, batchSize);
        for (int i = 1; i <= to; i++) {
            final int j = i;
            final int startIndex = -i*batchSize;
            List<Object> range = connectionManager.read(new ResultOperation<List<Object>, Object>() {
                @Override
                protected Future<List<Object>> execute(RedisAsyncConnection<Object, Object> async) {
                    return async.lrange(getName(), startIndex, size - (j-1)*batchSize);
                }
            });
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
    public List<V> subList(final int fromIndex, final int toIndex) {
        int size = size();
        if (fromIndex < 0 || toIndex > size) {
            throw new IndexOutOfBoundsException("fromIndex: " + fromIndex + " toIndex: " + toIndex + " size: " + size);
        }
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex: " + fromIndex + " toIndex: " + toIndex);
        }

        return connectionManager.read(new ResultOperation<List<V>, V>() {

            @Override
            protected Future<List<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.lrange(getName(), fromIndex, toIndex - 1);
            }
        });
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
