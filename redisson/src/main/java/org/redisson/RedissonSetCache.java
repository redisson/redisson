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
import io.netty.util.ReferenceCountUtil;
import org.redisson.api.*;
import org.redisson.api.listener.SetAddListener;
import org.redisson.api.listener.SetRemoveListener;
import org.redisson.api.listener.TrackingListener;
import org.redisson.api.mapreduce.RCollectionMapReduce;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.eviction.EvictionScheduler;
import org.redisson.iterator.RedissonBaseIterator;
import org.redisson.mapreduce.RedissonCollectionMapReduce;
import org.redisson.misc.CompletableFutureWrapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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

    final RedissonClient redisson;
    final EvictionScheduler evictionScheduler;

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
        return commandExecutor.evalReadAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                  "local values = redis.call('zrangebyscore', KEYS[1], ARGV[1], ARGV[2]);" +
                        "return #values;",
                Arrays.asList(getRawName()),
                System.currentTimeMillis(), 92233720368547758L);
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
                         "end;" +
                         "return 1;" +
                     "end; " +
                     "return 0;",
               Arrays.<Object>asList(name),
                System.currentTimeMillis(), encode(o));
    }

    @Override
    public ScanResult<Object> scanIterator(String name, RedisClient client, String startPos, String pattern, int count) {
        RFuture<ScanResult<Object>> f = scanIteratorAsync(name, client, startPos, pattern, count);
        return get(f);
    }

    @Override
    public RFuture<ScanResult<Object>> scanIteratorAsync(String name, RedisClient client, String startPos, String pattern, int count) {
        List<Object> params = new ArrayList<>();
        params.add(startPos);
        params.add(System.currentTimeMillis());
        if (pattern != null) {
            params.add(pattern);
        }
        params.add(count);
        
        return commandExecutor.evalReadAsync(client, name, codec, RedisCommands.EVAL_SCAN,
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
                + "return {res[1], result};", Arrays.asList(name), params.toArray());
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
    public Iterator<V> iterator(String pattern, int count) {
        return new RedissonBaseIterator<V>() {

            @Override
            protected ScanResult<Object> iterator(RedisClient client, String nextIterPos) {
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
        removeListeners();
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

    @Override
    public int addAllCounted(Collection<? extends V> c) {
        return get(addAllCountedAsync(c));
    }

    @Override
    public int removeAllCounted(Collection<? extends V> c) {
        return get(removeAllCountedAsync(c));
    }

    @Override
    public Iterator<V> distributedIterator(String pattern) {
        String iteratorName = "__redisson_scored_sorted_set_cursor_{" + getRawName() + "}";
        return distributedIterator(iteratorName, pattern, 10);
    }

    @Override
    public Iterator<V> distributedIterator(int count) {
        String iteratorName = "__redisson_scored_sorted_set_cursor_{" + getRawName() + "}";
        return distributedIterator(iteratorName, null, count);
    }

    @Override
    public Iterator<V> distributedIterator(String iteratorName, String pattern, int count) {
        return new RedissonBaseIterator<V>() {

            @Override
            protected ScanResult<Object> iterator(RedisClient client, String nextIterPos) {
                return distributedScanIterator(iteratorName, pattern, count);
            }

            @Override
            protected void remove(Object value) {
                RedissonSetCache.this.remove(value);
            }
        };
    }

    private ScanResult<Object> distributedScanIterator(String iteratorName, String pattern, int count) {
        return get(distributedScanIteratorAsync(iteratorName, pattern, count));
    }

    private RFuture<ScanResult<Object>> distributedScanIteratorAsync(String iteratorName, String pattern, int count) {
        List<Object> args = new ArrayList<>(2);
        args.add(System.currentTimeMillis());
        if (pattern != null) {
            args.add(pattern);
        }
        args.add(count);

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_SCAN,
                "local cursor = redis.call('get', KEYS[2]); "
                + "if cursor ~= false then "
                    + "cursor = tonumber(cursor); "
                + "else "
                    + "cursor = 0;"
                + "end;"
                + "if cursor == -1 then "
                    + "return {0, {}}; "
                + "end;"
                + "local result; "
                + "if (#ARGV == 3) then "
                    + "result = redis.call('zscan', KEYS[1], cursor, 'match', ARGV[2], 'count', ARGV[3]); "
                + "else "
                    + "result = redis.call('zscan', KEYS[1], cursor, 'count', ARGV[2]); "
                + "end;"
                + "local next_cursor = result[1]"
                + "if next_cursor ~= \"0\" then "
                    + "redis.call('setex', KEYS[2], 3600, next_cursor);"
                + "else "
                    + "redis.call('setex', KEYS[2], 3600, -1);"
                + "end; "
                + "local res = {};"
                + "for i, value in ipairs(result[2]) do "
                    + "if i % 2 == 0 then "
                        + "local expireDate = value; "
                        + "if tonumber(expireDate) > tonumber(ARGV[1]) then "
                            + "table.insert(res, result[2][i-1]); "
                        + "end; "
                    + "end; "
                + "end;"
                + "return {result[1], res};",
                Arrays.asList(getRawName(), iteratorName), args.toArray());
    }

    @Override
    public Set<V> removeRandom(int amount) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V removeRandom() {
        throw new UnsupportedOperationException();
    }

    @Override
    public V random() {
        return get(randomAsync());
    }

    @Override
    public Set<V> random(int count) {
        return get(randomAsync(count));
    }

    @Override
    public boolean move(String destination, V member) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int union(String... names) {
        return get(unionAsync(names));
    }

    @Override
    public Set<V> readUnion(String... names) {
        return get(readUnionAsync(names));
    }

    @Override
    public int diff(String... names) {
        return get(diffAsync(names));
    }

    @Override
    public Set<V> readDiff(String... names) {
        return get(readDiffAsync(names));
    }

    @Override
    public int intersection(String... names) {
        return get(intersectionAsync(names));
    }

    @Override
    public Set<V> readIntersection(String... names) {
        return get(readIntersectionAsync(names));
    }

    @Override
    public Integer countIntersection(String... names) {
        return get(countIntersectionAsync(names));
    }

    @Override
    public Integer countIntersection(int limit, String... names) {
        return get(countIntersectionAsync(limit, names));
    }

    @Override
    public List<V> containsEach(Collection<V> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Set<V>> removeRandomAsync(int amount) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<V> removeRandomAsync() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<V> randomAsync() {
        String tempName = prefixName("__redisson_cache_temp", getRawName());
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_OBJECT,
                  "local values = redis.call('zrangebyscore', KEYS[1], ARGV[1], ARGV[2], 'WITHSCORES');" +
                        "for i = 1, #values, 2 do "
                          + "redis.call('zadd', KEYS[2], values[i], values[i+1]); " +
                        "end;" +
                        "local res = redis.call('zrandmember', KEYS[2]); " +
                        "redis.call('del', KEYS[2]); " +
                        "return res;",
                Arrays.asList(getRawName(), tempName),
                System.currentTimeMillis(), 92233720368547758L);
    }

    @Override
    public RFuture<Set<V>> randomAsync(int count) {
        String tempName = prefixName("__redisson_cache_temp", getRawName());
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_SET,
                  "local values = redis.call('zrangebyscore', KEYS[1], ARGV[1], ARGV[2], 'WITHSCORES');" +
                        "for i = 1, #values, 2 do "
                            + "redis.call('zadd', KEYS[2], values[i], values[i+1]); " +
                        "end;" +
                        "local res = redis.call('zrandmember', KEYS[2], ARGV[3]); " +
                        "redis.call('del', KEYS[2]); " +
                        "return res;",
                Arrays.asList(getRawName(), tempName),
                System.currentTimeMillis(), 92233720368547758L, count);
    }

    @Override
    public RFuture<Boolean> moveAsync(String destination, V member) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Integer> unionAsync(String... names) {
        List<Object> keys = new LinkedList<>();
        keys.add(getRawName());
        keys.addAll(Arrays.asList(names));
        for (Object key : names) {
            String tempName = prefixName("__redisson_cache_temp", key.toString());
            keys.add(tempName);
        }

        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                   "local args = {KEYS[1], (#KEYS-1)/2};" +
                         "for i = 2, (#KEYS-1)/2 + 1, 1 do " +
                             "local values = redis.call('zrangebyscore', KEYS[i], ARGV[1], ARGV[2], 'WITHSCORES');" +
                             "local k = (#KEYS-1)/2 + i; " +
                             "table.insert(args, KEYS[k]); " +
                             "for j = 1, #values, 2 do " +
                                 "redis.call('zadd', KEYS[k], values[j+1], values[j]); " +
                             "end;" +
                        "end; " +
                        "table.insert(args, 'AGGREGATE'); " +
                        "table.insert(args, 'SUM'); " +
                        "local res = redis.call('zunionstore', unpack(args));" +
                        "redis.call('del', unpack(KEYS, (#KEYS-1)/2+2, #KEYS)); " +
                        "return res;",
                        keys,
                System.currentTimeMillis(), 92233720368547758L, names.length+1);
    }

    @Override
    public RFuture<Set<V>> readUnionAsync(String... names) {
        List<Object> keys = new LinkedList<>();
        keys.add(getRawName());
        keys.addAll(Arrays.asList(names));
        for (Object key : new ArrayList<>(keys)) {
            String tempName = prefixName("__redisson_cache_temp", key.toString());
            keys.add(tempName);
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_SET,
                  "for i = 1, #KEYS, 1 do " +
                            "local values = redis.call('zrangebyscore', KEYS[ARGV[3] + i], ARGV[1], ARGV[2], 'WITHSCORES');" +
                            "for j = 1, #values, 2 do "
                              + "redis.call('zadd', KEYS[ARGV[3] + i], values[j], values[j+1]); " +
                            "end;" +
                        "end; " +
                        "local values = redis.call('zunion', ARGV[3], unpack(KEYS, ARGV[3], #ARGV), 'AGGREGATE', 'SUM');" +
                        "redis.call('del', unpack(KEYS, ARGV[3], #KEYS)); " +
                        "return values;",
                keys,
                System.currentTimeMillis(), 92233720368547758L, names.length+1);
    }

    @Override
    public RFuture<Integer> diffAsync(String... names) {
        List<Object> keys = new LinkedList<>();
        keys.add(getRawName());
        keys.addAll(Arrays.asList(names));
        for (Object key : names) {
            String tempName = prefixName("__redisson_cache_temp", key.toString());
            keys.add(tempName);
        }

        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                   "local args = {KEYS[1], (#KEYS-1)/2};" +
                         "for i = 2, (#KEYS-1)/2 + 1, 1 do " +
                             "local values = redis.call('zrangebyscore', KEYS[i], ARGV[1], ARGV[2], 'WITHSCORES');" +
                             "local k = (#KEYS-1)/2 + i; " +
                             "table.insert(args, KEYS[k]); " +
                             "for j = 1, #values, 2 do " +
                                 "redis.call('zadd', KEYS[k], values[j+1], values[j]); " +
                             "end;" +
                        "end; " +
                        "local res = redis.call('zdiffstore', unpack(args));" +
                        "redis.call('del', unpack(KEYS, (#KEYS-1)/2+2, #KEYS)); " +
                        "return res;",
                        keys,
                System.currentTimeMillis(), 92233720368547758L, names.length+1);
    }

    @Override
    public RFuture<Set<V>> readDiffAsync(String... names) {
        List<Object> keys = new LinkedList<>();
        keys.add(getRawName());
        keys.addAll(Arrays.asList(names));
        for (Object key : new ArrayList<>(keys)) {
            String tempName = prefixName("__redisson_cache_temp", key.toString());
            keys.add(tempName);
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_SET,
                "for i = 1, #KEYS, 1 do " +
                        "local values = redis.call('zrangebyscore', KEYS[ARGV[3] + i], ARGV[1], ARGV[2], 'WITHSCORES');" +
                        "for j = 1, #values, 2 do "
                          + "redis.call('zadd', KEYS[ARGV[3] + i], values[j], values[j+1]); " +
                        "end;" +
                     "end; " +
                     "local values = redis.call('zdiff', ARGV[3], unpack(KEYS, ARGV[3], #ARGV));" +
                     "redis.call('del', unpack(KEYS, ARGV[3], #KEYS)); " +
                     "return values;",
                        keys,
                System.currentTimeMillis(), 92233720368547758L, names.length+1);
    }

    @Override
    public RFuture<Integer> intersectionAsync(String... names) {
        List<Object> keys = new LinkedList<>();
        keys.add(getRawName());
        keys.addAll(Arrays.asList(names));
        for (Object key : names) {
            String tempName = prefixName("__redisson_cache_temp", key.toString());
            keys.add(tempName);
        }

        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                   "local args = {KEYS[1], (#KEYS-1)/2};" +
                         "for i = 2, (#KEYS-1)/2 + 1, 1 do " +
                             "local values = redis.call('zrangebyscore', KEYS[i], ARGV[1], ARGV[2], 'WITHSCORES');" +
                             "local k = (#KEYS-1)/2 + i; " +
                             "table.insert(args, KEYS[k]); " +
                             "for j = 1, #values, 2 do " +
                                 "redis.call('zadd', KEYS[k], values[j+1], values[j]); " +
                             "end;" +
                        "end; " +
                        "table.insert(args, 'AGGREGATE'); " +
                        "table.insert(args, 'SUM'); " +
                        "local res = redis.call('zinterstore', unpack(args));" +
                        "redis.call('del', unpack(KEYS, (#KEYS-1)/2+2, #KEYS)); " +
                        "return res;",
                        keys,
                System.currentTimeMillis(), 92233720368547758L, names.length+1);
    }

    @Override
    public RFuture<Set<V>> readIntersectionAsync(String... names) {
        List<Object> keys = new LinkedList<>();
        keys.add(getRawName());
        keys.addAll(Arrays.asList(names));
        for (Object key : new ArrayList<>(keys)) {
            String tempName = prefixName("__redisson_cache_temp", key.toString());
            keys.add(tempName);
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_SET,
                  "for i = 1, #KEYS, 1 do " +
                        "local values = redis.call('zrangebyscore', KEYS[ARGV[3] + i], ARGV[1], ARGV[2], 'WITHSCORES');" +
                           "for j = 1, #values, 2 do "
                            + "redis.call('zadd', KEYS[ARGV[3] + i], values[j], values[j+1]); " +
                           "end;" +
                        "end; " +
                        "local values = redis.call('zinter', ARGV[3], unpack(KEYS, ARGV[3], #ARGV), 'AGGREGATE', 'SUM');" +
                        "redis.call('del', unpack(KEYS, ARGV[3], #KEYS)); " +
                        "return values;",
                         keys,
                System.currentTimeMillis(), 92233720368547758L, names.length+1);
    }

    @Override
    public RFuture<Integer> countIntersectionAsync(String... names) {
        return countIntersectionAsync(0, names);
    }

    @Override
    public RFuture<Integer> countIntersectionAsync(int limit, String... names) {
        List<Object> keys = new LinkedList<>();
        keys.add(getRawName());
        keys.addAll(Arrays.asList(names));
        for (Object key : new ArrayList<>(keys)) {
            String tempName = prefixName("__redisson_cache_temp", key.toString());
            keys.add(tempName);
        }

        return commandExecutor.evalWriteAsync(getRawName(), IntegerCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                   "local args = {ARGV[3]};" +
                         "for i = 1, ARGV[3], 1 do " +
                             "local values = redis.call('zrangebyscore', KEYS[i], ARGV[1], ARGV[2], 'WITHSCORES');" +
                             "local k = tonumber(ARGV[3]) + i; " +
                             "table.insert(args, KEYS[k]); " +
                             "for j = 1, #values, 2 do " +
                                 "redis.call('zadd', KEYS[k], values[j+1], values[j]); " +
                             "end;" +
                        "end; " +
                        "table.insert(args, 'LIMIT'); " +
                        "table.insert(args, ARGV[4]); " +
                        "local res = redis.call('zintercard', unpack(args));" +
                        "redis.call('del', unpack(KEYS, ARGV[3]+1, #KEYS)); " +
                        "return res;",
                         keys,
                System.currentTimeMillis(), 92233720368547758L, names.length+1, limit);
    }

    @Override
    public RFuture<Integer> addAllCountedAsync(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return new CompletableFutureWrapper<>(0);
        }

        List<Object> args = new ArrayList<>(c.size() + 1);
        args.add(getRawName());
        for (V v : c) {
            args.add(92233720368547758L);
            try {
                args.add(v);
            } catch (Exception e) {
                args.forEach(vv -> {
                    ReferenceCountUtil.safeRelease(vv);
                });
                throw e;
            }
        }

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.ZADD_INT, args.toArray());
    }

    @Override
    public RFuture<Integer> removeAllCountedAsync(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return new CompletableFutureWrapper<>(0);
        }

        List<Object> args = new ArrayList<>(c.size() + 1);
        args.add(getRawName());
        encode(args, c);

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.ZREM_INT, args.toArray());
    }

    @Override
    public RFuture<List<V>> containsEachAsync(Collection<V> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<V> readSort(SortOrder order) {
        return get(readSortAsync(order));
    }

    @Override
    public Set<V> readSort(SortOrder order, int offset, int count) {
        return get(readSortAsync(order, offset, count));
    }

    @Override
    public Set<V> readSort(String byPattern, SortOrder order) {
        return get(readSortAsync(byPattern, order));
    }

    @Override
    public Set<V> readSort(String byPattern, SortOrder order, int offset, int count) {
        return get(readSortAsync(byPattern, order, offset, count));
    }

    @Override
    public <T> Collection<T> readSort(String byPattern, List<String> getPatterns, SortOrder order) {
        return get(readSortAsync(byPattern, getPatterns, order));
    }

    @Override
    public <T> Collection<T> readSort(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return get(readSortAsync(byPattern, getPatterns, order, offset, count));
    }

    @Override
    public Set<V> readSortAlpha(SortOrder order) {
        return get(readSortAlphaAsync(order));
    }

    @Override
    public Set<V> readSortAlpha(SortOrder order, int offset, int count) {
        return get(readSortAlphaAsync(order, offset, count));
    }

    @Override
    public Set<V> readSortAlpha(String byPattern, SortOrder order) {
        return get(readSortAlphaAsync(byPattern, order));
    }

    @Override
    public Set<V> readSortAlpha(String byPattern, SortOrder order, int offset, int count) {
        return get(readSortAlphaAsync(byPattern, order, offset, count));
    }

    @Override
    public <T> Collection<T> readSortAlpha(String byPattern, List<String> getPatterns, SortOrder order) {
        return get(readSortAlphaAsync(byPattern, getPatterns, order));
    }

    @Override
    public <T> Collection<T> readSortAlpha(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return get(readSortAlphaAsync(byPattern, getPatterns, order, offset, count));
    }

    @Override
    public int sortTo(String destName, SortOrder order) {
        return get(sortToAsync(destName, order));
    }

    @Override
    public int sortTo(String destName, SortOrder order, int offset, int count) {
        return get(sortToAsync(destName, order, offset, count));
    }

    @Override
    public int sortTo(String destName, String byPattern, SortOrder order) {
        return get(sortToAsync(destName, byPattern, order));
    }

    @Override
    public int sortTo(String destName, String byPattern, SortOrder order, int offset, int count) {
        return get(sortToAsync(destName, byPattern, order, offset, count));
    }

    @Override
    public int sortTo(String destName, String byPattern, List<String> getPatterns, SortOrder order) {
        return get(sortToAsync(destName, byPattern, getPatterns, order));
    }

    @Override
    public int sortTo(String destName, String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return get(sortToAsync(destName, byPattern, getPatterns, order, offset, count));
    }

    @Override
    public RFuture<Set<V>> readSortAsync(SortOrder order) {
        return readSortAsync(null, null, order, -1, -1, false);
    }

    @Override
    public RFuture<Set<V>> readSortAsync(SortOrder order, int offset, int count) {
        return readSortAsync(null, null, order, offset, count, false);
    }

    @Override
    public RFuture<Set<V>> readSortAsync(String byPattern, SortOrder order) {
        return readSortAsync(byPattern, null, order, -1, -1, false);
    }

    @Override
    public RFuture<Set<V>> readSortAsync(String byPattern, SortOrder order, int offset, int count) {
        return readSortAsync(byPattern, null, order, offset, count, false);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order) {
        return readSortAsync(byPattern, getPatterns, order, -1, -1);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return readSortAsync(byPattern, getPatterns, order, offset, count, false);
    }

    private <T> RFuture<T> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count, boolean alpha) {
        throw new UnsupportedOperationException();
//        List<Object> params = new ArrayList<>();
//        params.add(System.currentTimeMillis());
//        params.add(92233720368547758L);
//        if (byPattern != null) {
//            params.add("BY");
//            params.add(byPattern);
//        }
//        if (offset != -1 && count != -1) {
//            params.add("LIMIT");
//        }
//        if (offset != -1) {
//            params.add(offset);
//        }
//        if (count != -1) {
//            params.add(count);
//        }
//        if (getPatterns != null) {
//            for (String pattern : getPatterns) {
//                params.add("GET");
//                params.add(pattern);
//            }
//        }
//        if (alpha) {
//            params.add("ALPHA");
//        }
//        if (order != null) {
//            params.add(order);
//        }
//
//        String tempName = prefixName("__redisson_cache_temp", getRawName());
//        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_SET,
//                "local values = redis.call('zrangebyscore', KEYS[1], ARGV[1], ARGV[2], 'WITHSCORES');" +
//                        "for i = 1, #values, 2 do "
//                        + "redis.call('zadd', KEYS[2], values[i], values[i+1]); " +
//                        "end;" +
//                        "local res = redis.call('sort', KEYS[2], unpack(ARGV, 3, #ARGV)); " +
//                        "redis.call('del', KEYS[2]); " +
//                        "return res;",
//                Arrays.asList(getRawName(), tempName), params.toArray());
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(SortOrder order) {
        return readSortAsync(null, null, order, -1, -1, true);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(SortOrder order, int offset, int count) {
        return readSortAsync(null, null, order, offset, count, true);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(String byPattern, SortOrder order) {
        return readSortAsync(byPattern, null, order, -1, -1, true);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(String byPattern, SortOrder order, int offset, int count) {
        return readSortAsync(byPattern, null, order, offset, count, true);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAlphaAsync(String byPattern, List<String> getPatterns, SortOrder order) {
        return readSortAsync(byPattern, getPatterns, order, -1, -1, true);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAlphaAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return readSortAsync(byPattern, getPatterns, order, offset, count, true);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, SortOrder order) {
        return sortToAsync(destName, null, null, order, -1, -1);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, SortOrder order, int offset, int count) {
        return sortToAsync(destName, null, null, order, offset, count);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, SortOrder order) {
        return sortToAsync(destName, byPattern, null, order, -1, -1);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, SortOrder order, int offset, int count) {
        return sortToAsync(destName, byPattern, null, order, offset, count);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, List<String> getPatterns, SortOrder order) {
        return sortToAsync(destName, byPattern, getPatterns, order, -1, -1);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        throw new UnsupportedOperationException();
//        List<Object> params = new ArrayList<>();
//        params.add(System.currentTimeMillis());
//        params.add(92233720368547758L);
//        String tempName = prefixName("__redisson_cache_temp", getRawName());
//        params.add(tempName);
//        if (byPattern != null) {
//            params.add("BY");
//            params.add(byPattern);
//        }
//        if (offset != -1 && count != -1) {
//            params.add("LIMIT");
//        }
//        if (offset != -1) {
//            params.add(offset);
//        }
//        if (count != -1) {
//            params.add(count);
//        }
//        if (getPatterns != null) {
//            for (String pattern : getPatterns) {
//                params.add("GET");
//                params.add(pattern);
//            }
//        }
//        params.add(order);
//        params.add("STORE");
//        params.add(destName);
//
//        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
//                  "local values = redis.call('zrangebyscore', KEYS[1], ARGV[1], ARGV[2], 'WITHSCORES');" +
//                        "for i = 1, #values, 2 do "
//                        + "redis.call('zadd', KEYS[2], values[i], values[i+1]); " +
//                        "end;" +
//                        "local res = redis.call('sort', unpack(ARGV, 3, #ARGV)); " +
//                        "redis.call('del', KEYS[2]); " +
//                        "return res;",
//                Arrays.asList(getRawName(), tempName), params.toArray());
    }

    @Override
    public boolean addIfAbsent(Duration ttl, V object) {
        return get(addIfAbsentAsync(ttl, object));
    }

    @Override
    public boolean addIfExists(Duration ttl, V object) {
        return get(addIfExistsAsync(ttl, object));
    }

    @Override
    public boolean addIfLess(Duration ttl, V object) {
        return get(addIfLessAsync(ttl, object));
    }

    @Override
    public boolean addIfGreater(Duration ttl, V object) {
        return get(addIfGreaterAsync(ttl, object));
    }

    @Override
    public RFuture<Boolean> addIfAbsentAsync(Duration ttl, V object) {
        long timeoutDate = System.currentTimeMillis() + ttl.toMillis();
        if (ttl.isZero()) {
            timeoutDate = 92233720368547758L - System.currentTimeMillis();
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[3]); " +
                        "if expireDateScore ~= false and tonumber(expireDateScore) > tonumber(ARGV[1]) then " +
                            "return 0; " +
                        "end; " +

                        "redis.call('zadd', KEYS[1], ARGV[2], ARGV[3]); " +
                        "return 1; ",
                Arrays.asList(getRawName()),
                System.currentTimeMillis(), timeoutDate, encode(object));
    }

    @Override
    public RFuture<Boolean> addIfExistsAsync(Duration ttl, V object) {
        long timeoutDate = System.currentTimeMillis() + ttl.toMillis();
        if (ttl.isZero()) {
            timeoutDate = 92233720368547758L - System.currentTimeMillis();
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[3]); " +
                      "if expireDateScore ~= false then " +
                        "if tonumber(expireDateScore) < tonumber(ARGV[1]) then " +
                            "return 0; " +
                        "end; " +
                        "redis.call('zadd', KEYS[1], ARGV[2], ARGV[3]); " +
                        "return 1; " +
                      "end; " +

                      "return 0; ",
                Arrays.asList(getRawName()),
                System.currentTimeMillis(), timeoutDate, encode(object));
    }

    @Override
    public RFuture<Boolean> addIfLessAsync(Duration ttl, V object) {
        long timeoutDate = System.currentTimeMillis() + ttl.toMillis();
        if (ttl.isZero()) {
            timeoutDate = 92233720368547758L - System.currentTimeMillis();
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[3]); " +
                      "if expireDateScore ~= false then " +
                        "if tonumber(expireDateScore) < tonumber(ARGV[1]) or tonumber(ARGV[2]) >= tonumber(expireDateScore) then " +
                            "return 0; " +
                        "end; " +
                        "redis.call('zadd', KEYS[1], ARGV[2], ARGV[3]); " +
                        "return 1; " +
                      "end; " +

                      "return 0; ",
                Arrays.asList(getRawName()),
                System.currentTimeMillis(), timeoutDate, encode(object));
    }

    @Override
    public RFuture<Boolean> addIfGreaterAsync(Duration ttl, V object) {
        long timeoutDate = System.currentTimeMillis() + ttl.toMillis();
        if (ttl.isZero()) {
            timeoutDate = 92233720368547758L - System.currentTimeMillis();
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[3]); " +
                      "if expireDateScore ~= false then " +
                        "if tonumber(expireDateScore) < tonumber(ARGV[1]) or tonumber(ARGV[2]) <= tonumber(expireDateScore) then " +
                            "return 0; " +
                        "end; " +
                        "redis.call('zadd', KEYS[1], ARGV[2], ARGV[3]); " +
                        "return 1; " +
                      "end; " +

                      "return 0; ",
                Arrays.asList(getRawName()),
                System.currentTimeMillis(), timeoutDate, encode(object));
    }

    @Override
    public int addAllIfAbsent(Map<V, Duration> objects) {
        return get(addAllIfAbsentAsync(objects));
    }

    @Override
    public int addAllIfExist(Map<V, Duration> objects) {
        return get(addAllIfExistAsync(objects));
    }

    @Override
    public int addAllIfGreater(Map<V, Duration> objects) {
        return get(addAllIfGreaterAsync(objects));
    }

    @Override
    public int addAllIfLess(Map<V, Duration> objects) {
        return get(addAllIfLessAsync(objects));
    }

    @Override
    public RFuture<Integer> addAllIfAbsentAsync(Map<V, Duration> objects) {
        List<Object> params = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        params.add(currentTime);
        for (Map.Entry<V, Duration> entry : objects.entrySet()) {
            long timeoutDate = currentTime + entry.getValue().toMillis();
            if (entry.getValue().isZero()) {
                timeoutDate = 92233720368547758L - currentTime;
            }
            params.add(timeoutDate);
            encode(params, entry.getKey());
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_INTEGER,
                  "local result = 0; " +
                        "for i=2, #ARGV, 2 do " +
                            "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[i+1]); " +
                            "if expireDateScore == false or tonumber(expireDateScore) <= tonumber(ARGV[1]) then " +
                                "result = result + 1; " +
                                "redis.call('zadd', KEYS[1], ARGV[i], ARGV[i+1]); " +
                            "end; " +
                        "end; " +
                        "return result; ",
                Arrays.asList(getRawName()), params.toArray());
    }

    @Override
    public RFuture<Integer> addAllIfExistAsync(Map<V, Duration> objects) {
        List<Object> params = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        params.add(currentTime);
        for (Map.Entry<V, Duration> entry : objects.entrySet()) {
            long timeoutDate = currentTime + entry.getValue().toMillis();
            if (entry.getValue().isZero()) {
                timeoutDate = 92233720368547758L - currentTime;
            }
            params.add(timeoutDate);
            encode(params, entry.getKey());
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_INTEGER,
                  "local result = 0; " +
                        "for i=2, #ARGV, 2 do " +
                            "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[i+1]); " +
                            "if expireDateScore ~= false and tonumber(expireDateScore) > tonumber(ARGV[1]) then " +
                                "result = result + 1; " +
                                "redis.call('zadd', KEYS[1], ARGV[i], ARGV[i+1]); " +
                            "end; " +
                        "end; " +
                        "return result; ",
                Arrays.asList(getRawName()), params.toArray());
    }

    @Override
    public RFuture<Integer> addAllIfGreaterAsync(Map<V, Duration> objects) {
        List<Object> params = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        params.add(currentTime);
        for (Map.Entry<V, Duration> entry : objects.entrySet()) {
            long timeoutDate = currentTime + entry.getValue().toMillis();
            if (entry.getValue().isZero()) {
                timeoutDate = 92233720368547758L - currentTime;
            }
            params.add(timeoutDate);
            encode(params, entry.getKey());
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_INTEGER,
                  "local result = 0; " +
                        "for i=2, #ARGV, 2 do " +
                            "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[i+1]); " +
                            "if expireDateScore ~= false and tonumber(expireDateScore) > tonumber(ARGV[1]) and tonumber(ARGV[i]) > tonumber(expireDateScore) then " +
                                "result = result + 1; " +
                                "redis.call('zadd', KEYS[1], ARGV[i], ARGV[i+1]); " +
                            "end; " +
                        "end; " +
                        "return result; ",
                Arrays.asList(getRawName()), params.toArray());
    }

    @Override
    public RFuture<Integer> addAllIfLessAsync(Map<V, Duration> objects) {
        List<Object> params = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        params.add(currentTime);
        for (Map.Entry<V, Duration> entry : objects.entrySet()) {
            long timeoutDate = currentTime + entry.getValue().toMillis();
            if (entry.getValue().isZero()) {
                timeoutDate = 92233720368547758L - currentTime;
            }
            params.add(timeoutDate);
            encode(params, entry.getKey());
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_INTEGER,
                  "local result = 0; " +
                        "for i=2, #ARGV, 2 do " +
                            "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[i+1]); " +
                            "if expireDateScore ~= false and tonumber(expireDateScore) > tonumber(ARGV[1]) and tonumber(ARGV[i]) < tonumber(expireDateScore) then " +
                                "result = result + 1; " +
                                "redis.call('zadd', KEYS[1], ARGV[i], ARGV[i+1]); " +
                            "end; " +
                        "end; " +
                        "return result; ",
                Arrays.asList(getRawName()), params.toArray());
    }

    @Override
    public int addAll(Map<V, Duration> objects) {
        return get(addAllAsync(objects));
    }

    @Override
    public RFuture<Integer> addAllAsync(Map<V, Duration> objects) {
        List<Object> params = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        params.add(currentTime);
        for (Map.Entry<V, Duration> entry : objects.entrySet()) {
            long timeoutDate = currentTime + entry.getValue().toMillis();
            if (entry.getValue().isZero()) {
                timeoutDate = 92233720368547758L - currentTime;
            }
            params.add(timeoutDate);
            encode(params, entry.getKey());
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_INTEGER,
                  "local result = 0; " +
                        "for i=2, #ARGV, 2 do " +
                            "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[i+1]); " +
                            "if not (expireDateScore ~= false and tonumber(expireDateScore) > tonumber(ARGV[1])) then " +
                                "result = result + 1; " +
                            "end; " +
                            "redis.call('zadd', KEYS[1], ARGV[i], ARGV[i+1]); " +
                        "end; " +
                        "return result; ",
                Arrays.asList(getRawName()), params.toArray());
    }

    @Override
    public int addListener(ObjectListener listener) {
        if (listener instanceof SetAddListener) {
            return addListener("__keyevent@*:zadd", (SetAddListener) listener, SetAddListener::onAdd);
        }
        if (listener instanceof SetRemoveListener) {
            return addListener("__keyevent@*:zrem", (SetRemoveListener) listener, SetRemoveListener::onRemove);
        }
        if (listener instanceof TrackingListener) {
            return addTrackingListener((TrackingListener) listener);
        }

        return super.addListener(listener);
    }

    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        if (listener instanceof SetAddListener) {
            return addListenerAsync("__keyevent@*:zadd", (SetAddListener) listener, SetAddListener::onAdd);
        }
        if (listener instanceof SetRemoveListener) {
            return addListenerAsync("__keyevent@*:zrem", (SetRemoveListener) listener, SetRemoveListener::onRemove);
        }
        if (listener instanceof TrackingListener) {
            return addTrackingListenerAsync((TrackingListener) listener);
        }

        return super.addListenerAsync(listener);
    }

    @Override
    public void removeListener(int listenerId) {
        removeTrackingListener(listenerId);
        removeListener(listenerId, "__keyevent@*:zadd", "__keyevent@*:zrem");
        super.removeListener(listenerId);
    }

    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        RFuture<Void> f1 = removeTrackingListenerAsync(listenerId);
        RFuture<Void> f2 = removeListenerAsync(listenerId,
                "__keyevent@*:zadd", "__keyevent@*:zrem");
        return new CompletableFutureWrapper<>(CompletableFuture.allOf(f1.toCompletableFuture(), f2.toCompletableFuture()));
    }

}
