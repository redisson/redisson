/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;

import org.redisson.client.RedisConnection;
import org.redisson.connection.ConnectionManager;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V>
 */
// TODO compare tail or head value with current in case of absence this value
class RedissonSubSortedSet<V> implements SortedSet<V> {

    private ConnectionManager connectionManager;
    private RedissonSortedSet<V> redissonSortedSet;

    private V headValue;
    private V tailValue;

    RedissonSubSortedSet(RedissonSortedSet<V> redissonSortedSet, ConnectionManager connectionManager, V headValue, V tailValue) {
        super();
        this.headValue = headValue;
        this.tailValue = tailValue;

        this.connectionManager = connectionManager;
        this.redissonSortedSet = redissonSortedSet;
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
//        RedisConnection<Object, V> connection = connectionManager.connectionReadOp(-1);
//        try {
//            double headScore = getHeadScore(connection);
//            double tailScore = getTailScore(connection);
//
//            return connection.zcount(redissonSortedSet.getName(), headScore, tailScore).intValue();
//        } finally {
//            connectionManager.releaseRead(-1, connection);
//        }
    }

    private int getTailScore(RedisConnection connection) {
        throw new UnsupportedOperationException();
//        BinarySearchResult<V> res = redissonSortedSet.binarySearch(tailValue, connection);
//        if (res.getIndex() < 0) {
//            return 0;
//        }
//        return res.getIndex();
    }

    private int getHeadScore(RedisConnection connection) {
        throw new UnsupportedOperationException();
//        BinarySearchResult<V> res = redissonSortedSet.binarySearch(headValue, connection);
//        if (res.getIndex() < 0) {
//            return 0;
//        }
//        return res.getIndex();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
//        RedisConnection<Object, V> connection = connectionManager.connectionReadOp(-1);
//        try {
//            int headScore = getHeadScore(connection);
//            int tailScore = getTailScore(connection);
//
//            BinarySearchResult<V> res = redissonSortedSet.binarySearch((V)o, connection);
//            return res.getIndex() < tailScore && res.getIndex() > headScore;
//        } finally {
//            connectionManager.releaseRead(-1, connection);
//        }
    }

    @Override
    public Iterator<V> iterator() {
        throw new UnsupportedOperationException();
//        RedisConnection<Object, V> connection = connectionManager.connectionReadOp();
//        try {
//            double headScore = getHeadScore(connection);
//            double tailScore = getTailScore(connection);
//            return redissonSortedSet.iterator(headScore, tailScore);
//        } finally {
//            connectionManager.releaseRead(connection);
//        }
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
//        RedisConnection<Object, V> connection = connectionManager.connectionReadOp(-1);
//        try {
//            double headScore = getHeadScore(connection);
//            double tailScore = getTailScore(connection);
//            return connection.zrangebyscore(redissonSortedSet.getName(), headScore, tailScore).toArray();
//        } finally {
//            connectionManager.releaseRead(-1, connection);
//        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
//        RedisConnection<Object, V> connection = connectionManager.connectionReadOp(-1);
//        try {
//            double headScore = getHeadScore(connection);
//            double tailScore = getTailScore(connection);
//            return connection.zrangebyscore(redissonSortedSet.getName(), headScore, tailScore).toArray(a);
//        } finally {
//            connectionManager.releaseRead(-1, connection);
//        }
    }

