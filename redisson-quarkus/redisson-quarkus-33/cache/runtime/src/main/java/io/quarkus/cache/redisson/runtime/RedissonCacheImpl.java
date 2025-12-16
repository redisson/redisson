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
package io.quarkus.cache.redisson.runtime;

import io.quarkus.arc.Arc;
import io.quarkus.cache.runtime.AbstractCache;
import io.smallrye.mutiny.Uni;
import org.redisson.RedissonObject;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RMapCacheNative;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCacheImpl extends AbstractCache implements RedissonCache {

    private RedissonCacheInfo cacheInfo;
    private RMap map;
    private RMapCache mapCache;
    private RMapCacheNative mapCacheNative;

    public RedissonCacheImpl(RedissonCacheInfo cacheInfo) {
        RedissonClient redisson = Arc.container().select(RedissonClient.class).get();
        CacheImplementation impl = cacheInfo.implementation.orElse(CacheImplementation.STANDARD);
        this.cacheInfo = cacheInfo;
        if (cacheInfo.expireAfterAccess.isPresent()
                || cacheInfo.expireAfterWrite.isPresent()
                    || cacheInfo.maxSize.isPresent()) {
            if (impl == CacheImplementation.STANDARD) {
                this.mapCache = redisson.getMapCache(cacheInfo.name);
                this.map = this.mapCache;

                if (cacheInfo.maxSize.isPresent()) {
                    mapCache.setMaxSizeAsync(cacheInfo.maxSize.orElse(0));
                }
            } else if (impl == CacheImplementation.NATIVE) {
                if (cacheInfo.expireAfterAccess.isPresent()) {
                    throw new IllegalArgumentException("expireAfterAccess isn't supported by NATIVE implementation");
                }
                if (cacheInfo.maxSize.isPresent()) {
                    throw new IllegalArgumentException("maxSize isn't supported by NATIVE implementation");
                }
                this.mapCacheNative = redisson.getMapCacheNative(cacheInfo.name);
                this.map = this.mapCacheNative;
            } else {
                throw new IllegalArgumentException("" + impl + " implementation is available only in PRO version. Please contact sales@redisson.pro");
            }
        } else {
            if (impl == CacheImplementation.STANDARD) {
                this.map = redisson.getMap(cacheInfo.name);
            } else if (impl == CacheImplementation.NATIVE) {
                this.map = redisson.getMapCacheNative(cacheInfo.name);
            } else {
                throw new IllegalArgumentException("" + impl + " implementation is available only in PRO version. Please contact sales@redisson.pro");
            }
        }
    }

    @Override
    public <K, V> Uni<V> put(K key, V value) {
        Objects.requireNonNull(key, NULL_KEYS_NOT_SUPPORTED_MSG);
        return Uni.createFrom().completionStage(new Supplier<CompletionStage<V>>() {
            @Override
            public CompletionStage<V> get() {
                long ttl = cacheInfo.expireAfterWrite.orElse(Duration.ZERO).toMillis();
                long maxIdleTime = cacheInfo.expireAfterAccess.orElse(Duration.ZERO).toMillis();

                if (maxIdleTime > 0 || ttl > 0) {
                    if (mapCacheNative != null) {
                        return mapCacheNative.putAsync(key, value, Duration.ofMillis(ttl));
                    }
                    return mapCache.putAsync(key, value, ttl, TimeUnit.MILLISECONDS, maxIdleTime, TimeUnit.MILLISECONDS);
                }
                return map.putAsync(key, value);
            }
        });
    }

    @Override
    public <K, V> Uni<Boolean> fastPut(K key, V value) {
        Objects.requireNonNull(key, NULL_KEYS_NOT_SUPPORTED_MSG);
        return Uni.createFrom().completionStage(new Supplier<CompletionStage<Boolean>>() {
            @Override
            public CompletionStage<Boolean> get() {
                long ttl = cacheInfo.expireAfterWrite.orElse(Duration.ZERO).toMillis();
                long maxIdleTime = cacheInfo.expireAfterAccess.orElse(Duration.ZERO).toMillis();

                if (maxIdleTime > 0 || ttl > 0) {
                    if (mapCacheNative != null) {
                        return mapCacheNative.fastPutAsync(key, value, Duration.ofMillis(ttl));
                    }
                    return mapCache.fastPutAsync(key, value, ttl, TimeUnit.MILLISECONDS, maxIdleTime, TimeUnit.MILLISECONDS);
                }
                return map.fastPutAsync(key, value);
            }
        });
    }

    @Override
    public <K, V> Uni<V> putIfAbsent(K key, V value) {
        Objects.requireNonNull(key, NULL_KEYS_NOT_SUPPORTED_MSG);
        return Uni.createFrom().completionStage(new Supplier<CompletionStage<V>>() {
            @Override
            public CompletionStage<V> get() {
                long ttl = cacheInfo.expireAfterWrite.orElse(Duration.ZERO).toMillis();
                long maxIdleTime = cacheInfo.expireAfterAccess.orElse(Duration.ZERO).toMillis();

                if (maxIdleTime > 0 || ttl > 0) {
                    if (mapCacheNative != null) {
                        return mapCacheNative.putIfAbsentAsync(key, value, Duration.ofMillis(ttl));
                    }
                    return mapCache.putIfAbsentAsync(key, value, ttl, TimeUnit.MILLISECONDS, maxIdleTime, TimeUnit.MILLISECONDS);
                }
                return map.putIfAbsentAsync(key, value);
            }
        });
    }

    @Override
    public <K, V> Uni<V> putIfExists(K key, V value) {
        Objects.requireNonNull(key, NULL_KEYS_NOT_SUPPORTED_MSG);
        return Uni.createFrom().completionStage(new Supplier<CompletionStage<V>>() {
            @Override
            public CompletionStage<V> get() {
                return map.putIfExistsAsync(key, value);
            }
        });
    }

    @Override
    public <K, V> Uni<Boolean> fastPutIfAbsent(K key, V value) {
        Objects.requireNonNull(key, NULL_KEYS_NOT_SUPPORTED_MSG);
        return Uni.createFrom().completionStage(new Supplier<CompletionStage<Boolean>>() {
            @Override
            public CompletionStage<Boolean> get() {
                long ttl = cacheInfo.expireAfterWrite.orElse(Duration.ZERO).toMillis();
                long maxIdleTime = cacheInfo.expireAfterAccess.orElse(Duration.ZERO).toMillis();

                if (maxIdleTime > 0 || ttl > 0) {
                    if (mapCacheNative != null) {
                        return mapCacheNative.fastPutIfAbsentAsync(key, value, Duration.ofMillis(ttl));
                    }
                    return mapCache.fastPutIfAbsentAsync(key, value, ttl, TimeUnit.MILLISECONDS, maxIdleTime, TimeUnit.MILLISECONDS);
                }
                return map.fastPutIfAbsentAsync(key, value);
            }
        });
    }

    @Override
    public <K, V> Uni<Boolean> fastPutIfExists(K key, V value) {
        Objects.requireNonNull(key, NULL_KEYS_NOT_SUPPORTED_MSG);
        return Uni.createFrom().completionStage(new Supplier<CompletionStage<Boolean>>() {
            @Override
            public CompletionStage<Boolean> get() {
                return map.fastPutIfExistsAsync(key, value);
            }
        });
    }

    @Override
    public <K, V> Uni<V> getOrDefault(K key, V defaultValue) {
        Objects.requireNonNull(key, NULL_KEYS_NOT_SUPPORTED_MSG);
        return Uni.createFrom().completionStage(new Supplier<CompletionStage<V>>() {
            @Override
            public CompletionStage<V> get() {
                return map.getAsync(key);
            }
        }).onItem().ifNull().continueWith(() -> defaultValue);
    }

    @Override
    public String getName() {
        return map.getName();
    }

    @Override
    public <K, V> Uni<V> get(K key, Function<K, V> valueLoader) {
        Objects.requireNonNull(key, NULL_KEYS_NOT_SUPPORTED_MSG);
        return Uni.createFrom().completionStage(new Supplier<CompletionStage<V>>() {
            @Override
            public CompletionStage<V> get() {
                return map.computeIfAbsentAsync(key, valueLoader);
            }
        });
    }

    @Override
    public <K, V> Uni<V> getAsync(K key, Function<K, Uni<V>> valueLoader) {
        Objects.requireNonNull(key, NULL_KEYS_NOT_SUPPORTED_MSG);
        return Uni.createFrom().completionStage(new Supplier<CompletionStage<V>>() {
            @Override
            public CompletionStage<V> get() {
                return map.computeIfAbsentAsync(key, new Function<K, V>() {
                    @Override
                    public V apply(K o) {
                        return valueLoader.apply(o).await().indefinitely();
                    }
                });
            }
        });
    }

    @Override
    public Uni<Void> invalidate(Object key) {
        Objects.requireNonNull(key, NULL_KEYS_NOT_SUPPORTED_MSG);
        return Uni.createFrom().completionStage(new Supplier<CompletionStage<Void>>() {
            @Override
            public CompletionStage<Void> get() {
                return map.removeAsync(key).thenApply(r -> null);
            }
        });
    }

    @Override
    public Uni<Void> invalidateAll() {
        return Uni.createFrom().completionStage(new Supplier<CompletionStage<Void>>() {
            @Override
            public CompletionStage<Void> get() {
                return map.deleteAsync().thenApply(r -> null);
            }
        });
    }

    @Override
    public Uni<Void> invalidateIf(Predicate<Object> predicate) {
        return Uni.createFrom().completionStage(new Supplier<CompletionStage<Void>>() {
            @Override
            public CompletionStage<Void> get() {
                ExecutorService executor = ((RedissonObject) map).getServiceManager().getExecutor();
                return map.readAllKeySetAsync().thenComposeAsync(keys -> {
                    Object[] deleted = ((Set<Object>) keys).stream().filter(k -> predicate.test(k)).toArray();
                    return map.fastRemoveAsync(deleted);
                }, executor);
            }
        });
    }

}
