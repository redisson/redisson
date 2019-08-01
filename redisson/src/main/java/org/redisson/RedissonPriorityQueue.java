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

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RPriorityQueue;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonPriorityQueue<V> extends RedissonList<V> implements RPriorityQueue<V> {

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
        private Integer index;

        public BinarySearchResult(V value) {
            super();
            this.value = value;
        }

        public BinarySearchResult() {
        }

        public void setIndex(Integer index) {
            this.index = index;
        }
        public Integer getIndex() {
            return index;
        }

        public V getValue() {
            return value;
        }


    }

    private Comparator<? super V> comparator = NaturalComparator.NATURAL_ORDER;

    CommandExecutor commandExecutor;
    
    RLock lock;
    private RBucket<String> comparatorHolder;

    public RedissonPriorityQueue(CommandExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name, redisson);
        this.commandExecutor = commandExecutor;

        comparatorHolder = redisson.getBucket(getComparatorKeyName(), StringCodec.INSTANCE);
        lock = redisson.getLock("redisson_sortedset_lock:{" + getName() + "}");
        
        loadComparator();
    }

    public RedissonPriorityQueue(Codec codec, CommandExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name, redisson);
        this.commandExecutor = commandExecutor;

        comparatorHolder = redisson.getBucket(getComparatorKeyName(), StringCodec.INSTANCE);
        lock = redisson.getLock("redisson_sortedset_lock:{" + getName() + "}");

        loadComparator();
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
        return binarySearch((V) o, codec).getIndex() >= 0;
    }

    @Override
    public boolean add(V value) {
        lock.lock();
        
        try {
            checkComparator();
    
            BinarySearchResult<V> res = binarySearch(value, codec);
            int index = 0;
            if (res.getIndex() < 0) {
                index = -(res.getIndex() + 1);
            } else {
                index = res.getIndex() + 1;
            }
                
            commandExecutor.evalWrite(getName(), RedisCommands.EVAL_VOID, 
               "local len = redis.call('llen', KEYS[1]);"
                + "if tonumber(ARGV[1]) < len then "
                    + "local pivot = redis.call('lindex', KEYS[1], ARGV[1]);"
                    + "redis.call('linsert', KEYS[1], 'before', pivot, ARGV[2]);"
                    + "return;"
                + "end;"
                + "redis.call('rpush', KEYS[1], ARGV[2]);", 
                    Arrays.<Object>asList(getName()), 
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
            
            BinarySearchResult<V> res = binarySearch((V) value, codec);
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

    public RFuture<V> pollAsync() {
        return pollAsync(RedisCommands.LPOP, getName());
    }

    protected <T> RFuture<V> pollAsync(RedisCommand<T> command, Object... params) {
        long threadId = Thread.currentThread().getId();
        RPromise<V> result = new RedissonPromise<V>();
        lock.lockAsync(threadId).onComplete((r, exc) -> {
            if (exc != null) {
                result.tryFailure(exc);
                return;
            }
            
            RFuture<V> f = commandExecutor.writeAsync(getName(), codec, command, params);
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

//    @Override
    public RFuture<V> peekAsync() {
        return getAsync(0);
    }

    @Override
    public V peek() {
        return getValue(0);
    }

    private String getComparatorKeyName() {
        return suffixName(getName(), "redisson_sortedset_comparator");
    }

    @Override
    public boolean trySetComparator(Comparator<? super V> comparator) {
        String className = comparator.getClass().getName();
        String comparatorSign = className + ":" + calcClassSign(className);

        Boolean res = commandExecutor.evalWrite(getName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('llen', KEYS[1]) == 0 then "
                + "redis.call('set', KEYS[2], ARGV[1]); "
                + "return 1; "
                + "else "
                + "return 0; "
                + "end",
                Arrays.<Object>asList(getName(), getComparatorKeyName()), comparatorSign);
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
    public BinarySearchResult<V> binarySearch(V value, Codec codec) {
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

    public RFuture<V> pollLastAndOfferFirstToAsync(String queueName) {
        return pollAsync(RedisCommands.RPOPLPUSH, getName(), queueName);
    }

}
