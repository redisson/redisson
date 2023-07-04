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
package org.redisson.cache;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.redisson.RedissonObject;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.misc.Hash;

import io.netty.buffer.ByteBuf;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class LocalCacheView<K, V> {

    private final RedissonObject object;
    private final ConcurrentMap<CacheKey, CacheValue> cache;
    
    public LocalCacheView(LocalCachedMapOptions<?, ?> options, RedissonObject object) {
        this.cache = createCache(options);
        this.object = object;
    }

    public Set<K> cachedKeySet() {
        return new LocalKeySet();
    }

    class LocalKeySet extends AbstractSet<K> {

        @Override
        public Iterator<K> iterator() {
            return new Iterator<K>() {

                private Iterator<CacheValue> iter = cache.values().iterator();
                
                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public K next() {
                    return (K) iter.next().getKey();
                }
                
                @Override
                public void remove() {
                    iter.remove();
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            CacheKey cacheKey = toCacheKey(o);
            return cache.containsKey(cacheKey);
        }

        @Override
        public boolean remove(Object o) {
            CacheKey cacheKey = toCacheKey(o);
            return cache.remove(cacheKey) != null;
        }

        @Override
        public int size() {
            return cache.size();
        }

        @Override
        public void clear() {
            cache.clear();
        }

    }
    
    public Collection<V> cachedValues() {
        return new LocalValues();
    }
    
    final class LocalValues extends AbstractCollection<V> {

        @Override
        public Iterator<V> iterator() {
            return new Iterator<V>() {
                
                private Iterator<CacheValue> iter = cache.values().iterator();
                
                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public V next() {
                    return (V) iter.next().getValue();
                }
                
                @Override
                public void remove() {
                    iter.remove();
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            CacheValue cacheValue = new CacheValue(null, o);
            return cache.containsValue(cacheValue);
        }

        @Override
        public int size() {
            return cache.size();
        }

        @Override
        public void clear() {
            cache.clear();
        }

    }

    public Set<Entry<K, V>> cachedEntrySet() {
        return new LocalEntrySet();
    }

    final class LocalEntrySet extends AbstractSet<Map.Entry<K, V>> {

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new Iterator<Map.Entry<K, V>>() {
                
                private Iterator<CacheValue> iter = cache.values().iterator();
                
                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public Map.Entry<K, V> next() {
                    CacheValue e = iter.next();
                    return new AbstractMap.SimpleEntry<K, V>((K) e.getKey(), (V) e.getValue());
                }
                
                @Override
                public void remove() {
                    iter.remove();
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            CacheKey cacheKey = toCacheKey(e.getKey());
            CacheValue entry = cache.get(cacheKey);
            return entry != null && entry.getValue().equals(e.getValue());
        }

        @Override
        public boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                CacheKey cacheKey = toCacheKey(e.getKey());
                return cache.remove(cacheKey) != null;
            }
            return false;
        }

        @Override
        public int size() {
            return cache.size();
        }

        @Override
        public void clear() {
            cache.clear();
        }

    }
    
    public Map<K, V> getCachedMap() {
        return new LocalMap();
    }
    
    final class LocalMap extends AbstractMap<K, V> {

        @Override
        public V get(Object key) {
            CacheKey cacheKey = toCacheKey(key);
            CacheValue e = cache.get(cacheKey);
            if (e != null) {
                return (V) e.getValue();
            }
            return null;
        }
        
        @Override
        public boolean containsKey(Object key) {
            CacheKey cacheKey = toCacheKey(key);
            return cache.containsKey(cacheKey);
        }
        
        @Override
        public boolean containsValue(Object value) {
            CacheValue cacheValue = new CacheValue(null, value);
            return cache.containsValue(cacheValue);
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return cachedEntrySet();
        }

    }
    
    public CacheKey toCacheKey(Object key) {
        ByteBuf encoded = object.encodeMapKey(key);
        try {
            return toCacheKey(encoded);
        } finally {
            encoded.release();
        }
    }

    public CacheKey toCacheKey(ByteBuf encodedKey) {
        return new CacheKey(Hash.hash128toArray(encodedKey));
    }

    public <K1, V1> ConcurrentMap<K1, V1> getCache() {
        return (ConcurrentMap<K1, V1>) cache;
    }

    public ConcurrentMap<CacheKey, CacheValue> createCache(LocalCachedMapOptions<?, ?> options) {
        if (options.getCacheSize() == -1) {
            return new NoOpCacheMap<CacheKey, CacheValue>();
        }

        if (options.getCacheProvider() == LocalCachedMapOptions.CacheProvider.CAFFEINE) {
            Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder();
            if (options.getTimeToLiveInMillis() > 0) {
                caffeineBuilder.expireAfterWrite(options.getTimeToLiveInMillis(), TimeUnit.MILLISECONDS);
            }
            if (options.getMaxIdleInMillis() > 0) {
                caffeineBuilder.expireAfterAccess(options.getMaxIdleInMillis(), TimeUnit.MILLISECONDS);
            }
            if (options.getCacheSize() > 0) {
                caffeineBuilder.maximumSize(options.getCacheSize());
            }
            if (options.getEvictionPolicy() == LocalCachedMapOptions.EvictionPolicy.SOFT) {
                caffeineBuilder.softValues();
            }
            if (options.getEvictionPolicy() == LocalCachedMapOptions.EvictionPolicy.WEAK) {
                caffeineBuilder.weakValues();
            }
            return caffeineBuilder.<CacheKey, CacheValue>build().asMap();
        }

        if (options.getEvictionPolicy() == LocalCachedMapOptions.EvictionPolicy.NONE) {
            return new NoneCacheMap<>(options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        }
        if (options.getEvictionPolicy() == LocalCachedMapOptions.EvictionPolicy.LRU) {
            return new LRUCacheMap<>(options.getCacheSize(), options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        }
        if (options.getEvictionPolicy() == LocalCachedMapOptions.EvictionPolicy.LFU) {
            return new LFUCacheMap<>(options.getCacheSize(), options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        }
        if (options.getEvictionPolicy() == LocalCachedMapOptions.EvictionPolicy.SOFT) {
            return ReferenceCacheMap.soft(options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        }
        if (options.getEvictionPolicy() == LocalCachedMapOptions.EvictionPolicy.WEAK) {
            return ReferenceCacheMap.weak(options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        }
        throw new IllegalArgumentException("Invalid eviction policy: " + options.getEvictionPolicy());
    }


}
