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
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.ScanCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.BooleanAmountReplayConvertor;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RSetMultiMap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import net.openhft.hashing.LongHashFunction;

/**
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonSetMultiMap<K, V> extends RedissonExpirable implements RSetMultiMap<K, V> {

    private static final RedisStrictCommand<Boolean> SCARD_VALUE = new RedisStrictCommand<Boolean>("SCARD", new BooleanAmountReplayConvertor());
    private static final RedisCommand<Boolean> SISMEMBER_VALUE = new RedisCommand<Boolean>("SISMEMBER", new BooleanReplayConvertor());

    RedissonSetMultiMap(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    RedissonSetMultiMap(Codec codec, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
    }

    private String hash(byte[] objectState) {
        long h1 = LongHashFunction.farmUo().hashBytes(objectState);
        long h2 = LongHashFunction.xx_r39().hashBytes(objectState);

        ByteBuf buf = Unpooled.buffer((2 * Long.SIZE) / Byte.SIZE).writeLong(h1).writeLong(h2);

        ByteBuf b = Base64.encode(buf);
        String s = b.toString(CharsetUtil.UTF_8);
        b.release();
        buf.release();
        return s.substring(0, s.length() - 2);
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    public Future<Integer> sizeAsync() {
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_INTEGER,
                "local keys = redis.call('hgetall', KEYS[1]); " +
                "local size = 0; " +
                "for i, v in ipairs(keys) do " +
                    "if i % 2 == 0 then " +
                        "local name = '{' .. KEYS[1] .. '}:' .. v; " +
                        "size = size + redis.call('scard', name); " +
                    "end;" +
                "end; " +
                "return size; ",
                Arrays.<Object>asList(getName()));
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(containsKeyAsync(key));
    }

    public Future<Boolean> containsKeyAsync(Object key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);

            String setName = getSetName(keyHash);
            return commandExecutor.readAsync(getName(), codec, SCARD_VALUE, setName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean containsValue(Object value) {
        return get(containsValueAsync(value));
    }

    public Future<Boolean> containsValueAsync(Object value) {
        try {
            byte[] valueState = codec.getMapValueEncoder().encode(value);

            return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "local keys = redis.call('hgetall', KEYS[1]); " +
                    "for i, v in ipairs(keys) do " +
                        "if i % 2 == 0 then " +
                            "local name = '{' .. KEYS[1] .. '}:' .. v; " +
                            "if redis.call('sismember', name, ARGV[1]) == 1 then "
                                + "return 1; " +
                            "end;" +
                        "end;" +
                    "end; " +
                    "return 0; ",
                    Arrays.<Object>asList(getName()), valueState);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean containsEntry(Object key, Object value) {
        return get(containsEntryAsync(key, value));
    }

    public Future<Boolean> containsEntryAsync(Object key, Object value) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            byte[] valueState = codec.getMapValueEncoder().encode(value);

            String setName = getSetName(keyHash);
            return commandExecutor.readAsync(getName(), codec, SISMEMBER_VALUE, setName, valueState);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean put(K key, V value) {
        return get(putAsync(key, value));
    }

    public Future<Boolean> putAsync(K key, V value) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            byte[] valueState = codec.getMapValueEncoder().encode(value);

            String setName = getSetName(keyHash);
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                    "return redis.call('sadd', KEYS[2], ARGV[3]); ",
                Arrays.<Object>asList(getName(), setName), keyState, keyHash, valueState);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    String getSetName(String hash) {
        return "{" + getName() + "}:" + hash;
    }

    @Override
    public boolean remove(Object key, Object value) {
        return get(removeAsync(key, value));
    }

    public Future<Boolean> removeAsync(Object key, Object value) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            byte[] valueState = codec.getMapValueEncoder().encode(value);

            String setName = getSetName(keyHash);
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "local res = redis.call('srem', KEYS[2], ARGV[2]); "
                  + "if res == 1 and redis.call('scard', KEYS[2]) == 0 then "
                      + "redis.call('hdel', KEYS[1], ARGV[1]); "
                  + "end; "
                  + "return res; ",
                Arrays.<Object>asList(getName(), setName), keyState, valueState);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean putAll(K key, Iterable<? extends V> values) {
        return get(putAllAsync(key, values));
    }

    public Future<Boolean> putAllAsync(K key, Iterable<? extends V> values) {
        try {
            List<Object> params = new ArrayList<Object>();
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            params.add(keyState);
            String keyHash = hash(keyState);
            params.add(keyHash);
            for (Object value : values) {
                byte[] valueState = codec.getMapValueEncoder().encode(value);
                params.add(valueState);
            }

            String setName = getSetName(keyHash);
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN_AMOUNT,
                    "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                    "return redis.call('sadd', KEYS[2], unpack(ARGV, 3, #ARGV)); ",
                Arrays.<Object>asList(getName(), setName), params.toArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void clear() {
        delete();
    }

    @Override
    public Set<K> keySet() {
        return new KeySet();
    }

    @Override
    public Collection<V> values() {
        return new Values();
    }

    @Override
    public Set<V> get(K key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            String setName = getSetName(keyHash);

            return new RedissonSet<V>(codec, commandExecutor, setName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<V> getAll(K key) {
        return (Set<V>) get(getAllAsync(key));
    }

    public Future<Collection<V>> getAllAsync(K key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            String setName = getSetName(keyHash);

            return commandExecutor.readAsync(getName(), codec, RedisCommands.SMEMBERS, setName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<V> removeAll(Object key) {
        return (Set<V>) get(removeAllAsync(key));
    }

    public Future<Collection<V>> removeAllAsync(Object key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);

            String setName = getSetName(keyHash);
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_SET,
                    "redis.call('hdel', KEYS[1], ARGV[1]); " +
                    "local members = redis.call('smembers', KEYS[2]); " +
                    "redis.call('del', KEYS[2]); " +
                    "return members; ",
                Arrays.<Object>asList(getName(), setName), keyState);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<V> replaceValues(K key, Iterable<? extends V> values) {
        return (Set<V>) get(replaceValuesAsync(key, values));
    }

    public Future<Collection<V>> replaceValuesAsync(K key, Iterable<? extends V> values) {
        try {
            List<Object> params = new ArrayList<Object>();
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            params.add(keyState);
            String keyHash = hash(keyState);
            params.add(keyHash);
            for (Object value : values) {
                byte[] valueState = codec.getMapValueEncoder().encode(value);
                params.add(valueState);
            }

            String setName = getSetName(keyHash);
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_SET,
                    "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                    "local members = redis.call('smembers', KEYS[2]); " +
                    "redis.call('del', KEYS[2]); " +
                    "redis.call('sadd', KEYS[2], unpack(ARGV, 3, #ARGV)); " +
                    "return members; ",
                Arrays.<Object>asList(getName(), setName), params.toArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public Set<Entry<K, V>> entries() {
        return new EntrySet();
    }

    public long fastRemove(K ... keys) {
        return get(fastRemoveAsync(keys));
    }

    public Future<Long> fastRemoveAsync(K ... keys) {
        if (keys == null || keys.length == 0) {
            return newSucceededFuture(0L);
        }

        try {
            List<Object> args = new ArrayList<Object>(keys.length*2);
            List<Object> hashes = new ArrayList<Object>();
            for (K key : keys) {
                byte[] keyState = codec.getMapKeyEncoder().encode(key);
                args.add(keyState);
                String keyHash = hash(keyState);
                String name = getSetName(keyHash);
                hashes.add(name);
            }
            args.addAll(hashes);

            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_LONG,
                        "local res = redis.call('hdel', KEYS[1], unpack(ARGV, 1, #ARGV/2)); " +
                        "if res > 0 then " +
                            "redis.call('del', unpack(ARGV, #ARGV/2, #ARGV)); " +
                        "end; " +
                        "return res; ",
                            Collections.<Object>singletonList(getName()), args.toArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    MapScanResult<ScanObjectEntry, ScanObjectEntry> scanIterator(InetSocketAddress client, long startPos) {
        Future<MapScanResult<ScanObjectEntry, ScanObjectEntry>> f = commandExecutor.readAsync(client, getName(), new ScanCodec(codec, StringCodec.INSTANCE), RedisCommands.HSCAN, getName(), startPos);
        return get(f);
    }

    final class KeySet extends AbstractSet<K> {

        @Override
        public Iterator<K> iterator() {
            return new RedissonMultiMapKeysIterator<K, V, K>(RedissonSetMultiMap.this) {
                @Override
                K getValue(java.util.Map.Entry<ScanObjectEntry, ScanObjectEntry> entry) {
                    return (K) entry.getKey().getObj();
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            return RedissonSetMultiMap.this.containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            return RedissonSetMultiMap.this.fastRemove((K)o) == 1;
        }

        @Override
        public int size() {
            return RedissonSetMultiMap.this.size();
        }

        @Override
        public void clear() {
            RedissonSetMultiMap.this.clear();
        }

    }

    final class Values extends AbstractCollection<V> {

        @Override
        public Iterator<V> iterator() {
            return new RedissonMultiMapIterator<K, V, V>(RedissonSetMultiMap.this, commandExecutor, codec) {
                @Override
                V getValue(V entry) {
                    return (V) entry;
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            return RedissonSetMultiMap.this.containsValue(o);
        }

        @Override
        public int size() {
            return RedissonSetMultiMap.this.size();
        }

        @Override
        public void clear() {
            RedissonSetMultiMap.this.clear();
        }

    }

    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {

        public final Iterator<Map.Entry<K,V>> iterator() {
            return new RedissonMultiMapIterator<K, V, Map.Entry<K, V>>(RedissonSetMultiMap.this, commandExecutor, codec);
        }

        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>) o;
            return containsEntry(e.getKey(), e.getValue());
        }

        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry<?,?> e = (Map.Entry<?,?>) o;
                Object key = e.getKey();
                Object value = e.getValue();
                return RedissonSetMultiMap.this.remove(key, value);
            }
            return false;
        }

        public final int size() {
            return RedissonSetMultiMap.this.size();
        }

        public final void clear() {
            RedissonSetMultiMap.this.clear();
        }

    }


}