    @Override
    public boolean add(V e) {
        throw new UnsupportedOperationException();
//        RedisConnection<Object, V> connection = connectionManager.connectionWriteOp();
//        try {
//            double headScore = getHeadScore(connection);
//            double tailScore = getTailScore(connection);
//
//            BinarySearchResult<V> res = redissonSortedSet.binarySearch(e, connection);
//            if (res.getScore() == null) {
//                NewScore score = redissonSortedSet.calcNewScore(res.getIndex(), connection);
//                if (score.getScore() < tailScore && score.getScore() > headScore) {
//                    return redissonSortedSet.add(e, connection);
//                } else {
//                    throw new IllegalArgumentException("value out of range");
//                }
//            }
//            return false;
//        } finally {
//            connectionManager.releaseWrite(connection);
//        }
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
//        RedisConnection<Object, V> connection = connectionManager.connectionWriteOp();
//        try {
//            double headScore = getHeadScore(connection);
//            double tailScore = getTailScore(connection);
//
//            BinarySearchResult<V> res = redissonSortedSet.binarySearch((V)o, connection);
//            if (res.getScore() != null && res.getScore() < tailScore && res.getScore() > headScore) {
//                return redissonSortedSet.remove(o, connection);
//            }
//            return false;
//        } finally {
//            connectionManager.releaseWrite(connection);
//        }
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
        for (Object object : this) {
            if (!c.contains(object)) {
                remove(object);
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
        throw new UnsupportedOperationException();
//        RedisConnection<Object, V> connection = connectionManager.connectionWriteOp();
//        try {
//            // TODO sync
//            int headScore = getHeadScore(connection);
//            int tailScore = getTailScore(connection);
//            connection.ltrim(redissonSortedSet.getName(), 0, index - 1);
//            connection.zremrangebyscore(redissonSortedSet.getName(), headScore, tailScore);
//        } finally {
//            connectionManager.releaseWrite(connection);
//        }
    }

    @Override
    public Comparator<? super V> comparator() {
        return redissonSortedSet.comparator();
    }

    @Override
    public SortedSet<V> subSet(V fromElement, V toElement) {
        throw new UnsupportedOperationException();
        // TODO check bounds
//        if (fromElement == null) {
//            fromElement = headValue;
//        }
//        if (toElement == null) {
//            toElement = tailValue;
//        }
//        return new RedissonSubSortedSet<V>(redissonSortedSet, connectionManager, fromElement, toElement);
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
        throw new UnsupportedOperationException();
//        RedisConnection<Object, V> connection = connectionManager.connectionReadOp();
//        try {
//            // TODO compare first value with headValue
//            if (headValue != null) {
//                BinarySearchResult<V> res = redissonSortedSet.binarySearch(headValue, connection);
//                if (res.getIndex() < 0) {
//                    NewScore headScore = redissonSortedSet.calcNewScore(res.getIndex(), connection);
//                    double tailScore = getTailScore(connection);
//                    List<V> vals = connection.zrangebyscore(redissonSortedSet.getName(), headScore.getScore(), tailScore);
//                    if (vals.isEmpty()) {
//                        throw new NoSuchElementException();
//                    }
//                    return vals.get(0);
//                }
//                return res.getValue();
//            }
//            return redissonSortedSet.first();
//        } finally {
//            connectionManager.releaseRead(connection);
//        }
    }

    @Override
    public V last() {
        throw new UnsupportedOperationException();
//        RedisConnection<Object, V> connection = connectionManager.connectionReadOp();
//        try {
//            // TODO compare last value with headValue
//            if (tailValue != null) {
//                BinarySearchResult<V> res = redissonSortedSet.binarySearch(tailValue, connection);
//                if (res.getIndex() < 0) {
//                    connection.lrange(redissonSortedSet.getName(), index + 1, size());
//                    NewScore tailScore = redissonSortedSet.calcNewScore(res.getIndex(), connection);
//                    double headScore = getHeadScore(connection);
//                    List<V> vals = connection.zrangebyscore(redissonSortedSet.getName(), headScore, tailScore.getScore());
//                    if (vals.isEmpty()) {
//                        throw new NoSuchElementException();
//                    }
//                    return vals.get(0);
//                }
//                return res.getValue();
//            }
//            return redissonSortedSet.last();
//        } finally {
//            connectionManager.releaseRead(connection);
//        }
    }

    @SuppressWarnings("AvoidInlineConditionals")
    @Override
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
