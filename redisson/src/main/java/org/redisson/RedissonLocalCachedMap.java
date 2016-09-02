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

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.LocalCachedMapOptions.EvictionPolicy;
import org.redisson.api.RFuture;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.Cache;
import org.redisson.misc.Hash;
import org.redisson.misc.LFUCacheMap;
import org.redisson.misc.LRUCacheMap;
import org.redisson.misc.NoneCacheMap;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonLocalCachedMap<K, V> extends RedissonExpirable implements RLocalCachedMap<K, V> {

    public static class LocalCachedMapClear {
        
    }
    
    public static class LocalCachedMapInvalidate {
        
        private byte[] keyHash;

        public LocalCachedMapInvalidate() {
        }
        
        public LocalCachedMapInvalidate(byte[] keyHash) {
            super();
            this.keyHash = keyHash;
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
    
    private static final RedisCommand<Object> EVAL_PUT = new RedisCommand<Object>("EVAL", -1, ValueType.OBJECT, ValueType.MAP_VALUE);
    private static final RedisCommand<Object> EVAL_REMOVE = new RedisCommand<Object>("EVAL", -1, ValueType.OBJECT, ValueType.MAP_VALUE);
    
    private RTopic<Object> invalidationTopic;
    private RMap<K, V> map;
    private Cache<CacheKey, CacheValue> cache;
    private int invalidateEntryOnChange;

    protected RedissonLocalCachedMap(RedissonClient redisson, CommandAsyncExecutor commandExecutor, String name, LocalCachedMapOptions options) {
        super(commandExecutor, name);
        init(redisson, name, options);
    }

    protected RedissonLocalCachedMap(RedissonClient redisson, Codec codec, CommandAsyncExecutor connectionManager, String name, LocalCachedMapOptions options) {
        super(codec, connectionManager, name);
        init(redisson, name, options);
    }

    private void init(RedissonClient redisson, String name, LocalCachedMapOptions options) {
        map = redisson.getMap(name);
        
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
        invalidationTopic.addListener(new MessageListener<Object>() {
            @Override
            public void onMessage(String channel, Object msg) {
                if (msg instanceof LocalCachedMapClear) {
                    cache.clear();
                }
                if (msg instanceof LocalCachedMapInvalidate) {
                    CacheKey key = new CacheKey(((LocalCachedMapInvalidate)msg).getKeyHash());
                    cache.remove(key);
                }
            }
        });
    }
    
    @Override
    public int size() {
        return get(sizeAsync());
    }
    
    @Override
    public RFuture<Integer> sizeAsync() {
        return map.sizeAsync();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return get(containsKeyAsync(key));
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
            return map.containsKeyAsync(key);
        }
        return newSucceededFuture(true);
    }

    @Override
    public boolean containsValue(Object value) {
        return get(containsValueAsync(value));
    }

    @Override
    public RFuture<Boolean> containsValueAsync(Object value) {
        CacheValue cacheValue = new CacheValue(null, value);
        if (!cache.containsValue(cacheValue)) {
            return map.containsValueAsync(value);
        }
        return newSucceededFuture(true);
    }
    
    @Override
    public V get(Object key) {
        return get(getAsync(key));
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

        RFuture<V> future = map.getAsync((K)key);
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


    @Override
    public V put(K key, V value) {
        return get(putAsync(key, value));
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
        byte[] msg = encode(new LocalCachedMapInvalidate(cacheKey.getKeyHash()));
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
    public boolean fastPut(K key, V value) {
        return get(fastPutAsync(key, value));
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
        byte[] msg = encode(new LocalCachedMapInvalidate(cacheKey.getKeyHash()));
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
    public V remove(Object key) {
        return get(removeAsync((K)key));
    }

    @Override
    public RFuture<V> removeAsync(K key) {
        if (key == null) {
            throw new NullPointerException();
        }

        byte[] keyEncoded = encodeMapKey(key);
        CacheKey cacheKey = toCacheKey(keyEncoded);
        byte[] msgEncoded = encode(new LocalCachedMapInvalidate(cacheKey.getKeyHash()));
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
    public boolean fastRemove(Object key) {
        return get(fastRemoveAsync((K)key));
    }
    
    @Override
    public RFuture<Boolean> fastRemoveAsync(K key) {
        if (key == null) {
            throw new NullPointerException();
        }

        byte[] keyEncoded = encodeMapKey(key);
        CacheKey cacheKey = toCacheKey(keyEncoded);
        byte[] msgEncoded = encode(new LocalCachedMapInvalidate(cacheKey.getKeyHash()));
        cache.remove(cacheKey);
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                  "if redis.call('hdel', KEYS[1], ARGV[1]) == 1 then "
                  + "if ARGV[3] == '1' then "
                      + "redis.call('publish', KEYS[2], ARGV[2]); "
                  + "end; "
                  + "return 1;"
                + "end;"
                + "return 0;",
                Arrays.<Object>asList(getName(), invalidationTopic.getChannelNames().get(0)), 
                keyEncoded, msgEncoded, invalidateEntryOnChange);
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
        map.putAll(m);
        for (CacheKey cacheKey : cacheMap.keySet()) {
            invalidationTopic.publish(new LocalCachedMapInvalidate(cacheKey.getKeyHash()));
        }
    }

    @Override
    public void clear() {
        delete();
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
            return new CompositeIterable<K>(cacheKeySetIterator(), map.keySet().iterator()) {

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
            return new CompositeIterable<Map.Entry<K,V>>(cacheEntrySetIterator(), map.entrySet().iterator()) {

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
                return RedissonLocalCachedMap.this.map.remove(key, value);
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

}
