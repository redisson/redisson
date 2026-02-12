/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.redisson.api.*;
import org.redisson.api.options.ClientSideCachingOptions;
import org.redisson.api.options.ClientSideCachingParams;
import org.redisson.cache.*;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.pubsub.PublishSubscribeService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class RedissonClientSideCaching implements RClientSideCaching {

    Map<CacheKeyParams, Object> cache;
    final Map<String, Set<CacheKeyParams>> name2cacheKey = new ConcurrentHashMap<>();

    final CommandAsyncExecutor commandExecutor;
    final int listenerId;

    RedissonClientSideCaching(CommandAsyncExecutor commandExecutor, ClientSideCachingOptions options) {
        ClientSideCachingParams params = (ClientSideCachingParams) options;
        if (params.getEvictionPolicy() == ClientSideCachingOptions.EvictionPolicy.NONE) {
            cache = new NoneCacheMap<>(params.getTtl(), params.getIdleTime());
        }
        if (params.getEvictionPolicy() == ClientSideCachingOptions.EvictionPolicy.LRU) {
            cache = new LRUCacheMap<>(params.getSize(), params.getTtl(), params.getIdleTime());
        }
        if (params.getEvictionPolicy() == ClientSideCachingOptions.EvictionPolicy.LFU) {
            cache = new LFUCacheMap<>(params.getSize(), params.getTtl(), params.getIdleTime());
        }
        if (params.getEvictionPolicy() == ClientSideCachingOptions.EvictionPolicy.SOFT) {
            cache = ReferenceCacheMap.soft(params.getTtl(), params.getIdleTime());
        }
        if (params.getEvictionPolicy() == ClientSideCachingOptions.EvictionPolicy.WEAK) {
            cache = ReferenceCacheMap.weak(params.getTtl(), params.getIdleTime());
        }

        commandExecutor.getServiceManager().addClientSideCaching(this);
        CommandAsyncExecutor tracked = commandExecutor.copy(true);
        this.commandExecutor = create(tracked, CommandAsyncExecutor.class);

        PublishSubscribeService subscribeService = this.commandExecutor.getConnectionManager().getSubscribeService();
        CompletableFuture<Integer> r = subscribeService.subscribe(this.commandExecutor, this::clearCache);
        listenerId = r.join();
    }

    public void clearCache(String name) {
        Set<CacheKeyParams> keys = name2cacheKey.remove(name);
        if (keys == null) {
            return;
        }

        for (CacheKeyParams key : keys) {
            cache.remove(key);
        }
    }

    public <T> T create(Object instance, Class<T> clazz) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (!method.getName().contains("read")) {
                    return method.invoke(instance, args);
                }

                String name = (String) Arrays.stream(args)
                                            .filter(r -> r instanceof String)
                                            .findFirst()
                                            .orElse(null);
                if (name == null) {
                    return method.invoke(instance, args);
                }

                CacheKeyParams key = new CacheKeyParams(args);
                Set<CacheKeyParams> values = name2cacheKey.computeIfAbsent(name, v -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
                values.add(key);
                return cache.computeIfAbsent(key, k -> {
                    try {
                        return method.invoke(instance, args);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new IllegalStateException(e);
                    }
                });
            }
        };
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] { clazz }, handler);
    }

    @Override
    public <V> RBucket<V> getBucket(String name) {
        return new RedissonBucket<>(commandExecutor, name);
    }

    @Override
    public <V> RBucket<V> getBucket(String name, Codec codec) {
        return new RedissonBucket<>(codec, commandExecutor, name);
    }

    @Override
    public <K, V> RStream<K, V> getStream(String name) {
        return new RedissonStream<>(commandExecutor, name);
    }

    @Override
    public <K, V> RStream<K, V> getStream(String name, Codec codec) {
        return new RedissonStream<>(codec, commandExecutor, name);
    }

    @Override
    public <V> RSet<V> getSet(String name) {
        return new RedissonSet<>(commandExecutor, name, null);
    }

    @Override
    public <V> RSet<V> getSet(String name, Codec codec) {
        return new RedissonSet<>(codec, commandExecutor, name, null);
    }

    @Override
    public <K, V> RMap<K, V> getMap(String name) {
        return new RedissonMap<>(commandExecutor, name, null, null, null);
    }

    @Override
    public <K, V> RMap<K, V> getMap(String name, Codec codec) {
        return new RedissonMap<>(codec, commandExecutor, name, null, null, null);
    }

    @Override
    public <V> RScoredSortedSet<V> getScoredSortedSet(String name) {
        return new RedissonScoredSortedSet<>(commandExecutor, name, null);
    }

    @Override
    public <V> RScoredSortedSet<V> getScoredSortedSet(String name, Codec codec) {
        return new RedissonScoredSortedSet<>(codec, commandExecutor, name, null);
    }

    @Override
    public <V> RList<V> getList(String name) {
        return new RedissonList<>(commandExecutor, name, null);
    }

    @Override
    public <V> RList<V> getList(String name, Codec codec) {
        return new RedissonList<>(codec, commandExecutor, name, null);
    }

    @Override
    public <V> RQueue<V> getQueue(String name) {
        return new RedissonQueue<>(commandExecutor, name, null);
    }

    @Override
    public <V> RQueue<V> getQueue(String name, Codec codec) {
        return new RedissonQueue<>(codec, commandExecutor, name, null);
    }

    @Override
    public <V> RDeque<V> getDeque(String name) {
        return new RedissonDeque<>(commandExecutor, name, null);
    }

    @Override
    public <V> RDeque<V> getDeque(String name, Codec codec) {
        return new RedissonDeque<>(codec, commandExecutor, name, null);
    }

    @Override
    public <V> RBlockingQueue<V> getBlockingQueue(String name) {
        return new RedissonBlockingQueue<>(commandExecutor, name, null);
    }

    @Override
    public <V> RBlockingQueue<V> getBlockingQueue(String name, Codec codec) {
        return new RedissonBlockingQueue<>(codec, commandExecutor, name, null);
    }

    @Override
    public <V> RBlockingDeque<V> getBlockingDeque(String name) {
        return new RedissonBlockingDeque<>(commandExecutor, name, null);
    }

    @Override
    public <V> RBlockingDeque<V> getBlockingDeque(String name, Codec codec) {
        return new RedissonBlockingDeque<>(codec, commandExecutor, name, null);
    }

    @Override
    public <V> RGeo<V> getGeo(String name) {
        return new RedissonGeo<>(commandExecutor, name, null);
    }

    @Override
    public <V> RGeo<V> getGeo(String name, Codec codec) {
        return new RedissonGeo<>(codec, commandExecutor, name, null);
    }

    @Override
    public void destroy() {
        PublishSubscribeService subscribeService = commandExecutor.getConnectionManager().getSubscribeService();
        commandExecutor.get(subscribeService.removeFlushListenerAsync(listenerId));
        commandExecutor.getServiceManager().removeClientSideCaching(this);
    }
}
