package org.redisson;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedSet;

import org.redisson.connection.ConnectionManager;
import org.redisson.core.RSortedSet;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.ScoredValue;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V>
 */
public class RedissonSortedSet<V> extends RedissonObject implements RSortedSet<V> {

    public static class BinarySearchResult<V> {

        private V value;
        private Double score;
        private int index;

        public BinarySearchResult(V value, double score) {
            super();
            this.value = value;
            this.score = score;
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

        public Double getScore() {
            return score;
        }

    }

    private final ConnectionManager connectionManager;
    private final Comparator<V> comparator = Collections.<V>reverseOrder(Collections.<V>reverseOrder());

    RedissonSortedSet(ConnectionManager connectionManager, String name) {
        super(name);
        this.connectionManager = connectionManager;
    }

    @Override
    public int size() {
        RedisConnection<Object, V> connection = connectionManager.connection();
        try {
            return size(connection);
        } finally {
            connectionManager.release(connection);
        }
    }

    private int size(RedisConnection<Object, V> connection) {
        return connection.zcard(getName()).intValue();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        RedisConnection<Object, V> connection = connectionManager.connection();
        try {
            return binarySearch((V)o, connection).getIndex() >= 0;
        } finally {
            connectionManager.release(connection);
        }
    }

    public Iterator<V> iterator() {
        double startScore;
        RedisConnection<Object, V> connection = connectionManager.connection();
        try {
            startScore = getScoreAtIndex(0, connection);
        } finally {
            connectionManager.release(connection);
        }

        return iterator(startScore, Double.MAX_VALUE);
    }

    public Iterator<V> iterator(final double startScore, final double endScore) {
        return new Iterator<V>() {

            private double currentScore = startScore;
            private boolean removeExecuted;
            private V value;

            @Override
            public boolean hasNext() {
                RedisConnection<Object, V> connection = connectionManager.connection();
                try {
                    Long remains = connection.zcount(getName(), currentScore, endScore);
                    return remains > 0;
                } finally {
                    connectionManager.release(connection);
                }
            }

            @Override
            public V next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No such element at index " + currentScore);
                }
                removeExecuted = false;

                RedisConnection<Object, V> connection = connectionManager.connection();
                try {
                    double lastScore = getScoreAtIndex(-1, connection);
                    double scoreDiff = lastScore - currentScore;

                    Long remains = connection.zcount(getName(), currentScore, lastScore);
                    if (remains == 0) {
                        throw new NoSuchElementException("No more elements!");
                    }
                    // TODO don't load whole set in memory
//                    if (remains < 50) {
                        List<ScoredValue<V>> values = connection.zrangebyscoreWithScores(getName(), currentScore, Double.MAX_VALUE);
                        if (values.isEmpty()) {
                            throw new NoSuchElementException("No more elements!");
                        }

                        ScoredValue<V> val = values.get(0);
                        value = val.value;

                        if (values.size() > 1) {
                            ScoredValue<V> nextVal = values.get(1);
                            currentScore = nextVal.score;
                        } else {
                            currentScore = endScore;
                        }
                        return value;
//                    }
                } finally {
                    connectionManager.release(connection);
                }
            }

