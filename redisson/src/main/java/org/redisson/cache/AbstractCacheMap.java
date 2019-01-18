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
import java.util.AbstractMap.SimpleEntry;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public abstract class AbstractCacheMap<K, V> implements Cache<K, V> {

    final int size;
    final ConcurrentMap<K, CachedValue<K, V>> map = PlatformDependent.newConcurrentHashMap();
    private final long timeToLiveInMillis;
    private final long maxIdleInMillis;


    public AbstractCacheMap(int size, long timeToLiveInMillis, long maxIdleInMillis) {
        if (size < 0) {
            throw new IllegalArgumentException("Size can't be " + size);
        }
        this.size = size;
        this.maxIdleInMillis = maxIdleInMillis;
        this.timeToLiveInMillis = timeToLiveInMillis;
    }

    protected void onValueRead(CachedValue<K, V> value) {
        
    }
    
    protected void onValueRemove(CachedValue<K, V> value) {
        
    }

    
    /*
     * (non-Javadoc)
     * @see java.util.Map#size()
     */
    @Override
    public int size() {
        return map.size();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#isEmpty()
     */
    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    @Override
    public boolean containsKey(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
        
        CachedValue<K, V> entry = map.get(key);
        if (entry == null) {
            return false;
        }
        if (isValueExpired(entry)) {
            if (map.remove(key, entry)) {
                onValueRemove(entry);
                return false;
            }
            return containsKey(key);
        }
        return true;
    }

    private boolean isValueExpired(CachedValue<K, V> entry) {
        if (entry.isExpired()) {
            return true;
        }
        if (entry.getValue() instanceof ExpirableValue) {
            if (((ExpirableValue) entry.getValue()).isExpired()) {
                return true;
            }
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#containsValue(java.lang.Object)
     */
    @Override
    public boolean containsValue(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }

        for (Map.Entry<K, CachedValue<K, V>> entry : map.entrySet()) {
            CachedValue<K, V> cachedValue = entry.getValue();
            if (cachedValue.getValue().equals(value)) {
                if (isValueExpired(cachedValue)) {
                    if (map.remove(cachedValue.getKey(), cachedValue)) {
                        onValueRemove(cachedValue);
                    }
                } else {
                    readValue(cachedValue);
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#get(java.lang.Object)
     */
    @Override
    public V get(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
        
        CachedValue<K, V> entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (isValueExpired(entry)) {
            if (map.remove(key, entry)) {
                onValueRemove(entry);
                return null;
            }
            return get(key);
        }
        return readValue(entry);
    }

    protected V readValue(CachedValue<K, V> entry) {
        onValueRead(entry);
        return (V) entry.getValue();
    }
    
    /*
     * (non-Javadoc)
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     */
    @Override
    public V put(K key, V value) {
        return put(key, value, timeToLiveInMillis, TimeUnit.MILLISECONDS, maxIdleInMillis, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public V put(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        CachedValue<K, V> entry = create(key, value, ttlUnit.toMillis(ttl), maxIdleUnit.toMillis(maxIdleTime));
        if (isFull(key)) {
            if (!removeExpiredEntries()) {
                onMapFull();
            }
        }
        onValueCreate(entry);
        CachedValue<K, V> prevCachedValue = map.put(key, entry);
        if (prevCachedValue != null) {
            onValueRemove(prevCachedValue);
            if (!isValueExpired(prevCachedValue)) {
                return (V) prevCachedValue.getValue();
            }
        }
        return null;
    }

    protected CachedValue<K, V> create(K key, V value, long ttl, long maxIdleTime) {
        return new StdCachedValue<K, V>(key, value, ttl, maxIdleTime);
    }
    
    protected void onValueCreate(CachedValue<K, V> entry) {
    }

    protected boolean removeExpiredEntries() {
        boolean removed = false;
        // TODO optimize
        for (CachedValue<K, V> value : map.values()) {
            if (isValueExpired(value)) {
                if (map.remove(value.getKey(), value)) {
                    onValueRemove(value);                    
                    removed = true;
                }
            }
        }
        return removed;
    }

    protected abstract void onMapFull();

    protected boolean isFull(K key) {
        if (size == 0) {
            return false;
        }
        if (map.size() >= size) {
            return !map.containsKey(key);
        }
        return false;
    }
    
    /*
     * (non-Javadoc)
     * @see java.util.Map#remove(java.lang.Object)
     */
    @Override
    public V remove(Object key) {
        CachedValue<K, V> entry = map.remove(key);
        if (entry != null) {
            onValueRemove(entry);
            if (!isValueExpired(entry)) {
                return (V) entry.getValue();
            }
        }
        return null;
    }
    
    /*
     * (non-Javadoc)
     * @see java.util.Map#putAll(java.util.Map)
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        removeExpiredEntries();
        for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#clear()
     */
    @Override
    public void clear() {
        map.clear();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#keySet()
     */
    @Override
    public Set<K> keySet() {
        removeExpiredEntries();
        return new KeySet();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#values()
     */
    @Override
    public Collection<V> values() {
        removeExpiredEntries();
        return new Values();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#entrySet()
     */
    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        removeExpiredEntries();
        return new EntrySet();
    }
    
    abstract class MapIterator<M> implements Iterator<M> {

        final Iterator<Map.Entry<K, CachedValue<K, V>>> keyIterator = map.entrySet().iterator();
        
        Map.Entry<K, CachedValue<K, V>> mapEntry;
        
        @Override
        public boolean hasNext() {
            if (mapEntry != null) {
                return true;
            }
            mapEntry = null;
            while (keyIterator.hasNext()) {
                Map.Entry<K, CachedValue<K, V>> entry = keyIterator.next();
                if (isValueExpired(entry.getValue())) {
                    continue; 
                }
                mapEntry = entry;
                break;
            }
            return mapEntry != null;
        }

    }

    final class KeySet extends AbstractSet<K> {

        @Override
        public Iterator<K> iterator() {
            return new MapIterator<K>() {
                @Override
                public K next() {
                    if (mapEntry == null) {
                        throw new NoSuchElementException();
                    }
                    
                    K key = mapEntry.getKey();
                    mapEntry = null;
                    return key;
                }

                @Override
                public void remove() {
                    if (mapEntry == null) {
                        throw new IllegalStateException();
                    }
                    map.remove(mapEntry.getKey());
                    mapEntry = null;
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            return AbstractCacheMap.this.containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            return AbstractCacheMap.this.remove(o) != null;
        }

        @Override
        public int size() {
            return AbstractCacheMap.this.size();
        }

        @Override
        public void clear() {
            AbstractCacheMap.this.clear();
        }

    }

    final class Values extends AbstractCollection<V> {

        @Override
        public Iterator<V> iterator() {
            return new MapIterator<V>() {
                @Override
                public V next() {
                    if (mapEntry == null) {
                        throw new NoSuchElementException();
                    }
                    
                    V value = readValue(mapEntry.getValue());
                    mapEntry = null;
                    return value;
                }

                @Override
                public void remove() {
                    if (mapEntry == null) {
                        throw new IllegalStateException();
                    }
                    map.remove(mapEntry.getKey(), mapEntry.getValue());
                    mapEntry = null;
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            return AbstractCacheMap.this.containsValue(o);
        }

        @Override
        public int size() {
            return AbstractCacheMap.this.size();
        }

        @Override
        public void clear() {
            AbstractCacheMap.this.clear();
        }

    }

    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {

        public final Iterator<Map.Entry<K,V>> iterator() {
            return new MapIterator<Map.Entry<K,V>>() {
                @Override
                public Map.Entry<K,V> next() {
                    if (mapEntry == null) {
                        throw new NoSuchElementException();
                    }
                    
                    SimpleEntry<K, V> result = new SimpleEntry<K, V>(mapEntry.getKey(), readValue(mapEntry.getValue()));
                    mapEntry = null;
                    return result;
                }

                @Override
                public void remove() {
                    if (mapEntry == null) {
                        throw new IllegalStateException();
                    }
                    map.remove(mapEntry.getKey(), mapEntry.getValue());
                    mapEntry = null;
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
                return AbstractCacheMap.this.map.remove(key, value);
            }
            return false;
        }

        public final int size() {
            return AbstractCacheMap.this.size();
        }

        public final void clear() {
            AbstractCacheMap.this.clear();
        }

    }

}
