/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
package org.redisson.spring.cache;

import org.redisson.RedissonObject;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.client.RedisException;
import org.redisson.connection.ServiceManager;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.lang.reflect.Constructor;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCache implements Cache {

    private RMapCache<Object, Object> mapCache;

    private final RMap<Object, Object> map;

    private CacheConfig config;
    
    private final boolean allowNullValues;
    
    private final AtomicLong hits = new AtomicLong();

    private final AtomicLong puts = new AtomicLong();
    
    private final AtomicLong misses = new AtomicLong();

    private final AtomicLong evictions = new AtomicLong();
    
    public RedissonCache(RMapCache<Object, Object> mapCache, CacheConfig config, boolean allowNullValues) {
        this(mapCache, allowNullValues);
        this.mapCache = mapCache;
        this.config = config;
    }

    public RedissonCache(RMap<Object, Object> map, boolean allowNullValues) {
        this.map = map;
        this.allowNullValues = allowNullValues;
    }

    @Override
    public String getName() {
        return map.getName();
    }

    @Override
    public RMap<?, ?> getNativeCache() {
        return map;
    }

    @Override
    public ValueWrapper get(Object key) {
        Object value;
        if (mapCache != null && config.getMaxIdleTime() == 0 && config.getMaxSize() == 0) {
            value = mapCache.getWithTTLOnly(key);
        } else {
            value = map.get(key);
        }

        if (value == null) {
            addCacheMiss();
        } else {
            addCacheHit();
        }
        return toValueWrapper(value);
    }

    public <T> T get(Object key, Class<T> type) {
        Object value;
        if (mapCache != null && config.getMaxIdleTime() == 0 && config.getMaxSize() == 0) {
            value = mapCache.getWithTTLOnly(key);
        } else {
            value = map.get(key);
        }

        if (value == null) {
            addCacheMiss();
        } else {
            addCacheHit();
            if (value.getClass().getName().equals(NullValue.class.getName())) {
                return null;
            }
            if (type != null && !type.isInstance(value)) {
                throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + value);
            }
        }
        return (T) fromStoreValue(value);
    }

    @Override
    public void put(Object key, Object value) {
        if (!allowNullValues && value == null) {
            map.fastRemove(key);
            return;
        }
        
        value = toStoreValue(value);
        if (mapCache != null) {
            mapCache.fastPut(key, value, config.getTTL(), TimeUnit.MILLISECONDS, config.getMaxIdleTime(), TimeUnit.MILLISECONDS);
        } else {
            map.fastPut(key, value);
        }
        addCachePut();
    }

    public ValueWrapper putIfAbsent(Object key, Object value) {
        Object prevValue;
        if (!allowNullValues && value == null) {
            prevValue = map.get(key);
        } else {
            value = toStoreValue(value);
            if (mapCache != null) {
                prevValue = mapCache.putIfAbsent(key, value, config.getTTL(), TimeUnit.MILLISECONDS, config.getMaxIdleTime(), TimeUnit.MILLISECONDS);
            } else {
                prevValue = map.putIfAbsent(key, value);
            }
            if (prevValue == null) {
                addCachePut();
            }
        }
        
        return toValueWrapper(prevValue);
    }

    @Override
    public void evict(Object key) {
        evictIfPresent(key);
    }

    public boolean evictIfPresent(Object key) {
        long delta = map.fastRemove(key);
        addCacheEvictions(delta);
        return delta > 0;
    }

    public CompletableFuture<?> retrieve(Object key) {
        RFuture<Object> f = map.getAsync(key);
        return f.thenApply(value -> {
            if (value == null) {
                addCacheMiss();
            } else {
                addCacheHit();
                if (value.getClass().getName().equals(NullValue.class.getName())) {
                    return null;
                }
            }
            return fromStoreValue(value);
        }).toCompletableFuture();
    }

    public <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
        return retrieve(key).thenCompose(v -> {
            if (v != null) {
                return CompletableFuture.completedFuture((T) v);
            }

            ServiceManager sm = ((RedissonObject) map).getServiceManager();
            long threadId = sm.generateValue();

            RLock lock = map.getLock(key);
            return lock.lockAsync(threadId).thenCompose(rr -> {
                return map.getAsync(key)
                        .thenCompose(r -> {
                            if (r != null) {
                                return CompletableFuture.completedFuture((T) r);
                            }

                            return valueLoader.get()
                                    .thenCompose(lv -> {
                                        Object sv = toStoreValue(lv);
                                        RFuture<Boolean> f;
                                        if (mapCache != null) {
                                            f = mapCache.fastPutAsync(key, sv, config.getTTL(), TimeUnit.MILLISECONDS, config.getMaxIdleTime(), TimeUnit.MILLISECONDS);
                                        } else {
                                            f = map.fastPutAsync(key, sv);
                                        }
                                        return f.thenApply(rs -> {
                                                    addCachePut();
                                                    return lv;
                                                });
                            });
                        })
                        .whenComplete((r1, e) -> {
                            lock.unlockAsync(threadId);
                        });
            });
        });
    }

    @Override
    public void clear() {
        map.clear();
    }

    public boolean invalidate() {
        return get(map.clearAsync());
    }

    private <V> V get(RFuture<V> future) {
        if (Thread.currentThread().getName().startsWith("redisson-netty")) {
            throw new IllegalStateException("Sync methods can't be invoked from async/rx/reactive listeners");
        }

        try {
            return future.get();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RedisException(e);
        } catch (ExecutionException e) {
            throw new RedisException(e.getCause());
        }
    }

    private ValueWrapper toValueWrapper(Object value) {
        if (value == null) {
            return null;
        }
        if (value.getClass().getName().equals(NullValue.class.getName())) {
            return NullValue.INSTANCE;
        }
        return new SimpleValueWrapper(value);
    }

    public <T> T get(Object key, Callable<T> valueLoader) {
        Object value;
        if (mapCache != null && config.getMaxIdleTime() == 0 && config.getMaxSize() == 0) {
            value = mapCache.getWithTTLOnly(key);
        } else {
            value = map.get(key);
        }

        if (value == null) {
            addCacheMiss();
            RLock lock = map.getLock(key);
            lock.lock();
            try {
                value = map.get(key);
                if (value == null) {
                    value = putValue(key, valueLoader, value);
                }
            } finally {
                lock.unlock();
            }
        } else {
            addCacheHit();
        }
        
        return (T) fromStoreValue(value);
    }

    private <T> Object putValue(Object key, Callable<T> valueLoader, Object value) {
        try {
            value = valueLoader.call();
        } catch (Exception ex) {
            RuntimeException exception;
            try {
                Class<?> c = Class.forName("org.springframework.cache.Cache$ValueRetrievalException");
                Constructor<?> constructor = c.getConstructor(Object.class, Callable.class, Throwable.class);
                exception = (RuntimeException) constructor.newInstance(key, valueLoader, ex);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            throw exception;
        }
        put(key, value);
        return value;
    }

    protected Object fromStoreValue(Object storeValue) {
        if (storeValue instanceof NullValue) {
            return null;
        }
        return storeValue;
    }

    protected Object toStoreValue(Object userValue) {
        if (userValue == null) {
            return NullValue.INSTANCE;
        }
        return userValue;
    }

    /** The number of get requests that were satisfied by the cache.
     * @return the number of hits
     */
    long getCacheHits(){
        return hits.get();
    }

    /** A miss is a get request that is not satisfied.
     * @return the number of misses
     */
    long getCacheMisses(){
        return misses.get();
    }
    
    long getCachePuts() {
        return puts.get();
    }

    long getCacheEvictions() {
        return evictions.get();
    }
    
    private void addCachePut() {
        puts.incrementAndGet();
    }

    private void addCacheHit(){
        hits.incrementAndGet();
    }

    private void addCacheMiss(){
        misses.incrementAndGet();
    }

    private void addCacheEvictions(long delta) {
        evictions.addAndGet(delta);
    }
}