            @Override
            public void remove() {
                if (removeExecuted) {
                    throw new IllegalStateException("Element been already deleted");
                }

                RedissonSortedSet.this.remove(value);
                removeExecuted = true;
            }

        };
    }

    @Override
    public Object[] toArray() {
        RedisConnection<Object, Object> connection = connectionManager.connection();
        try {
            return connection.zrange(getName(), 0, -1).toArray();
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        RedisConnection<Object, Object> connection = connectionManager.connection();
        try {
            return connection.zrange(getName(), 0, -1).toArray(a);
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean add(V value) {
        RedisConnection<Object, V> connection = connectionManager.connection();
        try {
            while (true) {
                connection.watch(getName());

                BinarySearchResult<V> res = binarySearch(value, connection);
                if (res.getIndex() < 0) {
                    double score = calcNewScore(res.getIndex(), connection);

                    connection.multi();
                    connection.zadd(getName(), score, value);
                    List<Object> re = connection.exec();
                    if (re.size() == 1) {
                        Object val = re.iterator().next();
                        return val != null && ((Number)val).intValue() > 0;
                    }
                } else {
                    connection.unwatch();
                    return false;
                }
            }
        } finally {
            connectionManager.release(connection);
        }
    }

    /**
     * score for entry added before head = head / 2
     * score for entry added after tail = tail + 1000000
     * score for entry inserted between head and tail = head + (tail - head) / 2
     *
     * @param index
     * @param connection
     * @return score for index
     */
    public double calcNewScore(int index, RedisConnection<Object, V> connection) {
        if (index >= 0) {
            throw new IllegalStateException("index should be negative, but value is " + index);
        }
        index = -(index + 1);

        double score = getScoreAtIndex(index, connection);
        if (index == 0) {
            if (score < 0) {
                score = 1000000;
            } else {
                score /= 2;
            }
        } else {
            double beginScore = getScoreAtIndex(index-1, connection);
            if (score < 0) {
                score = beginScore + 1000000;
            } else {
                score = beginScore + (score - beginScore) / 2;
            }
        }
        return score;
    }

    @Override
    public boolean remove(Object value) {
        RedisConnection<Object, V> connection = connectionManager.connection();
        try {
            BinarySearchResult<V> res = binarySearch((V) value, connection);
            if (res.getIndex() < 0) {
                return false;
            }
            return connection.zremrangebyscore(getName(), res.getScore(), res.getScore()) > 0;
        } finally {
            connectionManager.release(connection);
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
        RedisConnection<Object, Object> connection = connectionManager.connection();
        try {
            connection.del(getName());
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public Comparator<? super V> comparator() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SortedSet<V> subSet(V fromElement, V toElement) {
        return new RedissonSubSortedSet<V>(this, connectionManager, fromElement, toElement);
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
        RedisConnection<Object, V> connection = connectionManager.connection();
        try {
            BinarySearchResult<V> res = getAtIndex(0, connection);
            if (res.getScore() == null) {
                throw new NoSuchElementException();
            }
            return res.getValue();
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public V last() {
        RedisConnection<Object, V> connection = connectionManager.connection();
        try {
            BinarySearchResult<V> res = getAtIndex(-1, connection);
            if (res.getScore() == null) {
                throw new NoSuchElementException();
            }
            return res.getValue();
        } finally {
            connectionManager.release(connection);
        }
    }

    @Override
    public boolean trySetComparator(Comparator<V> comparator) {
        // TODO Auto-generated method stub
        return false;
    }

    private double getScoreAtIndex(int index, RedisConnection<Object, V> connection) {
        List<ScoredValue<V>> res = connection.zrangeWithScores(getName(), index, index);
        if (res.isEmpty()) {
            return -1;
        }
        return res.get(0).score;
    }

    private BinarySearchResult<V> getAtIndex(int index, RedisConnection<Object, V> connection) {
        List<ScoredValue<V>> res = connection.zrangeWithScores(getName(), index, index);
        if (res.isEmpty()) {
            return new BinarySearchResult<V>();
        }
        return new BinarySearchResult<V>(res.get(0).value, res.get(0).score);
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
    public BinarySearchResult<V> binarySearch(V value, RedisConnection<Object, V> connection, int lowerIndex, int upperIndex) {
        while (lowerIndex <= upperIndex) {
            int index = lowerIndex + (upperIndex - lowerIndex) / 2;

            BinarySearchResult<V> indexRes = getAtIndex(index, connection);
            int cmp = comparator.compare(value, indexRes.getValue());

            if (cmp == 0) {
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

    public double score(V value, RedisConnection<Object, V> connection, int indexDiff, boolean tail) {
        BinarySearchResult<V> res = binarySearch(value, connection);
        if (res.getIndex() < 0) {
            BinarySearchResult<V> element = getAtIndex(-res.getIndex() + indexDiff, connection);
            return element.getScore();
        }
        int ind = res.getIndex();
        if (tail) {
            ind = res.getIndex() - indexDiff;
        }
        BinarySearchResult<V> element = getAtIndex(ind, connection);
        return element.getScore();
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
