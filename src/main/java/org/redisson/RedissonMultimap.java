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
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RMultimap;

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
public abstract class RedissonMultimap<K, V> extends RedissonExpirable implements RMultimap<K, V> {

    RedissonMultimap(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    RedissonMultimap(Codec codec, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
    }

    protected String hash(byte[] objectState) {
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
                String name = getValuesName(keyHash);
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
            return RedissonMultimap.this.size();
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
