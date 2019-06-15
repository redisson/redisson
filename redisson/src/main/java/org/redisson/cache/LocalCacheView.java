/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.redisson.RedissonObject;
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
    private final Cache<CacheKey, CacheValue> cache;
    
    public LocalCacheView(Cache<CacheKey, CacheValue> cache, RedissonObject object) {
        this.cache = cache;
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

    private CacheKey toCacheKey(ByteBuf encodedKey) {
        return new CacheKey(Hash.hash128toArray(encodedKey));
    }
    
}
