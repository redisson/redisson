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
package org.redisson.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.redisson.*;
import org.redisson.api.*;
import org.redisson.api.LocalCachedMapOptions.EvictionPolicy;
import org.redisson.api.LocalCachedMapOptions.ReconnectionStrategy;
import org.redisson.api.LocalCachedMapOptions.SyncStrategy;
import org.redisson.api.listener.BaseStatusListener;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class LocalCacheListener {
    
    public static final String TOPIC_SUFFIX = "topic";
    public static final String DISABLED_KEYS_SUFFIX = "disabled-keys";
    public static final String DISABLED_ACK_SUFFIX = ":topic";
    
    private ConcurrentMap<CacheKey, String> disabledKeys = new ConcurrentHashMap<CacheKey, String>();
    
    private static final Logger log = LoggerFactory.getLogger(LocalCacheListener.class);
    
    private String name;
    private CommandAsyncExecutor commandExecutor;
    private Map<?, ?> cache;
    private RObject object;
    private byte[] instanceId = new byte[16];
    private Codec codec;
    private LocalCachedMapOptions<?, ?> options;
    
    private long cacheUpdateLogTime;
    private volatile long lastInvalidate;
    private RTopic invalidationTopic;
    private int syncListenerId;
    private int reconnectionListenerId;
    
    public LocalCacheListener(String name, CommandAsyncExecutor commandExecutor,
            RObject object, Codec codec, LocalCachedMapOptions<?, ?> options, long cacheUpdateLogTime) {
        super();
        this.name = name;
        this.commandExecutor = commandExecutor;
        this.object = object;
        this.codec = codec;
        this.options = options;
        this.cacheUpdateLogTime = cacheUpdateLogTime;
        
        ThreadLocalRandom.current().nextBytes(instanceId);
    }
    
    public byte[] generateId() {
        byte[] id = new byte[16];
        ThreadLocalRandom.current().nextBytes(id);
        return id;
    }
    
    public byte[] getInstanceId() {
        return instanceId;
    }
    
    public ConcurrentMap<CacheKey, CacheValue> createCache(LocalCachedMapOptions<?, ?> options) {
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

        if (options.getEvictionPolicy() == EvictionPolicy.NONE) {
            return new NoneCacheMap<>(options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        }
        if (options.getEvictionPolicy() == EvictionPolicy.LRU) {
            return new LRUCacheMap<>(options.getCacheSize(), options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        }
        if (options.getEvictionPolicy() == EvictionPolicy.LFU) {
            return new LFUCacheMap<>(options.getCacheSize(), options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        }
        if (options.getEvictionPolicy() == EvictionPolicy.SOFT) {
            return ReferenceCacheMap.soft(options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        }
        if (options.getEvictionPolicy() == EvictionPolicy.WEAK) {
            return ReferenceCacheMap.weak(options.getTimeToLiveInMillis(), options.getMaxIdleInMillis());
        }
        throw new IllegalArgumentException("Invalid eviction policy: " + options.getEvictionPolicy());
    }
    
    public boolean isDisabled(Object key) {
        return disabledKeys.containsKey(key);
    }
    
    public void add(Map<?, ?> cache) {
        this.cache = cache;
        
        invalidationTopic = RedissonTopic.createRaw(LocalCachedMessageCodec.INSTANCE, commandExecutor, getInvalidationTopicName());

        if (options.getReconnectionStrategy() != ReconnectionStrategy.NONE) {
            reconnectionListenerId = invalidationTopic.addListener(new BaseStatusListener() {
                @Override
                public void onSubscribe(String channel) {
                    if (options.getReconnectionStrategy() == ReconnectionStrategy.CLEAR) {
                        cache.clear();
                    }
                    if (options.getReconnectionStrategy() == ReconnectionStrategy.LOAD
                            // check if instance has already been used
                            && lastInvalidate > 0) {

                        loadAfterReconnection();
                    }
                }
            });
        }
        
        if (options.getSyncStrategy() != SyncStrategy.NONE) {
            syncListenerId = invalidationTopic.addListener(Object.class, new MessageListener<Object>() {
                @Override
                public void onMessage(CharSequence channel, Object msg) {
                    if (msg instanceof LocalCachedMapDisable) {
                        LocalCachedMapDisable m = (LocalCachedMapDisable) msg;
                        String requestId = m.getRequestId();
                        Set<CacheKey> keysToDisable = new HashSet<CacheKey>();
                        for (byte[] keyHash : ((LocalCachedMapDisable) msg).getKeyHashes()) {
                            CacheKey key = new CacheKey(keyHash);
                            keysToDisable.add(key);
                        }
                        
                        disableKeys(requestId, keysToDisable, m.getTimeout());
                        
                        RedissonTopic topic = RedissonTopic.createRaw(LocalCachedMessageCodec.INSTANCE,
                                                            commandExecutor, RedissonObject.suffixName(name, requestId + DISABLED_ACK_SUFFIX));
                        topic.publishAsync(new LocalCachedMapDisableAck());
                    }
                    
                    if (msg instanceof LocalCachedMapEnable) {
                        LocalCachedMapEnable m = (LocalCachedMapEnable) msg;
                        for (byte[] keyHash : m.getKeyHashes()) {
                            CacheKey key = new CacheKey(keyHash);
                            disabledKeys.remove(key, m.getRequestId());
                        }
                    }
                    
                    if (msg instanceof LocalCachedMapClear) {
                        LocalCachedMapClear clearMsg = (LocalCachedMapClear) msg;
                        if (!Arrays.equals(clearMsg.getExcludedId(), instanceId)) {
                            cache.clear();

                            if (clearMsg.isReleaseSemaphore()) {
                                RSemaphore semaphore = getClearSemaphore(clearMsg.getRequestId());
                                semaphore.releaseAsync();
                            }
                        }
                    }
                    
                    if (msg instanceof LocalCachedMapInvalidate) {
                        LocalCachedMapInvalidate invalidateMsg = (LocalCachedMapInvalidate) msg;
                        if (!Arrays.equals(invalidateMsg.getExcludedId(), instanceId)) {
                            for (byte[] keyHash : invalidateMsg.getKeyHashes()) {
                                CacheKey key = new CacheKey(keyHash);
                                cache.remove(key);
                            }
                        }
                    }
                    
                    if (msg instanceof LocalCachedMapUpdate) {
                        LocalCachedMapUpdate updateMsg = (LocalCachedMapUpdate) msg;

                        if (!Arrays.equals(updateMsg.getExcludedId(), instanceId)) {
                            for (LocalCachedMapUpdate.Entry entry : updateMsg.getEntries()) {
                                ByteBuf keyBuf = Unpooled.wrappedBuffer(entry.getKey());
                                ByteBuf valueBuf = Unpooled.wrappedBuffer(entry.getValue());
                                try {
                                    updateCache(keyBuf, valueBuf);
                                } catch (IOException e) {
                                    log.error("Can't decode map entry", e);
                                } finally {
                                    keyBuf.release();
                                    valueBuf.release();
                                }
                            }
                        }
                    }
                    
                    if (options.getReconnectionStrategy() == ReconnectionStrategy.LOAD) {
                        lastInvalidate = System.currentTimeMillis();
                    }
                }

            });
            
            String disabledKeysName = RedissonObject.suffixName(name, DISABLED_KEYS_SUFFIX);
            RListMultimapCache<LocalCachedMapDisabledKey, String> multimap = new RedissonListMultimapCache<LocalCachedMapDisabledKey, String>(null, codec, commandExecutor, disabledKeysName);
            
            for (LocalCachedMapDisabledKey key : multimap.readAllKeySet()) {
                Set<CacheKey> keysToDisable = new HashSet<CacheKey>();
                for (String hash : multimap.getAll(key)) {
                    CacheKey cacheKey = new CacheKey(ByteBufUtil.decodeHexDump(hash));
                    keysToDisable.add(cacheKey);
                }
                
                disableKeys(key.getRequestId(), keysToDisable, key.getTimeout());
            }
        }
    }
    
    public RFuture<Void> clearLocalCacheAsync() {
        cache.clear();
        if (syncListenerId == 0) {
            return new CompletableFutureWrapper<>((Void) null);
        }

        byte[] id = generateId();
        RFuture<Long> future = invalidationTopic.publishAsync(new LocalCachedMapClear(instanceId, id, true));
        CompletionStage<Void> f = future.thenCompose(res -> {
            if (res.intValue() == 0) {
                return CompletableFuture.completedFuture(null);
            }

            RSemaphore semaphore = getClearSemaphore(id);
            return semaphore.tryAcquireAsync(res.intValue() - 1, 50, TimeUnit.SECONDS)
                    .thenCompose(r -> {
                        return semaphore.deleteAsync().thenApply(re -> null);
                    });
        });
        return new CompletableFutureWrapper<>(f);
    }

    public RTopic getInvalidationTopic() {
        return invalidationTopic;
    }

    public String getInvalidationTopicName() {
        return RedissonObject.suffixName(name, TOPIC_SUFFIX);
    }

    protected abstract void updateCache(ByteBuf keyBuf, ByteBuf valueBuf) throws IOException;
    
    private void disableKeys(final String requestId, final Set<CacheKey> keys, long timeout) {
        for (CacheKey key : keys) {
            disabledKeys.put(key, requestId);
            cache.remove(key);
        }
        
        commandExecutor.getConnectionManager().getGroup().schedule(new Runnable() {
            @Override
            public void run() {
                for (CacheKey cacheKey : keys) {
                    disabledKeys.remove(cacheKey, requestId);
                }
            }
        }, timeout, TimeUnit.MILLISECONDS);
    }
    
    public void remove() {
        List<Integer> ids = new ArrayList<Integer>(2);
        if (syncListenerId != 0) {
            ids.add(syncListenerId);
        }
        if (reconnectionListenerId != 0) {
            ids.add(reconnectionListenerId);
        }
        invalidationTopic.removeListenerAsync(ids.toArray(new Integer[ids.size()]));
    }

    public String getUpdatesLogName() {
        return RedissonObject.prefixName("redisson__cache_updates_log", name);
    }

    private void loadAfterReconnection() {
        if (System.currentTimeMillis() - lastInvalidate > cacheUpdateLogTime) {
            cache.clear();
            return;
        }
        
        object.isExistsAsync().whenComplete((res, e) -> {
            if (e != null) {
                log.error("Can't check existance", e);
                return;
            }

            if (!res) {                                        
                cache.clear();
                return;
            }
            
            RScoredSortedSet<byte[]> logs = new RedissonScoredSortedSet<>(ByteArrayCodec.INSTANCE, commandExecutor, getUpdatesLogName(), null);
            logs.valueRangeAsync(lastInvalidate, true, Double.POSITIVE_INFINITY, true)
            .whenComplete((r, ex) -> {
                if (ex != null) {
                    log.error("Can't load update log", ex);
                    return;
                }
                
                for (byte[] entry : r) {
                    byte[] keyHash = Arrays.copyOf(entry, 16);
                    CacheKey key = new CacheKey(keyHash);
                    cache.remove(key);
                }
            });
        });
    }

    private RSemaphore getClearSemaphore(byte[] requestId) {
        String id = ByteBufUtil.hexDump(requestId);
        RSemaphore semaphore = new RedissonSemaphore(commandExecutor, name + ":clear:" + id);
        return semaphore;
    }

}
