/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.eviction.EvictionScheduler;
import org.redisson.iterator.RedissonBaseIterator;
import org.redisson.mapreduce.RedissonCollectionMapReduce;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * <p>Set-based cache with ability to set TTL for each entry via
 * {@link RSetCache#add(Object, long, TimeUnit)} method.
 * </p>
 *
 * <p>Current Redis implementation doesn't have set entry eviction functionality.
 * Thus values are checked for TTL expiration during any value read operation.
 * If entry expired then it doesn't returns and clean task runs asynchronous.
 * Clean task deletes removes 100 expired entries at once.
 * In addition there is {@link org.redisson.eviction.EvictionScheduler}. This scheduler
 * deletes expired entries in time interval between 5 seconds to 2 hours.</p>
 *
 * <p>If eviction is not required then it's better to use {@link org.redisson.api.RSet}.</p>
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetCache<V> extends RedissonExpirable implements RSetCache<V>, ScanIterator {

    RedissonClient redisson;
    EvictionScheduler evictionScheduler;
    
    public RedissonSetCache(EvictionScheduler evictionScheduler, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name);
        if (evictionScheduler != null) {
            evictionScheduler.schedule(getRawName(), 0);
        }
        this.evictionScheduler = evictionScheduler;
        this.redisson = redisson;
    }

    public RedissonSetCache(Codec codec, EvictionScheduler evictionScheduler, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name);
        if (evictionScheduler != null) {
            evictionScheduler.schedule(getRawName(), 0);
        }
        this.evictionScheduler = evictionScheduler;
        this.redisson = redisson;
    }
    
    @Override
    public <KOut, VOut> RCollectionMapReduce<V, KOut, VOut> mapReduce() {
        return new RedissonCollectionMapReduce<>(this, redisson, commandExecutor);
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public RFuture<Integer> sizeAsync() {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.ZCARD_INT, getRawName());
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return get(containsAsync(o));
    }

    @Override
    public RFuture<Boolean> containsAsync(Object o) {
        String name = getRawName(o);
        return commandExecutor.evalReadAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                    "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); " + 
                     "if expireDateScore ~= false then " +
                         "if tonumber(expireDateScore) <= tonumber(ARGV[1]) then " +
                             "return 0;" + 
                         "else " +
                             "return 1;" +
                         "end;" +
                     "else " +
                         "return 0;" +
                     "end; ",
               Arrays.<Object>asList(name), System.currentTimeMillis(), encode(o));
    }

    @Override
    public ScanResult<Object> scanIterator(String name, RedisClient client, long startPos, String pattern, int count) {
        RFuture<ScanResult<Object>> f = scanIteratorAsync(name, client, startPos, pattern, count);
        return get(f);
    }

    @Override
    public RFuture<ScanResult<Object>> scanIteratorAsync(String name, RedisClient client, long startPos, String pattern, int count) {
        List<Object> params = new ArrayList<Object>();
        params.add(startPos);
        params.add(System.currentTimeMillis());
        if (pattern != null) {
            params.add(pattern);
        }
        params.add(count);
        
        return commandExecutor.evalReadAsync(client, name, codec, RedisCommands.EVAL_ZSCAN,
                  "local result = {}; "
                + "local res; "
                + "if (#ARGV == 4) then "
                  + " res = redis.call('zscan', KEYS[1], ARGV[1], 'match', ARGV[3], 'count', ARGV[4]); "
                + "else "
                  + " res = redis.call('zscan', KEYS[1], ARGV[1], 'count', ARGV[3]); "
                + "end;"
                + "for i, value in ipairs(res[2]) do "
                    + "if i % 2 == 0 then "
                        + "local expireDate = value; "
                        + "if tonumber(expireDate) > tonumber(ARGV[2]) then "
                            + "table.insert(result, res[2][i-1]); "
                        + "end; "
                    + "end;"
                + "end;"
                + "return {res[1], result};", Arrays.<Object>asList(name), params.toArray());
    }

    @Override
    public Iterator<V> iterator(int count) {
        return iterator(null, count);
    }
    
    @Override
    public Iterator<V> iterator(String pattern) {
        return iterator(pattern, 10);
    }
    
    @Override
    public Iterator<V> iterator(final String pattern, final int count) {
        return new RedissonBaseIterator<V>() {

            @Override
            protected ScanResult<Object> iterator(RedisClient client, long nextIterPos) {
                return scanIterator(getRawName(), client, nextIterPos, pattern, count);
            }

            @Override
            protected void remove(Object value) {
                RedissonSetCache.this.remove((V) value);
            }
            
        };
    }
    
    @Override
    public Iterator<V> iterator() {
        return iterator(null);
    }

    @Override
    public Set<V> readAll() {
        return get(readAllAsync());
    }

    @Override
    public RFuture<Set<V>> readAllAsync() {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.ZRANGEBYSCORE, getRawName(), System.currentTimeMillis(), 92233720368547758L);
    }

    @Override
    public Object[] toArray() {
        Set<V> res = get(readAllAsync());
        return res.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        Set<V> res = get(readAllAsync());
        return res.toArray(a);
    }

    @Override
    public boolean add(V e) {
        return get(addAsync(e));
    }

    @Override
    public boolean add(V value, long ttl, TimeUnit unit) {
        return get(addAsync(value, ttl, unit));
    }

    @Override
    public RFuture<Boolean> addAsync(V value, long ttl, TimeUnit unit) {
        if (ttl < 0) {
            throw new IllegalArgumentException("TTL can't be negative");
        }
        if (ttl == 0) {
            return addAsync(value);
        }

        if (unit == null) {
            throw new NullPointerException("TimeUnit param can't be null");
        }

        ByteBuf objectState = encode(value);

        long timeoutDate = System.currentTimeMillis() + unit.toMillis(ttl);
        String name = getRawName(value);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[3]); " +
                "redis.call('zadd', KEYS[1], ARGV[2], ARGV[3]); " +
                "if expireDateScore ~= false and tonumber(expireDateScore) > tonumber(ARGV[1]) then " +
                    "return 0;" +
                "end; " +
                "return 1; ",
                Arrays.asList(name), System.currentTimeMillis(), timeoutDate, objectState);
    }

    @Override
    public boolean tryAdd(V... values) {
        return get(tryAddAsync(values));
    }

    @Override
    public RFuture<Boolean> tryAddAsync(V... values) {
        return tryAddAsync(92233720368547758L - System.currentTimeMillis(), TimeUnit.MILLISECONDS, values);
    }

    @Override
    public boolean tryAdd(long ttl, TimeUnit unit, V... values) {
        return get(tryAddAsync(ttl, unit, values));
    }

    @Override
    public RFuture<Boolean> tryAddAsync(long ttl, TimeUnit unit, V... values) {
        long timeoutDate = System.currentTimeMillis() + unit.toMillis(ttl);
        if (ttl == 0) {
            timeoutDate = 92233720368547758L - System.currentTimeMillis();
        }

        List<Object> params = new ArrayList<>();
        params.add(System.currentTimeMillis());
        params.add(timeoutDate);
        params.addAll(encode(Arrays.asList(values)));

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                  "for i, v in ipairs(ARGV) do " +
                            "local expireDateScore = redis.call('zscore', KEYS[1], v); " +
                            "if expireDateScore ~= false and tonumber(expireDateScore) > tonumber(ARGV[1]) then " +
                                "return 0; " +
                            "end; " +
                        "end; " +

                        "for i=3, #ARGV, 1 do " +
                            "redis.call('zadd', KEYS[1], ARGV[2], ARGV[i]); " +
                        "end; " +
                        "return 1; ",
                       Arrays.asList(getRawName()), params.toArray());
    }

    @Override
    public RFuture<Boolean> addAsync(V value) {
        return addAsync(value, 92233720368547758L - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public RFuture<Boolean> removeAsync(Object o) {
        String name = getRawName(o);
        return commandExecutor.writeAsync(name, codec, RedisCommands.ZREM, name, encode(o));
    }

    @Override
    public boolean remove(Object value) {
        return get(removeAsync((V) value));
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return get(containsAllAsync(c));
    }

    @Override
    public RFuture<Boolean> containsAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return new CompletableFutureWrapper<>(true);
        }
        
        List<Object> params = new ArrayList<Object>(c.size() + 1);
        params.add(System.currentTimeMillis());
        encode(params, c);
        
        return commandExecutor.evalReadAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                            "for j = 2, #ARGV, 1 do "
                            + "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[j]) "
                            + "if expireDateScore ~= false then "
                                + "if tonumber(expireDateScore) <= tonumber(ARGV[1]) then "
                                    + "return 0;"
                                + "end; "
                            + "else "
                                + "return 0;"
                            + "end; "
                        + "end; "
                       + "return 1; ",
                Collections.<Object>singletonList(getRawName()), params.toArray());
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        return get(addAllAsync(c));
    }

    @Override
    public RFuture<Boolean> addAllAsync(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return new CompletableFutureWrapper<>(false);
        }

        long score = 92233720368547758L - System.currentTimeMillis();
        List<Object> params = new ArrayList<Object>(c.size()*2 + 1);
        params.add(getRawName());
        for (V value : c) {
            ByteBuf objectState = encode(value);
            params.add(score);
            params.add(objectState);
        }

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.ZADD_BOOL_RAW, params.toArray());
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return get(retainAllAsync(c));
    }

    @Override
    public RFuture<Boolean> retainAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return deleteAsync();
        }
        
        long score = 92233720368547758L - System.currentTimeMillis();
        List<Object> params = new ArrayList<>(c.size() * 2);
        for (Object object : c) {
            params.add(score);
            encode(params, object);
        }
        
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zadd', KEYS[2], unpack(ARGV)); "
                 + "local prevSize = redis.call('zcard', KEYS[1]); "
                 + "local size = redis.call('zinterstore', KEYS[1], #ARGV/2, KEYS[1], KEYS[2], 'aggregate', 'min');"
                 + "redis.call('del', KEYS[2]); "
                 + "return size ~= prevSize and 1 or 0; ",
             Arrays.<Object>asList(getRawName(), "redisson_temp__{" + getRawName() + "}"), params.toArray());
    }

    @Override
    public RFuture<Boolean> removeAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return new CompletableFutureWrapper<>(false);
        }
        
        List<Object> params = new ArrayList<Object>(c.size()+1);
        params.add(getRawName());
        encode(params, c);

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.ZREM, params.toArray());
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return get(removeAllAsync(c));
    }

    @Override
    public void clear() {
        delete();
    }

    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(V value) {
        String lockName = getLockByValue(value, "permitexpirablesemaphore");
        return new RedissonPermitExpirableSemaphore(commandExecutor, lockName);
    }

    @Override
    public RSemaphore getSemaphore(V value) {
        String lockName = getLockByValue(value, "semaphore");
        return new RedissonSemaphore(commandExecutor, lockName);
    }
    
    @Override
    public RCountDownLatch getCountDownLatch(V value) {
        String lockName = getLockByValue(value, "countdownlatch");
        return new RedissonCountDownLatch(commandExecutor, lockName);
    }
    
    @Override
    public RLock getFairLock(V value) {
        String lockName = getLockByValue(value, "fairlock");
        return new RedissonFairLock(commandExecutor, lockName);
    }
    
    @Override
    public RLock getLock(V value) {
        String lockName = getLockByValue(value, "lock");
        return new RedissonLock(commandExecutor, lockName);
    }
    
    @Override
    public RReadWriteLock getReadWriteLock(V value) {
        String lockName = getLockByValue(value, "rw_lock");
        return new RedissonReadWriteLock(commandExecutor, lockName);
    }

    @Override
    public void destroy() {
        if (evictionScheduler != null) {
            evictionScheduler.remove(getRawName());
        }
    }

    @Override
    public Stream<V> stream(int count) {
        return toStream(iterator(count));
    }

    @Override
    public Stream<V> stream(String pattern, int count) {
        return toStream(iterator(pattern, count));
    }

    @Override
    public Stream<V> stream(String pattern) {
        return toStream(iterator(pattern));
    }

}
