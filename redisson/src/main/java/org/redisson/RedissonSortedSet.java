/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import io.netty.buffer.ByteBuf;
import org.redisson.api.*;
import org.redisson.api.mapreduce.RCollectionMapReduce;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.iterator.RedissonBaseIterator;
import org.redisson.mapreduce.RedissonCollectionMapReduce;
import org.redisson.misc.CompletableFutureWrapper;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonSortedSet<V> extends RedissonExpirable implements RSortedSet<V> {

    public static class BinarySearchResult<V> {

        private V value;
        private int index = -1;

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

    private Comparator comparator = Comparator.naturalOrder();

    private RLock lock;
    private RedissonList<V> list;
    private RBucket<String> comparatorHolder;
    private RedissonClient redisson;

    protected RedissonSortedSet(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name);
        this.redisson = redisson;

        comparatorHolder = new RedissonBucket<>(getComparatorKeyName(), StringCodec.INSTANCE, commandExecutor);
        lock = new RedissonLock(getLockName(), commandExecutor);
        list = (RedissonList<V>) redisson.<V>getList(getName(), codec);
    }

    public RedissonSortedSet(Codec codec, CommandAsyncExecutor commandExecutor, String name, Redisson redisson) {
        super(codec, commandExecutor, name);

        comparatorHolder = new RedissonBucket<>(getComparatorKeyName(), StringCodec.INSTANCE, commandExecutor);
        lock = new RedissonLock(getLockName(), commandExecutor);
        list = (RedissonList<V>) redisson.<V>getList(getName(), codec);
    }
    
    @Override
    public <KOut, VOut> RCollectionMapReduce<V, KOut, VOut> mapReduce() {
        return new RedissonCollectionMapReduce<V, KOut, VOut>(this, redisson, commandExecutor);
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
    public Collection<V> readAll() {
        return get(readAllAsync());
    }

    @Override
    public RFuture<Collection<V>> readAllAsync() {
        return (RFuture<Collection<V>>) (Object) list.readAllAsync();
    }

    protected final <T> RFuture<V> wrapLockedAsync(RedisCommand<T> command, Object... params) {
        return wrapLockedAsync(() -> {
            return commandExecutor.writeAsync(list.getRawName(), codec, command, params);
        });
    }

    protected final <T, R> RFuture<R> wrapLockedAsync(Supplier<RFuture<R>> callable) {
        long randomId = getServiceManager().getRandom().nextLong();
        CompletionStage<R> f = lock.lockAsync(randomId).thenCompose(r -> {
            RFuture<R> callback = callable.get();
            return callback.handle((value, ex) -> {
                CompletableFuture<R> result = new CompletableFuture<>();
                lock.unlockAsync(randomId)
                        .whenComplete((r2, ex2) -> {
                            if (ex2 != null) {
                                if (ex != null) {
                                    ex2.addSuppressed(ex);
                                }
                                result.completeExceptionally(ex2);
                                return;
                            }
                            if (ex != null) {
                                result.completeExceptionally(ex);
                                return;
                            }
                            result.complete(value);
                        });
                return result;
            }).thenCompose(ff -> ff);
        });
        return new CompletableFutureWrapper<>(f);
    }

    protected <T> void takeAsync(CompletableFuture<V> result, long delay, long timeoutInMicro, RedisCommand<T> command, Object... params) {
        if (result.isDone()) {
            return;
        }

        long start = System.currentTimeMillis();
        getServiceManager().newTimeout(t -> {
            if (result.isDone()) {
                return;
            }

            RFuture<V> future = wrapLockedAsync(command, params);
            future.whenComplete((res, e) -> {
                if (e != null && !(e instanceof RedisConnectionException)) {
                    result.completeExceptionally(e);
                    return;
                }

                if (res != null && !(res instanceof List)) {
                    result.complete(res);
                    return;
                }

                if (res instanceof List && !((List) res).isEmpty()) {
                    result.complete(res);
                    return;
                }

                if (result.isCancelled()) {
                    return;
                }

                long remain = 0;
                if (timeoutInMicro > 0) {
                    remain = timeoutInMicro - ((System.currentTimeMillis() - start))*1000;
                    if (remain <= 0) {
                        result.complete(res);
                        return;
                    }
                }

                long del = ThreadLocalRandom.current().nextInt(2000000);
                if (timeoutInMicro > 0 && remain < 2000000) {
                    del = 0;
                }

                takeAsync(result, del, remain, command, params);
            });
        }, delay, TimeUnit.MICROSECONDS);
    }

    @Override
    public V pollFirst() {
        return get(pollFirstAsync());
    }

    @Override
    public RFuture<V> pollFirstAsync() {
        return wrapLockedAsync(RedisCommands.LPOP, list.getRawName());
    }

    @Override
    public Collection<V> pollFirst(int count) {
        return get(pollFirstAsync(count));
    }

    @Override
    public RFuture<Collection<V>> pollFirstAsync(int count) {
        return (RFuture<Collection<V>>) wrapLockedAsync(RedisCommands.LPOP_LIST, list.getRawName(), count);
    }

    @Override
    public V pollFirst(Duration duration) {
        return get(pollFirstAsync(duration));
    }

    @Override
    public RFuture<V> pollFirstAsync(Duration duration) {
        CompletableFuture<V> result = new CompletableFuture<V>();
        takeAsync(result, 0, duration.toMillis() * 1000, RedisCommands.LPOP, list.getRawName());
        return new CompletableFutureWrapper<>(result);
    }

    @Override
    public List<V> pollFirst(Duration duration, int count) {
        return get(pollFirstAsync(duration, count));
    }

    @Override
    public RFuture<List<V>> pollFirstAsync(Duration duration, int count) {
        CompletableFuture<V> result = new CompletableFuture<>();
        takeAsync(result, 0, duration.toMillis() * 1000, RedisCommands.LPOP_LIST, list.getRawName(), count);
        return new CompletableFutureWrapper<>((CompletableFuture<List<V>>) result);
    }

    @Override
    public V pollLast() {
        return get(pollLastAsync());
    }

    @Override
    public RFuture<V> pollLastAsync() {
        return wrapLockedAsync(RedisCommands.RPOP, list.getRawName());
    }

    @Override
    public Collection<V> pollLast(int count) {
        return get(pollLastAsync(count));
    }

    @Override
    public RFuture<Collection<V>> pollLastAsync(int count) {
        return (RFuture<Collection<V>>) wrapLockedAsync(RedisCommands.RPOP_LIST, list.getRawName(), count);
    }

    @Override
    public V pollLast(Duration duration) {
        return get(pollLastAsync(duration));
    }

    @Override
    public RFuture<V> pollLastAsync(Duration duration) {
        CompletableFuture<V> result = new CompletableFuture<V>();
        takeAsync(result, 0, duration.toMillis() * 1000, RedisCommands.RPOP, list.getRawName());
        return new CompletableFutureWrapper<>(result);
    }

    @Override
    public List<V> pollLast(Duration duration, int count) {
        return get(pollLastAsync(duration, count));
    }

    @Override
    public RFuture<List<V>> pollLastAsync(Duration duration, int count) {
        CompletableFuture<V> result = new CompletableFuture<>();
        takeAsync(result, 0, duration.toMillis() * 1000, RedisCommands.RPOP_LIST, list.getRawName(), count);
        return new CompletableFutureWrapper<>((CompletableFuture<List<V>>) result);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return binarySearch((V) o, codec).getIndex() >= 0;
    }

    @Override
    public Iterator<V> iterator() {
        return list.iterator();
    }

    @Override
    public Object[] toArray() {
        return list.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return list.toArray(a);
    }

    @Override
    public boolean add(V value) {
        lock.lock();
        
        try {
            checkComparator();
    
            BinarySearchResult<V> res = binarySearch(value, codec);
            if (res.getIndex() < 0) {
                int index = -(res.getIndex() + 1);
                
                ByteBuf encodedValue = encode(value);
                
                commandExecutor.get(commandExecutor.evalWriteNoRetryAsync(list.getRawName(), codec, RedisCommands.EVAL_VOID,
                   "local len = redis.call('llen', KEYS[1]);"
                    + "if tonumber(ARGV[1]) < len then "
                        + "local pivot = redis.call('lindex', KEYS[1], ARGV[1]);"
                        + "redis.call('linsert', KEYS[1], 'before', pivot, ARGV[2]);"
                        + "return;"
                    + "end;"
                    + "redis.call('rpush', KEYS[1], ARGV[2]);", Arrays.<Object>asList(list.getRawName()), index, encodedValue));
                return true;
            } else {
                return false;
            }
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
    public RFuture<Boolean> addAsync(V value) {
        CompletableFuture<Boolean> f = CompletableFuture.supplyAsync(() -> add(value), getServiceManager().getExecutor());
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Boolean> removeAsync(Object value) {
        CompletableFuture<Boolean> f = CompletableFuture.supplyAsync(() -> remove(value), getServiceManager().getExecutor());
        return new CompletableFutureWrapper<>(f);
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

            list.remove((int) res.getIndex());
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
            Object object = iterator.next();
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
        V res = list.getValue(0);
        if (res == null) {
            throw new NoSuchElementException();
        }
        return res;
    }

    @Override
    public V last() {
        V res = list.getValue(-1);
        if (res == null) {
            throw new NoSuchElementException();
        }
        return res;
    }

    private String getLockName() {
        return prefixName("redisson_sortedset_lock", getRawName());
    }

    private String getComparatorKeyName() {
        return prefixName("redisson_sortedset_comparator", getRawName());
    }

    @Override
    public boolean trySetComparator(Comparator<? super V> comparator) {
        String className = comparator.getClass().getName();
        final String comparatorSign = className + ":" + calcClassSign(className);

        Boolean res = commandExecutor.get(commandExecutor.evalWriteAsync(list.getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('llen', KEYS[1]) == 0 then "
                + "redis.call('set', KEYS[2], ARGV[1]); "
                + "return 1; "
                + "else "
                + "return 0; "
                + "end",
                Arrays.asList(list.getRawName(), getComparatorKeyName()), comparatorSign));
        if (res) {
            this.comparator = comparator;
        }
        return res;
    }

    @Override
    public Iterator<V> distributedIterator(final int count) {
        String iteratorName = "__redisson_sorted_set_cursor_{" + getRawName() + "}";
        return distributedIterator(iteratorName, count);
    }

    @Override
    public Iterator<V> distributedIterator(final String iteratorName, final int count) {
        return new RedissonBaseIterator<V>() {

            @Override
            protected ScanResult<Object> iterator(RedisClient client, String nextIterPos) {
                return distributedScanIterator(iteratorName, count);
            }

            @Override
            protected void remove(Object value) {
                RedissonSortedSet.this.remove(value);
            }
        };
    }

    private ScanResult<Object> distributedScanIterator(String iteratorName, int count) {
        return get(distributedScanIteratorAsync(iteratorName, count));
    }

    private RFuture<ScanResult<Object>> distributedScanIteratorAsync(String iteratorName, int count) {
        return commandExecutor.evalWriteAsync(list.getRawName(), codec, RedisCommands.EVAL_SCAN,
                "local start_index = redis.call('get', KEYS[2]); "
                + "if start_index ~= false then "
                    + "start_index = tonumber(start_index); "
                + "else "
                    + "start_index = 0;"
                + "end;"
                + "if start_index == -1 then "
                    + "return {'0', {}}; "
                + "end;"
                + "local end_index = start_index + ARGV[1];"
                + "local result; "
                + "result = redis.call('lrange', KEYS[1], start_index, end_index - 1); "
                + "if end_index > redis.call('llen', KEYS[1]) then "
                    + "end_index = -1;"
                + "end; "
                + "redis.call('setex', KEYS[2], 3600, end_index);"
                + "return {tostring(end_index), result};",
                Arrays.asList(list.getRawName(), iteratorName), count);
    }

    // TODO optimize: get three values each time instead of single
    public BinarySearchResult<V> binarySearch(V value, Codec codec) {
        int size = list.size();
        int upperIndex = size - 1;
        int lowerIndex = 0;
        while (lowerIndex <= upperIndex) {
            int index = lowerIndex + (upperIndex - lowerIndex) / 2;

            V res = list.getValue(index);
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
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), getComparatorKeyName(), getLockName());
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return super.expireAsync(timeToLive, timeUnit, param, getRawName(), getComparatorKeyName(), getLockName());
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return super.expireAtAsync(timestamp, param, getRawName(), getComparatorKeyName(), getLockName());
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), getComparatorKeyName(), getLockName());
    }

}
