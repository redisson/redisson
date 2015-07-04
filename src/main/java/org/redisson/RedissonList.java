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

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisConnection;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.redisson.async.AsyncOperation;
import org.redisson.async.OperationListener;
import org.redisson.async.ResultOperation;
import org.redisson.async.SyncOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RList;
import org.redisson.core.RScript;

import java.util.*;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonList<V> extends RedissonExpirable implements RList<V> {

    private int batchSize = 50;

    protected RedissonList(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);
    }

    @Override
    public int size() {
        return connectionManager.read(getName(), new ResultOperation<Long, V>() {
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
        List<V> list = readAllList();
        return list.toArray();
    }

    protected List<V> readAllList() {
        List<V> list = connectionManager.read(getName(), new ResultOperation<List<V>, V>() {
            @Override
            protected Future<List<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.lrange(getName(), 0, -1);
            }
        });
        return list;
    }

    @Override
    public <T> T[] toArray(T[] a) {
        List<V> list = readAllList();
        return list.toArray(a);
    }

    @Override
    public boolean add(V e) {
        return addAll(Collections.singleton(e));
    }

    @Override
    public Future<Boolean> addAsync(V e) {
        return addAllAsync(Collections.singleton(e));
    }

    @Override
    public boolean remove(Object o) {
        return remove(o, 1);
    }

    protected boolean remove(final Object o, final int count) {
        return connectionManager.write(getName(), new ResultOperation<Long, Object>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, Object> async) {
                return async.lrem(getName(), count, o);
            }
        }) > 0;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        if (isEmpty() || c.isEmpty()) {
            return false;
        }

        Collection<Object> copy = new ArrayList<Object>(c);
            int to = div(size(), batchSize);
            for (int i = 0; i < to; i++) {
                final int j = i;
                List<Object> range = connectionManager.read(getName(), new ResultOperation<List<Object>, Object>() {
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
    public boolean addAll(Collection<? extends V> c) {
        return connectionManager.get(addAllAsync(c));
    }

    @Override
    public Future<Boolean> addAllAsync(final Collection<? extends V> c) {
        if (c.isEmpty()) {
            return connectionManager.getGroup().next().newSucceededFuture(false);
        }
        return connectionManager.writeAsync(getName(), new AsyncOperation<Object, Boolean>() {
            @Override
            public void execute(final Promise<Boolean> promise, RedisAsyncConnection<Object, Object> async) {
                async.rpush((Object) getName(), c.toArray()).addListener(new OperationListener<Object, Boolean, Object>(promise, async, this) {
                    @Override
                    public void onOperationComplete(Future<Object> future) throws Exception {
                        promise.setSuccess(true);
                    }
                });
            }
        });
    }

    @Override
    public boolean addAll(final int index, final Collection<? extends V> coll) {
        checkPosition(index);
        if (coll.isEmpty()) {
            return false;
        }
        if (index < size()) {

            if (index == 0) { // prepend elements to list
                final ArrayList<V> elemens = new ArrayList<V>(coll);
                Collections.reverse(elemens);
                return connectionManager.write(getName(), new SyncOperation<Object, Boolean>() {
                    @Override
                    public Boolean execute(RedisConnection<Object, Object> conn) {
                        conn.lpush(getName(), elemens.toArray());
                        return true;
                    }
                });
            }

            // insert into middle of list

            return "OK".equals(new RedissonScript(connectionManager).evalR(
                    "local ind = table.remove(ARGV); " + // index is last parameter
                            "local tail = redis.call('lrange', KEYS[1], ind, -1); " +
                            "redis.call('ltrim', KEYS[1], 0, ind - 1); " +
                            "for i, v in ipairs(ARGV) do redis.call('rpush', KEYS[1], v) end;" +
                            "for i, v in ipairs(tail) do redis.call('rpush', KEYS[1], v) end;" +
                            "return 'OK'",
                    RScript.ReturnType.STATUS,
                    Collections.<Object>singletonList(getName()), new ArrayList<Object>(coll), Collections.singletonList(index)));
        } else {
            // append to list
            return addAll(coll);
        }
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        if (c.isEmpty()) {
            return false;
        }

        return connectionManager.write(getName(), new SyncOperation<Object, Boolean>() {
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
        connectionManager.write(getName(), new ResultOperation<Long, V>() {
            @Override
            protected Future<Long> execute(RedisAsyncConnection<Object, V> async) {
                return async.del(getName());
            }
        });
    }

    @Override
    public Future<V> getAsync(final int index) {
        return connectionManager.readAsync(getName(), new ResultOperation<V, V>() {
            @Override
            protected Future<V> execute(RedisAsyncConnection<Object, V> async) {
                return async.lindex(getName(), index);
            }
        });
    }

    @Override
    public V get(int index) {
        checkIndex(index);
        return getValue(index);
    }

    private V getValue(int index) {
        return connectionManager.get(getAsync(index));
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

        return new RedissonScript(connectionManager).evalR(
                "local v = redis.call('lindex', KEYS[1], ARGV[2]); " +
                        "redis.call('lset', KEYS[1], ARGV[2], ARGV[1]); " +
                        "return v",
                RScript.ReturnType.VALUE,
                Collections.<Object>singletonList(getName()),
                Collections.singletonList(element),
                Collections.singletonList(index)

        );
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

        if (index == 0) {
            return connectionManager.write(getName(), new SyncOperation<Object, V>() {
                @Override
                public V execute(RedisConnection<Object, Object> conn) {
                    return (V) conn.lpop(getName());
                }
            });
        }
        // else
        return new RedissonScript(connectionManager).evalR(
                "local v = redis.call('lindex', KEYS[1], ARGV[1]); " +
                        "local tail = redis.call('lrange', KEYS[1], ARGV[1]);" +
                        "redis.call('ltrim', KEYS[1], 0, ARGV[1] - 1);" +
                        "for i, v in ipairs(tail) do redis.call('rpush', KEYS[1], v) end;" +
                        "return v",
                RScript.ReturnType.VALUE,
                Collections.<Object>singletonList(getName()), Collections.emptyList(), Collections.singletonList(index));
    }

    @Override
    public int indexOf(Object o) {
        if (isEmpty()) {
            return -1;
        }

        Long index = new RedissonScript(connectionManager).eval(
                "local s = redis.call('llen', KEYS[1]);" +
                        "for i = 0, s, 1 do if ARGV[1] == redis.call('lindex', KEYS[1], i) then return i end end;" +
                        "return -1",
                RScript.ReturnType.INTEGER,
                Collections.<Object>singletonList(getName()), o);
        return index.intValue();
    }

    @Override
    public int lastIndexOf(Object o) {
        if (isEmpty()) {
            return -1;
        }

        Long index = new RedissonScript(connectionManager).eval(
                "local s = redis.call('llen', KEYS[1]);" +
                        "for i = s, 0, -1 do if ARGV[1] == redis.call('lindex', KEYS[1], i) then return i end end;" +
                        "return -1",
                RScript.ReturnType.INTEGER,
                Collections.<Object>singletonList(getName()), o);
        return index.intValue();
    }

    @Override
    public ListIterator<V> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<V> listIterator(final int ind) {
        return new ListIterator<V>() {

            private V prevCurrentValue;
            private V nextCurrentValue;
            private V currentValueHasRead;
            private int currentIndex = ind - 1;
            private boolean removeExecuted;

            @Override
            public boolean hasNext() {
                V val = RedissonList.this.getValue(currentIndex+1);
                if (val != null) {
                    nextCurrentValue = val;
                }
                return val != null;
            }

            @Override
            public V next() {
                if (nextCurrentValue == null && !hasNext()) {
                    throw new NoSuchElementException("No such element at index " + currentIndex);
                }
                currentIndex++;
                currentValueHasRead = nextCurrentValue;
                nextCurrentValue = null;
                removeExecuted = false;
                return currentValueHasRead;
            }

            @Override
            public void remove() {
                if (currentValueHasRead == null) {
                    throw new IllegalStateException("Neither next nor previous have been called");
                }
                if (removeExecuted) {
                    throw new IllegalStateException("Element been already deleted");
                }
                RedissonList.this.remove(currentValueHasRead);
                currentIndex--;
                removeExecuted = true;
                currentValueHasRead = null;
            }

            @Override
            public boolean hasPrevious() {
                if (currentIndex < 0) {
                    return false;
                }
                V val = RedissonList.this.getValue(currentIndex);
                if (val != null) {
                    prevCurrentValue = val;
                }
                return val != null;
            }

            @Override
            public V previous() {
                if (prevCurrentValue == null && !hasPrevious()) {
                    throw new NoSuchElementException("No such element at index " + currentIndex);
                }
                currentIndex--;
                removeExecuted = false;
                currentValueHasRead = prevCurrentValue;
                prevCurrentValue = null;
                return currentValueHasRead;
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

        return connectionManager.read(getName(), new ResultOperation<List<V>, V>() {

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
