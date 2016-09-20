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

import java.math.BigDecimal;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.LocalCachedMapOptions.EvictionPolicy;
import org.redisson.api.RFuture;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.NumberConvertor;
import org.redisson.client.protocol.decoder.ObjectMapEntryReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectSetReplayDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.Cache;
import org.redisson.misc.Hash;
import org.redisson.misc.LFUCacheMap;
import org.redisson.misc.LRUCacheMap;
import org.redisson.misc.NoneCacheMap;
import org.redisson.misc.RPromise;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.ThreadLocalRandom;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonLocalCachedMap<K, V> extends RedissonMap<K, V> implements RLocalCachedMap<K, V> {

    public static class LocalCachedMapClear {
        
    }
    
    public static class LocalCachedMapInvalidate {
        
        private byte[] excludedId;
        private byte[] keyHash;

        public LocalCachedMapInvalidate() {
        }
        
        public LocalCachedMapInvalidate(byte[] excludedId, byte[] keyHash) {
            super();
            this.keyHash = keyHash;
            this.excludedId = excludedId;
        }
        
        public byte[] getExcludedId() {
            return excludedId;
        }
        
        public byte[] getKeyHash() {
            return keyHash;
        }
        
    }
    
    public static class CacheKey {
        
        private final byte[] keyHash;

        public CacheKey(byte[] keyHash) {
            super();
            this.keyHash = keyHash;
        }

        public byte[] getKeyHash() {
            return keyHash;
        }
        
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(keyHash);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CacheKey other = (CacheKey) obj;
            if (!Arrays.equals(keyHash, other.keyHash))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "CacheKey [keyHash=" + Arrays.toString(keyHash) + "]";
        }
        
    }
    
    public static class CacheValue {
        
        private final Object key;
        private final Object value;
        
        public CacheValue(Object key, Object value) {
            super();
            this.key = key;
            this.value = value;
        }
        
        public Object getKey() {
            return key;
        }
        
        public Object getValue() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CacheValue other = (CacheValue) obj;
            if (value == null) {
                if (other.value != null)
                    return false;
            } else if (!value.equals(other.value))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "CacheValue [key=" + key + ", value=" + value + "]";
        }
        
    }

    private static final RedisCommand<Set<Object>> ALL_KEYS = new RedisCommand<Set<Object>>("EVAL", new ObjectSetReplayDecoder(), ValueType.MAP_KEY);
    private static final RedisCommand<Set<Entry<Object, Object>>> ALL_ENTRIES = new RedisCommand<Set<Entry<Object, Object>>>("EVAL", new ObjectMapEntryReplayDecoder(), ValueType.MAP);
    private static final RedisCommand<Object> EVAL_PUT = new RedisCommand<Object>("EVAL", -1, ValueType.OBJECT, ValueType.MAP_VALUE);
    private static final RedisCommand<Object> EVAL_REMOVE = new RedisCommand<Object>("EVAL", -1, ValueType.OBJECT, ValueType.MAP_VALUE);
    
    private byte[] id;
    private RTopic<Object> invalidationTopic;
    private Cache<CacheKey, CacheValue> cache;
    private int invalidateEntryOnChange;
    private int invalidationListenerId;

    protected RedissonLocalCachedMap(RedissonClient redisson, CommandAsyncExecutor commandExecutor, String name, LocalCachedMapOptions options) {
        super(commandExecutor, name);
        init(redisson, name, options);
    }

    protected RedissonLocalCachedMap(RedissonClient redisson, Codec codec, CommandAsyncExecutor connectionManager, String name, LocalCachedMapOptions options) {
        super(codec, connectionManager, name);
        init(redisson, name, options);
    }

    private void init(RedissonClient redisson, String name, LocalCachedMapOptions options) {
        id = generateId();
        
        if (options.isInvalidateEntryOnChange()) {
            invalidateEntryOnChange = 1;
        }
        if (options.getEvictionPolicy() == EvictionPolicy.NONE) {
            cache = new NoneCacheMap<CacheKey, CacheValue>(options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        }
        if (options.getEvictionPolicy() == EvictionPolicy.LRU) {
            cache = new LRUCacheMap<CacheKey, CacheValue>(options.getCacheSize(), options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        }
        if (options.getEvictionPolicy() == EvictionPolicy.LFU) {
            cache = new LFUCacheMap<CacheKey, CacheValue>(options.getCacheSize(), options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        }

        invalidationTopic = redisson.getTopic(name + ":topic");
        if (options.isInvalidateEntryOnChange()) {
            invalidationListenerId = invalidationTopic.addListener(new MessageListener<Object>() {
                @Override
                public void onMessage(String channel, Object msg) {
                    if (msg instanceof LocalCachedMapClear) {
                        cache.clear();
                    }
                    if (msg instanceof LocalCachedMapInvalidate) {
                        LocalCachedMapInvalidate invalidateMsg = (LocalCachedMapInvalidate)msg;
                        if (!Arrays.equals(invalidateMsg.getExcludedId(), id)) {
                            CacheKey key = new CacheKey(invalidateMsg.getKeyHash());
                            cache.remove(key);
                        }
                    }
                }
            });
        }
    }
    
    private CacheKey toCacheKey(Object key) {
        byte[] encoded = encodeMapKey(key);
        return toCacheKey(encoded);
    }
    
    private CacheKey toCacheKey(byte[] encodedKey) {
        return new CacheKey(Hash.hash(encodedKey));
    }
    
    @Override
    public RFuture<Boolean> containsKeyAsync(Object key) {
        CacheKey cacheKey = toCacheKey(key);
        if (!cache.containsKey(cacheKey)) {
            return super.containsKeyAsync(key);
        }
        return newSucceededFuture(true);
    }

    @Override
    public RFuture<Boolean> containsValueAsync(Object value) {
        CacheValue cacheValue = new CacheValue(null, value);
        if (!cache.containsValue(cacheValue)) {
            return super.containsValueAsync(value);
        }
        return newSucceededFuture(true);
    }
    
    @Override
    public RFuture<V> getAsync(final Object key) {
        if (key == null) {
            throw new NullPointerException();
        }

        final CacheKey cacheKey = toCacheKey(key);
        CacheValue cacheValue = cache.get(cacheKey);
        if (cacheValue != null && cacheValue.getValue() != null) {
            return newSucceededFuture((V)cacheValue.getValue());
        }

        RFuture<V> future = super.getAsync((K)key);
        future.addListener(new FutureListener<V>() {
            @Override
            public void operationComplete(Future<V> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                
                V value = future.getNow();
                if (value != null) {
                    cache.put(cacheKey, new CacheValue(key, value));
                }
            }
        });
        return future;
    }
    
    protected byte[] generateId() {
        byte[] id = new byte[16];
        // TODO JDK UPGRADE replace to native ThreadLocalRandom
        ThreadLocalRandom.current().nextBytes(id);
        return id;
    }

    @Override
    public RFuture<V> putAsync(K key, V value) {
        if (key == null) {
            throw new NullPointerException();
        }
        if (value == null) {
            throw new NullPointerException();
        }
        
        byte[] mapKey = encodeMapKey(key);
        CacheKey cacheKey = toCacheKey(mapKey);
        byte[] msg = encode(new LocalCachedMapInvalidate(id, cacheKey.getKeyHash()));
        CacheValue cacheValue = new CacheValue(key, value);
        cache.put(cacheKey, cacheValue);
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_PUT,
                  "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                + "if redis.call('hset', KEYS[1], ARGV[1], ARGV[2]) == 0 and ARGV[4] == '1' then "
                    + "redis.call('publish', KEYS[2], ARGV[3]); "
                + "end; "
                + "return v; ",
                Arrays.<Object>asList(getName(), invalidationTopic.getChannelNames().get(0)), 
                mapKey, encodeMapValue(value), msg, invalidateEntryOnChange);
    }

    @Override
    public RFuture<Boolean> fastPutAsync(K key, V value) {
        if (key == null) {
            throw new NullPointerException();
        }
        if (value == null) {
            throw new NullPointerException();
        }
        
        byte[] encodedKey = encodeMapKey(key);
        byte[] encodedValue = encodeMapKey(value);
        CacheKey cacheKey = toCacheKey(encodedKey);
        byte[] msg = encode(new LocalCachedMapInvalidate(id, cacheKey.getKeyHash()));
        CacheValue cacheValue = new CacheValue(key, value);
        cache.put(cacheKey, cacheValue);
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                  "if redis.call('hset', KEYS[1], ARGV[1], ARGV[2]) == 0 then "
                  + "if ARGV[4] == '1' then "
                      + "redis.call('publish', KEYS[2], ARGV[3]); "
                  + "end;"
                  + "return 0; "
                + "end; "
                + "return 1; ",
                Arrays.<Object>asList(getName(), invalidationTopic.getChannelNames().get(0)), 
                encodedKey, encodedValue, msg, invalidateEntryOnChange);
    }
    
    @Override
    public void destroy() {
        if (invalidationListenerId != 0) {
            invalidationTopic.removeListener(invalidationListenerId);
        }
    }

    @Override
    public RFuture<V> removeAsync(K key) {
        if (key == null) {
            throw new NullPointerException();
        }

        byte[] keyEncoded = encodeMapKey(key);
        CacheKey cacheKey = toCacheKey(keyEncoded);
        byte[] msgEncoded = encode(new LocalCachedMapInvalidate(id, cacheKey.getKeyHash()));
        cache.remove(cacheKey);
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_REMOVE,
                "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                + "if redis.call('hdel', KEYS[1], ARGV[1]) == 1 and ARGV[3] == '1' then "
                    + "redis.call('publish', KEYS[2], ARGV[2]); "
                + "end; "
                + "return v",
                Arrays.<Object>asList(getName(), invalidationTopic.getChannelNames().get(0)), 
                keyEncoded, msgEncoded, invalidateEntryOnChange);
    }

    @Override
    public RFuture<Long> fastRemoveAsync(K ... keys) {
        if (keys == null) {
            throw new NullPointerException();
        }

        List<Object> params = new ArrayList<Object>();
        params.add(invalidateEntryOnChange);
        for (K k : keys) {
            byte[] keyEncoded = encodeMapKey(k);
            params.add(keyEncoded);
            
            CacheKey cacheKey = toCacheKey(keyEncoded);
            cache.remove(cacheKey);
            if (invalidateEntryOnChange == 1) {
                byte[] msgEncoded = encode(new LocalCachedMapInvalidate(id, cacheKey.getKeyHash()));
                params.add(msgEncoded);
            } else {
                params.add(null);
            }
        }
        
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_LONG,
                  "local counter = 0; " + 
                  "for j = 2, #ARGV, 2 do " 
                      + "if redis.call('hdel', KEYS[1], ARGV[j]) == 1 then "
                          + "if ARGV[1] == '1' then "
                              + "redis.call('publish', KEYS[2], ARGV[j+1]); "
                          + "end; "
                          + "counter = counter + 1;"
                      + "end;"
                + "end;"
                + "return counter;",
                Arrays.<Object>asList(getName(), invalidationTopic.getChannelNames().get(0)), 
                params.toArray());
    }

    
    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        Map<CacheKey, CacheValue> cacheMap = new HashMap<CacheKey, CacheValue>(m.size());
        for (java.util.Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
            CacheKey cacheKey = toCacheKey(entry.getKey());
            CacheValue cacheValue = new CacheValue(entry.getKey(), entry.getValue());
            cacheMap.put(cacheKey, cacheValue);
        }
        cache.putAll(cacheMap);
        super.putAll(m);
        
        if (invalidateEntryOnChange == 1) {
            for (CacheKey cacheKey : cacheMap.keySet()) {
                invalidationTopic.publish(new LocalCachedMapInvalidate(id, cacheKey.getKeyHash()));
            }
        }
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        cache.clear();
        byte[] msgEncoded = encode(new LocalCachedMapClear());
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('del', KEYS[1]) == 1 and ARGV[2] == '1' then "
                + "redis.call('publish', KEYS[2], ARGV[1]); " 
              + "end; ",
              Arrays.<Object>asList(getName(), invalidationTopic.getChannelNames().get(0)), 
              msgEncoded, invalidateEntryOnChange);
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
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    private Iterator<Map.Entry<K,V>> cacheEntrySetIterator() {
        final Iterator<Map.Entry<CacheKey, CacheValue>> iter = cache.entrySet().iterator();
        
        return new Iterator<Map.Entry<K,V>>() {

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public java.util.Map.Entry<K, V> next() {
                Map.Entry<CacheKey, CacheValue> entry = iter.next();
                return new AbstractMap.SimpleEntry(entry.getValue().getKey(), entry.getValue().getValue());
            }
            
            @Override
            public void remove() {
                iter.remove();
            }
            
        };
    }
    
    private Iterator<K> cacheKeySetIterator() {
        final Iterator<CacheValue> iter = cache.values().iterator();
        
        return new Iterator<K>() {

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public K next() {
                CacheValue value = iter.next();
                return (K) value.getKey();
            }
            
            @Override
            public void remove() {
                iter.remove();
            }
            
        };
    }

    
    final class KeySet extends AbstractSet<K> {

        @Override
        public Iterator<K> iterator() {
            return new CompositeIterable<K>(cacheKeySetIterator(), RedissonLocalCachedMap.super.keySet().iterator()) {

                @Override
                boolean isCacheContains(Object object) {
                    CacheKey cacheKey = toCacheKey(object);
                    return cache.containsKey(cacheKey);
                }
                
            };
        }

        @Override
        public boolean contains(Object o) {
            return RedissonLocalCachedMap.this.containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            return RedissonLocalCachedMap.this.remove(o) != null;
        }

        @Override
        public int size() {
            return RedissonLocalCachedMap.this.size();
        }

        @Override
        public void clear() {
            RedissonLocalCachedMap.this.clear();
        }

    }

    final class Values extends AbstractCollection<V> {

        @Override
        public Iterator<V> iterator() {
            final Iterator<Map.Entry<K, V>> iter = RedissonLocalCachedMap.this.entrySet().iterator();
            return new Iterator<V>() {

                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public V next() {
                    return iter.next().getValue();
                }
                
                @Override
                public void remove() {
                    iter.remove();
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            return RedissonLocalCachedMap.this.containsValue(o);
        }

        @Override
        public int size() {
            return RedissonLocalCachedMap.this.size();
        }

        @Override
        public void clear() {
            RedissonLocalCachedMap.this.clear();
        }

    }

    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {

        public final Iterator<Map.Entry<K,V>> iterator() {
            return new CompositeIterable<Map.Entry<K,V>>(cacheEntrySetIterator(), RedissonLocalCachedMap.super.entrySet().iterator()) {

                @Override
                boolean isCacheContains(Map.Entry<K,V> entry) {
                    CacheKey cacheKey = toCacheKey(entry.getKey());
                    return cache.containsKey(cacheKey);
                }
                
            };
        }

        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>) o;
            Object key = e.getKey();
            V value = get(key);
            return value != null && value.equals(e);
        }

        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry<?,?> e = (Map.Entry<?,?>) o;
                Object key = e.getKey();
                Object value = e.getValue();
                return RedissonLocalCachedMap.super.remove(key, value);
            }
            return false;
        }

        public final int size() {
            return RedissonLocalCachedMap.this.size();
        }

        public final void clear() {
            RedissonLocalCachedMap.this.clear();
        }

    }

    abstract class CompositeIterable<T> implements Iterator<T> {

        private T currentObject;
        private Iterator<T> cacheIterator;
        private Iterator<T> mapIterator;

        public CompositeIterable(Iterator<T> cacheIterator, Iterator<T> mapIterator) {
            this.cacheIterator = cacheIterator;
            this.mapIterator = mapIterator;
        }

        @Override
        public boolean hasNext() {
            if (!cacheIterator.hasNext()) {
                while (true) {
                    if (mapIterator.hasNext()) {
                        currentObject = mapIterator.next();
                        if (!isCacheContains(currentObject)) {
                            return true;
                        }
                    } else {
                        break;
                    }
                }
                return false;
            }
            return true;
        }

        abstract boolean isCacheContains(T object);

        @Override
        public T next() {
            if (currentObject != null) {
                T val = currentObject;
                currentObject = null;
                return val;
            }
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return cacheIterator.next();
        }

        @Override
        public void remove() {
            if (currentObject != null) {
                mapIterator.remove();
                currentObject = null;
                return;
            }

            cacheIterator.remove();
        }

    }
    
    @Override
    public RFuture<Map<K, V>> getAllAsync(Set<K> keys) {
        final Map<K, V> result = new HashMap<K, V>();
        Set<K> mapKeys = new HashSet<K>(keys);
        for (Iterator<K> iterator = mapKeys.iterator(); iterator.hasNext();) {
            K key = iterator.next();
            CacheValue value = cache.get(key);
            if (value != null) {
                result.put(key, (V)value.getValue());
                iterator.remove();
            }
        }
        
        final RPromise<Map<K, V>> promise = newPromise();
        RFuture<Map<K, V>> future = super.getAllAsync(mapKeys);
        future.addListener(new FutureListener<Map<K, V>>() {
            @Override
            public void operationComplete(Future<Map<K, V>> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }
                
                Map<K, V> map = future.getNow();
                result.putAll(map);

                cacheMap(map);
                
                promise.trySuccess(result);
            }

        });
        return promise;
    }
    
    private void cacheMap(Map<?, ?> map) {
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            byte[] mapKey = encodeMapKey(entry.getKey());
            CacheKey cacheKey = toCacheKey(mapKey);
            CacheValue cacheValue = new CacheValue(entry.getKey(), entry.getValue());
            cache.put(cacheKey, cacheValue);
        }
    }

    @Override
    public RFuture<Void> putAllAsync(final Map<? extends K, ? extends V> map) {
        if (map.isEmpty()) {
            return newSucceededFuture(null);
        }

        List<Object> params = new ArrayList<Object>(map.size()*3);
        List<Object> msgs = new ArrayList<Object>(map.size());
        params.add(invalidateEntryOnChange);
        params.add(map.size()*2);
        for (java.util.Map.Entry<? extends K, ? extends V> t : map.entrySet()) {
            byte[] mapKey = encodeMapKey(t.getKey());
            byte[] mapValue = encodeMapValue(t.getValue());
            params.add(mapKey);
            params.add(mapValue);
            CacheKey cacheKey = toCacheKey(mapKey);
            byte[] msgEncoded = encode(new LocalCachedMapInvalidate(id, cacheKey.getKeyHash()));
            msgs.add(msgEncoded);
        }
        params.addAll(msgs);

        RFuture<Void> future = commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_VOID,
                "redis.call('hmset', KEYS[1], unpack(ARGV, 3, tonumber(ARGV[2]) + 2));"
              + "if ARGV[1] == '1' then "
                  + "for i = tonumber(ARGV[2]) + 3, #ARGV, 1 do "
                      + "redis.call('publish', KEYS[2], ARGV[i]); "
                  + "end; "
              + "end;",
                Arrays.<Object>asList(getName(), invalidationTopic.getChannelNames().get(0)), params.toArray());

        future.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                
                cacheMap(map);
            }
        });
        return future;
    }

    @Override
    public RFuture<V> addAndGetAsync(final K key, Number value) {
        final byte[] keyState = encodeMapKey(key);
        CacheKey cacheKey = toCacheKey(keyState);
        byte[] msg = encode(new LocalCachedMapInvalidate(id, cacheKey.getKeyHash()));
        
        RFuture<V> future = commandExecutor.evalWriteAsync(getName(), StringCodec.INSTANCE, new RedisCommand<Object>("EVAL", new NumberConvertor(value.getClass())),
                "local result = redis.call('HINCRBYFLOAT', KEYS[1], ARGV[1], ARGV[2]); "
              + "if ARGV[3] == '1' then "
                  + "redis.call('publish', KEYS[2], ARGV[4]); "
              + "end; "
              + "return result; ",
              Arrays.<Object>asList(getName(), invalidationTopic.getChannelNames().get(0)), 
              keyState, new BigDecimal(value.toString()).toPlainString(), invalidateEntryOnChange, msg);

        future.addListener(new FutureListener<V>() {
            @Override
            public void operationComplete(Future<V> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                
                V value = future.getNow();
                if (value != null) {
                    CacheKey cacheKey = toCacheKey(keyState);
                    cache.put(cacheKey, new CacheValue(key, value));
                }
            }
        });
        return future;
    }

    @Override
    public RFuture<Boolean> fastPutIfAbsentAsync(final K key, final V value) {
        RFuture<Boolean> future = super.fastPutIfAbsentAsync(key, value);
        future.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                
                if (future.getNow()) {
                    CacheKey cacheKey = toCacheKey(key);
                    cache.put(cacheKey, new CacheValue(key, value));
                }
            }
        });
        return future;
    }

    @Override
    public RFuture<Collection<V>> readAllValuesAsync() {
        final List<V> result = new ArrayList<V>();
        final List<Object> mapKeys = new ArrayList<Object>();
        for (CacheValue value : cache.values()) {
            mapKeys.add(encodeMapKey(value.getKey()));
            result.add((V) value.getValue());
        }
        
        final RPromise<Collection<V>> promise = newPromise();
        RFuture<Collection<V>> future = commandExecutor.evalReadAsync(getName(), codec, ALL_KEYS,
                "local entries = redis.call('hgetall', KEYS[1]); "
              + "local result = {};"
              + "for j = 1, #entries, 2 do "
                  + "local founded = false;"
                  + "for i = 1, #ARGV, 1 do "
                      + "if ARGV[i] == entries[j] then "
                          + "founded = true;"
                      + "end;"
                  + "end; "
                  + "if founded == false then "
                      + "table.insert(result, entries[j+1]);"
                  + "end;"
              + "end; "
              + "return result; ",
              Arrays.<Object>asList(getName()), 
              mapKeys.toArray());
        
        future.addListener(new FutureListener<Collection<V>>() {

            @Override
            public void operationComplete(Future<Collection<V>> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }
                
                result.addAll(future.get());
                promise.trySuccess(result);
            }
        });
        
        return promise;
    }

    @Override
    public RFuture<Set<Entry<K, V>>> readAllEntrySetAsync() {
        final Set<Entry<K, V>> result = new HashSet<Entry<K, V>>();
        List<Object> mapKeys = new ArrayList<Object>();
        for (CacheValue value : cache.values()) {
            mapKeys.add(encodeMapKey(value.getKey()));
            result.add(new AbstractMap.SimpleEntry<K, V>((K)value.getKey(), (V)value.getValue()));
        }
        
        final RPromise<Set<Entry<K, V>>> promise = newPromise();
        RFuture<Set<Entry<K, V>>> future = commandExecutor.evalReadAsync(getName(), codec, ALL_ENTRIES,
                "local entries = redis.call('hgetall', KEYS[1]); "
              + "local result = {};"
              + "for j = 1, #entries, 2 do "
                  + "local founded = false;"
                  + "for i = 1, #ARGV, 1 do "
                      + "if ARGV[i] == entries[j] then "
                          + "founded = true;"
                      + "end;"
                  + "end; "
                  + "if founded == false then "
                      + "table.insert(result, entries[j]);"
                      + "table.insert(result, entries[j+1]);"
                  + "end;"
              + "end; "
              + "return result; ",
              Arrays.<Object>asList(getName()), 
              mapKeys.toArray());
        
        future.addListener(new FutureListener<Set<Entry<K, V>>>() {
            @Override
            public void operationComplete(Future<Set<Entry<K, V>>> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                
                result.addAll(future.getNow());
                promise.trySuccess(result);
            }
        });
        
        return promise;
    }

    @Override
    public RFuture<V> replaceAsync(final K key, final V value) {
        final byte[] keyState = encodeMapKey(key);
        byte[] valueState = encodeMapValue(value);
        final CacheKey cacheKey = toCacheKey(keyState);
        byte[] msg = encode(new LocalCachedMapInvalidate(id, cacheKey.getKeyHash()));
        
        RFuture<V> future = commandExecutor.evalWriteAsync(getName(key), codec, RedisCommands.EVAL_MAP_VALUE,
                "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then "
                    + "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                    + "if ARGV[3] == '1' then "
                        + "redis.call('publish', KEYS[2], ARGV[4]); "
                    + "end; "
                    + "return v; "
                + "else "
                    + "return nil; "
                + "end",
                Arrays.<Object>asList(getName(key), invalidationTopic.getChannelNames().get(0)), 
                keyState, valueState, invalidateEntryOnChange, msg);
        
        future.addListener(new FutureListener<V>() {
            @Override
            public void operationComplete(Future<V> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                
                if (future.getNow() != null) {
                    CacheKey cacheKey = toCacheKey(key);
                    cache.put(cacheKey, new CacheValue(key, value));
                }
            }
        });
        
        return future;
    }

    @Override
    public RFuture<Boolean> replaceAsync(final K key, V oldValue, final V newValue) {
        final byte[] keyState = encodeMapKey(key);
        byte[] oldValueState = encodeMapValue(oldValue);
        byte[] newValueState = encodeMapValue(newValue);
        final CacheKey cacheKey = toCacheKey(keyState);
        byte[] msg = encode(new LocalCachedMapInvalidate(id, cacheKey.getKeyHash()));
        
        RFuture<Boolean> future = commandExecutor.evalWriteAsync(getName(key), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[3]); "
                    + "if ARGV[4] == '1' then "
                        + "redis.call('publish', KEYS[2], ARGV[5]); "
                    + "end; "
                    + "return 1; "
                + "else "
                    + "return 0; "
                + "end",
                Arrays.<Object>asList(getName(key), invalidationTopic.getChannelNames().get(0)), 
                keyState, oldValueState, newValueState, invalidateEntryOnChange, msg);
        
        future.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                
                if (future.getNow()) {
                    cache.put(cacheKey, new CacheValue(key, newValue));
                }
            }
        });
        
        return future;
    }

    @Override
    public RFuture<Boolean> removeAsync(Object key, Object value) {
        final byte[] keyState = encodeMapKey(key);
        byte[] valueState = encodeMapValue(value);
        final CacheKey cacheKey = toCacheKey(keyState);
        byte[] msg = encode(new LocalCachedMapInvalidate(id, cacheKey.getKeyHash()));
        
        RFuture<Boolean> future = commandExecutor.evalWriteAsync(getName(key), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then "
                    + "if ARGV[3] == '1' then "
                        + "redis.call('publish', KEYS[2], ARGV[4]); "
                    + "end; "
                    + "return redis.call('hdel', KEYS[1], ARGV[1]) "
                + "else "
                    + "return 0 "
                + "end",
            Arrays.<Object>asList(getName(key), invalidationTopic.getChannelNames().get(0)), 
            keyState, valueState, invalidateEntryOnChange, msg);

        future.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                
                if (future.getNow()) {
                    cache.remove(cacheKey);
                }
            }
        });
        return future;
    }

    @Override
    public RFuture<V> putIfAbsentAsync(final K key, final V value) {
        RFuture<V> future = super.putIfAbsentAsync(key, value);
        future.addListener(new FutureListener<V>() {
            @Override
            public void operationComplete(Future<V> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                
                if (future.getNow() == null) {
                    CacheKey cacheKey = toCacheKey(key);
                    cache.put(cacheKey, new CacheValue(key, value));
                }
            }
        });
        return future;
    }

}
