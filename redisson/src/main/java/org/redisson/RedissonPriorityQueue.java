/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import org.redisson.api.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonPriorityQueue<V> extends RedissonList<V> implements RPriorityQueue<V> {

    public static class BinarySearchResult<V> {

        private V value;
        private int index = -1;

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

    private Comparator comparator = Comparator.naturalOrder();

    CommandAsyncExecutor commandExecutor;
    
    RLock lock;
    private RBucket<String> comparatorHolder;

    public RedissonPriorityQueue(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name, redisson);
        this.commandExecutor = commandExecutor;

        comparatorHolder = redisson.getBucket(getComparatorKeyName(), StringCodec.INSTANCE);
        lock = redisson.getLock("redisson_sortedset_lock:{" + getRawName() + "}");
    }

    public RedissonPriorityQueue(Codec codec, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name, redisson);
        this.commandExecutor = commandExecutor;

        comparatorHolder = redisson.getBucket(getComparatorKeyName(), StringCodec.INSTANCE);
        lock = redisson.getLock("redisson_sortedset_lock:{" + getRawName() + "}");
    }

    private void loadComparator() {
        try {
            String comparatorSign = comparatorHolder.get();
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
            } else {
                throw new IllegalStateException("Comparator is not set!");
            }
        } catch (IllegalStateException e) {
            throw e;
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
    public boolean offer(V e) {
        return add(e);
    }

    @Override
    public boolean contains(Object o) {
        checkComparator();
        return binarySearch((V) o).getIndex() >= 0;
    }

    @Override
    public boolean add(V value) {
        lock.lock();
        
        try {
            checkComparator();
    
            BinarySearchResult<V> res = binarySearch(value);
            int index = 0;
            if (res.getIndex() < 0) {
                index = -(res.getIndex() + 1);
            } else {
                index = res.getIndex() + 1;
            }
                
            commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_VOID,
               "local len = redis.call('llen', KEYS[1]);"
                + "if tonumber(ARGV[1]) < len then "
                    + "local pivot = redis.call('lindex', KEYS[1], ARGV[1]);"
                    + "redis.call('linsert', KEYS[1], 'before', pivot, ARGV[2]);"
                    + "return;"
                + "end;"
                + "redis.call('rpush', KEYS[1], ARGV[2]);", 
                    Arrays.<Object>asList(getRawName()),
                    index, encode(value));
            return true;
        } finally {
            lock.unlock();
        }
    }

    private void checkComparator() {
        String comparatorSign = comparatorHolder.get();
        if (comparatorSign != null) {
            String[] vals = comparatorSign.split(":");
            String className = vals[0];
            if (!comparator.getClass().getName().equals(className)) {
                loadComparator();
            }
        }
    }

    @Override
    public boolean remove(Object value) {
        lock.lock();

        try {
            checkComparator();
            
            BinarySearchResult<V> res = binarySearch((V) value);
            if (res.getIndex() < 0) {
                return false;
            }

            remove((int) res.getIndex());
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        checkComparator();
        for (Object object : c) {
            if (binarySearch((V) object).getIndex() < 0) {
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
        for (Iterator<?> iterator = iterator(); iterator.hasNext();) {
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
    public RFuture<V> pollAsync() {
        return wrapLockedAsync(RedisCommands.LPOP, getRawName());
    }

    protected <T> RFuture<V> wrapLockedAsync(RedisCommand<T> command, Object... params) {
        return wrapLockedAsync(() -> {
            return commandExecutor.writeAsync(getRawName(), codec, command, params);
        });
    };

    protected final <T, R> RFuture<R> wrapLockedAsync(Supplier<RFuture<R>> callable) {
        long threadId = Thread.currentThread().getId();
        RPromise<R> result = new RedissonPromise<R>();
        lock.lockAsync(threadId).onComplete((r, exc) -> {
            if (exc != null) {
                result.tryFailure(exc);
                return;
            }

            RFuture<R> f = callable.get();
            f.onComplete((value, e) -> {
                if (e != null) {
                    result.tryFailure(e);
                    return;
                }
                
                lock.unlockAsync(threadId).onComplete((res, ex) -> {
                    if (ex != null) {
                        result.tryFailure(ex);
                        return;
                    }
                    
                    result.trySuccess(value);
                });
            });
        });
        return result;
    }

    public V getFirst() {
        V value = getValue(0);
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public V poll() {
        return get(pollAsync());
    }

    @Override
    public V element() {
        return getFirst();
    }

    @Override
    public RFuture<V> peekAsync() {
        return getAsync(0);
    }

    @Override
    public V peek() {
        return getValue(0);
    }

    private String getComparatorKeyName() {
        return suffixName(getRawName(), "redisson_sortedset_comparator");
    }

    @Override
    public boolean trySetComparator(Comparator<? super V> comparator) {
        String className = comparator.getClass().getName();
        String comparatorSign = className + ":" + calcClassSign(className);

        Boolean res = get(commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.SETNX, getComparatorKeyName(), comparatorSign));
        if (res) {
            this.comparator = comparator;
        }
        return res;
    }
    
    @Override
    public V remove() {
        return removeFirst();
    }

    public V removeFirst() {
        V value = poll();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }
    
    // TODO optimize: get three values each time instead of single
    public BinarySearchResult<V> binarySearch(V value) {
        int size = size();
        int upperIndex = size - 1;
        int lowerIndex = 0;
        while (lowerIndex <= upperIndex) {
            int index = lowerIndex + (upperIndex - lowerIndex) / 2;

            V res = getValue(index);
            if (res == null) {
                return new BinarySearchResult<V>();
            }
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

    @Override
    @SuppressWarnings("AvoidInlineConditionals")
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
    
    @Override
    public V pollLastAndOfferFirstTo(String queueName) {
        return get(pollLastAndOfferFirstToAsync(queueName));
    }

    @Override
    public RFuture<V> pollLastAndOfferFirstToAsync(String queueName) {
        return wrapLockedAsync(RedisCommands.RPOPLPUSH, getRawName(), queueName);
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), getComparatorKeyName());
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return expireAsync(timeToLive, timeUnit, getRawName(), getComparatorKeyName());
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String... keys) {
        return super.expireAtAsync(timestamp, getRawName(), getComparatorKeyName());
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), getComparatorKeyName());
    }

    @Override
    public List<V> poll(int limit) {
        return get(pollAsync(limit));
    }

    @Override
    public RFuture<Boolean> offerAsync(V e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> addAsync(V e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<List<V>> pollAsync(int limit) {
        return wrapLockedAsync(() -> {
            return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_LIST,
                       "local result = {};"
                     + "for i = 1, ARGV[1], 1 do " +
                           "local value = redis.call('lpop', KEYS[1]);" +
                           "if value ~= false then " +
                               "table.insert(result, value);" +
                           "else " +
                               "return result;" +
                           "end;" +
                       "end; " +
                       "return result;",
                    Collections.singletonList(getRawName()), limit);
        });
    }
}
