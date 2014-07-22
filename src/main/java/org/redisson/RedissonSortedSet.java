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

import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedSet;

import org.redisson.async.ResultOperation;
import org.redisson.async.SyncOperation;
import org.redisson.connection.ConnectionManager;
import org.redisson.core.RSortedSet;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisConnection;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V>
 */
public class RedissonSortedSet<V> extends RedissonObject implements RSortedSet<V> {

    private static class NaturalComparator<V> implements Comparator<V>, Serializable {

        private static final long serialVersionUID = 7207038068494060240L;

        static final NaturalComparator NATURAL_ORDER = new NaturalComparator();

        public int compare(V c1, V c2) {
            Comparable<Object> c1co = (Comparable<Object>) c1;
            Comparable<Object> c2co = (Comparable<Object>) c2;
            return c1co.compareTo(c2co);
        }

    }

    public static class BinarySearchResult<V> {

        private V value;
        private int index;

        public BinarySearchResult(V value) {
            super();
            this.value = value;
        }

        public BinarySearchResult() {
        }

        public void setIndex(int index) {
            this.index = index;
        }
        public int getIndex() {
            return index;
        }

        public V getValue() {
            return value;
        }


    }

    private Comparator<? super V> comparator = NaturalComparator.NATURAL_ORDER;

    RedissonSortedSet(ConnectionManager connectionManager, String name) {
        super(connectionManager, name);

        loadComparator();

        connectionManager.write(new ResultOperation<Boolean, Object>() {
            @Override
            protected Future<Boolean> execute(RedisAsyncConnection<Object, Object> async) {
                return async.setnx(getCurrentVersionKey(), 0L);
            }
        });
    }

    private void loadComparator() {
        connectionManager.read(new SyncOperation<V, Void>() {
            @Override
            public Void execute(RedisConnection<Object, V> conn) {
                loadComparator(conn);
                return null;
            }
        });
    }

