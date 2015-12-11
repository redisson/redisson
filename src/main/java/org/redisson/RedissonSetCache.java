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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.client.protocol.convertor.VoidReplayConvertor;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RSetCache;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import net.openhft.hashing.LongHashFunction;

/**
 * <p>Set-based cache with ability to set TTL for each entry via
 * {@link #put(Object, Object, long, TimeUnit)} method.
 * And therefore has an complex lua-scripts inside.
 * Uses map(value_hash, value) to tie with sorted set which contains expiration record for every value with TTL.
 * </p>
 *
 * <p>Current Redis implementation doesn't have set entry eviction functionality.
 * Thus values are checked for TTL expiration during any value read operation.
 * If entry expired then it doesn't returns and clean task runs asynchronous.
 * Clean task deletes removes 100 expired entries at once.
 * In addition there is {@link org.redisson.RedissonEvictionScheduler}. This scheduler
 * deletes expired entries in time interval between 5 seconds to 2 hours.</p>
 *
 * <p>If eviction is not required then it's better to use {@link org.redisson.reactive.RedissonSet}.</p>
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonSetCache<V> extends RedissonExpirable implements RSetCache<V> {

    private static final RedisCommand<Void> ADD_ALL = new RedisCommand<Void>("HMSET", new VoidReplayConvertor());
    private static final RedisCommand<Boolean> EVAL_ADD = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 5);
    private static final RedisCommand<Boolean> EVAL_OBJECTS = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4);
    private static final RedisCommand<Long> EVAL_REMOVE_EXPIRED = new RedisCommand<Long>("EVAL", 5);
    private static final RedisCommand<List<Object>> EVAL_CONTAINS_KEY = new RedisCommand<List<Object>>("EVAL", new ObjectListReplayDecoder<Object>());
    private static final RedisStrictCommand<Boolean> HDEL = new RedisStrictCommand<Boolean>("HDEL", new BooleanReplayConvertor());

    protected RedissonSetCache(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        RedissonEvictionScheduler.INSTANCE.schedule(getName(), getTimeoutSetName(), commandExecutor);
    }

    protected RedissonSetCache(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        RedissonEvictionScheduler.INSTANCE.schedule(getName(), getTimeoutSetName(), commandExecutor);
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public Future<Integer> sizeAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.HLEN, getName());
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return get(containsAsync(o));
    }

    private byte[] hash(Object o) {
        if (o == null) {
            throw new NullPointerException("Value can't be null");
        }
        try {
            byte[] objectState = codec.getValueEncoder().encode(o);
            return hash(objectState);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] hash(byte[] objectState) {
        long h1 = LongHashFunction.farmUo().hashBytes(objectState);
        long h2 = LongHashFunction.xx_r39().hashBytes(objectState);

        return ByteBuffer.allocate((2 * Long.SIZE) / Byte.SIZE)
                .putLong(h1)
                .putLong(h2)
                .array();
    }

    String getTimeoutSetName() {
        return "redisson__timeout__set__{" + getName() + "}";
    }

    @Override
    public Future<Boolean> containsAsync(Object o) {
        Promise<Boolean> result = newPromise();

        byte[] key = hash(o);

        Future<List<Object>> future = commandExecutor.evalReadAsync(getName(), codec, EVAL_CONTAINS_KEY,
                "local value = redis.call('hexists', KEYS[1], KEYS[3]); " +
                "local expireDate = 92233720368547758; " +
                "if value == 1 then " +
                    "local expireDateScore = redis.call('zscore', KEYS[2], KEYS[3]); "
                    + "if expireDateScore ~= false then "
                        + "expireDate = tonumber(expireDateScore) "
                    + "end; " +
                "end;" +
                "return {expireDate, value}; ",
               Arrays.<Object>asList(getName(), getTimeoutSetName(), key));

        addExpireListener(result, future, new BooleanReplayConvertor(), false);

        return result;
    }

    private <T> void addExpireListener(final Promise<T> result, Future<List<Object>> future, final Convertor<T> convertor, final T nullValue) {
        future.addListener(new FutureListener<List<Object>>() {
            @Override
            public void operationComplete(Future<List<Object>> future) throws Exception {
                if (!future.isSuccess()) {
                    result.setFailure(future.cause());
                    return;
                }

                List<Object> res = future.getNow();
                Long expireDate = (Long) res.get(0);
                long currentDate = System.currentTimeMillis();
                if (expireDate <= currentDate) {
                    result.setSuccess(nullValue);
                    expireMap(currentDate);
                    return;
                }

                if (convertor != null) {
                    result.setSuccess((T) convertor.convert(res.get(1)));
                } else {
                    result.setSuccess((T) res.get(1));
                }
            }
        });
    }

    private void expireMap(long currentDate) {
        commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, EVAL_REMOVE_EXPIRED,
                "local expiredKeys = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1], 'limit', 0, 100); "
                        + "if #expiredKeys > 0 then "
                            + "redis.call('zrem', KEYS[2], unpack(expiredKeys)); "
                            + "redis.call('hdel', KEYS[1], unpack(expiredKeys)); "
                        + "end;",
                        Arrays.<Object>asList(getName(), getTimeoutSetName()), currentDate);
    }

    ListScanResult<V> scanIterator(InetSocketAddress client, long startPos) {
        Future<ListScanResult<V>> f = commandExecutor.evalReadAsync(client, getName(), codec, RedisCommands.EVAL_SSCAN,
                  "local result = {}; "
                + "local res = redis.call('hscan', KEYS[1], ARGV[1]); "
                + "for i, value in ipairs(res[2]) do "
                    + "if i % 2 == 0 then "
                        + "local key = res[2][i-1]; "
                        + "local expireDate = redis.call('zscore', KEYS[2], key); "
                        + "if (expireDate == false) or (expireDate ~= false and tonumber(expireDate) > tonumber(ARGV[2])) then "
                            + "table.insert(result, value); "
                        + "end; "
                    + "end; "
                + "end;"
                + "return {res[1], result};", Arrays.<Object>asList(getName(), getTimeoutSetName()), startPos, System.currentTimeMillis());
        return get(f);
    }

    @Override
    public Iterator<V> iterator() {
        return new Iterator<V>() {

            private List<V> firstValues;
            private Iterator<V> iter;
            private InetSocketAddress client;
            private long nextIterPos;

            private boolean currentElementRemoved;
            private boolean removeExecuted;
            private V value;

            @Override
            public boolean hasNext() {
                if (iter == null || !iter.hasNext()) {
                    if (nextIterPos == -1) {
                        return false;
                    }
                    long prevIterPos = nextIterPos;
                    ListScanResult<V> res = scanIterator(client, nextIterPos);
                    client = res.getRedisClient();
                    if (nextIterPos == 0 && firstValues == null) {
                        firstValues = res.getValues();
                    } else if (res.getValues().equals(firstValues)) {
                        return false;
                    }
                    iter = res.getValues().iterator();
                    nextIterPos = res.getPos();
                    if (prevIterPos == nextIterPos && !removeExecuted) {
                        nextIterPos = -1;
                    }
                }
                return iter.hasNext();
            }

            @Override
            public V next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No such element at index");
                }

                value = iter.next();
                currentElementRemoved = false;
                return value;
            }

            @Override
            public void remove() {
                if (currentElementRemoved) {
                    throw new IllegalStateException("Element been already deleted");
                }
                if (iter == null) {
                    throw new IllegalStateException();
                }

                iter.remove();
                RedissonSetCache.this.remove(value);
                currentElementRemoved = true;
                removeExecuted = true;
            }

        };
    }

    private Future<Collection<V>> readAllAsync() {
        final Promise<Collection<V>> result = newPromise();
        Future<List<Object>> future = commandExecutor.evalReadAsync(getName(), codec, new RedisCommand<List<Object>>("EVAL", new ObjectListReplayDecoder<Object>()),
                        "local expireHead = redis.call('zrange', KEYS[2], 0, 0, 'withscores');" +
                        "local keys = redis.call('hkeys', KEYS[1]);" +
                        "local maxDate = ARGV[1]; " +
                        "local minExpireDate = 92233720368547758;" +
                        "if #expireHead == 2 and tonumber(expireHead[2]) <= tonumber(maxDate) then " +
                            "for i, key in pairs(keys) do " +
                                "local expireDate = redis.call('zscore', KEYS[2], key); " +
                                "if expireDate ~= false and tonumber(expireDate) <= tonumber(maxDate) then " +
                                    "minExpireDate = math.min(tonumber(expireDate), minExpireDate); " +
                                    "table.remove(keys, i); " +
                                "end;" +
                            "end;" +
                        "end; " +
                        "return {minExpireDate, redis.call('hmget', KEYS[1], unpack(keys))};",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), System.currentTimeMillis());

        future.addListener(new FutureListener<List<Object>>() {
            @Override
            public void operationComplete(Future<List<Object>> future) throws Exception {
                if (!future.isSuccess()) {
                    result.setFailure(future.cause());
                    return;
                }

                List<Object> res = future.getNow();
                Long expireDate = (Long) res.get(0);
                long currentDate = System.currentTimeMillis();
                if (expireDate <= currentDate) {
                    expireMap(currentDate);
                }

                result.setSuccess((Collection<V>) res.get(1));
            }
        });

        return result;
    }

    @Override
    public Object[] toArray() {
        List<Object> res = (List<Object>) get(readAllAsync());
        return res.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        List<Object> res = (List<Object>) get(readAllAsync());
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
    public Future<Boolean> addAsync(V value, long ttl, TimeUnit unit) {
        if (unit == null) {
            throw new NullPointerException("TimeUnit param can't be null");
        }

        try {
            byte[] objectState = encode(value);
            byte[] key = hash(objectState);

            long timeoutDate = System.currentTimeMillis() + unit.toMillis(ttl);
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "redis.call('zadd', KEYS[2], ARGV[1], KEYS[3]); " +
                            "if redis.call('hexists', KEYS[1], KEYS[3]) == 0 then " +
                            "redis.call('hset', KEYS[1], KEYS[3], ARGV[2]); " +
                            "return 1; " +
                            "end;" +
                            "return 0; ",
                            Arrays.<Object>asList(getName(), getTimeoutSetName(), key), timeoutDate, objectState);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] encode(V value) throws IOException {
        return codec.getValueEncoder().encode(value);
    }

    @Override
    public Future<Boolean> addAsync(V e) {
        byte[] key = hash(e);
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_ADD,
                  "if redis.call('hexists', KEYS[1], KEYS[2]) == 0 then " +
                      "redis.call('hset', KEYS[1], KEYS[2], ARGV[1]); " +
                      "return 1; " +
                  "end; " +
                  "return 0; ",
                Arrays.<Object>asList(getName(), key), e);
    }

    @Override
    public Future<Boolean> removeAsync(Object o) {
        byte[] key = hash(o);
        return commandExecutor.writeAsync(getName(), codec, HDEL, getName(), key);
    }

    @Override
    public boolean remove(Object value) {
        return get(removeAsync((V)value));
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return get(containsAllAsync(c));
    }

    @Override
    public Future<Boolean> containsAllAsync(Collection<?> c) {
        return commandExecutor.evalReadAsync(getName(), codec, EVAL_OBJECTS,
                "local s = redis.call('hvals', KEYS[1]);" +
                        "for i = 0, table.getn(s), 1 do " +
                            "for j = 0, table.getn(ARGV), 1 do "
                            + "if ARGV[j] == s[i] then "
                                + "table.remove(ARGV, j) "
                            + "end "
                        + "end; "
                       + "end;"
                       + "return table.getn(ARGV) == 0 and 1 or 0; ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        return get(addAllAsync(c));
    }

    @Override
    public Future<Boolean> addAllAsync(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return newSucceededFuture(false);
        }

        List<Object> params = new ArrayList<Object>(c.size()*2 + 1);
        params.add(getName());
        try {
            for (V value : c) {
                byte[] objectState = encode(value);
                byte[] key = hash(objectState);
                params.add(key);
                params.add(objectState);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return commandExecutor.writeAsync(getName(), codec, ADD_ALL, params.toArray());
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return get(retainAllAsync(c));
    }

    @Override
    public Future<Boolean> retainAllAsync(Collection<?> c) {
        List<byte[]> params = new ArrayList<byte[]>(c.size());
        for (Object object : c) {
            params.add(hash(object));
        }
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "local keys = redis.call('hkeys', KEYS[1]); " +
                    "local i=1;" +
                    "while i <= #keys do "
                        + "local changed = false;"
                        + "local element = keys[i];"
                        + "for j, argElement in pairs(ARGV) do "
                            + "if argElement == element then "
                                + "changed = true;"
                                + "table.remove(keys, i); "
                                + "table.remove(ARGV, j); "
                                + "break; "
                            + "end; "
                        + "end; " +
                        "if changed == false then " +
                            "i = i + 1 " +
                        "end " +
                    "end " +
                   "if #keys > 0 then "
                       + "for i=1, #keys,5000 do "
                           + "redis.call('hdel', KEYS[1], unpack(keys, i, math.min(i+4999, #keys))); "
                           + "redis.call('zrem', KEYS[2], unpack(keys, i, math.min(i+4999, #keys))); "
                       + "end "
                       + "return 1;"
                   + "end; "
                   + "return 0; ",
                   Arrays.<Object>asList(getName(), getTimeoutSetName()), params.toArray());
    }

    @Override
    public Future<Boolean> removeAllAsync(Collection<?> c) {
        List<Object> params = new ArrayList<Object>(c.size()+1);
        params.add(getName());
        for (Object object : c) {
            params.add(hash(object));
        }

        return commandExecutor.writeAsync(getName(), codec, HDEL, params.toArray());
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
    public Future<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_SINGLE, getName(), getTimeoutSetName());
    }

    @Override
    public Future<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('pexpire', KEYS[2], ARGV[1]); "
                + "return redis.call('pexpire', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), timeUnit.toSeconds(timeToLive));
    }

    @Override
    public Future<Boolean> expireAtAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('pexpireat', KEYS[2], ARGV[1]); "
                + "return redis.call('pexpireat', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), timestamp);
    }

    @Override
    public Future<Boolean> clearExpireAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('persist', KEYS[2]); "
                + "return redis.call('persist', KEYS[1]); ",
                Arrays.<Object>asList(getName(), getTimeoutSetName()));
    }

}
