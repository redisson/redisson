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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.redisson.RedissonListMultimapCache;
import org.redisson.RedissonObject;
import org.redisson.RedissonScoredSortedSet;
import org.redisson.RedissonTopic;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.LocalCachedMapOptions.ReconnectionStrategy;
import org.redisson.api.LocalCachedMapOptions.SyncStrategy;
import org.redisson.api.RFuture;
import org.redisson.api.RListMultimapCache;
import org.redisson.api.RObject;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RTopic;
import org.redisson.api.listener.BaseStatusListener;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

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
    private Cache<?, ?> cache;
    private RObject object;
    private byte[] instanceId;
    private Codec codec;
    private LocalCachedMapOptions<?, ?> options;
    
    private long cacheUpdateLogTime;
    private volatile long lastInvalidate;
    private RTopic invalidationTopic;
    private int syncListenerId;
    private int reconnectionListenerId;
    
    public LocalCacheListener(String name, CommandAsyncExecutor commandExecutor, Cache<?, ?> cache,
            RObject object, byte[] instanceId, Codec codec, LocalCachedMapOptions<?, ?> options, long cacheUpdateLogTime) {
        super();
        this.name = name;
        this.commandExecutor = commandExecutor;
        this.cache = cache;
        this.object = object;
        this.instanceId = instanceId;
        this.codec = codec;
        this.options = options;
        this.cacheUpdateLogTime = cacheUpdateLogTime;
    }

    public boolean isDisabled(Object key) {
        return disabledKeys.containsKey(key);
    }
    
    public void add() {
        invalidationTopic = new RedissonTopic(LocalCachedMessageCodec.INSTANCE, commandExecutor, getInvalidationTopicName());

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

                        if (System.currentTimeMillis() - lastInvalidate > cacheUpdateLogTime) {
                            cache.clear();
                            return;
                        }
                        
                        object.isExistsAsync().addListener(new FutureListener<Boolean>() {
                            @Override
                            public void operationComplete(Future<Boolean> future) throws Exception {
                                if (!future.isSuccess()) {
                                    log.error("Can't check existance", future.cause());
                                    return;
                                }

                                if (!future.getNow()) {                                        
                                    cache.clear();
                                    return;
                                }
                                
                                RScoredSortedSet<byte[]> logs = new RedissonScoredSortedSet<byte[]>(ByteArrayCodec.INSTANCE, commandExecutor, getUpdatesLogName(), null);
                                logs.valueRangeAsync(lastInvalidate, true, Double.POSITIVE_INFINITY, true)
                                .addListener(new FutureListener<Collection<byte[]>>() {
                                    @Override
                                    public void operationComplete(Future<Collection<byte[]>> future) throws Exception {
                                        if (!future.isSuccess()) {
                                            log.error("Can't load update log", future.cause());
                                            return;
                                        }
                                        
                                        for (byte[] entry : future.getNow()) {
                                            byte[] keyHash = Arrays.copyOf(entry, 16);
                                            CacheKey key = new CacheKey(keyHash);
                                            cache.remove(key);
                                        }
                                    }
                                });
                            }
                        });
                        
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
                        
                        RedissonTopic topic = new RedissonTopic(LocalCachedMessageCodec.INSTANCE, 
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
                        cache.clear();
                    }
                    
                    if (msg instanceof LocalCachedMapInvalidate) {
                        LocalCachedMapInvalidate invalidateMsg = (LocalCachedMapInvalidate)msg;
                        if (!Arrays.equals(invalidateMsg.getExcludedId(), instanceId)) {
                            for (byte[] keyHash : invalidateMsg.getKeyHashes()) {
                                CacheKey key = new CacheKey(keyHash);
                                cache.remove(key);
                            }
                        }
                    }
                    
                    if (msg instanceof LocalCachedMapUpdate) {
                        LocalCachedMapUpdate updateMsg = (LocalCachedMapUpdate) msg;
                        
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
        final RPromise<Void> result = new RedissonPromise<Void>();
        RFuture<Long> future = invalidationTopic.publishAsync(new LocalCachedMapClear());
        future.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }

                result.trySuccess(null);
            }
        });
        
        return result;
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

}