    private void loadComparator(RedisConnection<Object, ?> connection) {
        try {
            String comparatorSign = (String) connection.get(getComparatorKeyName());
            if (comparatorSign != null) {
                String[] parts = comparatorSign.split(":");
                String className = parts[0];
                String sign = parts[1];

                String result = calcClassSign(className);
                if (!result.equals(sign)) {
                    throw new IllegalStateException("Local class signature of " + className + " differs from used by this SortedSet!");
                }

                Class<?> clazz = Class.forName(className);
                comparator = (Comparator<V>) clazz.newInstance();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // TODO cache result
    private static String calcClassSign(String name) {
        try {
            Class<?> clazz = Class.forName(name);

            ByteArrayOutputStream result = new ByteArrayOutputStream();
            ObjectOutputStream outputStream = new ObjectOutputStream(result);
            outputStream.writeObject(clazz);
            outputStream.close();

            MessageDigest crypt = MessageDigest.getInstance("SHA-1");
            crypt.reset();
            crypt.update(result.toByteArray());

            return new BigInteger(1, crypt.digest()).toString(16);
        } catch (Exception e) {
            throw new IllegalStateException("Can't calculate sign of " + name, e);
        }
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

    private int size(RedisConnection<Object, ?> connection) {
        return connection.llen(getName()).intValue();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(final Object o) {
        return connectionManager.read(new SyncOperation<V, Boolean>() {
            @Override
            public Boolean execute(RedisConnection<Object, V> conn) {
                return binarySearch((V)o, conn).getIndex() >= 0;
            }
        });
    }

    public Iterator<V> iterator() {
        final int ind = 0;
        return new Iterator<V>() {

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
                return RedissonSortedSet.this.get(currentIndex);
            }

            @Override
            public void remove() {
                if (removeExecuted) {
                    throw new IllegalStateException("Element been already deleted");
                }
                RedissonSortedSet.this.remove(currentIndex);
                currentIndex--;
                removeExecuted = true;
            }

        };
//        Double startScore;
//        RedisConnection<Object, V> connection = connectionManager.connectionReadOp();
//        try {
//            startScore = getScoreAtIndex(0, connection);
//        } finally {
//            connectionManager.releaseRead(connection);
//        }
//        if (startScore == null) {
//            return new Iterator<V>() {
//                @Override
//                public boolean hasNext() {
//                    return false;
//                }
//
//                @Override
//                public V next() {
//                    throw new NoSuchElementException();
//                }
//
//                @Override
//                public void remove() {
//                }
//            };
//        }
//
//        return iterator(startScore, Double.MAX_VALUE);
    }

    private void remove(final int index) {
        connectionManager.write(new SyncOperation<Object, V>() {
            @Override
            public V execute(RedisConnection<Object, Object> conn) {
                if (index == 0) {
                    return (V) conn.lpop(getName());
                }
                while (true) {
                    conn.watch(getName());
                    List<Object> tail = conn.lrange(getName(), index + 1, size());
                    conn.multi();
                    conn.ltrim(getName(), 0, index - 1);
                    if (tail.isEmpty()) {
                        if (conn.exec().size() == 1) {
                            return null;
                        }
                    } else {
                        conn.rpush(getName(), tail.toArray());
                        if (conn.exec().size() == 2) {
                            return null;
                        }
                    }
                }
            }
        });
    }
    
    private V get(final int index) {
        return connectionManager.read(new ResultOperation<V, V>() {
            @Override
            protected Future<V> execute(RedisAsyncConnection<Object, V> async) {
                return async.lindex(getName(), index);
            }
        });
    }
    
    @Override
    public Object[] toArray() {
        List<V> res = connectionManager.read(new ResultOperation<List<V>, V>() {
            @Override
            protected Future<List<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.lrange(getName(), 0, -1);
            }
        });
        return res.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        List<V> res = connectionManager.read(new ResultOperation<List<V>, V>() {
            @Override
            protected Future<List<V>> execute(RedisAsyncConnection<Object, V> async) {
                return async.lrange(getName(), 0, -1);
            }
        });
        return res.toArray(a);
    }

    private String getCurrentVersionKey() {
        return "redisson__sortedset__version__" + getName();
    }

    private Long getCurrentVersion(RedisConnection<Object, Object> simpleConnection) {
        return ((Number)simpleConnection.get(getCurrentVersionKey())).longValue();
    }

    @Override
    public boolean add(final V value) {
        return connectionManager.write(new SyncOperation<V, Boolean>() {
            @Override
            public Boolean execute(RedisConnection<Object, V> conn) {
                return add(value, conn);
            }
        });
    }
    
    public Future<Boolean> addAsync(final V value) {
        EventLoop loop = connectionManager.getGroup().next();
        final Promise<Boolean> promise = loop.newPromise();
        
        loop.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean result = add(value);
                    promise.setSuccess(result);
                } catch (Exception e) {
                    promise.setFailure(e);
                }
            }
        });
        
