/**
 * Copyright 2016 Nikita Koksharov
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RSetCache;

import io.netty.util.concurrent.Future;

/**
 * <p>Set-based cache with ability to set TTL for each entry via
 * {@link #put(Object, Object, long, TimeUnit)} method.
 * </p>
 *
 * <p>Current Redis implementation doesn't have set entry eviction functionality.
 * Thus values are checked for TTL expiration during any value read operation.
 * If entry expired then it doesn't returns and clean task runs asynchronous.
 * Clean task deletes removes 100 expired entries at once.
 * In addition there is {@link org.redisson.EvictionScheduler}. This scheduler
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

    public RedissonSetCache(EvictionScheduler evictionScheduler, CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        evictionScheduler.schedule(getName());
    }

    public RedissonSetCache(Codec codec, EvictionScheduler evictionScheduler, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        evictionScheduler.schedule(getName());
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public Future<Integer> sizeAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.ZCARD_INT, getName());
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
    public Future<Boolean> containsAsync(Object o) {
        return commandExecutor.evalReadAsync(getName(), codec, new RedisStrictCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 5),
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
               Arrays.<Object>asList(getName()), System.currentTimeMillis(), o);
    }

    ListScanResult<V> scanIterator(InetSocketAddress client, long startPos) {
        Future<ListScanResult<V>> f = scanIteratorAsync(client, startPos);
        return get(f);
    }

    public Future<ListScanResult<V>> scanIteratorAsync(InetSocketAddress client, long startPos) {
        return commandExecutor.evalReadAsync(client, getName(), codec, RedisCommands.EVAL_ZSCAN,
                  "local result = {}; "
                + "local res = redis.call('zscan', KEYS[1], ARGV[1]); "
                + "for i, value in ipairs(res[2]) do "
                    + "if i % 2 == 0 then "
                        + "local expireDate = value; "
                        + "if tonumber(expireDate) > tonumber(ARGV[2]) then "
                            + "table.insert(result, res[2][i-1]); "
                        + "end; "
                    + "end;"
                + "end;"
                + "return {res[1], result};", Arrays.<Object>asList(getName()), startPos, System.currentTimeMillis());
    }

    @Override
    public Iterator<V> iterator() {
        return new RedissonBaseIterator<V>() {

            @Override
            ListScanResult<V> iterator(InetSocketAddress client, long nextIterPos) {
                return scanIterator(client, nextIterPos);
            }

            @Override
            void remove(V value) {
                RedissonSetCache.this.remove(value);
            }
            
        };
    }

    @Override
    public Set<V> readAll() {
        return get(readAllAsync());
    }

    @Override
    public Future<Set<V>> readAllAsync() {
        return (Future<Set<V>>)readAllAsync(RedisCommands.ZRANGEBYSCORE);
    }

    private Future<?> readAllAsync(RedisCommand<? extends Collection<?>> command) {
        return commandExecutor.readAsync(getName(), codec, command, getName(), System.currentTimeMillis(), 92233720368547758L);
    }

    
    private Future<List<Object>> readAllasListAsync() {
        return (Future<List<Object>>)readAllAsync(RedisCommands.ZRANGEBYSCORE_LIST);
    }

    @Override
    public Object[] toArray() {
        List<Object> res = get(readAllasListAsync());
        return res.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        List<Object> res = get(readAllasListAsync());
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
        if (ttl < 0) {
            throw new IllegalArgumentException("TTL can't be negative");
        }
        if (ttl == 0) {
            return addAsync(value);
        }

        if (unit == null) {
            throw new NullPointerException("TimeUnit param can't be null");
        }

        byte[] objectState = encode(value);

        long timeoutDate = System.currentTimeMillis() + unit.toMillis(ttl);
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[3]); "
                + "if expireDateScore ~= false and tonumber(expireDateScore) > tonumber(ARGV[1]) then "
                    + "return 0;"
                + "end; " +
                "redis.call('zadd', KEYS[1], ARGV[2], ARGV[3]); " +
                "return 1; ",
                Arrays.<Object>asList(getName()), System.currentTimeMillis(), timeoutDate, objectState);
    }

    private byte[] encode(V value) {
        try {
            return codec.getValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Future<Boolean> addAsync(V value) {
        return addAsync(value, 92233720368547758L - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public Future<Boolean> removeAsync(Object o) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.ZREM, getName(), o);
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
        if (c.isEmpty()) {
            return newSucceededFuture(true);
        }
        
        List<Object> params = new ArrayList<Object>(c.size() + 1);
        params.add(System.currentTimeMillis());
        params.addAll(c);
        
        return commandExecutor.evalReadAsync(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 5, ValueType.OBJECTS),
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
                Collections.<Object>singletonList(getName()), params.toArray());
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

        long score = 92233720368547758L - System.currentTimeMillis();
        List<Object> params = new ArrayList<Object>(c.size()*2 + 1);
        params.add(getName());
        for (V value : c) {
            byte[] objectState = encode(value);
            params.add(score);
            params.add(objectState);
        }

        return commandExecutor.writeAsync(getName(), codec, RedisCommands.ZADD_BOOL_RAW, params.toArray());
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return get(retainAllAsync(c));
    }

    @Override
    public Future<Boolean> retainAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return deleteAsync();
        }
        
        long score = 92233720368547758L - System.currentTimeMillis();
        List<Object> params = new ArrayList<Object>(c.size()*2);
        for (Object object : c) {
            params.add(score);
            params.add(encode((V)object));
        }
        
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zadd', KEYS[2], unpack(ARGV)); "
                 + "local prevSize = redis.call('zcard', KEYS[1]); "
                 + "local size = redis.call('zinterstore', KEYS[1], #ARGV/2, KEYS[1], KEYS[2], 'aggregate', 'min');"
                 + "redis.call('del', KEYS[2]); "
                 + "return size ~= prevSize and 1 or 0; ",
             Arrays.<Object>asList(getName(), "redisson_temp__{" + getName() + "}"), params.toArray());
    }

    @Override
    public Future<Boolean> removeAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return newSucceededFuture(false);
        }
        
        List<Object> params = new ArrayList<Object>(c.size()+1);
        params.add(getName());
        params.addAll(c);

        return commandExecutor.writeAsync(getName(), codec, RedisCommands.ZREM, params.toArray());
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return get(removeAllAsync(c));
    }

    @Override
    public void clear() {
        delete();
    }

}
