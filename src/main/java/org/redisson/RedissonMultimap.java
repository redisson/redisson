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
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.ScanCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RMultimap;
import org.redisson.misc.Hash;

import io.netty.util.concurrent.Future;

/**
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public abstract class RedissonMultimap<K, V> extends RedissonExpirable implements RMultimap<K, V> {

    RedissonMultimap(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    RedissonMultimap(Codec codec, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
    }

    protected String hash(byte[] objectState) {
        return Hash.hashToBase64(objectState);
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }
    
    @Override
    public int keySize() {
    	return get(keySizeAsync());
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(containsKeyAsync(key));
    }

    @Override
    public boolean containsValue(Object value) {
        return get(containsValueAsync(value));
    }

    @Override
    public boolean containsEntry(Object key, Object value) {
        return get(containsEntryAsync(key, value));
    }

    @Override
    public boolean put(K key, V value) {
        return get(putAsync(key, value));
    }

    String getValuesName(String hash) {
        return "{" + getName() + "}:" + hash;
    }

    @Override
    public boolean remove(Object key, Object value) {
        return get(removeAsync(key, value));
    }

    @Override
    public boolean putAll(K key, Iterable<? extends V> values) {
        return get(putAllAsync(key, values));
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
    public Collection<V> getAll(K key) {
        return get(getAllAsync(key));
    }

    @Override
    public Collection<V> removeAll(Object key) {
        return get(removeAllAsync(key));
    }

    @Override
    public Collection<V> replaceValues(K key, Iterable<? extends V> values) {
        return get(replaceValuesAsync(key, values));
    }

    @Override
    public Collection<Entry<K, V>> entries() {
        return new EntrySet();
    }

    @Override
    public long fastRemove(K ... keys) {
        return get(fastRemoveAsync(keys));
    }

    @Override
    public Future<Long> fastRemoveAsync(K ... keys) {
        if (keys == null || keys.length == 0) {
            return newSucceededFuture(0L);
        }

        try {
            List<Object> mapKeys = new ArrayList<Object>(keys.length);
            List<Object> listKeys = new ArrayList<Object>(keys.length + 1);
            listKeys.add(getName());
            for (K key : keys) {
                byte[] keyState = codec.getMapKeyEncoder().encode(key);
                mapKeys.add(keyState);
                String keyHash = hash(keyState);
                String name = getValuesName(keyHash);
                listKeys.add(name);
            }

            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_LONG,
                        "local res = redis.call('hdel', KEYS[1], unpack(ARGV)); " +
                        "if res > 0 then " +
                            "redis.call('del', unpack(KEYS, 2, #KEYS)); " +
                        "end; " +
                        "return res; ",
                        listKeys, mapKeys.toArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public Future<Boolean> deleteAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN_AMOUNT,
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "local keys = {KEYS[1]}; " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = '{' .. KEYS[1] .. '}:' .. v; " + 
                        "table.insert(keys, name); " +
                    "end;" +
                "end; " +
                
                "local n = 0 "
                + "for i=1, #keys,5000 do "
                    + "n = n + redis.call('del', unpack(keys, i, math.min(i+4999, table.getn(keys)))) "
                + "end; "
                + "return n;",
                Arrays.<Object>asList(getName()));
    }

    @Override
    public Future<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = '{' .. KEYS[1] .. '}:' .. v; " + 
                        "redis.call('pexpire', name, ARGV[1]); " +
                    "end;" +
                "end; " +
                "return redis.call('pexpire', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(getName()), timeUnit.toMillis(timeToLive));
    }

    @Override
    public Future<Boolean> expireAtAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = '{' .. KEYS[1] .. '}:' .. v; " + 
                        "redis.call('pexpireat', name, ARGV[1]); " +
                    "end;" +
                "end; " +
                "return redis.call('pexpireat', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(getName()), timestamp);
    }

    @Override
    public Future<Boolean> clearExpireAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = '{' .. KEYS[1] .. '}:' .. v; " + 
                        "redis.call('persist', name); " +
                    "end;" +
                "end; " +
                "return redis.call('persist', KEYS[1]); ",
                Arrays.<Object>asList(getName()));
    }
    
    public Future<Integer> keySizeAsync() {
    	return commandExecutor.readAsync(getName(), LongCodec.INSTANCE, RedisCommands.HLEN, getName());
    }
    
    
    MapScanResult<ScanObjectEntry, ScanObjectEntry> scanIterator(InetSocketAddress client, long startPos) {
        Future<MapScanResult<ScanObjectEntry, ScanObjectEntry>> f = commandExecutor.readAsync(client, getName(), new ScanCodec(codec, StringCodec.INSTANCE), RedisCommands.HSCAN, getName(), startPos);
        return get(f);
    }

    abstract Iterator<V> valuesIterator();

    abstract RedissonMultiMapIterator<K, V, Entry<K, V>> entryIterator();

    final class KeySet extends AbstractSet<K> {

        @Override
        public Iterator<K> iterator() {
            return new RedissonMultiMapKeysIterator<K, V, K>(RedissonMultimap.this) {
                @Override
                K getValue(java.util.Map.Entry<ScanObjectEntry, ScanObjectEntry> entry) {
                    return (K) entry.getKey().getObj();
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            return RedissonMultimap.this.containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            return RedissonMultimap.this.fastRemove((K)o) == 1;
        }

        @Override
        public int size() {
            return RedissonMultimap.this.keySize();
        }

        @Override
        public void clear() {
            RedissonMultimap.this.clear();
        }

    }

    final class Values extends AbstractCollection<V> {

        @Override
        public Iterator<V> iterator() {
            return valuesIterator();
        }

        @Override
        public boolean contains(Object o) {
            return RedissonMultimap.this.containsValue(o);
        }

        @Override
        public int size() {
            return RedissonMultimap.this.size();
        }

        @Override
        public void clear() {
            RedissonMultimap.this.clear();
        }

    }

    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {

        public final Iterator<Map.Entry<K,V>> iterator() {
            return entryIterator();
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
                return RedissonMultimap.this.remove(key, value);
            }
            return false;
        }

        public final int size() {
            return RedissonMultimap.this.size();
        }

        public final void clear() {
            RedissonMultimap.this.clear();
        }

    }


}
