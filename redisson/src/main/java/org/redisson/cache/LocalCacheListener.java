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
import org.redisson.api.listener.LocalCacheInvalidateListener;
import org.redisson.api.listener.LocalCacheUpdateListener;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
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

    String name;
    CommandAsyncExecutor commandExecutor;
    private Map<CacheKey, ? extends CacheValue> cache;
    private RObject object;
    byte[] instanceId;
    private Codec codec;
    private LocalCachedMapOptions<?, ?> options;
    private final String keyeventPattern;

    private long cacheUpdateLogTime;
    private volatile long lastInvalidate;
    private RTopic invalidationTopic;
    private int syncListenerId;
    private int reconnectionListenerId;

    private int expireListenerId;

    private final Map<Integer, LocalCacheInvalidateListener<?, ?>> invalidateListeners = new ConcurrentHashMap<>();

    private final Map<Integer, LocalCacheUpdateListener<?, ?>> updateListeners = new ConcurrentHashMap<>();

    private boolean isSharded;

    public LocalCacheListener(String name, CommandAsyncExecutor commandExecutor,
                              RObject object, Codec codec, LocalCachedMapOptions<?, ?> options, long cacheUpdateLogTime, boolean isSharded) {
        super();
        this.name = name;
        this.commandExecutor = commandExecutor;
        this.object = object;
        this.codec = codec;
        this.options = options;
        this.cacheUpdateLogTime = cacheUpdateLogTime;
        this.isSharded = isSharded;
        this.keyeventPattern = "__keyspace@" + commandExecutor.getServiceManager().getConfig().getDatabase() + "__:" + name;

        instanceId = commandExecutor.getServiceManager().generateIdArray();
    }

    public byte[] getInstanceId() {
        return instanceId;
    }

    public boolean isDisabled(Object key) {
        return disabledKeys.containsKey(key);
    }

    public void add(Map<CacheKey, ? extends CacheValue> cache) {
        this.cache = cache;

        createTopic(name, commandExecutor);

        if (options.getExpirationEventPolicy() == LocalCachedMapOptions.ExpirationEventPolicy.SUBSCRIBE_WITH_KEYEVENT_PATTERN) {
            RPatternTopic topic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, "__keyevent@*:expired");
            expireListenerId = topic.addListener(String.class, (pattern, channel, msg) -> {
                if (msg.equals(name)) {
                    cache.clear();
                }
            });
        } else if (options.getExpirationEventPolicy() == LocalCachedMapOptions.ExpirationEventPolicy.SUBSCRIBE_WITH_KEYSPACE_CHANNEL) {
            RTopic topic = new RedissonTopic(StringCodec.INSTANCE, commandExecutor, keyeventPattern);
            expireListenerId = topic.addListener(String.class, (channel, msg) -> {
                if (msg.equals("expired")) {
                    cache.clear();
                }
            });
        }

        if (options.getReconnectionStrategy() != ReconnectionStrategy.NONE) {
            reconnectionListenerId = addReconnectionListener();
        }

        if (options.getSyncStrategy() != SyncStrategy.NONE) {
            syncListenerId = addMessageListener();

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

    void createTopic(String name, CommandAsyncExecutor commandExecutor) {
        if (isSharded) {
            invalidationTopic = RedissonShardedTopic.createRaw(LocalCachedMessageCodec.INSTANCE, commandExecutor, getInvalidationTopicName());
        } else {
            invalidationTopic = RedissonTopic.createRaw(LocalCachedMessageCodec.INSTANCE, commandExecutor, getInvalidationTopicName());
        }
    }

    int addMessageListener() {
        return invalidationTopic.addListener(Object.class, new MessageListener<Object>() {
            @Override
            public void onMessage(CharSequence channel, Object msg) {
                LocalCacheListener.this.onMessage(msg);
            }

        });
    }

    int addReconnectionListener() {
        return invalidationTopic.addListener(new BaseStatusListener() {
            @Override
            public void onSubscribe(String channel) {
                LocalCacheListener.this.onSubscribe();
            }
        });
    }

    final void onMessage(Object msg) {
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
                    CacheValue value = cache.remove(key);
                    if (value == null) {
                        continue;
                    }
                    notifyInvalidate(value);
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
                        CacheValue value = updateCache(keyBuf, valueBuf);
                        notifyUpdate(value);
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

    final void onSubscribe() {
        if (options.getReconnectionStrategy() == ReconnectionStrategy.CLEAR) {
            cache.clear();
        }
        if (options.getReconnectionStrategy() == ReconnectionStrategy.LOAD
                // check if instance has already been used
                && lastInvalidate > 0) {

            loadAfterReconnection();
        }
    }

    public void notifyUpdate(CacheValue value) {
        for (LocalCacheUpdateListener listener : updateListeners.values()) {
            listener.onUpdate(value.getKey(), value.getValue());
        }
    }

    public void notifyInvalidate(CacheValue value) {
        for (LocalCacheInvalidateListener listener : invalidateListeners.values()) {
            listener.onInvalidate(value.getKey(), value.getValue());
        }
    }

    public RFuture<Void> clearLocalCacheAsync() {
        cache.clear();
        if (syncListenerId == 0) {
            return new CompletableFutureWrapper<>((Void) null);
        }

        byte[] id = commandExecutor.getServiceManager().generateIdArray();
        RFuture<Long> future = publishAsync(id);
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

    RFuture<Long> publishAsync(byte[] id) {
        return invalidationTopic.publishAsync(new LocalCachedMapClear(instanceId, id, true));
    }

    public RTopic getInvalidationTopic() {
        return invalidationTopic;
    }

    public String getInvalidationTopicName() {
        return RedissonObject.suffixName(name, TOPIC_SUFFIX);
    }

    protected abstract CacheValue updateCache(ByteBuf keyBuf, ByteBuf valueBuf) throws IOException;

    private void disableKeys(final String requestId, final Set<CacheKey> keys, long timeout) {
        for (CacheKey key : keys) {
            disabledKeys.put(key, requestId);
            cache.remove(key);
        }

        commandExecutor.getServiceManager().newTimeout(t -> {
            for (CacheKey cacheKey : keys) {
                disabledKeys.remove(cacheKey, requestId);
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
        removeAsync(ids);

        if (options.getExpirationEventPolicy() == LocalCachedMapOptions.ExpirationEventPolicy.SUBSCRIBE_WITH_KEYEVENT_PATTERN) {
            RPatternTopic topic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, "__keyevent@*:expired");
            topic.removeListenerAsync(expireListenerId);
        } else if (options.getExpirationEventPolicy() == LocalCachedMapOptions.ExpirationEventPolicy.SUBSCRIBE_WITH_KEYSPACE_CHANNEL) {
            RTopic topic = new RedissonTopic(StringCodec.INSTANCE, commandExecutor, keyeventPattern);
            topic.removeListenerAsync(expireListenerId);
        }
    }

    void removeAsync(List<Integer> ids) {
        invalidationTopic.removeListenerAsync(ids.toArray(new Integer[0]));
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
        semaphore.expireAsync(Duration.ofSeconds(60));
        return semaphore;
    }

    public <K, V> int addListener(LocalCacheInvalidateListener<K, V> listener) {
        int listenerId = System.identityHashCode(listener);
        invalidateListeners.put(listenerId, listener);
        return listenerId;
    }

    public <K, V> int addListener(LocalCacheUpdateListener<K, V> listener) {
        int listenerId = System.identityHashCode(listener);
        updateListeners.put(listenerId, listener);
        return listenerId;
    }

    public void removeListener(int listenerId) {
        updateListeners.remove(listenerId);
        invalidateListeners.remove(listenerId);
    }

}
