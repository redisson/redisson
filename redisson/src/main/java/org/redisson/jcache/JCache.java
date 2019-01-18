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
package org.redisson.jcache;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.event.EventType;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import javax.cache.integration.CacheWriter;
import javax.cache.integration.CacheWriterException;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;

import org.redisson.Redisson;
import org.redisson.RedissonBaseMapIterator;
import org.redisson.RedissonObject;
import org.redisson.ScanResult;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RSemaphore;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.connection.decoder.MapGetAllDecoder;
import org.redisson.jcache.JMutableEntry.Action;
import org.redisson.jcache.configuration.JCacheConfiguration;
import org.redisson.misc.Hash;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.PlatformDependent;

/**
 * JCache implementation
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class JCache<K, V> extends RedissonObject implements Cache<K, V> {

    private final boolean atomicExecution = System.getProperty("org.jsr107.tck.management.agentId") == null;
    
    private final JCacheManager cacheManager;
    private final JCacheConfiguration<K, V> config;
    private final ConcurrentMap<CacheEntryListenerConfiguration<K, V>, Map<Integer, String>> listeners = 
                                        new ConcurrentHashMap<CacheEntryListenerConfiguration<K, V>, Map<Integer, String>>();
    private final Redisson redisson;

    private CacheLoader<K, V> cacheLoader;
    private CacheWriter<K, V> cacheWriter;
    private boolean closed;
    private boolean hasOwnRedisson;
    
    /*
     * No locking required in atomic execution mode.
     */
    private static final RLock DUMMY_LOCK = (RLock) Proxy.newProxyInstance(JCache.class.getClassLoader(), new Class[] {RLock.class}, new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return null;
        }
    });
    
    public JCache(JCacheManager cacheManager, Redisson redisson, String name, JCacheConfiguration<K, V> config, boolean hasOwnRedisson) {
        super(redisson.getConfig().getCodec(), redisson.getCommandExecutor(), name);
        
        this.hasOwnRedisson = hasOwnRedisson;
        this.redisson = redisson;
        
        Factory<CacheLoader<K, V>> cacheLoaderFactory = config.getCacheLoaderFactory();
        if (cacheLoaderFactory != null) {
            cacheLoader = cacheLoaderFactory.create();
        }
        Factory<CacheWriter<? super K, ? super V>> cacheWriterFactory = config.getCacheWriterFactory();
        if (config.getCacheWriterFactory() != null) {
            cacheWriter = (CacheWriter<K, V>) cacheWriterFactory.create();
        }
        
        this.cacheManager = cacheManager;
        this.config = config;
        
        redisson.getEvictionScheduler().scheduleJCache(getName(), getTimeoutSetName(), getExpiredChannelName());
        
        for (CacheEntryListenerConfiguration<K, V> listenerConfig : config.getCacheEntryListenerConfigurations()) {
            registerCacheEntryListener(listenerConfig, false);
        }
    }
    
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException();
        }
    }
    
    String getTimeoutSetName() {
        return "jcache_timeout_set:{" + getName() + "}";
    }
    
    String getSyncName(Object syncId) {
        return "jcache_sync:" + syncId + ":{" + getName() + "}";
    }

    String getCreatedSyncChannelName() {
        return "jcache_created_sync_channel:{" + getName() + "}";
    }
    
    String getUpdatedSyncChannelName() {
        return "jcache_updated_sync_channel:{" + getName() + "}";
    }

    String getRemovedSyncChannelName() {
        return "jcache_removed_sync_channel:{" + getName() + "}";
    }
    
    String getCreatedChannelName() {
        return "jcache_created_channel:{" + getName() + "}";
    }
    
    String getUpdatedChannelName() {
        return "jcache_updated_channel:{" + getName() + "}";
    }

    String getExpiredChannelName() {
        return "jcache_expired_channel:{" + getName() + "}";
    }
    
    String getRemovedChannelName() {
        return "jcache_removed_channel:{" + getName() + "}";
    }

    private long currentNanoTime() {
        if (config.isStatisticsEnabled()) {
            return System.nanoTime();
        }
        return 0;
    }

    @Override
    public V get(K key) {
        checkNotClosed();
        if (key == null) {
            throw new NullPointerException();
        }
        long startTime = currentNanoTime();
        RLock lock = getLockedLock(key);
        try {
            V value;
            if (atomicExecution) {
                value = getValue(key);
            } else {
                value = getValueLocked(key);
            }
            if (value == null) {
                cacheManager.getStatBean(this).addMisses(1);
                if (config.isReadThrough()) {
                    value = loadValue(key);
                }
            } else {
                cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                cacheManager.getStatBean(this).addHits(1);
            }
            return value;
        } finally {
            lock.unlock();
        }
    }
    
    V getValueLocked(K key) {
        
        V value = evalWrite(getName(), codec, RedisCommands.EVAL_MAP_VALUE,
                "local value = redis.call('hget', KEYS[1], ARGV[3]); "
              + "if value == false then "
                  + "return nil; "
              + "end; "
                  
              + "local expireDate = 92233720368547758; "
              + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[3]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
              
              + "if expireDate <= tonumber(ARGV[2]) then "
                  + "return nil; "
              + "end; "
              + "return value; ",
              Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName()), 
              0, System.currentTimeMillis(), encodeMapKey(key));
        
        if (value != null) {
            Long accessTimeout = getAccessTimeout();
            if (accessTimeout == -1) {
                return value;
            }

            List<Object> result = new ArrayList<Object>(3);
            result.add(value);
            double syncId = PlatformDependent.threadLocalRandom().nextDouble();
            Long syncs = evalWrite(getName(), codec, RedisCommands.EVAL_LONG,
                "if ARGV[1] == '0' then "
                  + "redis.call('hdel', KEYS[1], ARGV[3]); "
                  + "redis.call('zrem', KEYS[2], ARGV[3]); "
                  + "local value = redis.call('hget', KEYS[1], ARGV[3]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(tostring(value)), tostring(value)); "
                  + "redis.call('publish', KEYS[3], msg); "
                  + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[3]), ARGV[3], string.len(tostring(value)), tostring(value), ARGV[4]); "
                  + "local syncs = redis.call('publish', KEYS[4], syncMsg); "
                  + "return syncs;"
              + "elseif ARGV[1] ~= '-1' then " 
                  + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[3]); "
                  + "return 0;"
              + "end; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(),
                     getRemovedSyncChannelName()), 
             accessTimeout, System.currentTimeMillis(), encodeMapKey(key), syncId);
            
            result.add(syncs);
            result.add(syncId);
            
            waitSync(result);
            return value;
        }

        return value;
    }

    private V getValue(K key) {
        Long accessTimeout = getAccessTimeout();
        
        if (accessTimeout == -1) {
            V value = evalRead(getName(), codec, RedisCommands.EVAL_MAP_VALUE,
                    "local value = redis.call('hget', KEYS[1], ARGV[3]); "
                  + "if value == false then "
                      + "return nil; "
                  + "end; "
                      
                  + "local expireDate = 92233720368547758; "
                  + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[3]); "
                  + "if expireDateScore ~= false then "
                      + "expireDate = tonumber(expireDateScore); "
                  + "end; "
                  
                  + "if expireDate <= tonumber(ARGV[2]) then "
                      + "return nil; "
                  + "end; "
                  
                  + "return value; ",
                 Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName()), 
                 accessTimeout, System.currentTimeMillis(), encodeMapKey(key));
            return value;
        }
        
        V value = evalWrite(getName(), codec, RedisCommands.EVAL_MAP_VALUE,
                "local value = redis.call('hget', KEYS[1], ARGV[3]); "
              + "if value == false then "
                  + "return nil; "
              + "end; "
                  
              + "local expireDate = 92233720368547758; "
              + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[3]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
              
              + "if expireDate <= tonumber(ARGV[2]) then "
                  + "return nil; "
              + "end; "
              
              + "if ARGV[1] == '0' then "
                  + "redis.call('hdel', KEYS[1], ARGV[3]); "
                  + "redis.call('zrem', KEYS[2], ARGV[3]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(tostring(value)), tostring(value)); "
                  + "redis.call('publish', KEYS[3], msg); "
              + "elseif ARGV[1] ~= '-1' then " 
                  + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[3]); "
              + "end; "

              + "return value; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName()), 
             accessTimeout, System.currentTimeMillis(), encodeMapKey(key));
        return value;
    }

    private Long getAccessTimeout() {
        if (config.getExpiryPolicy().getExpiryForAccess() == null) {
            return -1L;
        }
        Long accessTimeout = config.getExpiryPolicy().getExpiryForAccess().getAdjustedTime(System.currentTimeMillis());

        if (config.getExpiryPolicy().getExpiryForAccess().isZero()) {
            accessTimeout = 0L;
        } else if (accessTimeout.longValue() == Long.MAX_VALUE) {
            accessTimeout = -1L;
        }
        return accessTimeout;
    }

    V load(K key) {
        RLock lock = getLockedLock(key);
        try {
            V value;
            if (atomicExecution) {
                value = getValue(key);
            } else {
                value = getValueLocked(key);
            }
            if (value == null) {
                value = loadValue(key);
            }
            return value;
        } finally {
            lock.unlock();
        }
    }

    private V loadValue(K key) {
        V value = null;
        try {
            value = cacheLoader.load(key);
        } catch (Exception ex) {
            throw new CacheLoaderException(ex);
        }
        if (value != null) {
            long startTime = currentNanoTime();
            if (atomicExecution) {
                putValue(key, value);
            } else {
                putValueLocked(key, value);
            }
            cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
        }
        return value;
    }
    
    private <T, R> R write(String key, RedisCommand<T> command, Object ... params) {
        RFuture<R> future = commandExecutor.writeAsync(key, command, params);
        try {
            return get(future);
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }
    
    private <T, R> R evalWrite(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        RFuture<R> future = commandExecutor.evalWriteAsync(key, codec, evalCommandType, script, keys, params);
        try {
            return get(future);
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }
    
    private <T, R> R evalRead(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params) {
        RFuture<R> future = commandExecutor.evalReadAsync(key, codec, evalCommandType, script, keys, params);
        try {
            return get(future);
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }
    
    private boolean putValueLocked(K key, Object value) {
        double syncId = PlatformDependent.threadLocalRandom().nextDouble();
        
        if (containsKey(key)) {
            Long updateTimeout = getUpdateTimeout();
            List<Object> res = evalWrite(getName(), codec, RedisCommands.EVAL_LIST,
                        "if ARGV[2] == '0' then "
                          + "redis.call('hdel', KEYS[1], ARGV[4]); "
                          + "redis.call('zrem', KEYS[2], ARGV[4]); "
                          + "local value = redis.call('hget', KEYS[1], ARGV[4]);"
                          + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(tostring(value)), tostring(value)); "
                          + "redis.call('publish', KEYS[4], msg); "
                          + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(tostring(value)), tostring(value), ARGV[6]); "
                          + "local syncs = redis.call('publish', KEYS[7], syncMsg); "
                          + "return {0, syncs};"
                      + "elseif ARGV[2] ~= '-1' then "
                          + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                          + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[4]); "
                          + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "                      
                          + "redis.call('publish', KEYS[5], msg); "
                          + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                          + "local syncs = redis.call('publish', KEYS[8], syncMsg); "
                          + "return {1, syncs};"
                      + "else "
                          + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                          + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                          + "redis.call('publish', KEYS[5], msg); "
                          + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                          + "local syncs = redis.call('publish', KEYS[8], syncMsg); "
                          + "return {1, syncs};"
                      + "end; ",
                 Arrays.<Object>asList(getName(), getTimeoutSetName(), getCreatedChannelName(), getRemovedChannelName(), getUpdatedChannelName(),
                         getCreatedSyncChannelName(), getRemovedSyncChannelName(), getUpdatedSyncChannelName()), 
                 0, updateTimeout, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), syncId);
            
            res.add(syncId);
            waitSync(res);
            
            return (Long) res.get(0) == 1;
        }
        
        Long creationTimeout = getCreationTimeout();
        if (creationTimeout == 0) {
            return false;
        }
        List<Object> res = evalWrite(getName(), codec, RedisCommands.EVAL_LIST,
                    "if ARGV[1] ~= '-1' then "
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                      + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[4]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                      + "redis.call('publish', KEYS[3], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[6], syncMsg); "
                      + "return {1, syncs};"
                  + "else "
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                      + "redis.call('publish', KEYS[3], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[6], syncMsg); "
                      + "return {1, syncs};"
                  + "end; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getCreatedChannelName(), getRemovedChannelName(), getUpdatedChannelName(),
                     getCreatedSyncChannelName(), getRemovedSyncChannelName(), getUpdatedSyncChannelName()), 
             creationTimeout, 0, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), syncId);
        
        res.add(syncId);
        waitSync(res);
        
        return (Long) res.get(0) == 1;

    }


    private boolean putValue(K key, Object value) {
        double syncId = PlatformDependent.threadLocalRandom().nextDouble();
        Long creationTimeout = getCreationTimeout();
        Long updateTimeout = getUpdateTimeout();
        
        List<Object> res = evalWrite(getName(), codec, RedisCommands.EVAL_LIST,
                "if redis.call('hexists', KEYS[1], ARGV[4]) == 1 then "
                  + "if ARGV[2] == '0' then "
                      + "redis.call('hdel', KEYS[1], ARGV[4]); "
                      + "redis.call('zrem', KEYS[2], ARGV[4]); "
                      + "local value = redis.call('hget', KEYS[1], ARGV[4]);"
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(tostring(value)), tostring(value)); "
                      + "redis.call('publish', KEYS[4], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(tostring(value)), tostring(value), ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[7], syncMsg); "
                      + "return {0, syncs};"
                  + "elseif ARGV[2] ~= '-1' then "
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                      + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[4]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "                      
                      + "redis.call('publish', KEYS[5], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[8], syncMsg); "
                      + "return {1, syncs};"
                  + "else "
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                      + "redis.call('publish', KEYS[5], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[8], syncMsg); "
                      + "return {1, syncs};"
                  + "end; "
              + "else "
                  + "if ARGV[1] == '0' then "
                      + "return {0};"
                  + "elseif ARGV[1] ~= '-1' then "
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                      + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[4]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                      + "redis.call('publish', KEYS[3], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[6], syncMsg); "
                      + "return {1, syncs};"
                  + "else "
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                      + "redis.call('publish', KEYS[3], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[6], syncMsg); "
                      + "return {1, syncs};"
                  + "end; "
              + "end; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getCreatedChannelName(), getRemovedChannelName(), getUpdatedChannelName(),
                     getCreatedSyncChannelName(), getRemovedSyncChannelName(), getUpdatedSyncChannelName()), 
             creationTimeout, updateTimeout, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), syncId);
        
        res.add(syncId);
        waitSync(res);
        
        return (Long) res.get(0) == 1;
    }

    private Long getUpdateTimeout() {
        if (config.getExpiryPolicy().getExpiryForUpdate() == null) {
            return -1L;
        }
        
        Long updateTimeout = config.getExpiryPolicy().getExpiryForUpdate().getAdjustedTime(System.currentTimeMillis());
        if (config.getExpiryPolicy().getExpiryForUpdate().isZero()) {
            updateTimeout = 0L;
        } else if (updateTimeout.longValue() == Long.MAX_VALUE) {
            updateTimeout = -1L;
        }
        return updateTimeout;
    }

    private Long getCreationTimeout() {
        if (config.getExpiryPolicy().getExpiryForCreation() == null) {
            return -1L;
        }
        Long creationTimeout = config.getExpiryPolicy().getExpiryForCreation().getAdjustedTime(System.currentTimeMillis());
        if (config.getExpiryPolicy().getExpiryForCreation().isZero()) {
            creationTimeout = 0L;
        } else if (creationTimeout.longValue() == Long.MAX_VALUE) {
            creationTimeout = -1L;
        }
        return creationTimeout;
    }
    
    private boolean putIfAbsentValue(K key, Object value) {
        Long creationTimeout = getCreationTimeout();
        if (creationTimeout == 0) {
            return false;
        }
        
        return evalWrite(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('hexists', KEYS[1], ARGV[2]) == 1 then "
                  + "return 0; "
              + "else "
                  + "if ARGV[1] ~= '-1' then "
                      + "redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); "                                  
                      + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[2]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3]); "
                      + "redis.call('publish', KEYS[3], msg); "
                      + "return 1;"
                  + "else "
                      + "redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3]); "
                      + "redis.call('publish', KEYS[3], msg); "                  
                      + "return 1;"
                  + "end; "
              + "end; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getCreatedChannelName()), 
             creationTimeout, encodeMapKey(key), encodeMapValue(value));
    }
    
    private boolean putIfAbsentValueLocked(K key, Object value) {
        if (containsKey(key)) {
            return false;
        }
        
        Long creationTimeout = getCreationTimeout();
        if (creationTimeout == 0) {
            return false;
        }
        return evalWrite(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "if ARGV[1] ~= '-1' then "
                      + "redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); "                                  
                      + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[2]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3]); "
                      + "redis.call('publish', KEYS[3], msg); "
                      + "return 1;"
                  + "else "
                      + "redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3]); "
                      + "redis.call('publish', KEYS[3], msg); "                  
                      + "return 1;"
                  + "end; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getCreatedChannelName()), 
             creationTimeout, encodeMapKey(key), encodeMapValue(value));
    }

    
    private String getLockName(Object key) {
        ByteBuf keyState = encodeMapKey(key);
        try {
            return "{" + getName() + "}:" + Hash.hash128toBase64(keyState) + ":key";
        } finally {
            keyState.release();
        }
    }

    @Override
    public Map<K, V> getAll(Set<? extends K> keys) {
        checkNotClosed();
        if (keys == null) {
            throw new NullPointerException();
        }
        for (K key : keys) {
            if (key == null) {
                throw new NullPointerException();
            }
        }

        long startTime = currentNanoTime();
        if (!config.isReadThrough()) {
            boolean exists = false;
            for (K key : keys) {
                if (containsKey(key)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                return Collections.emptyMap();
            }
        }
        
        
        Long accessTimeout = getAccessTimeout();
        
        List<Object> args = new ArrayList<Object>(keys.size() + 2);
        args.add(accessTimeout);
        args.add(System.currentTimeMillis());
        encode(args, keys);
        
        Map<K, V> res;
        if (accessTimeout == -1) {
            res = evalRead(getName(), codec, new RedisCommand<Map<Object, Object>>("EVAL", new MapGetAllDecoder(new ArrayList<Object>(keys), 0, true), ValueType.MAP_VALUE),
                    "local expireHead = redis.call('zrange', KEYS[2], 0, 0, 'withscores');"
                  + "local accessTimeout = ARGV[1]; "
                  + "local currentTime = tonumber(ARGV[2]); "
                  + "local hasExpire = #expireHead == 2 and tonumber(expireHead[2]) <= currentTime; "
                  + "local map = redis.call('hmget', KEYS[1], unpack(ARGV, 3, #ARGV)); "
                  + "local result = {};"
                  + "for i, value in ipairs(map) do "
                      + "if value ~= false then "
                          + "local key = ARGV[i+2]; "

                          + "if hasExpire then "
                              + "local expireDate = 92233720368547758; "
                              + "local expireDateScore = redis.call('zscore', KEYS[2], key); "
                              + "if expireDateScore ~= false then "
                                  + "expireDate = tonumber(expireDateScore); "
                              + "end; "
                              + "if expireDate <= currentTime then "
                                  + "value = false; "
                              + "end; "
                          + "end; "
                      + "end; "

                      + "table.insert(result, value); "
                  + "end; "
                  + "return result;",
            Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName()), args.toArray());
        } else {
            res = evalWrite(getName(), codec, new RedisCommand<Map<Object, Object>>("EVAL", new MapGetAllDecoder(new ArrayList<Object>(keys), 0, true), ValueType.MAP_VALUE),
                            "local expireHead = redis.call('zrange', KEYS[2], 0, 0, 'withscores');"
                          + "local accessTimeout = ARGV[1]; "
                          + "local currentTime = tonumber(ARGV[2]); "
                          + "local hasExpire = #expireHead == 2 and tonumber(expireHead[2]) <= currentTime; "
                          + "local map = redis.call('hmget', KEYS[1], unpack(ARGV, 3, #ARGV)); "
                          + "local result = {};"
                          + "for i, value in ipairs(map) do "
                              + "if value ~= false then "
                                  + "local key = ARGV[i+2]; "

                                  + "if hasExpire then "
                                      + "local expireDate = 92233720368547758; "
                                      + "local expireDateScore = redis.call('zscore', KEYS[2], key); "
                                      + "if expireDateScore ~= false then "
                                          + "expireDate = tonumber(expireDateScore); "
                                      + "end; "
                                      + "if expireDate <= currentTime then "
                                          + "value = false; "
                                      + "end; "
                                  + "end; "
                                      
                                  + "if accessTimeout == '0' then "
                                      + "redis.call('hdel', KEYS[1], key); "
                                      + "redis.call('zrem', KEYS[2], key); "
                                      + "local msg = struct.pack('Lc0Lc0', string.len(key), key, string.len(value), value); "
                                      + "redis.call('publish', KEYS[3], {key, value}); "
                                  + "elseif accessTimeout ~= '-1' then " 
                                      + "redis.call('zadd', KEYS[2], accessTimeout, key); "
                                  + "end; "
                              + "end; "

                              + "table.insert(result, value); "
                          + "end; "
                          + "return result;",
                    Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName()), args.toArray());            
        }

        
        Map<K, V> result = new HashMap<K, V>();
        for (Map.Entry<K, V> entry : res.entrySet()) {
            if (entry.getValue() != null) {
                cacheManager.getStatBean(this).addHits(1);
                result.put(entry.getKey(), entry.getValue());
            } else {
                if (config.isReadThrough()) {
                    cacheManager.getStatBean(this).addMisses(1);
                    V value = load(entry.getKey());
                    if (value != null) {
                        result.put(entry.getKey(), value);
                    }
                }
            }
        }
        
        cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);

        return result;
    }

    @Override
    public boolean containsKey(K key) {
        checkNotClosed();
        if (key == null) {
            throw new NullPointerException();
        }

        return evalRead(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                  "if redis.call('hexists', KEYS[1], ARGV[2]) == 0 then "
                    + "return 0;"
                + "end;"
                      
                + "local expireDate = 92233720368547758; "
                + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
                + "if expireDateScore ~= false then "
                    + "expireDate = tonumber(expireDateScore); "
                + "end; "
                    
                + "if expireDate <= tonumber(ARGV[1]) then "
                    + "return 0; "
                + "end; "
                + "return 1;",
             Arrays.<Object>asList(getName(), getTimeoutSetName()), 
             System.currentTimeMillis(), encodeMapKey(key));
    }

    @Override
    public void loadAll(final Set<? extends K> keys, final boolean replaceExistingValues, final CompletionListener completionListener) {
        checkNotClosed();
        if (keys == null) {
            throw new NullPointerException();
        }
        
        for (K key : keys) {
            if (key == null) {
                throw new NullPointerException();
            }
        }

        if (cacheLoader == null) {
            if (completionListener != null) {
                completionListener.onCompletion();
            }
            return;
        }

        commandExecutor.getConnectionManager().getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                for (K key : keys) {
                    try {
                        if (!containsKey(key) || replaceExistingValues) {
                            RLock lock = getLockedLock(key);
                            try {
                                if (!containsKey(key)|| replaceExistingValues) {
                                    V value;
                                    try {
                                        value = cacheLoader.load(key);
                                    } catch (Exception ex) {
                                        throw new CacheLoaderException(ex);
                                    }
                                    if (value != null) {
                                        if (atomicExecution) {
                                            putValue(key, value);
                                        } else {
                                            putValueLocked(key, value);
                                        }
                                    }
                                }
                            } finally {
                                lock.unlock();
                            }
                        }
                    } catch (Exception e) {
                        if (completionListener != null) {
                            completionListener.onException(e);
                        }
                        return;
                    }
                }
                if (completionListener != null) {
                    completionListener.onCompletion();
                }
            }
        });
    }
    
    private RLock getLockedLock(K key) {
        if (atomicExecution) {
            return DUMMY_LOCK;
        }
        
        String lockName = getLockName(key);
        RLock lock = redisson.getLock(lockName);
        try {
            lock.lock();
        } catch (Exception e) {
            throw new CacheException(e);
        }
        return lock;
    }


    @Override
    public void put(K key, V value) {
        checkNotClosed();
        if (key == null) {
            throw new NullPointerException();
        }
        if (value == null) {
            throw new NullPointerException();
        }
        
        long startTime = currentNanoTime();
        if (config.isWriteThrough()) {
            RLock lock = getLockedLock(key);
            try {
                List<Object> result;
                if (atomicExecution) {
                    result = getAndPutValue(key, value);
                } else {
                    result = getAndPutValueLocked(key, value);
                }
                if (result.isEmpty()) {
                    cacheManager.getStatBean(this).addPuts(1);
                    cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                    return;
                }
                Long added = (Long) result.get(0);
                if (added == null) {
                    cacheManager.getStatBean(this).addPuts(1);
                    cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                    return;
                }
                
                if (Long.valueOf(1).equals(added)) {
                    try {
                        cacheWriter.write(new JCacheEntry<K, V>(key, value));
                    } catch (CacheWriterException e) {
                        removeValues(key);
                        throw e;
                    } catch (Exception e) {
                        removeValues(key);
                        throw new CacheWriterException(e);
                    }
                } else {
                    try {
                        cacheWriter.delete(key);
                    } catch (CacheWriterException e) {
                        if (result.size() == 4 && result.get(1) != null) {
                            putValue(key, result.get(1));
                        }
                        throw e;
                    } catch (Exception e) {
                        if (result.size() == 4 && result.get(1) != null) {
                            putValue(key, result.get(1));
                        }
                        throw new CacheWriterException(e);
                    }
                }
                cacheManager.getStatBean(this).addPuts(1);
            } finally {
                lock.unlock();
            }
        } else {
            RLock lock = getLockedLock(key);
            try {
                boolean result;
                if (atomicExecution) {
                    result = putValue(key, value);
                } else {
                    result = putValueLocked(key, value);
                }
                if (result) {
                    cacheManager.getStatBean(this).addPuts(1);
                }
            } finally {
                lock.unlock();
            }
        }
        cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
    }
    
    private long removeValues(Object... keys) {
        List<Object> params = new ArrayList<Object>(keys.length);
        encodeMapKeys(params, Arrays.asList(keys));
        return evalWrite(getName(), codec, RedisCommands.EVAL_LONG,
                  "redis.call('zrem', KEYS[2], unpack(ARGV)); "
                + "return redis.call('hdel', KEYS[1], unpack(ARGV)); ",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), params.toArray());
    }

    private List<Object> getAndPutValueLocked(K key, V value) {
        double syncId = PlatformDependent.threadLocalRandom().nextDouble();
        if (containsKey(key)) {
            Long updateTimeout = getUpdateTimeout();
            List<Object> result = evalWrite(getName(), codec, RedisCommands.EVAL_LIST,
                        "local value = redis.call('hget', KEYS[1], ARGV[4]);"
                      + "if ARGV[2] == '0' then "
                          + "redis.call('hdel', KEYS[1], ARGV[4]); "
                          + "redis.call('zrem', KEYS[2], ARGV[4]); "
                          + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(tostring(value)), tostring(value)); "
                          + "redis.call('publish', KEYS[3], msg); "
                          + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(tostring(value)), tostring(value), ARGV[6]); "
                          + "local syncs = redis.call('publish', KEYS[6], syncMsg); "
                          + "return {0, value, syncs};"
                      + "elseif ARGV[2] ~= '-1' then " 
                          + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                          + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[4]); "
                          + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                          + "redis.call('publish', KEYS[5], msg); "
                          + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                          + "local syncs = redis.call('publish', KEYS[8], syncMsg); "
                          + "return {1, value, syncs};"
                      + "else " 
                          + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                          + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                          + "redis.call('publish', KEYS[5], msg); "
                          + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                          + "local syncs = redis.call('publish', KEYS[8], syncMsg); "
                          + "return {1, value, syncs};"
                      + "end; ",
                 Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getCreatedChannelName(), getUpdatedChannelName(),
                         getRemovedSyncChannelName(), getCreatedSyncChannelName(), getUpdatedSyncChannelName()), 
                 0, updateTimeout, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), syncId);
            
            result.add(syncId);
            waitSync(result);
            return result;
        }
        
        Long creationTimeout = getCreationTimeout();
        if (creationTimeout == 0) {
            return Collections.emptyList();
        }
        List<Object> result = evalWrite(getName(), codec, RedisCommands.EVAL_LIST,
                    "if ARGV[1] ~= '-1' then "
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                      + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[4]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                      + "redis.call('publish', KEYS[3], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[4], syncMsg); "
                      + "return {1, syncs};"
                  + "else " 
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                      + "redis.call('publish', KEYS[3], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[4], syncMsg); "
                      + "return {1, syncs};"
                  + "end; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getCreatedChannelName(), getCreatedSyncChannelName()), 
             creationTimeout, 0, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), syncId);
        
        result.add(syncId);
        waitSync(result);
        return result;
    }
    
    private List<Object> getAndPutValue(K key, V value) {
        Long creationTimeout = getCreationTimeout();
        
        Long updateTimeout = getUpdateTimeout();
        
        double syncId = PlatformDependent.threadLocalRandom().nextDouble();
        
        List<Object> result = evalWrite(getName(), codec, RedisCommands.EVAL_LIST,
                "local value = redis.call('hget', KEYS[1], ARGV[4]);"
              + "if value ~= false then "
                  + "if ARGV[2] == '0' then "
                      + "redis.call('hdel', KEYS[1], ARGV[4]); "
                      + "redis.call('zrem', KEYS[2], ARGV[4]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(tostring(value)), tostring(value)); "
                      + "redis.call('publish', KEYS[3], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(tostring(value)), tostring(value), ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[6], syncMsg); "
                      + "return {0, value, syncs};"
                  + "elseif ARGV[2] ~= '-1' then " 
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                      + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[4]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                      + "redis.call('publish', KEYS[5], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[8], syncMsg); "
                      + "return {1, value, syncs};"
                  + "else " 
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                      + "redis.call('publish', KEYS[5], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[8], syncMsg); "
                      + "return {1, value, syncs};"
                  + "end; "
              + "else "
                  + "if ARGV[1] == '0' then "
                      + "return {nil};"                      
                  + "elseif ARGV[1] ~= '-1' then "
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "                                  
                      + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[4]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                      + "redis.call('publish', KEYS[4], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[7], syncMsg); "
                      + "return {1, syncs};"
                  + "else " 
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[5]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5]); "
                      + "redis.call('publish', KEYS[4], msg); "
                      + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[5]), ARGV[5], ARGV[6]); "
                      + "local syncs = redis.call('publish', KEYS[7], syncMsg); "
                      + "return {1, syncs};"
                  + "end; "
              + "end; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getCreatedChannelName(), getUpdatedChannelName(), 
                     getRemovedSyncChannelName(), getCreatedSyncChannelName(), getUpdatedSyncChannelName()), 
             creationTimeout, updateTimeout, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), syncId);
        
        if (!result.isEmpty()) {
            result.add(syncId);
        }
        
        return result;
    }
    
    @Override
    public V getAndPut(K key, V value) {
        checkNotClosed();
        if (key == null) {
            throw new NullPointerException();
        }
        if (value == null) {
            throw new NullPointerException();
        }
        
        long startTime = currentNanoTime();
        if (config.isWriteThrough()) {
            RLock lock = getLockedLock(key);
            try {
                List<Object> result;
                if (atomicExecution) {
                    result = getAndPutValue(key, value);
                } else {
                    result = getAndPutValueLocked(key, value);
                }
                if (result.isEmpty()) {
                    cacheManager.getStatBean(this).addPuts(1);
                    cacheManager.getStatBean(this).addMisses(1);
                    cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                    cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                    return null;
                }
                Long added = (Long) result.get(0);
                if (added == null) {
                    cacheManager.getStatBean(this).addPuts(1);
                    cacheManager.getStatBean(this).addHits(1);
                    cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                    cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                    return (V) result.get(1);
                }
                
                if (Long.valueOf(1).equals(added)) {
                    try {
                        cacheWriter.write(new JCacheEntry<K, V>(key, value));
                    } catch (CacheWriterException e) {
                        removeValues(key);
                        throw e;
                    } catch (Exception e) {
                        removeValues(key);
                        throw new CacheWriterException(e);
                    }
                } else {
                    try {
                        cacheWriter.delete(key);
                    } catch (CacheWriterException e) {
                        if (result.size() == 4 && result.get(1) != null) {
                            putValue(key, result.get(1));
                        }
                        throw e;
                    } catch (Exception e) {
                        if (result.size() == 4 && result.get(1) != null) {
                            putValue(key, result.get(1));
                        }
                        throw new CacheWriterException(e);
                    }
                }
                return getAndPutResult(startTime, result);
            } finally {
                lock.unlock();
            }
        } else {
            RLock lock = getLockedLock(key);
            try {
                List<Object> result;
                if (atomicExecution) {
                    result = getAndPutValue(key, value);
                } else {
                    result = getAndPutValueLocked(key, value);
                }
                return getAndPutResult(startTime, result);
            } finally {
                lock.unlock();
            }
        }
    }

    private V getAndPutResult(long startTime, List<Object> result) {
        if (result.size() != 4) {
            cacheManager.getStatBean(this).addPuts(1);
            cacheManager.getStatBean(this).addMisses(1);
            cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
            cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
            return null;
        }
        cacheManager.getStatBean(this).addPuts(1);
        cacheManager.getStatBean(this).addHits(1);
        cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
        cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
        return (V) result.get(1);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        checkNotClosed();
        Map<K, V> deletedKeys = new HashMap<K, V>();
        Map<K, Cache.Entry<? extends K, ? extends V>> addedEntries = new HashMap<K, Cache.Entry<? extends K, ? extends V>>();

        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            K key = entry.getKey();
            if (key == null) {
                throw new NullPointerException();
            }
            V value = entry.getValue();
            if (value == null) {
                throw new NullPointerException();
            }
        }
        
        List<RLock> lockedLocks = new ArrayList<RLock>();
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            K key = entry.getKey();
            V value = entry.getValue();
            
            long startTime = currentNanoTime();
            if (config.isWriteThrough()) {
                RLock lock = getLockedLock(key);
                lockedLocks.add(lock);
                
                List<Object> result = getAndPutValue(key, value);
                if (result.isEmpty()) {
                    cacheManager.getStatBean(this).addPuts(1);
                    cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                    continue;
                }
                Long added = (Long) result.get(0);
                if (added == null) {
                    cacheManager.getStatBean(this).addPuts(1);
                    cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                    continue;
                }
                
                if (Long.valueOf(1).equals(added)) {
                    addedEntries.put(key, new JCacheEntry<K, V>(key, value));
                } else {
                    V val = null;
                    if (result.size() == 4) {
                        val = (V) result.get(1);
                    }
                    
                    deletedKeys.put(key, val);
                }
                cacheManager.getStatBean(this).addPuts(1);
                waitSync(result);
            } else {
                boolean result = putValue(key, value);
                if (result) {
                    cacheManager.getStatBean(this).addPuts(1);
                }
            }
            cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
        }
        
        if (config.isWriteThrough()) {
            try {
                try {
                    cacheWriter.writeAll(addedEntries.values());
                } catch (CacheWriterException e) {
                    removeValues(addedEntries.keySet().toArray());
                    throw e;
                } catch (Exception e) {
                    removeValues(addedEntries.keySet().toArray());
                    throw new CacheWriterException(e);
                }
                
                try {
                    cacheWriter.deleteAll(deletedKeys.keySet());
                } catch (CacheWriterException e) {
                    for (Map.Entry<K, V> deletedEntry : deletedKeys.entrySet()) {
                        if (deletedEntry.getValue() != null) {
                            putValue(deletedEntry.getKey(), deletedEntry.getValue());
                        }
                    }
                    throw e;
                } catch (Exception e) {
                    for (Map.Entry<K, V> deletedEntry : deletedKeys.entrySet()) {
                        if (deletedEntry.getValue() != null) {
                            putValue(deletedEntry.getKey(), deletedEntry.getValue());
                        }
                    }
                    throw new CacheWriterException(e);
                }
            } finally {
                for (RLock lock : lockedLocks) {
                    lock.unlock();
                }
            }
        }
    }
    
    void waitSync(List<Object> result) {
        if (result.size() < 2) {
            return;
        }
        
        Long syncs = (Long) result.get(result.size() - 2);
        Double syncId = (Double) result.get(result.size() - 1);
        if (syncs != null && syncs > 0) {
            RSemaphore semaphore = redisson.getSemaphore(getSyncName(syncId));
            try {
                semaphore.acquire(syncs.intValue());
                semaphore.delete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        checkNotClosed();
        if (key == null) {
            throw new NullPointerException();
        }
        if (value == null) {
            throw new NullPointerException();
        }
        
        long startTime = currentNanoTime();
        if (config.isWriteThrough()) {
            RLock lock = getLockedLock(key);
            try {
                boolean result;
                if (atomicExecution) {
                    result = putIfAbsentValue(key, value);
                } else {
                    result = putIfAbsentValueLocked(key, value);
                }
                if (result) {
                    cacheManager.getStatBean(this).addPuts(1);
                    try {
                        cacheWriter.write(new JCacheEntry<K, V>(key, value));
                    } catch (CacheWriterException e) {
                        removeValues(key);
                        throw e;
                    } catch (Exception e) {
                        removeValues(key);
                        throw new CacheWriterException(e);
                    }
                }
                cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                return result;
            } finally {
                lock.unlock();
            }
        } else {
            RLock lock = getLockedLock(key);
            try {
                boolean result;
                if (atomicExecution) {
                    result = putIfAbsentValue(key, value);
                } else {
                    result = putIfAbsentValueLocked(key, value);
                }
                if (result) {
                    cacheManager.getStatBean(this).addPuts(1);
                }
                cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                return result;
            } finally {
                lock.unlock();
            }
        }
    }
    
    private boolean removeValue(K key) {
        double syncId = PlatformDependent.threadLocalRandom().nextDouble();
        
        List<Object> res = evalWrite(getName(), codec, RedisCommands.EVAL_LIST,
                "local value = redis.call('hexists', KEYS[1], ARGV[2]); "
              + "if value == 0 then "
                  + "return {0}; "
              + "end; "
                  
              + "local expireDate = 92233720368547758; "
              + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
          
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return {0}; "
              + "end; "

              + "value = redis.call('hget', KEYS[1], ARGV[2]); "
              + "redis.call('hdel', KEYS[1], ARGV[2]); "
              + "redis.call('zrem', KEYS[2], ARGV[2]); "
              + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(tostring(value)), tostring(value)); "
              + "redis.call('publish', KEYS[3], msg); "
              + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[2]), ARGV[2], string.len(tostring(value)), tostring(value), ARGV[3]); "
              + "local syncs = redis.call('publish', KEYS[4], syncMsg); "
              + "return {1, syncs};",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getRemovedSyncChannelName()), 
             System.currentTimeMillis(), encodeMapKey(key), syncId);
        
        res.add(syncId);
        waitSync(res);
        
        return (Long) res.get(0) == 1;
    }


    @Override
    public boolean remove(K key) {
        checkNotClosed();
        if (key == null) {
            throw new NullPointerException();
        }

        long startTime = System.currentTimeMillis();
        if (config.isWriteThrough()) {
            RLock lock = getLockedLock(key);
            try {
                V oldValue = getAndRemoveValue(key);
                try {
                    cacheWriter.delete(key);
                } catch (CacheWriterException e) {
                    if (oldValue != null) {
                        putValue(key, oldValue);
                    }
                    throw e;
                } catch (Exception e) {
                    if (oldValue != null) {
                        putValue(key, oldValue);
                    }
                    throw new CacheWriterException(e);
                }
                if (oldValue != null) {
                    cacheManager.getStatBean(this).addRemovals(1);
                }
                cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                return oldValue != null;
            } finally {
                lock.unlock();
            }
        } else {
            boolean result = removeValue(key);
            if (result) {
                cacheManager.getStatBean(this).addRemovals(1);
            }
            cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
            return result;
        }
        
    }

    private boolean removeValueLocked(K key, V value) {
        
        Boolean result = evalWrite(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local value = redis.call('hget', KEYS[1], ARGV[3]); "
              + "if value == false then "
                  + "return 0; "
              + "end; "
                  
              + "local expireDate = 92233720368547758; "
              + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[3]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
          
              + "if expireDate <= tonumber(ARGV[2]) then "
                  + "return 0; "
              + "end; "
          
              + "if ARGV[4] == value then "
                  + "redis.call('hdel', KEYS[1], ARGV[3]); "
                  + "redis.call('zrem', KEYS[2], ARGV[3]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(tostring(value)), tostring(value)); "
                  + "redis.call('publish', KEYS[3], msg); "
                  + "return 1; "
              + "end; "
              + "return nil;",
              Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName()), 
              0, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value));

        if (result == null) {
            Long accessTimeout = getAccessTimeout();
            if (accessTimeout == -1) {
                return false;
            }
            return evalWrite(getName(), codec, RedisCommands.EVAL_BOOLEAN,
              "if ARGV[1] == '0' then "
                + "redis.call('hdel', KEYS[1], ARGV[3]); "
                + "redis.call('zrem', KEYS[2], ARGV[3]); "
                + "local value = redis.call('hget', KEYS[1], ARGV[3]); " 
                + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(tostring(value)), tostring(value)); "
                + "redis.call('publish', KEYS[3], msg); "
            + "elseif ARGV[1] ~= '-1' then " 
                + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[3]); "
            + "end; ",
           Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName()), 
           accessTimeout, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value));            
        }

        return result;
    }
    
    private boolean removeValue(K key, V value) {
        Long accessTimeout = getAccessTimeout();
        
        return evalWrite(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local value = redis.call('hget', KEYS[1], ARGV[3]); "
              + "if value == false then "
                  + "return 0; "
              + "end; "
                  
              + "local expireDate = 92233720368547758; "
              + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[3]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
          
              + "if expireDate <= tonumber(ARGV[2]) then "
                  + "return 0; "
              + "end; "

              + "if ARGV[4] == value then "
                  + "redis.call('hdel', KEYS[1], ARGV[3]); "
                  + "redis.call('zrem', KEYS[2], ARGV[3]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(tostring(value)), tostring(value)); "
                  + "redis.call('publish', KEYS[3], msg); "
                  + "return 1; "
              + "end; "
              
              + "if ARGV[1] == '0' then "
                  + "redis.call('hdel', KEYS[1], ARGV[3]); "
                  + "redis.call('zrem', KEYS[2], ARGV[3]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(tostring(value)), tostring(value)); "
                  + "redis.call('publish', KEYS[3], msg); "
              + "elseif ARGV[1] ~= '-1' then " 
                  + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[3]); "
              + "end; "
              + "return 0; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName()), 
             accessTimeout, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value));
    }

    
    @Override
    public boolean remove(K key, V value) {
        checkNotClosed();
        if (key == null) {
            throw new NullPointerException();
        }
        if (value == null) {
            throw new NullPointerException();
        }

        long startTime = currentNanoTime();
        boolean result;
        if (config.isWriteThrough()) {
            RLock lock = getLockedLock(key);
            try {
                if (atomicExecution) {
                    result = removeValue(key, value);
                } else {
                    result = removeValueLocked(key, value);
                }
                if (result) {
                    try {
                        cacheWriter.delete(key);
                    } catch (CacheWriterException e) {
                        putValue(key, value);
                        throw e;
                    } catch (Exception e) {
                        putValue(key, value);
                        throw new CacheWriterException(e);
                    }
                    cacheManager.getStatBean(this).addHits(1);
                    cacheManager.getStatBean(this).addRemovals(1);
                    cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                    return true;
                } else {
                    cacheManager.getStatBean(this).addMisses(1);
                    cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                    return false;
                }
            } finally {
                lock.unlock();
            }
        } else {
            RLock lock = getLockedLock(key);
            try {
                if (atomicExecution) {
                    result = removeValue(key, value);
                } else {
                    result = removeValueLocked(key, value);
                }
                if (result) {
                    cacheManager.getStatBean(this).addHits(1);
                    cacheManager.getStatBean(this).addRemovals(1);
                } else {
                    cacheManager.getStatBean(this).addMisses(1);
                }
                cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                return result;
            } finally {
                lock.unlock();
            }
        }
    }

    private V getAndRemoveValue(K key) {
        double syncId = PlatformDependent.threadLocalRandom().nextDouble();
        List<Object> result = evalWrite(getName(), codec, RedisCommands.EVAL_MAP_VALUE_LIST,
                "local value = redis.call('hget', KEYS[1], ARGV[2]); "
              + "if value == false then "
                  + "return {nil}; "
              + "end; "
                  
              + "local expireDate = 92233720368547758; "
              + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
          
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return {nil}; "
              + "end; "

              + "redis.call('hdel', KEYS[1], ARGV[2]); "
              + "redis.call('zrem', KEYS[2], ARGV[2]); "
              + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(tostring(value)), tostring(value)); "
              + "redis.call('publish', KEYS[3], msg); "
              + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[2]), ARGV[2], string.len(tostring(value)), tostring(value), ARGV[3]); "
              + "local syncs = redis.call('publish', KEYS[4], syncMsg); "
              + "return {value, syncs}; ",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getRemovedSyncChannelName()), 
                System.currentTimeMillis(), encodeMapKey(key), syncId);
        
        if (result.isEmpty()) {
            return null;
        }
        
        result.add(syncId);
        waitSync(result);
        
        return (V) result.get(0);
    }

    
    @Override
    public V getAndRemove(K key) {
        checkNotClosed();
        if (key == null) {
            throw new NullPointerException();
        }

        long startTime = currentNanoTime();
        if (config.isWriteThrough()) {
            RLock lock = getLockedLock(key);
            try {
                Object value = getAndRemoveValue(key);
                if (value != null) {
                    cacheManager.getStatBean(this).addHits(1);
                    cacheManager.getStatBean(this).addRemovals(1);
                } else {
                    cacheManager.getStatBean(this).addMisses(1);
                }

                try {
                    cacheWriter.delete(key);
                } catch (CacheWriterException e) {
                    if (value != null) {
                        putValue(key, value);
                    }
                    throw e;
                } catch (Exception e) {
                    if (value != null) {
                        putValue(key, value);
                    }
                    throw new CacheWriterException(e);
                }
                cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                return (V) value;
            } finally {
                lock.unlock();
            }
        } else {
            V value = getAndRemoveValue(key);
            if (value != null) {
                cacheManager.getStatBean(this).addHits(1);
                cacheManager.getStatBean(this).addRemovals(1);
            } else {
                cacheManager.getStatBean(this).addMisses(1);
            }
            cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
            cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
            return value;
        }
    }

    private long replaceValueLocked(K key, V oldValue, V newValue) {
        Long res = evalWrite(getName(), codec, RedisCommands.EVAL_LONG,
                "local value = redis.call('hget', KEYS[1], ARGV[4]); "
              + "if value == false then "
                  + "return 0; "
              + "end; "
                  
              + "local expireDate = 92233720368547758; "
              + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[4]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
          
              + "if expireDate <= tonumber(ARGV[3]) then "
                  + "return 0; "
              + "end; "

              + "if ARGV[5] == value then "
                  + "return 1;"
              + "end; "
              + "return -1;",
              Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getUpdatedChannelName()), 
              0, 0, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(oldValue), encodeMapValue(newValue));
             
       if (res == 1) {
           Long updateTimeout = getUpdateTimeout();
           double syncId = PlatformDependent.threadLocalRandom().nextDouble();
           Long syncs = evalWrite(getName(), codec, RedisCommands.EVAL_LONG,
                         "if ARGV[2] == '0' then "
                           + "redis.call('hdel', KEYS[1], ARGV[4]); "
                           + "redis.call('zrem', KEYS[2], ARGV[4]); "
                           + "local value = redis.call('hget', KEYS[1], ARGV[4]); "
                           + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(tostring(value)), tostring(value)); "
                           + "redis.call('publish', KEYS[3], msg); "
                           + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(tostring(value)), tostring(value), ARGV[7]); "
                           + "return redis.call('publish', KEYS[5], syncMsg); "
                       + "elseif ARGV[2] ~= '-1' then " 
                           + "redis.call('hset', KEYS[1], ARGV[4], ARGV[6]); "
                           + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[4]); "
                           + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[6]), ARGV[6]); "
                           + "redis.call('publish', KEYS[4], msg); "
                           + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[6]), ARGV[6], ARGV[7]); "
                           + "return redis.call('publish', KEYS[6], syncMsg); "
                       + "else " 
                           + "redis.call('hset', KEYS[1], ARGV[4], ARGV[6]); "
                           + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[6]), ARGV[6]); "
                           + "redis.call('publish', KEYS[4], msg); "
                           + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(ARGV[6]), ARGV[6], ARGV[7]); "
                           + "return redis.call('publish', KEYS[6], syncMsg); "
                       + "end; ",
                       Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getUpdatedChannelName(),
                               getRemovedSyncChannelName(), getUpdatedSyncChannelName()), 
                       0, updateTimeout, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(oldValue), encodeMapValue(newValue), syncId);
           
           List<Object> result = Arrays.<Object>asList(syncs, syncId);
           waitSync(result);
           
           return res;
       } else if (res == 0) {
           return res;
       }
       
       Long accessTimeout = getAccessTimeout();
       if (accessTimeout == -1) {
           return -1;
       }

       double syncId = PlatformDependent.threadLocalRandom().nextDouble();
       List<Object> result = evalWrite(getName(), codec, RedisCommands.EVAL_LIST,
                "if ARGV[1] == '0' then "
                  + "redis.call('hdel', KEYS[1], ARGV[4]); "
                  + "redis.call('zrem', KEYS[2], ARGV[4]); "
                  + "local value = redis.call('hget', KEYS[1], ARGV[4]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(value), value); "
                  + "redis.call('publish', KEYS[3], msg); "
                  + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[4]), ARGV[4], string.len(value), value, ARGV[7]); "
                  + "local syncs = redis.call('publish', KEYS[4], syncMsg); "
                  + "return {-1, syncs}; "                  
              + "elseif ARGV[1] ~= '-1' then " 
                  + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[3]); "
                  + "return {0};"
              + "end; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getRemovedSyncChannelName()), 
             accessTimeout, 0, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(oldValue), encodeMapValue(newValue), syncId);
       
       result.add(syncId);
       waitSync(result);
       return (Long) result.get(0);
    }

    
    private long replaceValue(K key, V oldValue, V newValue) {
        Long accessTimeout = getAccessTimeout();
        
        Long updateTimeout = getUpdateTimeout();

        return evalWrite(getName(), codec, RedisCommands.EVAL_LONG,
                "local value = redis.call('hget', KEYS[1], ARGV[4]); "
              + "if value == false then "
                  + "return 0; "
              + "end; "
                  
              + "local expireDate = 92233720368547758; "
              + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[4]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
          
              + "if expireDate <= tonumber(ARGV[3]) then "
                  + "return 0; "
              + "end; "

              + "if ARGV[5] == value then "
                  + "if ARGV[2] == '0' then "
                      + "redis.call('hdel', KEYS[1], ARGV[4]); "
                      + "redis.call('zrem', KEYS[2], ARGV[4]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(tostring(value)), tostring(value)); "
                      + "redis.call('publish', KEYS[3], msg); "
                  + "elseif ARGV[2] ~= '-1' then " 
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[6]); "
                      + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[4]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[6]), ARGV[6]); "
                      + "redis.call('publish', KEYS[4], msg); "
                  + "else " 
                      + "redis.call('hset', KEYS[1], ARGV[4], ARGV[6]); "
                      + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(ARGV[6]), ARGV[6]); "
                      + "redis.call('publish', KEYS[4], msg); "
                  + "end; "
                  + "return 1;"
              + "end; "
              
              + "if ARGV[1] == '0' then "
                  + "redis.call('hdel', KEYS[1], ARGV[4]); "
                  + "redis.call('zrem', KEYS[2], ARGV[4]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[4]), ARGV[4], string.len(value), value); "
                  + "redis.call('publish', KEYS[3], msg); "                  
              + "elseif ARGV[1] ~= '-1' then " 
                  + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[3]); "
                  + "return 0;"
              + "end; "
              + "return -1; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getUpdatedChannelName()), 
             accessTimeout, updateTimeout, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(oldValue), encodeMapValue(newValue));
        
    }
    
    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        checkNotClosed();
        if (key == null) {
            throw new NullPointerException();
        }
        if (oldValue == null) {
            throw new NullPointerException();
        }
        if (newValue == null) {
            throw new NullPointerException();
        }

        long startTime = currentNanoTime();
        if (config.isWriteThrough()) {
            RLock lock = getLockedLock(key);
            try {
                long result;
                if (atomicExecution) {
                    result = replaceValue(key, oldValue, newValue);
                } else {
                    result = replaceValueLocked(key, oldValue, newValue);
                }
                if (result == 1) {
                    try {
                        cacheWriter.write(new JCacheEntry<K, V>(key, newValue));
                    } catch (CacheWriterException e) {
                        removeValues(key);
                        throw e;
                    } catch (Exception e) {
                        removeValues(key);
                        throw new CacheWriterException(e);
                    }
                    cacheManager.getStatBean(this).addHits(1);
                    cacheManager.getStatBean(this).addPuts(1);
                    cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                    cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                    return true;
                } else {
                    if (result == 0) {
                        cacheManager.getStatBean(this).addMisses(1);
                    } else {
                        cacheManager.getStatBean(this).addHits(1);
                    }
                    cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                    cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                    return false;
                }
            } finally {
                lock.unlock();
            }
        } else {
            RLock lock = getLockedLock(key);
            try {
                long result;
                if (atomicExecution) {
                    result = replaceValue(key, oldValue, newValue);
                } else {
                    result = replaceValueLocked(key, oldValue, newValue);
                }
                if (result == 1) {
                    cacheManager.getStatBean(this).addHits(1);
                    cacheManager.getStatBean(this).addPuts(1);
                } else if (result == 0){
                    cacheManager.getStatBean(this).addMisses(1);
                } else {
                    cacheManager.getStatBean(this).addHits(1);
                }
                cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                return result == 1;
            } finally {
                lock.unlock();
            }
        }
    }
    
    private boolean replaceValueLocked(K key, V value) {

        if (containsKey(key)) {
            double syncId = PlatformDependent.threadLocalRandom().nextDouble();
            Long updateTimeout = getUpdateTimeout();
        Long syncs = evalWrite(getName(), codec, RedisCommands.EVAL_LONG,
                "if ARGV[1] == '0' then "
                  + "redis.call('hdel', KEYS[1], ARGV[3]); "
                  + "redis.call('zrem', KEYS[2], ARGV[3]); "
                  + "local value = redis.call('hget', KEYS[1], ARGV[3]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(tostring(value)), tostring(value)); "
                  + "redis.call('publish', KEYS[3], msg); "
                  + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[3]), ARGV[3], string.len(tostring(value)), tostring(value), ARGV[5]); "
                  + "return redis.call('publish', KEYS[5], syncMsg); "
              + "elseif ARGV[1] ~= '-1' then "
                  + "redis.call('hset', KEYS[1], ARGV[3], ARGV[4]); "
                  + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[3]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(ARGV[4]), ARGV[4]); "
                  + "redis.call('publish', KEYS[4], msg); "
                  + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[3]), ARGV[3], string.len(ARGV[4]), ARGV[4], ARGV[5]); "
                  + "return redis.call('publish', KEYS[6], syncMsg); "
              + "else " 
                  + "redis.call('hset', KEYS[1], ARGV[3], ARGV[4]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(ARGV[4]), ARGV[4]); "
                  + "redis.call('publish', KEYS[4], msg); "
                  + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[3]), ARGV[3], string.len(ARGV[4]), ARGV[4], ARGV[5]); "
                  + "return redis.call('publish', KEYS[6], syncMsg); "
              + "end; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getUpdatedChannelName(),
                     getRemovedSyncChannelName(), getUpdatedSyncChannelName()), 
             updateTimeout, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), syncId);
        
        List<Object> result = Arrays.<Object>asList(syncs, syncId);
        waitSync(result);
            return true;
        }
        
        return false;

    }

    
    private boolean replaceValue(K key, V value) {
        Long updateTimeout = getUpdateTimeout();

        return evalWrite(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local value = redis.call('hget', KEYS[1], ARGV[3]); "
              + "if value == false then "
                  + "return 0; "
              + "end; "
                  
              + "local expireDate = 92233720368547758; "
              + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[3]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
          
              + "if expireDate <= tonumber(ARGV[2]) then "
                  + "return 0; "
              + "end; "

              + "if ARGV[1] == '0' then "
                  + "redis.call('hdel', KEYS[1], ARGV[3]); "
                  + "redis.call('zrem', KEYS[2], ARGV[3]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(tostring(value)), tostring(value)); "
                  + "redis.call('publish', KEYS[3], msg); "
              + "elseif ARGV[1] ~= '-1' then "
                  + "redis.call('hset', KEYS[1], ARGV[3], ARGV[4]); "
                  + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[3]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(ARGV[4]), ARGV[4]); "
                  + "redis.call('publish', KEYS[4], msg); "
              + "else " 
                  + "redis.call('hset', KEYS[1], ARGV[3], ARGV[4]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(ARGV[4]), ARGV[4]); "
                  + "redis.call('publish', KEYS[4], msg); "
              + "end; "
              + "return 1;",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getUpdatedChannelName()), 
             updateTimeout, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value));
        
    }
    
    private V getAndReplaceValue(K key, V value) {
        Long updateTimeout = getUpdateTimeout();

        return evalWrite(getName(), codec, RedisCommands.EVAL_MAP_VALUE,
                "local value = redis.call('hget', KEYS[1], ARGV[3]); "
              + "if value == false then "
                  + "return nil; "
              + "end; "
                  
              + "local expireDate = 92233720368547758; "
              + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[3]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
          
              + "if expireDate <= tonumber(ARGV[2]) then "
                  + "return nil; "
              + "end; "

              + "if ARGV[1] == '0' then "
                  + "redis.call('hdel', KEYS[1], ARGV[3]); "
                  + "redis.call('zrem', KEYS[2], ARGV[3]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(tostring(value)), tostring(value)); "
                  + "redis.call('publish', KEYS[3], msg); "
              + "elseif ARGV[1] ~= '-1' then " 
                  + "redis.call('hset', KEYS[1], ARGV[3], ARGV[4]); "
                  + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[3]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(ARGV[4]), ARGV[4]); "
                  + "redis.call('publish', KEYS[4], msg); "
              + "else " 
                  + "redis.call('hset', KEYS[1], ARGV[3], ARGV[4]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(ARGV[4]), ARGV[4]); "
                  + "redis.call('publish', KEYS[4], msg); "
              + "end; "
              + "return value;",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getUpdatedChannelName()), 
             updateTimeout, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value));
        
    }
    
    private V getAndReplaceValueLocked(K key, V value) {
        V oldValue = evalWrite(getName(), codec, RedisCommands.EVAL_MAP_VALUE,
                "local value = redis.call('hget', KEYS[1], ARGV[3]); "
              + "if value == false then "
                  + "return nil; "
              + "end; "
                  
              + "local expireDate = 92233720368547758; "
              + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[3]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
          
              + "if expireDate <= tonumber(ARGV[2]) then "
                  + "return nil; "
              + "end; "
              
              + "return value;", Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getUpdatedChannelName()), 
              0, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value));

        if (oldValue != null) {
            Long updateTimeout = getUpdateTimeout();
            double syncId = PlatformDependent.threadLocalRandom().nextDouble();
            Long syncs = evalWrite(getName(), codec, RedisCommands.EVAL_LONG,
                "if ARGV[1] == '0' then "
                  + "local value = redis.call('hget', KEYS[1], ARGV[3]); "
                  + "redis.call('hdel', KEYS[1], ARGV[3]); "
                  + "redis.call('zrem', KEYS[2], ARGV[3]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(tostring(value)), tostring(value)); "
                  + "redis.call('publish', KEYS[3], msg); "
                  + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[3]), ARGV[3], string.len(tostring(value)), tostring(value), ARGV[5]); "
                  + "return redis.call('publish', KEYS[5], msg); "
              + "elseif ARGV[1] ~= '-1' then " 
                  + "redis.call('hset', KEYS[1], ARGV[3], ARGV[4]); "
                  + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[3]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(ARGV[4]), ARGV[4]); "
                  + "redis.call('publish', KEYS[4], msg); "
                  + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[3]), ARGV[3], string.len(ARGV[4]), ARGV[4], ARGV[5]); "
                  + "return redis.call('publish', KEYS[6], syncMsg); "
              + "else " 
                  + "redis.call('hset', KEYS[1], ARGV[3], ARGV[4]); "
                  + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[3]), ARGV[3], string.len(ARGV[4]), ARGV[4]); "
                  + "redis.call('publish', KEYS[4], msg); "
                  + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[3]), ARGV[3], string.len(ARGV[4]), ARGV[4], ARGV[5]); "
                  + "return redis.call('publish', KEYS[6], syncMsg); "
              + "end; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getUpdatedChannelName(),
                     getRemovedSyncChannelName(), getUpdatedSyncChannelName()), 
             updateTimeout, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), syncId);
            
            List<Object> result = Arrays.<Object>asList(syncs, syncId);
            waitSync(result);
        }
        return oldValue;
    }


    @Override
    public boolean replace(K key, V value) {
        checkNotClosed();
        if (key == null) {
            throw new NullPointerException();
        }
        if (value == null) {
            throw new NullPointerException();
        }

        long startTime = currentNanoTime();
        if (config.isWriteThrough()) {
            RLock lock = getLockedLock(key);
            try {
                boolean result;
                if (atomicExecution) {
                    result = replaceValue(key, value);
                } else {
                    result = replaceValueLocked(key, value);
                }
                if (result) {
                    cacheManager.getStatBean(this).addHits(1);
                    cacheManager.getStatBean(this).addPuts(1);
                    try {
                        cacheWriter.write(new JCacheEntry<K, V>(key, value));
                    } catch (CacheWriterException e) {
                        removeValues(key);
                        throw e;
                    } catch (Exception e) {
                        removeValues(key);
                        throw new CacheWriterException(e);
                    }
                } else {
                    cacheManager.getStatBean(this).addMisses(1);
                }
                cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                return result;
            } finally {
                lock.unlock();
            }
        } else {
            RLock lock = getLockedLock(key);
            try {
                boolean result;
                if (atomicExecution) {
                    result = replaceValue(key, value);
                } else {
                    result = replaceValueLocked(key, value);
                }
                if (result) {
                    cacheManager.getStatBean(this).addHits(1);
                    cacheManager.getStatBean(this).addPuts(1);
                } else {
                    cacheManager.getStatBean(this).addMisses(1);
                }
                cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                return result;
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public V getAndReplace(K key, V value) {
        checkNotClosed();
        if (key == null) {
            throw new NullPointerException();
        }
        if (value == null) {
            throw new NullPointerException();
        }

        long startTime = currentNanoTime();
        if (config.isWriteThrough()) {
            RLock lock = getLockedLock(key);
            try {
                V result;
                if (atomicExecution) {
                    result = getAndReplaceValue(key, value);
                } else {
                    result = getAndReplaceValueLocked(key, value);
                }
                if (result != null) {
                    cacheManager.getStatBean(this).addHits(1);
                    cacheManager.getStatBean(this).addPuts(1);
                    try {
                        cacheWriter.write(new JCacheEntry<K, V>(key, value));
                    } catch (CacheWriterException e) {
                        removeValues(key);
                        throw e;
                    } catch (Exception e) {
                        removeValues(key);
                        throw new CacheWriterException(e);
                    }
                } else {
                    cacheManager.getStatBean(this).addMisses(1);
                }
                cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                return result;
            } finally {
                lock.unlock();
            }
        } else {
            RLock lock = getLockedLock(key);
            try {
                V result;
                if (atomicExecution) {
                    result = getAndReplaceValue(key, value);
                } else {
                    result = getAndReplaceValueLocked(key, value);
                }
                if (result != null) {
                    cacheManager.getStatBean(this).addHits(1);
                    cacheManager.getStatBean(this).addPuts(1);
                } else {
                    cacheManager.getStatBean(this).addMisses(1);
                }
                cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                return result;
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void removeAll(Set<? extends K> keys) {
        checkNotClosed();
        Map<K, V> deletedKeys = new HashMap<K, V>();
        
        for (K key : keys) {
            if (key == null) {
                throw new NullPointerException();
            }
        }
        
        List<RLock> lockedLocks = new ArrayList<RLock>();
        long startTime = currentNanoTime();
        if (config.isWriteThrough()) {
            for (K key : keys) {
                RLock lock = getLockedLock(key);
                lockedLocks.add(lock);
                V result = getAndRemoveValue(key);
                if (result != null) {
                    deletedKeys.put(key, result);
                }
            }
            
            try {
                try {
                    cacheWriter.deleteAll(deletedKeys.keySet());
                } catch (CacheWriterException e) {
                    for (Map.Entry<K, V> deletedEntry : deletedKeys.entrySet()) {
                        if (deletedEntry.getValue() != null) {
                            putValue(deletedEntry.getKey(), deletedEntry.getValue());
                        }
                    }
                    throw e;
                } catch (Exception e) {
                    for (Map.Entry<K, V> deletedEntry : deletedKeys.entrySet()) {
                        if (deletedEntry.getValue() != null) {
                            putValue(deletedEntry.getKey(), deletedEntry.getValue());
                        }
                    }
                    throw new CacheWriterException(e);
                }
                cacheManager.getStatBean(this).addRemovals(deletedKeys.size());
            } finally {
                for (RLock lock : lockedLocks) {
                    lock.unlock();
                }
            }
        } else {
            long removedKeys = removeValues(keys.toArray());
            cacheManager.getStatBean(this).addRemovals(removedKeys);
        }
        cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
    }
    
    MapScanResult<Object, Object> scanIterator(String name, RedisClient client, long startPos) {
        RFuture<MapScanResult<Object, Object>> f 
            = commandExecutor.readAsync(client, name, codec, RedisCommands.HSCAN, name, startPos);
        try {
            return get(f);
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }

    protected Iterator<K> keyIterator() {
        return new RedissonBaseMapIterator<K>() {
            @Override
            protected K getValue(Map.Entry<Object, Object> entry) {
                return (K) entry.getKey();
            }

            @Override
            protected void remove(java.util.Map.Entry<Object, Object> value) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected Object put(java.util.Map.Entry<Object, Object> entry, Object value) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected ScanResult<java.util.Map.Entry<Object, Object>> iterator(RedisClient client,
                    long nextIterPos) {
                return JCache.this.scanIterator(JCache.this.getName(), client, nextIterPos);
            }
        };
    }
    
    @Override
    public void removeAll() {
        checkNotClosed();
        if (config.isWriteThrough()) {
            for (Iterator<K> iterator = keyIterator(); iterator.hasNext();) {
                K key = iterator.next();
                remove(key);
            }
        } else {
            long startTime = currentNanoTime();
            long removedObjects = evalWrite(getName(), codec, RedisCommands.EVAL_LONG,
                      "local expiredEntriesCount = redis.call('zcount', KEYS[2], 0, ARGV[1]); "
                    + "local result = 0; "
                    + "if expiredEntriesCount > 0 then "
                        + "result = redis.call('zcard', KEYS[2]) - expiredEntriesCount; "
                    + "else "
                        + "result = redis.call('hlen', KEYS[1]); "
                    + "end; "
                    + "redis.call('del', KEYS[1], KEYS[2]); "
                    + "return result; ",
                    Arrays.<Object>asList(getName(), getTimeoutSetName()),
                    System.currentTimeMillis());
            cacheManager.getStatBean(this).addRemovals(removedObjects);
            cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
        }
    }

    @Override
    public void clear() {
        checkNotClosed();
        write(getName(), RedisCommands.DEL_OBJECTS, getName(), getTimeoutSetName());
    }

    @Override
    public <C extends Configuration<K, V>> C getConfiguration(Class<C> clazz) {
        if (clazz.isInstance(config)) {
            return clazz.cast(config);
        }

        throw new IllegalArgumentException("Configuration object is not an instance of " + clazz);
    }

    @Override
    public <T> T invoke(K key, EntryProcessor<K, V, T> entryProcessor, Object... arguments)
            throws EntryProcessorException {
        checkNotClosed();
        if (key == null) {
            throw new NullPointerException();
        }
        if (entryProcessor == null) {
            throw new NullPointerException();
        }

        long startTime = currentNanoTime();
        if (containsKey(key)) {
            cacheManager.getStatBean(this).addHits(1);
        } else {
            cacheManager.getStatBean(this).addMisses(1);
        }
        cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);

        JMutableEntry<K, V> entry = new JMutableEntry<K, V>(this, key, null, config.isReadThrough());

        RLock lock = getLockedLock(key);
        try {
            T result = entryProcessor.process(entry, arguments);
            if (entry.getAction() == Action.CREATED
                    || entry.getAction() == Action.UPDATED) {
                put(key, entry.value());
            }
            if (entry.getAction() == Action.DELETED) {
                remove(key);
            }
            return result;
        } catch (EntryProcessorException e) {
            throw e;
        } catch (Exception e) {
            throw new EntryProcessorException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor,
            Object... arguments) {
        checkNotClosed();
        if (entryProcessor == null) {
            throw new NullPointerException();
        }

        Map<K, EntryProcessorResult<T>> results = new HashMap<K, EntryProcessorResult<T>>();
        for (K key : keys) {
            try {
                final T result = invoke(key, entryProcessor, arguments);
                if (result != null) {
                    results.put(key, new EntryProcessorResult<T>() {
                        @Override
                        public T get() throws EntryProcessorException {
                            return result;
                        }
                    });
                }
            } catch (final EntryProcessorException e) {
                results.put(key, new EntryProcessorResult<T>() {
                    @Override
                    public T get() throws EntryProcessorException {
                        throw e;
                    }
                });
            }
        }

        return results;
    }

    @Override
    public CacheManager getCacheManager() {
        checkNotClosed();
        return cacheManager;
    }

    @Override
    public void close() {
        if (isClosed()) {
            return;
        }
        
        synchronized (cacheManager) {
            if (!isClosed()) {
                if (hasOwnRedisson) {
                    redisson.shutdown();
                }
                cacheManager.closeCache(this);
                for (CacheEntryListenerConfiguration<K, V> config : listeners.keySet()) {
                    deregisterCacheEntryListener(config);
                }
                
                closed = true;
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        if (clazz.isAssignableFrom(getClass())) {
            return clazz.cast(this);
        }

        return null;
    }

    @Override
    public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        registerCacheEntryListener(cacheEntryListenerConfiguration, true);
    }

    private void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration, boolean addToConfig) {
        Factory<CacheEntryListener<? super K, ? super V>> factory = cacheEntryListenerConfiguration.getCacheEntryListenerFactory();
        final CacheEntryListener<? super K, ? super V> listener = factory.create();
        
        Factory<CacheEntryEventFilter<? super K, ? super V>> filterFactory = cacheEntryListenerConfiguration.getCacheEntryEventFilterFactory();
        final CacheEntryEventFilter<? super K, ? super V> filter;
        if (filterFactory != null) {
            filter = filterFactory.create();
        } else {
            filter = null;
        }
        
        Map<Integer, String> values = new ConcurrentHashMap<Integer, String>();
        
        Map<Integer, String> oldValues = listeners.putIfAbsent(cacheEntryListenerConfiguration, values);
        if (oldValues != null) {
            values = oldValues;
        }
        
        final boolean sync = cacheEntryListenerConfiguration.isSynchronous();
        
        if (CacheEntryRemovedListener.class.isAssignableFrom(listener.getClass())) {
            String channelName = getRemovedChannelName();
            if (sync) {
                channelName = getRemovedSyncChannelName();
            }
            
            RTopic topic = redisson.getTopic(channelName, new JCacheEventCodec(codec, sync));
            int listenerId = topic.addListener(List.class, new MessageListener<List<Object>>() {
                @Override
                public void onMessage(CharSequence channel, List<Object> msg) {
                    JCacheEntryEvent<K, V> event = new JCacheEntryEvent<K, V>(JCache.this, EventType.REMOVED, msg.get(0), msg.get(1));
                    try {
                        if (filter == null || filter.evaluate(event)) {
                            List<CacheEntryEvent<? extends K, ? extends V>> events = Collections.<CacheEntryEvent<? extends K, ? extends V>>singletonList(event);
                            ((CacheEntryRemovedListener<K, V>) listener).onRemoved(events);
                        }
                    } finally {
                        sendSync(sync, msg);
                    }
                }
            });
            values.put(listenerId, channelName);
        }
        if (CacheEntryCreatedListener.class.isAssignableFrom(listener.getClass())) {
            String channelName = getCreatedChannelName();
            if (sync) {
                channelName = getCreatedSyncChannelName();
            }

            RTopic topic = redisson.getTopic(channelName, new JCacheEventCodec(codec, sync));
            int listenerId = topic.addListener(List.class, new MessageListener<List<Object>>() {
                @Override
                public void onMessage(CharSequence channel, List<Object> msg) {
                    JCacheEntryEvent<K, V> event = new JCacheEntryEvent<K, V>(JCache.this, EventType.CREATED, msg.get(0), msg.get(1));
                    try {
                        if (filter == null || filter.evaluate(event)) {
                            List<CacheEntryEvent<? extends K, ? extends V>> events = Collections.<CacheEntryEvent<? extends K, ? extends V>>singletonList(event);
                            ((CacheEntryCreatedListener<K, V>) listener).onCreated(events);
                        }
                    } finally {
                        sendSync(sync, msg);
                    }
                }
            });
            values.put(listenerId, channelName);
        }
        if (CacheEntryUpdatedListener.class.isAssignableFrom(listener.getClass())) {
            String channelName = getUpdatedChannelName();
            if (sync) {
                channelName = getUpdatedSyncChannelName();
            }

            RTopic topic = redisson.getTopic(channelName, new JCacheEventCodec(codec, sync));
            int listenerId = topic.addListener(List.class, new MessageListener<List<Object>>() {
                @Override
                public void onMessage(CharSequence channel, List<Object> msg) {
                    JCacheEntryEvent<K, V> event = new JCacheEntryEvent<K, V>(JCache.this, EventType.UPDATED, msg.get(0), msg.get(1));
                    try {
                        if (filter == null || filter.evaluate(event)) {
                            List<CacheEntryEvent<? extends K, ? extends V>> events = Collections.<CacheEntryEvent<? extends K, ? extends V>>singletonList(event);
                            ((CacheEntryUpdatedListener<K, V>) listener).onUpdated(events);
                        }
                    } finally {
                        sendSync(sync, msg);
                    }
                }
            });
            values.put(listenerId, channelName);
        }
        if (CacheEntryExpiredListener.class.isAssignableFrom(listener.getClass())) {
            String channelName = getExpiredChannelName();

            RTopic topic = redisson.getTopic(channelName, new JCacheEventCodec(codec, false));
            int listenerId = topic.addListener(List.class, new MessageListener<List<Object>>() {
                @Override
                public void onMessage(CharSequence channel, List<Object> msg) {
                    JCacheEntryEvent<K, V> event = new JCacheEntryEvent<K, V>(JCache.this, EventType.EXPIRED, msg.get(0), msg.get(1));
                    if (filter == null || filter.evaluate(event)) {
                        List<CacheEntryEvent<? extends K, ? extends V>> events = Collections.<CacheEntryEvent<? extends K, ? extends V>>singletonList(event);
                        ((CacheEntryExpiredListener<K, V>) listener).onExpired(events);
                    }
                }
            });
            values.put(listenerId, channelName);
        }
        
        if (addToConfig) {
            config.addCacheEntryListenerConfiguration(cacheEntryListenerConfiguration);
        }
    }

    private void sendSync(boolean sync, List<Object> msg) {
        if (sync) {
            RSemaphore semaphore = redisson.getSemaphore(getSyncName(msg.get(2)));
            semaphore.release();
        }
    }
    
    @Override
    public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        Map<Integer, String> listenerIds = listeners.remove(cacheEntryListenerConfiguration);
        if (listenerIds != null) {
            for (Map.Entry<Integer, String> entry : listenerIds.entrySet()) {
                redisson.getTopic(entry.getValue()).removeListener(entry.getKey());
            }
        }
        config.removeCacheEntryListenerConfiguration(cacheEntryListenerConfiguration);
    }

    @Override
    public Iterator<javax.cache.Cache.Entry<K, V>> iterator() {
        checkNotClosed();
        return new RedissonBaseMapIterator<javax.cache.Cache.Entry<K, V>>() {
            @Override
            protected Cache.Entry<K, V> getValue(Map.Entry<Object, Object> entry) {
                cacheManager.getStatBean(JCache.this).addHits(1);
                Long accessTimeout = getAccessTimeout();
                JCacheEntry<K, V> je = new JCacheEntry<K, V>((K) entry.getKey(), (V) entry.getValue());
                if (accessTimeout == 0) {
                    remove();
                } else if (accessTimeout != -1) {
                    write(getName(), RedisCommands.ZADD_BOOL, getTimeoutSetName(), accessTimeout, encodeMapKey(entry.getKey()));
                }
                return je;
            }

            @Override
            protected void remove(Map.Entry<Object, Object> entry) {
                JCache.this.remove((K) entry.getKey());
            }

            @Override
            protected Object put(java.util.Map.Entry<Object, Object> entry, Object value) {
                throw new UnsupportedOperationException();
            }



            @Override
            protected ScanResult<java.util.Map.Entry<Object, Object>> iterator(RedisClient client,
                    long nextIterPos) {
                return JCache.this.scanIterator(JCache.this.getName(), client, nextIterPos);
            }

        };
    }

}