        return promise;
    }

    boolean add(V value, RedisConnection<Object, V> connection) {
        RedisConnection<Object, Object> simpleConnection = (RedisConnection<Object, Object>)connection;

        while (true) {
            connection.watch(getName(), getComparatorKeyName());

            checkComparator(connection);

            Long version = getCurrentVersion(simpleConnection);
            BinarySearchResult<V> res = binarySearch(value, connection);
            if (res.getIndex() < 0) {
//                System.out.println("index: " + res.getIndex() + " value: " + value);
                if (!version.equals(getCurrentVersion(simpleConnection))) {
                    connection.unwatch();
                    continue;
                }
//                NewScore newScore = calcNewScore(res.getIndex(), connection);
//                if (!version.equals(getCurrentVersion(simpleConnection))) {
//                    connection.unwatch();
//                    continue;
//                }
//
//                String leftScoreKey = getScoreKeyName(newScore.getLeftScore());
//                String rightScoreKey = getScoreKeyName(newScore.getRightScore());
//
//                if (simpleConnection.setnx(leftScoreKey, 1)) {
//                    if (!version.equals(getCurrentVersion(simpleConnection))) {
//                        connection.unwatch();
//
//                        connection.del(leftScoreKey);
//                        continue;
//                    }
//                    if (rightScoreKey != null) {
//
//                        if (!simpleConnection.setnx(rightScoreKey, 1)) {
//                            connection.unwatch();
//
//                            connection.del(leftScoreKey);
//                            continue;
//                        }
//                    }
//                } else {
//                    connection.unwatch();
//                    continue;
//                }

                V pivot = null;
                boolean before = false;
                int index = -(res.getIndex() + 1);

                if (index < size()) {
                    before = true;
                    pivot = connection.lindex(getName(), index);
                }
                
                connection.multi();
                if (index >= size()) {
                    connection.rpush(getName(), value);
                } else {
                    connection.linsert(getName(), before, pivot, value);
                }
//                System.out.println("adding: " + newScore.getScore() + " " + value);
//                connection.zadd(getName(), newScore.getScore(), value);
//                if (rightScoreKey != null) {
//                    connection.del(leftScoreKey, rightScoreKey);
//                } else {
//                    connection.del(leftScoreKey);
//                }
                connection.incr(getCurrentVersionKey());
                List<Object> re = connection.exec();
                if (re.size() == 2) {
//                    System.out.println("index: " + index + " value: " + value + " pivot: " + pivot);
                    return true;
//                    Number val = (Number) re.get(0);
//                    Long delCount = (Long) re.get(1);
//                    if (rightScoreKey != null) {
//                        if (delCount != 2) {
//                            throw new IllegalStateException();
//                        }
//                    } else {
//                        if (delCount != 1) {
//                            throw new IllegalStateException();
//                        }
//                    }
//                    return val != null && val.intValue() > 0;
                }
            } else {
                connection.unwatch();
                return false;
            }
        }
    }

    private void checkComparator(RedisConnection<Object, ?> connection) {
        String comparatorSign = (String) connection.get(getComparatorKeyName());
        if (comparatorSign != null) {
            String[] vals = comparatorSign.split(":");
            String className = vals[0];
            if (!comparator.getClass().getName().equals(className)) {
                try {
                    loadComparator(connection);
                } finally {
                    connection.unwatch();
                }
            }
        }
    }

    public static double calcIncrement(double value) {
        BigDecimal b = BigDecimal.valueOf(value);
        BigDecimal r = b.remainder(BigDecimal.ONE);
        if (r.compareTo(BigDecimal.ZERO) == 0) {
            return 1;
        }
        double res = 1/Math.pow(10, r.scale());
        return res;
    }

    @Override
    public Future<Boolean> removeAsync(final V value) {
        EventLoop loop = connectionManager.getGroup().next();
        final Promise<Boolean> promise = loop.newPromise();
        
        loop.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean result = remove(value);
                    promise.setSuccess(result);
                } catch (Exception e) {
                    promise.setFailure(e);
                }
            }
        });
        
        return promise;
    }
    
    @Override
    public boolean remove(final Object value) {
        return connectionManager.write(new SyncOperation<V, Boolean>() {
            @Override
            public Boolean execute(RedisConnection<Object, V> conn) {
                return remove(value, conn);
            }
        });
    }

    boolean remove(Object value, RedisConnection<Object, V> conn) {
        while (true) {
            conn.watch(getName());
            BinarySearchResult<V> res = binarySearch((V) value, conn);
            if (res.getIndex() < 0) {
                conn.unwatch();
                return false;
            }
            
            if (res.getIndex() == 0) {
                conn.multi();
                conn.lpop(getName());
                if (conn.exec().size() == 1) {
                    return true;
                }
            }
            
            RedisConnection<Object, Object> сonnection = (RedisConnection<Object, Object>)conn;
            List<Object> tail = сonnection.lrange(getName(), res.getIndex() + 1, size());
            сonnection.multi();
            сonnection.ltrim(getName(), 0, res.getIndex() - 1);
            if (tail.isEmpty()) {
                if (сonnection.exec().size() == 1) {
                    return true;
                }
            } else {
                сonnection.rpush(getName(), tail.toArray());
                if (сonnection.exec().size() == 2) {
                    return true;
                }
            }
        }
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
        boolean changed = false;
        for (V v : c) {
            if (add(v)) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean changed = false;
        for (Iterator iterator = iterator(); iterator.hasNext();) {
            Object object = (Object) iterator.next();
            if (!c.contains(object)) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean changed = false;
        for (Object obj : c) {
            if (remove(obj)) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void clear() {
        delete();
    }

    @Override
    public Comparator<? super V> comparator() {
        return comparator;
    }

    @Override
    public SortedSet<V> subSet(V fromElement, V toElement) {
        throw new UnsupportedOperationException();
//        return new RedissonSubSortedSet<V>(this, connectionManager, fromElement, toElement);
    }

    @Override
    public SortedSet<V> headSet(V toElement) {
        return subSet(null, toElement);
    }

    @Override
    public SortedSet<V> tailSet(V fromElement) {
        return subSet(fromElement, null);
    }

    @Override
    public V first() {
        V res = connectionManager.read(new ResultOperation<V, V>() {
            @Override
            protected Future<V> execute(RedisAsyncConnection<Object, V> async) {
                return async.lindex(getName(), 0);
            }
        });
        if (res == null) {
            throw new NoSuchElementException();
        }
        return res;
    }

    @Override
    public V last() {
        V res = connectionManager.read(new ResultOperation<V, V>() {
            @Override
            protected Future<V> execute(RedisAsyncConnection<Object, V> async) {
                return async.lindex(getName(), -1);
            }
        });
        if (res == null) {
            throw new NoSuchElementException();
        }
        return res;
    }

    private String getScoreKeyName(int index) {
        return "redisson__sortedset__score__" + getName() + "__" + index;
    }

    private String getComparatorKeyName() {
        return "redisson__sortedset__comparator__" + getName();
    }

    @Override
    public boolean trySetComparator(final Comparator<? super V> comparator) {
        return connectionManager.write(new SyncOperation<String, Boolean>() {
            @Override
            public Boolean execute(RedisConnection<Object, String> connection) {
                connection.watch(getName(), getComparatorKeyName());
                if (size(connection) > 0) {
                    connection.unwatch();
                    return false;
                }
                connection.multi();
                
                String className = comparator.getClass().getName();
                String comparatorSign = className + ":" + calcClassSign(className);
                connection.set(getComparatorKeyName(), comparatorSign);
                List<Object> res = connection.exec();
                if (res.size() == 1) {
                    RedissonSortedSet.this.comparator = comparator;
                    return true;
                }
                return false;
            }
        });
    }

    private V getAtIndex(int index, RedisConnection<Object, V> connection) {
        return connection.lindex(getName(), index);
    }

    /**
     * Binary search algorithm
     *
     * @param value
     * @param connection
     * @param lowerIndex
     * @param upperIndex
     * @return
     */
    private BinarySearchResult<V> binarySearch(V value, RedisConnection<Object, V> connection, int lowerIndex, int upperIndex) {
        while (lowerIndex <= upperIndex) {
            int index = lowerIndex + (upperIndex - lowerIndex) / 2;

            V res = getAtIndex(index, connection);
            int cmp = comparator.compare(value, res);

            if (cmp == 0) {
                BinarySearchResult<V> indexRes = new BinarySearchResult<V>();
                indexRes.setIndex(index);
                return indexRes;
            } else if (cmp < 0) {
                upperIndex = index - 1;
            } else {
                lowerIndex = index + 1;
            }
        }

        BinarySearchResult<V> indexRes = new BinarySearchResult<V>();
        indexRes.setIndex(-(lowerIndex + 1));
        return indexRes;
    }

    public BinarySearchResult<V> binarySearch(V value, RedisConnection<Object, V> connection) {
        int upperIndex = size(connection) - 1;
        return binarySearch(value, connection, 0, upperIndex);
    }

    double score(V value, RedisConnection<Object, V> connection, int indexDiff, boolean tail) {
        return -1;
//        BinarySearchResult<V> res = binarySearch(value, connection);
//        if (res.getIndex() < 0) {
//            V element = getAtIndex(-res.getIndex() + indexDiff, connection);
//            if (element.getScore() == null && res.getScore() == null && tail) {
//                element = getAtIndex(-res.getIndex() - 2, connection);
//                return element.getScore();
//            }
//            return element.getScore();
//        }
//        int ind = res.getIndex();
//        if (tail) {
//            ind = res.getIndex() - indexDiff;
//        }
//        BinarySearchResult<V> element = getAtIndex(ind, connection);
//        return element.getScore();
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
