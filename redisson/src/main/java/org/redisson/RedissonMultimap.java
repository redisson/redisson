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

import io.netty.buffer.ByteBuf;
import org.redisson.api.*;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.codec.CompositeCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.iterator.RedissonBaseMapIterator;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.Hash;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public abstract class RedissonMultimap<K, V> extends RedissonExpirable implements RMultimap<K, V> {

    final String prefix;
    
    RedissonMultimap(CommandAsyncExecutor commandAsyncExecutor, String name) {
        super(commandAsyncExecutor, name);
        prefix = suffixName(getRawName(), "");
    }

    RedissonMultimap(Codec codec, CommandAsyncExecutor commandAsyncExecutor, String name) {
        super(codec, commandAsyncExecutor, name);
        prefix = suffixName(getRawName(), "");
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "local keys = redis.call('hgetall', KEYS[1]); " +
                "local size = 0; " +
                "for i, v in ipairs(keys) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[1] .. v; " +
                        "size = size + redis.call('memory', 'usage', name); " +
                    "end;" +
                "end; " +
                "return size; ", Arrays.asList(getRawName()), prefix);
    }

    @Override
    public RLock getFairLock(K key) {
        String lockName = getLockByMapKey(key, "fairlock");
        return new RedissonFairLock(commandExecutor, lockName);
    }
    
    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(K key) {
        String lockName = getLockByMapKey(key, "permitexpirablesemaphore");
        return new RedissonPermitExpirableSemaphore(commandExecutor, lockName);
    }
    
    @Override
    public RCountDownLatch getCountDownLatch(K key) {
        String lockName = getLockByMapKey(key, "countdownlatch");
        return new RedissonCountDownLatch(commandExecutor, lockName);
    }
    
    @Override
    public RSemaphore getSemaphore(K key) {
        String lockName = getLockByMapKey(key, "semaphore");
        return new RedissonSemaphore(commandExecutor, lockName);
    }
    
    @Override
    public RLock getLock(K key) {
        String lockName = getLockByMapKey(key, "lock");
        return new RedissonLock(commandExecutor, lockName);
    }
    
    @Override
    public RReadWriteLock getReadWriteLock(K key) {
        String lockName = getLockByMapKey(key, "rw_lock");
        return new RedissonReadWriteLock(commandExecutor, lockName);
    }
    
    protected String hash(ByteBuf objectState) {
        return Hash.hash128toBase64(objectState);
    }

    protected String keyHash(Object key) {
        ByteBuf objectState = encodeMapKey(key);
        try {
            return Hash.hash128toBase64(objectState);
        } finally {
            objectState.release();
        }
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
        return suffixName(getRawName(), hash);
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
    public Set<K> readAllKeySet() {
        return get(readAllKeySetAsync());
    }

    @Override
    public RFuture<Set<K>> readAllKeySetAsync() {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.HKEYS, getRawName());
    }

    @Override
    public long fastRemove(K... keys) {
        return get(fastRemoveAsync(keys));
    }

    @Override
    public RFuture<Long> fastRemoveAsync(K... keys) {
        if (keys == null || keys.length == 0) {
            return new CompletableFutureWrapper<>(0L);
        }

        List<Object> mapKeys = new ArrayList<Object>(keys.length);
        List<Object> listKeys = new ArrayList<Object>(keys.length + 1);
        listKeys.add(getRawName());
        for (K key : keys) {
            ByteBuf keyState = encodeMapKey(key);
            mapKeys.add(keyState);
            String keyHash = hash(keyState);
            String name = getValuesName(keyHash);
            listKeys.add(name);
        }

        return fastRemoveAsync(mapKeys, listKeys, RedisCommands.EVAL_LONG);
    }

    protected <T> RFuture<T> fastRemoveAsync(List<Object> mapKeys, List<Object> listKeys, RedisCommand<T> evalCommandType) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, evalCommandType,
                    "local res = redis.call('hdel', KEYS[1], unpack(ARGV)); " +
                    "if res > 0 then " +
                        "redis.call('del', unpack(KEYS, 2, #KEYS)); " +
                    "end; " +
                    "return res; ",
                    listKeys, mapKeys.toArray());
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN_AMOUNT,
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "local keys = {KEYS[1]}; " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[1] .. v; " + 
                        "table.insert(keys, name); " +
                    "end;" +
                "end; " +
                
                "local n = 0 "
                + "for i=1, #keys,5000 do "
                    + "n = n + redis.call('del', unpack(keys, i, math.min(i+4999, table.getn(keys)))) "
                + "end; "
                + "return n;",
                Arrays.<Object>asList(getRawName()), prefix);
    }

    @Override
    public RFuture<Void> renameAsync(String newName) {
        String newPrefix = suffixName(newName, "");
        RFuture<Void> future = commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "local keys = {}; " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "table.insert(keys, v); " +
                    "end;" +
                "end; " +

                "redis.call('rename', KEYS[1], ARGV[3]); "
              + "for i=1, #keys, 1 do "
                  + "redis.call('rename', ARGV[1] .. keys[i], ARGV[2] .. keys[i]); "
              + "end; ",
                Arrays.asList(getRawName()), prefix, newPrefix, newName);
        CompletionStage<Void> f = future.thenAccept(r -> setName(newName));
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Boolean> renamenxAsync(String newName) {
        String newPrefix = suffixName(newName, "");
        RFuture<Boolean> future = commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "local keys = {}; " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "table.insert(keys, v); " +
                    "end;" +
                "end; " +

                "local r = redis.call('exists', ARGV[3]);" +
                "if r == 1 then " +
                    "return 0;" +
                "end; " +
                "for i=1, #keys, 1 do " +
                    "local r = redis.call('exists', ARGV[2] .. keys[i]);" +
                    "if r == 1 then " +
                        "return 0;" +
                    "end; " +
                "end; " +

                "redis.call('rename', KEYS[1], ARGV[3]); "
              + "for i=1, #keys, 1 do "
                  + "redis.call('rename', ARGV[1] .. keys[i], ARGV[2] .. keys[i]); "
              + "end; " +
                "return 1; ",
                Arrays.asList(getRawName()), prefix, newPrefix, newName);
        CompletionStage<Boolean> f = future.thenApply(value -> {
            if (value) {
                setName(newName);
            }
            return value;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[2] .. v; "
                      + "if ARGV[3] ~= '' then "
                          + "redis.call('pexpire', name, ARGV[1], ARGV[3]); "
                      + "else "
                          + "redis.call('pexpire', name, ARGV[1]); "
                      + "end; "
                  + "end;" +
                "end; "
              + "if ARGV[3] ~= '' then "
                  + "return redis.call('pexpire', KEYS[1], ARGV[1], ARGV[3]); "
              + "end; "
              + "return redis.call('pexpire', KEYS[1], ARGV[1]); ",
                Arrays.asList(getRawName()),
                timeUnit.toMillis(timeToLive), prefix, param);
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
          "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[2] .. v; "
                      + "if ARGV[3] ~= '' then "
                          + "redis.call('pexpireat', name, ARGV[1], ARGV[3]); "
                      + "else "
                          + "redis.call('pexpireat', name, ARGV[1]); "
                      + "end; "
                  + "end;"
              + "end; "
              + "if ARGV[3] ~= '' then "
                  + "return redis.call('pexpireat', KEYS[1], ARGV[1], ARGV[3]); "
              + "end; "
              + "return redis.call('pexpireat', KEYS[1], ARGV[1]); ",
                Arrays.asList(getRawName()),
                timestamp, prefix, param);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local entries = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(entries) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[1] .. v; " + 
                        "redis.call('persist', name); " +
                    "end;" +
                "end; " +
                "return redis.call('persist', KEYS[1]); ",
                Arrays.<Object>asList(getRawName()),
                prefix);
    }
    
    @Override
    public RFuture<Integer> keySizeAsync() {
        return commandExecutor.readAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.HLEN, getRawName());
    }
    
    
    MapScanResult<Object, Object> scanIterator(RedisClient client, long startPos) {
        RFuture<MapScanResult<Object, Object>> f = commandExecutor.readAsync(client, getRawName(), new CompositeCodec(codec, StringCodec.INSTANCE, codec), RedisCommands.HSCAN, getRawName(), startPos);
        return get(f);
    }

    abstract Iterator<V> valuesIterator();

    abstract RedissonMultiMapIterator<K, V, Entry<K, V>> entryIterator();

    final class KeySet extends AbstractSet<K> {

        @Override
        public Iterator<K> iterator() {
            return new RedissonBaseMapIterator<K>() {
                @Override
                protected K getValue(java.util.Map.Entry<Object, Object> entry) {
                    return (K) entry.getKey();
                }

                @Override
                protected Object put(Entry<Object, Object> entry, Object value) {
                    return RedissonMultimap.this.put((K) entry.getKey(), (V) value);
                }

                @Override
                protected ScanResult<Entry<Object, Object>> iterator(RedisClient client, long nextIterPos) {
                    return RedissonMultimap.this.scanIterator(client, nextIterPos);
                }

                @Override
                protected void remove(Entry<Object, Object> value) {
                    RedissonMultimap.this.fastRemove((K) value.getKey());
                }


            };
        }

        @Override
        public boolean contains(Object o) {
            return RedissonMultimap.this.containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            return RedissonMultimap.this.fastRemove((K) o) == 1;
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

    final class EntrySet extends AbstractSet<Map.Entry<K, V>> {

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return entryIterator();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return containsEntry(e.getKey(), e.getValue());
        }

        @Override
        public boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                Object key = e.getKey();
                Object value = e.getValue();
                return RedissonMultimap.this.remove(key, value);
            }
            return false;
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


}
