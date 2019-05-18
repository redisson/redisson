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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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
import org.redisson.api.CacheAsync;
import org.redisson.api.CacheReactive;
import org.redisson.api.CacheRx;
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
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.reactive.CommandReactiveService;
import org.redisson.reactive.ReactiveProxyBuilder;
import org.redisson.rx.CommandRxService;
import org.redisson.rx.RxProxyBuilder;

import io.netty.buffer.ByteBuf;

/**
 * JCache implementation
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class JCache<K, V> extends RedissonObject implements Cache<K, V>, CacheAsync<K, V> {

    final boolean atomicExecution = System.getProperty("org.jsr107.tck.management.agentId") == null;
    
    final JCacheManager cacheManager;
    final JCacheConfiguration<K, V> config;
    private final ConcurrentMap<CacheEntryListenerConfiguration<K, V>, Map<Integer, String>> listeners = 
                                        new ConcurrentHashMap<CacheEntryListenerConfiguration<K, V>, Map<Integer, String>>();
    final Redisson redisson;

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
    
    void checkNotClosed() {
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

    long currentNanoTime() {
        if (config.isStatisticsEnabled()) {
            return System.nanoTime();
        }
        return 0;
    }

    protected void checkKey(Object key) {
        if (key == null) {
            throw new NullPointerException();
        }
    }
    
    @Override
    public V get(K key) {
        RLock lock = getLockedLock(key);
        try {
            RFuture<V> result = getAsync(key);
            result.syncUninterruptibly();
            return result.getNow();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public RFuture<V> getAsync(K key) {
        checkNotClosed();
        checkKey(key);

        long startTime = currentNanoTime();
        RFuture<V> future;
        if (atomicExecution) {
            future = getValue(key);
        } else {
            V value = getValueLocked(key);
            future = RedissonPromise.newSucceededFuture(value);
        }

        RPromise<V> result = new RedissonPromise<>();
        future.onComplete((value, e) -> {
            if (value == null) {
                cacheManager.getStatBean(this).addMisses(1);
                if (config.isReadThrough()) {
                    redisson.getConnectionManager().getExecutor().execute(() -> {
                        try {
                            V val = loadValue(key);
                            result.trySuccess(val);
                        } catch (Exception ex) {
                            result.tryFailure(ex);
                        }
                    });
                    return;
                }
            } else {
                cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                cacheManager.getStatBean(this).addHits(1);
            }
            result.trySuccess(value);
        });
        return result;
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
            double syncId = ThreadLocalRandom.current().nextDouble();
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

    RFuture<V> getValue(K key) {
        Long accessTimeout = getAccessTimeout();
        
        if (accessTimeout == -1) {
            return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_MAP_VALUE,
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
        }
        
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_MAP_VALUE,
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
    }

    Long getAccessTimeout(long baseTime) {
        if (config.getExpiryPolicy().getExpiryForAccess() == null) {
            return -1L;
        }
        Long accessTimeout = config.getExpiryPolicy().getExpiryForAccess().getAdjustedTime(baseTime);

        if (config.getExpiryPolicy().getExpiryForAccess().isZero()) {
            accessTimeout = 0L;
        } else if (accessTimeout.longValue() == Long.MAX_VALUE) {
            accessTimeout = -1L;
        }
        return accessTimeout;
    }

    Long getAccessTimeout() {
        return getAccessTimeout(System.currentTimeMillis());
    }
    
    V loadValue(K key) {
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
    
    private <T, R> R write(String key, RedisCommand<T> command, Object... params) {
        RFuture<R> future = commandExecutor.writeAsync(key, command, params);
        try {
            return get(future);
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }
    
    <T, R> R evalWrite(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        RFuture<R> future = commandExecutor.evalWriteAsync(key, codec, evalCommandType, script, keys, params);
        try {
            return get(future);
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }
    
    <T, R> R evalRead(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params) {
        RFuture<R> future = commandExecutor.evalReadAsync(key, codec, evalCommandType, script, keys, params);
        try {
            return get(future);
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }
    
    private boolean putValueLocked(K key, Object value) {
        double syncId = ThreadLocalRandom.current().nextDouble();
        
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


    RFuture<Long> putAllValues(Map<? extends K, ? extends V> map) {
        Long creationTimeout = getCreationTimeout();
        Long updateTimeout = getUpdateTimeout();

        List<Object> params = new ArrayList<>();
        params.add(creationTimeout);
        params.add(updateTimeout);
        params.add(System.currentTimeMillis());
        double syncId = ThreadLocalRandom.current().nextDouble();
        params.add(syncId);
        
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            params.add(encodeMapKey(entry.getKey()));
            params.add(encodeMapValue(entry.getValue()));
        }
        
        RFuture<List<Object>> res = commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_LIST,
              "local added = 0; "
            + "local syncs = 0; "
            + "for i = 5, #ARGV, 2 do "
              + "if redis.call('hexists', KEYS[1], ARGV[i]) == 1 then "
                + "if ARGV[2] == '0' then "
                    + "redis.call('hdel', KEYS[1], ARGV[i]); "
                    + "redis.call('zrem', KEYS[2], ARGV[i]); "
                    + "local value = redis.call('hget', KEYS[1], ARGV[i]);"
                    + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[i]), ARGV[i], string.len(tostring(value)), tostring(value)); "
                    + "redis.call('publish', KEYS[4], msg); "
                    + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[i]), ARGV[i], string.len(tostring(value)), tostring(value), ARGV[4]); "
                    + "syncs = syncs + redis.call('publish', KEYS[7], syncMsg); "
                + "elseif ARGV[2] ~= '-1' then "
                    + "redis.call('hset', KEYS[1], ARGV[i], ARGV[i+1]); "
                    + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[i]); "
                    + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[i]), ARGV[i], string.len(ARGV[i+1]), ARGV[i+1]); "                      
                    + "redis.call('publish', KEYS[5], msg); "
                    + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[i]), ARGV[i], string.len(ARGV[i+1]), ARGV[i+1], ARGV[4]); "
                    + "syncs = syncs + redis.call('publish', KEYS[8], syncMsg); "
                    + "added = added + 1;"
                + "else "
                    + "redis.call('hset', KEYS[1], ARGV[i], ARGV[i+1]); "
                    + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[i]), ARGV[i], string.len(ARGV[i+1]), ARGV[i+1]); "
                    + "redis.call('publish', KEYS[5], msg); "
                    + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[i]), ARGV[i], string.len(ARGV[i+1]), ARGV[i+1], ARGV[4]); "
                    + "syncs = syncs + redis.call('publish', KEYS[8], syncMsg); "
                    + "added = added + 1;"
                + "end; "
            + "else "
                + "if ARGV[1] == '0' then "
                + "elseif ARGV[1] ~= '-1' then "
                    + "redis.call('hset', KEYS[1], ARGV[i], ARGV[i+1]); "
                    + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[i]); "
                    + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[i]), ARGV[i], string.len(ARGV[i+1]), ARGV[i+1]); "
                    + "redis.call('publish', KEYS[3], msg); "
                    + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[i]), ARGV[i], string.len(ARGV[i+1]), ARGV[i+1], ARGV[4]); "
                    + "syncs = syncs + redis.call('publish', KEYS[6], syncMsg); "
                    + "added = added + 1;"
                + "else "
                    + "redis.call('hset', KEYS[1], ARGV[i], ARGV[i+1]); "
                    + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[i]), ARGV[i], string.len(ARGV[i+1]), ARGV[i+1]); "
                    + "redis.call('publish', KEYS[3], msg); "
                    + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[i]), ARGV[i], string.len(ARGV[i+1]), ARGV[i+1], ARGV[4]); "
                    + "syncs = syncs + redis.call('publish', KEYS[6], syncMsg); "
                    + "added = added + 1;"
                + "end; "
            + "end; "
          + "end; "
          + "return {added, syncs};",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getCreatedChannelName(), getRemovedChannelName(), getUpdatedChannelName(),
                     getCreatedSyncChannelName(), getRemovedSyncChannelName(), getUpdatedSyncChannelName()), 
                     params.toArray());
        
        RPromise<Long> result = new RedissonPromise<>();
        if (atomicExecution) {
            res.onComplete((r, e) -> {
                if (e != null) {
                    result.tryFailure(new CacheException(e));
                    return;
                }
                
                Long added = (Long) r.get(0);
                Long syncs = (Long) r.get(1);
                if (syncs > 0) {
                    RSemaphore semaphore = redisson.getSemaphore(getSyncName(syncId));
                    semaphore.acquireAsync(syncs.intValue()).onComplete((obj1, ex) -> {
                        if (ex != null) {
                            result.tryFailure(new CacheException(ex));
                            return;
                        }
                        semaphore.deleteAsync().onComplete((obj, exc) -> {
                            if (exc != null) {
                                result.tryFailure(new CacheException(exc));
                                return;
                            }
                            result.trySuccess(added);
                        });
                    });
                } else {
                    result.trySuccess(added);
                }
            });
        } else {
            res.syncUninterruptibly();
            
            List<Object> r = res.getNow();
            Long added = (Long) r.get(0);
            Long syncs = (Long) r.get(1);
            if (syncs > 0) {
                RSemaphore semaphore = redisson.getSemaphore(getSyncName(syncId));
                try {
                    semaphore.acquire(syncs.intValue());
                    semaphore.delete();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            result.trySuccess(added);
        }
        
        return result;
        
    }
    
    RFuture<Boolean> putValue(K key, Object value) {
        double syncId = ThreadLocalRandom.current().nextDouble();
        Long creationTimeout = getCreationTimeout();
        Long updateTimeout = getUpdateTimeout();
        
        RFuture<List<Object>> res = commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_LIST,
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
        
        RPromise<Boolean> result = waitSync(syncId, res);
        
        return result;
    }

    protected RPromise<Boolean> waitSync(double syncId, RFuture<List<Object>> res) {
        RPromise<Boolean> result = new RedissonPromise<>();
        if (atomicExecution) {
            res.onComplete((r, e) -> {
                if (e != null) {
                    result.tryFailure(new CacheException(e));
                    return;
                }
                
                r.add(syncId);
                
                if (r.size() < 2) {
                    result.trySuccess((Long) r.get(0) >= 1);
                    return;
                }
                
                Long syncs = (Long) r.get(r.size() - 2);
                if (syncs != null && syncs > 0) {
                    RSemaphore semaphore = redisson.getSemaphore(getSyncName(syncId));
                    semaphore.acquireAsync(syncs.intValue()).onComplete((obj1, ex) -> {
                        if (ex != null) {
                            result.tryFailure(new CacheException(ex));
                            return;
                        }
                        semaphore.deleteAsync().onComplete((obj, exc) -> {
                            if (exc != null) {
                                result.tryFailure(new CacheException(exc));
                                return;
                            }
                            result.trySuccess((Long) r.get(0) >= 1);
                        });
                    });
                } else {
                    result.trySuccess((Long) r.get(0) >= 1);
                }
            });
        } else {
            res.syncUninterruptibly();
            
            List<Object> r = res.getNow();
            r.add(syncId);
            waitSync(r);
            result.trySuccess((Long) r.get(0) >= 1);
        }
        return result;
    }

    Long getUpdateTimeout(long baseTime) {
        if (config.getExpiryPolicy().getExpiryForUpdate() == null) {
            return -1L;
        }
        
        Long updateTimeout = config.getExpiryPolicy().getExpiryForUpdate().getAdjustedTime(baseTime);
        if (config.getExpiryPolicy().getExpiryForUpdate().isZero()) {
            updateTimeout = 0L;
        } else if (updateTimeout.longValue() == Long.MAX_VALUE) {
            updateTimeout = -1L;
        }
        return updateTimeout;
    }
    
    Long getUpdateTimeout() {
        return getUpdateTimeout(System.currentTimeMillis());
    }

    Long getCreationTimeout(long baseTime) {
        if (config.getExpiryPolicy().getExpiryForCreation() == null) {
            return -1L;
        }
        Long creationTimeout = config.getExpiryPolicy().getExpiryForCreation().getAdjustedTime(baseTime);
        if (config.getExpiryPolicy().getExpiryForCreation().isZero()) {
            creationTimeout = 0L;
        } else if (creationTimeout.longValue() == Long.MAX_VALUE) {
            creationTimeout = -1L;
        }
        return creationTimeout;
    }
    
    Long getCreationTimeout() {
        return getCreationTimeout(System.currentTimeMillis());
    }
    
    RFuture<Boolean> putIfAbsentValue(K key, Object value) {
        Long creationTimeout = getCreationTimeout();
        if (creationTimeout == 0) {
            return RedissonPromise.newSucceededFuture(false);
        }
        
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
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
            checkKey(key);
        }

        if (!atomicExecution && !config.isReadThrough()) {
            long startTime = currentNanoTime();
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
        
        RFuture<Map<K, V>> result = getAllAsync(keys);

        result.syncUninterruptibly();
        return result.getNow();
    }

    @Override
    public RFuture<Map<K, V>> getAllAsync(Set<? extends K> keys) {
        checkNotClosed();
        long startTime = currentNanoTime();
        Long accessTimeout = getAccessTimeout();
        
        List<Object> args = new ArrayList<Object>(keys.size() + 2);
        args.add(accessTimeout);
        args.add(System.currentTimeMillis());
        encode(args, keys);
        
        RFuture<Map<K, V>> res;
        if (accessTimeout == -1) {
            res = commandExecutor.evalReadAsync(getName(), codec, new RedisCommand<Map<Object, Object>>("EVAL", new MapGetAllDecoder(new ArrayList<Object>(keys), 0, true), ValueType.MAP_VALUE),
                    "local expireHead = redis.call('zrange', KEYS[2], 0, 0, 'withscores');"
                  + "local accessTimeout = ARGV[1]; "
                  + "local currentTime = tonumber(ARGV[2]); "
                  + "local hasExpire = #expireHead == 2 and tonumber(expireHead[2]) <= currentTime; "
                  + "local map = {};"
                  + "for i=3, #ARGV, 5000 do "
                     + "local m = redis.call('hmget', KEYS[1], unpack(ARGV, i, math.min(i+4999, #ARGV))); "
                     + "for k,v in ipairs(m) do " 
                     +     "table.insert(map, v) " 
                     + "end; "
                  + "end; "
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
            res = commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Map<Object, Object>>("EVAL", new MapGetAllDecoder(new ArrayList<Object>(keys), 0, true), ValueType.MAP_VALUE),
                            "local expireHead = redis.call('zrange', KEYS[2], 0, 0, 'withscores');"
                          + "local accessTimeout = ARGV[1]; "
                          + "local currentTime = tonumber(ARGV[2]); "
                          + "local hasExpire = #expireHead == 2 and tonumber(expireHead[2]) <= currentTime; "
                          + "local map = {};"
                          + "for i=3, #ARGV, 5000 do "
                             + "local m = redis.call('hmget', KEYS[1], unpack(ARGV, i, math.min(i+4999, #ARGV))); "
                             + "for k,v in ipairs(m) do " 
                             +     "table.insert(map, v) " 
                             + "end; "
                          + "end; "

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

        RPromise<Map<K, V>> result = new RedissonPromise<>();
        res.onComplete((r, ex) -> {
            Map<K, V> map = r.entrySet().stream()
                                .filter(e -> e.getValue() != null)
                                .collect(Collectors.toMap(x -> x.getKey(), x -> x.getValue()));
            
            cacheManager.getStatBean(this).addHits(map.size());
            
            int nullValues = r.size() - map.size();
            if (config.isReadThrough() && nullValues > 0) {
                cacheManager.getStatBean(this).addMisses(nullValues);
                commandExecutor.getConnectionManager().getExecutor().execute(() -> {
                    try {
                        r.entrySet().stream()
                            .filter(e -> e.getValue() == null)
                            .forEach(entry -> {
                                V value = loadValue(entry.getKey());
                                if (value != null) {
                                    map.put(entry.getKey(), value);
                                }
                            });
                    } catch (Exception exc) {
                        result.tryFailure(exc);
                        return;
                    }
                    cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                    result.trySuccess(map);
                });
            } else {
                cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                result.trySuccess(map);
            }
        });
        return result;
    }

    @Override
    public boolean containsKey(K key) {
        RFuture<Boolean> future = containsKeyAsync(key);
        future.syncUninterruptibly();
        return future.getNow();
    }

    @Override
    public RFuture<Boolean> containsKeyAsync(K key) {
        checkNotClosed();
        checkKey(key);

        RFuture<Boolean> future = commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
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
        return future;
    }

    @Override
    @SuppressWarnings("NestedTryDepth")
    public void loadAll(final Set<? extends K> keys, final boolean replaceExistingValues, final CompletionListener completionListener) {
        checkNotClosed();
        if (keys == null) {
            throw new NullPointerException();
        }
        
        for (K key : keys) {
            checkKey(key);
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
                        throw e;
                    }
                }
                if (completionListener != null) {
                    completionListener.onCompletion();
                }
            }
        });
    }
    
    RLock getLockedLock(K key) {
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
    public RFuture<Void> putAsync(K key, V value) {
        checkNotClosed();
        checkKey(key);
        if (value == null) {
            throw new NullPointerException();
        }
        
        RPromise<Void> result = new RedissonPromise<>();
        long startTime = currentNanoTime();
        if (config.isWriteThrough()) {
            RLock lock = getLockedLock(key);
            try {
                RFuture<List<Object>> future;
                if (atomicExecution) {
                    future = getAndPutValue(key, value);
                } else {
                    List<Object> res = getAndPutValueLocked(key, value);
                    future = RedissonPromise.newSucceededFuture(res);
                }
                future.onComplete((res, ex) -> {
                    if (ex != null) {
                        result.tryFailure(new CacheException(ex));
                        return;
                    }

                    if (res.isEmpty()) {
                        cacheManager.getStatBean(this).addPuts(1);
                        cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                        result.trySuccess(null);
                        return;
                    }
                    Long added = (Long) res.get(0);
                    if (added == null) {
                        cacheManager.getStatBean(this).addPuts(1);
                        cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                        result.trySuccess(null);
                        return;
                    }
                    
                    writeCache(key, value, result, startTime, res, added);
                });
            } finally {
                lock.unlock();
            }
        } else {
            RLock lock = getLockedLock(key);
            try {
                RFuture<Boolean> future;
                if (atomicExecution) {
                    future = putValue(key, value);
                } else {
                    boolean res = putValueLocked(key, value);
                    future = RedissonPromise.newSucceededFuture(res);
                }
                future.onComplete((r, e) -> {
                    if (e != null) {
                        result.tryFailure(new CacheException(e));
                        return;
                    }
                    
                    if (r) {
                        cacheManager.getStatBean(this).addPuts(1);
                    }
                    cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                    result.trySuccess(null);
                });
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    private void writeCache(K key, V value, RPromise<Void> result, long startTime, List<Object> res, Long added) {
        Runnable r;
        if (added >= 1) {
            r = () -> {
                try {
                    cacheWriter.write(new JCacheEntry<K, V>(key, value));
                    cacheManager.getStatBean(this).addPuts(1);
                    cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                    result.trySuccess(null);
                } catch (Exception e) {
                    removeValues(key).onComplete((obj, exc) -> {
                        if (exc != null) {
                            result.tryFailure(new CacheWriterException(exc));
                            return;
                        }
                        
                        handleException(result, e);
                    });
                }
            };
        } else {
            r = () -> {
                try {
                    cacheWriter.delete(key);
                    cacheManager.getStatBean(this).addPuts(1);
                    cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                    result.trySuccess(null);
                } catch (Exception e) {
                    if (res.size() == 4 && res.get(1) != null) {
                        putValue(key, res.get(1)).onComplete((obj, exc) -> {
                            if (exc != null) {
                                result.tryFailure(new CacheWriterException(exc));
                                return;
                            }
                            
                            handleException(result, e);
                        });
                        return;
                    }
                    
                    handleException(result, e);
                }
            };
        }

        if (atomicExecution) {
            commandExecutor.getConnectionManager().getExecutor().execute(r);
        } else {
            r.run();
        }
    }

    @Override
    public void put(K key, V value) {
        RFuture<Void> future = putAsync(key, value);
        future.syncUninterruptibly();
    }
    
    RFuture<Long> removeValues(Object... keys) {
        List<Object> params = new ArrayList<Object>(keys.length+1);
        params.add(System.currentTimeMillis());
        encodeMapKeys(params, Arrays.asList(keys));
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_LONG,
                  "local counter = 0;"
                + "for i=2, #ARGV do "
                      + "local value = redis.call('hget', KEYS[1], ARGV[i]); "
                      + "if value ~= false then "
                          + "redis.call('hdel', KEYS[1], ARGV[i]); "
                      
                          + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[i]); "
                          + "if not (expireDateScore ~= false and tonumber(expireDateScore) <= tonumber(ARGV[1])) then "
                              + "counter = counter + 1;"
                          + "end; "
                          
                          + "redis.call('zrem', KEYS[2], ARGV[i]); "
                          + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[i]), ARGV[i], string.len(tostring(value)), tostring(value)); "
                          + "redis.call('publish', KEYS[3], msg); "
                      + "end;"
                + "end; "
                + "return counter;",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getRemovedSyncChannelName()), 
                params.toArray());
    }

    private List<Object> getAndPutValueLocked(K key, V value) {
        double syncId = ThreadLocalRandom.current().nextDouble();
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
    
    RFuture<List<Object>> getAndPutValue(K key, V value) {
        Long creationTimeout = getCreationTimeout();
        
        Long updateTimeout = getUpdateTimeout();
        
        double syncId = ThreadLocalRandom.current().nextDouble();
        
        RPromise<List<Object>> result = new RedissonPromise<>();
        RFuture<List<Object>> future = commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_LIST,
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
        
        future.onComplete((r, e) -> {
            if (e != null) {
                result.tryFailure(e);
                return;
            }
            
            if (!r.isEmpty()) {
                r.add(syncId);
            }
            result.trySuccess(r);
        });
        
        return result;
    }

    @Override
    public V getAndPut(K key, V value) {
        RFuture<V> future = getAndPutAsync(key, value);
        future.syncUninterruptibly();
        return future.getNow();
    }
    
    @Override
    public RFuture<V> getAndPutAsync(K key, V value) {
        checkNotClosed();
        checkKey(key);
        if (value == null) {
            throw new NullPointerException();
        }
        
        RPromise<V> result = new RedissonPromise<>();
        long startTime = currentNanoTime();
        if (config.isWriteThrough()) {
            RLock lock = getLockedLock(key);
            try {
                RFuture<List<Object>> future;
                if (atomicExecution) {
                    future = getAndPutValue(key, value);
                } else {
                    List<Object> res = getAndPutValueLocked(key, value);
                    future = RedissonPromise.newSucceededFuture(res);
                }
                future.onComplete((res, ex) -> {
                    if (ex != null) {
                        result.tryFailure(new CacheException(ex));
                        return;
                    }
                    
                    if (res.isEmpty()) {
                        cacheManager.getStatBean(this).addPuts(1);
                        cacheManager.getStatBean(this).addMisses(1);
                        cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                        cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                        result.trySuccess(null);
                        return;
                    }
                    Long added = (Long) res.get(0);
                    if (added == null) {
                        cacheManager.getStatBean(this).addPuts(1);
                        cacheManager.getStatBean(this).addHits(1);
                        cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                        cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                        result.trySuccess((V) res.get(1));
                        return;
                    }
                    
                    RPromise<Void> writeRes = new RedissonPromise<>();
                    writeCache(key, value, writeRes, startTime, res, added);
                    writeRes.onComplete((r, e) -> {
                        if (e != null) {
                            result.tryFailure(e);
                            return;
                        }
                        
                        V val = getAndPutResult(startTime, res);
                        result.trySuccess(val);
                    });
                });
            } finally {
                lock.unlock();
            }
        } else {
            RLock lock = getLockedLock(key);
            try {
                RFuture<List<Object>> future;
                if (atomicExecution) {
                    future = getAndPutValue(key, value);
                } else {
                    List<Object> res = getAndPutValueLocked(key, value);
                    future = RedissonPromise.newSucceededFuture(res);
                }
                future.onComplete((r, e) -> {
                    if (e != null) {
                        result.tryFailure(new CacheException(e));
                        return;
                    }
                    
                    V val = getAndPutResult(startTime, r);
                    result.trySuccess(val);
                });
            } finally {
                lock.unlock();
            }
        }
        return result;
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
        RFuture<Void> result = putAllAsync(map);
        result.syncUninterruptibly();
    }

    @Override
    public RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map) {
        checkNotClosed();

        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            K key = entry.getKey();
            checkKey(key);
            V value = entry.getValue();
            if (value == null) {
                throw new NullPointerException();
            }
        }
        
        
        long startTime = currentNanoTime();
        RFuture<Long> future = putAllValues(map);
        RPromise<Void> result = new RedissonPromise<>();
        
        Runnable r = () -> {
            Map<K, Cache.Entry<? extends K, ? extends V>> addedEntries = new HashMap<>();
            for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
                addedEntries.put(entry.getKey(), new JCacheEntry<K, V>(entry.getKey(), entry.getValue()));
            }
            
            try {
                cacheWriter.writeAll(addedEntries.values());
            } catch (Exception e) {
                removeValues(addedEntries.keySet().toArray()).onComplete((res, ex) -> {
                    if (ex != null) {
                        result.tryFailure(new CacheException(ex));
                        return;
                    }
                    
                    handleException(result, e);
                });
                return;
            }
            
            result.trySuccess(null);
        };

        future.onComplete((res, ex) -> {
            if (ex != null) {
                result.tryFailure(new CacheException(ex));
                return;
            }
            
            cacheManager.getStatBean(this).addPuts(res);
            for (int i = 0; i < res; i++) {
                cacheManager.getStatBean(this).addPutTime((currentNanoTime() - startTime) / res);
            }
        });
        
        if (atomicExecution) {
            future.onComplete((res, ex) -> {
                if (config.isWriteThrough()) {
                    commandExecutor.getConnectionManager().getExecutor().execute(r);
                } else {
                    result.trySuccess(null);
                }
            });
        } else {
            if (config.isWriteThrough()) {
                r.run();
            } else {
                result.trySuccess(null);
            }
        }
        return result;
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
        RFuture<Boolean> result = putIfAbsentAsync(key, value);
        result.syncUninterruptibly();
        return result.getNow();
    }

    @Override
    public RFuture<Boolean> putIfAbsentAsync(K key, V value) {
        checkNotClosed();
        checkKey(key);
        if (value == null) {
            throw new NullPointerException();
        }
        
        long startTime = currentNanoTime();
        RLock lock = getLockedLock(key);
        RPromise<Boolean> result = new RedissonPromise<>();
        try {
            RFuture<Boolean> future;
            if (atomicExecution) {
                future = putIfAbsentValue(key, value);
            } else {
                boolean r = putIfAbsentValueLocked(key, value);
                future = RedissonPromise.newSucceededFuture(r);
            }
            future.onComplete((r, ex) -> {
                if (ex != null) {
                    result.tryFailure(new CacheException(ex));
                    return;
                }
                
                if (r) {
                    cacheManager.getStatBean(this).addPuts(1);
                    if (config.isWriteThrough()) {
                        commandExecutor.getConnectionManager().getExecutor().execute(() -> {
                            try {
                                cacheWriter.write(new JCacheEntry<K, V>(key, value));
                            } catch (Exception e) {
                                removeValues(key);
                                handleException(result, e);
                                return;
                            }
                            cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                            result.trySuccess(r);
                        });
                        return;
                    }
                }
                cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                result.trySuccess(r);
            });
        } finally {
            lock.unlock();
        }
        return result;
    }

    protected void handleException(RPromise<?> result, Exception e) {
        if (e instanceof CacheWriterException) {
            result.tryFailure(e);
        }
        result.tryFailure(new CacheWriterException(e));
    }
    
    RFuture<Boolean> removeValue(K key) {
        double syncId = ThreadLocalRandom.current().nextDouble();
        
        RFuture<List<Object>> future = commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_LIST,
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
        
        RPromise<Boolean> result = waitSync(syncId, future);
        return result;
    }

    @Override
    public boolean remove(K key) {
        RFuture<Boolean> future = removeAsync(key);
        future.syncUninterruptibly();
        return future.getNow();
    }
    
    @Override
    public RFuture<Boolean> removeAsync(K key) {
        checkNotClosed();
        checkKey(key);

        long startTime = System.currentTimeMillis();
        if (config.isWriteThrough()) {
            RLock lock = getLockedLock(key);
            try {
                RPromise<Boolean> result = new RedissonPromise<>();
                RFuture<V> future = getAndRemoveValue(key);
                if (atomicExecution) {
                    future.onComplete((oldValue, ex) -> {
                        if (ex != null) {
                            result.tryFailure(new CacheException(ex));
                            return;
                        }
                        
                        commandExecutor.getConnectionManager().getExecutor().submit(() -> {
                            try {
                                cacheWriter.delete(key);
                                if (oldValue != null) {
                                    cacheManager.getStatBean(this).addRemovals(1);
                                }
                                cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                                result.trySuccess(oldValue != null);
                            } catch (Exception e) {
                                if (oldValue != null) {
                                    putValue(key, oldValue);
                                }
                                handleException(result, e);
                            }
                        });
                    });
                } else {
                    future.syncUninterruptibly();
                    V oldValue = future.getNow();
                    try {
                        cacheWriter.delete(key);
                        if (oldValue != null) {
                            cacheManager.getStatBean(this).addRemovals(1);
                        }
                        cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                        result.trySuccess(oldValue != null);
                    } catch (Exception e) {
                        if (oldValue != null) {
                            putValue(key, oldValue);
                        }
                        handleException(result, e);
                    }
                }
                return result;
            } finally {
                lock.unlock();
            }
        } else {
            RFuture<Boolean> result = removeValue(key);
            result.onComplete((res, ex) -> {
                if (res) {
                    cacheManager.getStatBean(this).addRemovals(1);
                }
                cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
            });
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
    
    RFuture<Boolean> removeValue(K key, V value) {
        Long accessTimeout = getAccessTimeout();
        
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
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
        RFuture<Boolean> future = removeAsync(key, value);
        future.syncUninterruptibly();
        return future.getNow();
    }
    
    @Override
    public RFuture<Boolean> removeAsync(K key, V value) {
        checkNotClosed();
        checkKey(key);
        if (value == null) {
            throw new NullPointerException();
        }

        long startTime = currentNanoTime();
        RPromise<Boolean> result = new RedissonPromise<>();
        if (config.isWriteThrough()) {
            RLock lock = getLockedLock(key);
            try {
                if (atomicExecution) {
                    RFuture<Boolean> future = removeValue(key, value);
                    future.onComplete((r, ex) -> {
                        if (ex != null) {
                            result.tryFailure(new CacheException(ex));
                            return;
                        }
                        
                        if (r) {
                            commandExecutor.getConnectionManager().getExecutor().submit(() -> {
                                try {
                                    cacheWriter.delete(key);
                                } catch (Exception e) {
                                    putValue(key, value);
                                    
                                    handleException(result, e);
                                    return;
                                }
                                
                                cacheManager.getStatBean(this).addHits(1);
                                cacheManager.getStatBean(this).addRemovals(1);
                                cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                                result.trySuccess(r);
                            });
                        } else {
                            cacheManager.getStatBean(this).addMisses(1);
                            cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                            result.trySuccess(r);
                        }
                    });
                } else {
                    boolean res = removeValueLocked(key, value);
                    if (res) {
                        try {
                            cacheWriter.delete(key);
                        } catch (Exception e) {
                            putValue(key, value).syncUninterruptibly();
                            if (e instanceof CacheWriterException) {
                                throw e;
                            }
                            throw new CacheWriterException(e);
                        }
                        cacheManager.getStatBean(this).addHits(1);
                        cacheManager.getStatBean(this).addRemovals(1);
                        cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                    } else {
                        cacheManager.getStatBean(this).addMisses(1);
                        cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                    }
                    return RedissonPromise.newSucceededFuture(res);
                }
            } finally {
                lock.unlock();
            }
        } else {
            RLock lock = getLockedLock(key);
            try {
                RFuture<Boolean> future;
                if (atomicExecution) {
                    future = removeValue(key, value);
                } else {
                    boolean res = removeValueLocked(key, value);
                    future = RedissonPromise.newSucceededFuture(res);
                }
                future.onComplete((r, ex) -> {
                    if (ex != null) {
                        result.tryFailure(new CacheException(ex));
                        return;
                    }
                    
                    if (r) {
                        cacheManager.getStatBean(this).addHits(1);
                        cacheManager.getStatBean(this).addRemovals(1);
                    } else {
                        cacheManager.getStatBean(this).addMisses(1);
                    }
                    cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                    result.trySuccess(r);
                });
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    RFuture<Map<K, V>> getAndRemoveValues(Collection<K> keys) {
        double syncId = ThreadLocalRandom.current().nextDouble();
        RPromise<Map<K, V>> result = new RedissonPromise<>();
        
        List<Object> params = new ArrayList<>();
        params.add(System.currentTimeMillis());
        params.add(syncId);
        
        for (K key : keys) {
            params.add(encodeMapKey(key));
        }
        
        RFuture<List<Object>> future = commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_MAP_VALUE_LIST,
                "local syncs = 0; "
              + "local values = {}; "
              + "local result = {}; "
              + "local nulls = {}; "
              
              + "for i = 3, #ARGV, 1 do "
                  + "local value = redis.call('hget', KEYS[1], ARGV[i]); "
                  + "if value == false then "
                      + "table.insert(nulls, i-3); "
                  + "else "
                      + "local expireDate = 92233720368547758; "
                      + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[i]); "
                      + "if expireDateScore ~= false then "
                          + "expireDate = tonumber(expireDateScore); "
                      + "end; "
                      
                      + "if expireDate <= tonumber(ARGV[1]) then "
                          + "table.insert(nulls, i-3); "
                      + "else "
                          + "redis.call('hdel', KEYS[1], ARGV[i]); "
                          + "redis.call('zrem', KEYS[2], ARGV[i]); "
                          + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[i]), ARGV[i], string.len(tostring(value)), tostring(value)); "
                          + "redis.call('publish', KEYS[3], msg); "
                          + "local syncMsg = struct.pack('Lc0Lc0d', string.len(ARGV[i]), ARGV[i], string.len(tostring(value)), tostring(value), ARGV[2]); "
                          + "syncs = syncs + redis.call('publish', KEYS[4], syncMsg); "
                          + "table.insert(values, value); "
                      + "end; "
                  + "end; "
              + "end; "
              
              + "table.insert(result, syncs); "
              + "table.insert(result, #nulls); "
              + "for i = 1, #nulls, 1 do "
                  + "table.insert(result, nulls[i]); "
              + "end; "
              + "for i = 1, #values, 1 do "
                  + "table.insert(result, values[i]); "
              + "end; "
              + "return result; ",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getRemovedSyncChannelName()),
                params.toArray());
        
        if (atomicExecution) {
            future.onComplete((r, exc1) -> {
                if (exc1 != null) {
                    result.tryFailure(exc1);
                    return;
                }

                long nullsAmount = (long) r.get(1);
                if (nullsAmount == keys.size()) {
                    result.trySuccess(Collections.emptyMap());
                    return;
                }
                
                long syncs = (long) r.get(0);
                if (syncs > 0) {
                    RSemaphore semaphore = redisson.getSemaphore(getSyncName(syncId));
                    semaphore.acquireAsync((int) syncs).onComplete((obj1, ex) -> {
                        if (ex != null) {
                            result.tryFailure(ex);
                            return;
                        }
                        semaphore.deleteAsync().onComplete((obj, exc) -> {
                            if (exc != null) {
                                result.tryFailure(exc);
                                return;
                            }
                            
                            getAndRemoveValuesResult(keys, result, r, nullsAmount);
                        });
                    });
                } else {
                    getAndRemoveValuesResult(keys, result, r, nullsAmount);
                }
            });
        } else {
            future.syncUninterruptibly();
            
            List<Object> r = future.getNow();
            
            long nullsAmount = (long) r.get(1);
            if (nullsAmount == keys.size()) {
                result.trySuccess(Collections.emptyMap());
                return result;
            }
            
            long syncs = (long) r.get(0);
            if (syncs > 0) {
                RSemaphore semaphore = redisson.getSemaphore(getSyncName(syncId));
                try {
                    semaphore.acquire((int) syncs);
                    semaphore.delete();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            getAndRemoveValuesResult(keys, result, r, nullsAmount);
        }
        
        return result;
    }

    private void getAndRemoveValuesResult(Collection<K> keys, RPromise<Map<K, V>> result, List<Object> r,
            long nullsAmount) {
        HashSet<Long> nullIndexes = new HashSet<>((List<Long>) (Object) r.subList(2, (int) nullsAmount + 2));
        Map<K, V> res = new HashMap<>();
        long i = 0;
        for (K key : keys) {
            if (nullIndexes.contains(i)) {
                continue;
            }
            V value = (V) r.get((int) (i+nullsAmount+2));
            res.put(key, value);
            i++;
        }
        result.trySuccess(res);
    }
    
    RFuture<V> getAndRemoveValue(K key) {
        double syncId = ThreadLocalRandom.current().nextDouble();
        RPromise<V> result = new RedissonPromise<>();
        RFuture<List<Object>> future = commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_MAP_VALUE_LIST,
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
        
        if (atomicExecution) {
            future.onComplete((r, exc1) -> {
                if (exc1 != null) {
                    result.tryFailure(exc1);
                    return;
                }

                if (r.size() < 2) {
                    result.trySuccess(null);
                    return;
                }
                
                Long syncs = (Long) r.get(1);
                if (syncs != null && syncs > 0) {
                    RSemaphore semaphore = redisson.getSemaphore(getSyncName(syncId));
                    semaphore.acquireAsync(syncs.intValue()).onComplete((obj1, ex) -> {
                        if (ex != null) {
                            result.tryFailure(ex);
                            return;
                        }
                        semaphore.deleteAsync().onComplete((obj, exc) -> {
                            if (exc != null) {
                                result.tryFailure(exc);
                                return;
                            }
                            result.trySuccess((V) r.get(0));
                        });
                    });
                } else {
                    result.trySuccess((V) r.get(0));
                }
            });
        } else {
            future.syncUninterruptibly();
            
            List<Object> r = future.getNow();
            
            if (r.size() < 2) {
                result.trySuccess(null);
                return result;
            }
            
            Long syncs = (Long) r.get(1);
            if (syncs != null && syncs > 0) {
                RSemaphore semaphore = redisson.getSemaphore(getSyncName(syncId));
                try {
                    semaphore.acquire(syncs.intValue());
                    semaphore.delete();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            result.trySuccess((V) r.get(0));
        }
        
        return result;
    }

    @Override
    public V getAndRemove(K key) {
        RFuture<V> future = getAndRemoveAsync(key);
        future.syncUninterruptibly();
        return future.getNow();
    }
    
    @Override
    public RFuture<V> getAndRemoveAsync(K key) {
        checkNotClosed();
        checkKey(key);

        long startTime = currentNanoTime();
        RPromise<V> result = new RedissonPromise<>();
        RLock lock = getLockedLock(key);
        try {
            RFuture<V> future = getAndRemoveValue(key);
            future.onComplete((value, e) -> {
                if (e != null) {
                    result.tryFailure(new CacheException(e));
                    return;
                }
                
                if (value != null) {
                    cacheManager.getStatBean(this).addHits(1);
                    cacheManager.getStatBean(this).addRemovals(1);
                } else {
                    cacheManager.getStatBean(this).addMisses(1);
                }
                
                if (config.isWriteThrough()) {
                    commandExecutor.getConnectionManager().getExecutor().submit(() -> {
                        try {
                            cacheWriter.delete(key);
                        } catch (Exception ex) {
                            if (value != null) {
                                putValue(key, value);
                            }
                            
                            handleException(result, ex);
                            return;
                        }
                        cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                        cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                        result.trySuccess(value);
                    });
                } else {
                    cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                    cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                    result.trySuccess(value);
                }
            });
        } finally {
            lock.unlock();
        }
        return result;
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
           double syncId = ThreadLocalRandom.current().nextDouble();
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

       double syncId = ThreadLocalRandom.current().nextDouble();
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

    
    RFuture<Long> replaceValue(K key, V oldValue, V newValue) {
        Long accessTimeout = getAccessTimeout();
        
        Long updateTimeout = getUpdateTimeout();

        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_LONG,
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
                  + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[4]); "
                  + "return 0;"
              + "end; "
              + "return -1; ",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getRemovedChannelName(), getUpdatedChannelName()), 
             accessTimeout, updateTimeout, System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(oldValue), encodeMapValue(newValue));
        
    }
    
    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        RFuture<Boolean> future = replaceAsync(key, oldValue, newValue);
        future.syncUninterruptibly();
        return future.getNow();
    }
    
    @Override
    public RFuture<Boolean> replaceAsync(K key, V oldValue, V newValue) {
        checkNotClosed();
        checkKey(key);
        if (oldValue == null) {
            throw new NullPointerException();
        }
        if (newValue == null) {
            throw new NullPointerException();
        }

        long startTime = currentNanoTime();
        RLock lock = getLockedLock(key);
        RPromise<Boolean> result = new RedissonPromise<>();
        try {
            RFuture<Long> future;
            if (atomicExecution) {
                future = replaceValue(key, oldValue, newValue);
            } else {
                long res = replaceValueLocked(key, oldValue, newValue);
                future = RedissonPromise.newSucceededFuture(res);
            }
            future.onComplete((res, ex) -> {
                if (ex != null) {
                    result.tryFailure(new CacheException(ex));
                    return;
                }
                
                if (res == 1) {
                    if (config.isWriteThrough()) {
                        commandExecutor.getConnectionManager().getExecutor().submit(() -> {
                            try {
                                cacheWriter.write(new JCacheEntry<K, V>(key, newValue));
                            } catch (Exception e) {
                                removeValues(key);
                                
                                handleException(result, e);
                                return;
                            }
                            
                            cacheManager.getStatBean(this).addHits(1);
                            cacheManager.getStatBean(this).addPuts(1);
                            cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                            cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                            result.trySuccess(true);
                        });
                    } else {
                        cacheManager.getStatBean(this).addHits(1);
                        cacheManager.getStatBean(this).addPuts(1);
                        cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                        cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                        result.trySuccess(true);
                    }
                } else {
                    if (res == 0) {
                        cacheManager.getStatBean(this).addMisses(1);
                    } else {
                        cacheManager.getStatBean(this).addHits(1);
                    }
                    cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                    cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                    result.trySuccess(false);
                }
            });
        } finally {
            lock.unlock();
        }
        return result;
    }
    
    private boolean replaceValueLocked(K key, V value) {

        if (containsKey(key)) {
            double syncId = ThreadLocalRandom.current().nextDouble();
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

    
    RFuture<Boolean> replaceValue(K key, V value) {
        Long updateTimeout = getUpdateTimeout();

        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
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
    
    RFuture<V> getAndReplaceValue(K key, V value) {
        Long updateTimeout = getUpdateTimeout();

        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_MAP_VALUE,
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
            double syncId = ThreadLocalRandom.current().nextDouble();
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
        RFuture<Boolean> future = replaceAsync(key, value);
        future.syncUninterruptibly();
        return future.getNow();
    }
    
    @Override
    public RFuture<Boolean> replaceAsync(K key, V value) {
        checkNotClosed();
        checkKey(key);
        if (value == null) {
            throw new NullPointerException();
        }

        long startTime = currentNanoTime();
        RPromise<Boolean> result = new RedissonPromise<>();
        RLock lock = getLockedLock(key);
        try {
            RFuture<Boolean> future;
            if (atomicExecution) {
                future = replaceValue(key, value);
            } else {
                boolean res = replaceValueLocked(key, value);
                future = RedissonPromise.newSucceededFuture(res);
            }
            
            future.onComplete((r, ex) -> {
                if (ex != null) {
                    result.tryFailure(new CacheException(ex));
                    return;
                }
                
                if (r) {
                    if (config.isWriteThrough()) {
                        commandExecutor.getConnectionManager().getExecutor().submit(() -> {
                            try {
                                cacheWriter.write(new JCacheEntry<K, V>(key, value));
                            } catch (Exception e) {
                                removeValues(key);
                                
                                handleException(result, e);
                            }
                            
                            cacheManager.getStatBean(this).addHits(1);
                            cacheManager.getStatBean(this).addPuts(1);
                            cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                            result.trySuccess(r);
                        });
                        return;
                    }
                    
                    cacheManager.getStatBean(this).addHits(1);
                    cacheManager.getStatBean(this).addPuts(1);
                } else {
                    cacheManager.getStatBean(this).addMisses(1);
                }
                cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                result.trySuccess(r);
            });
        } finally {
            lock.unlock();
        }
        
        return result;
    }

    @Override
    public V getAndReplace(K key, V value) {
        RFuture<V> future = getAndReplaceAsync(key, value);
        future.syncUninterruptibly();
        return future.getNow();
    }
    
    @Override
    public RFuture<V> getAndReplaceAsync(K key, V value) {
        checkNotClosed();
        checkKey(key);
        if (value == null) {
            throw new NullPointerException();
        }

        long startTime = currentNanoTime();
        RPromise<V> result = new RedissonPromise<>();
        RLock lock = getLockedLock(key);
        try {
            RFuture<V> future;
            if (atomicExecution) {
                future = getAndReplaceValue(key, value);
            } else {
                V res = getAndReplaceValueLocked(key, value);
                future = RedissonPromise.newSucceededFuture(res);
            }
            future.onComplete((r, ex) -> {
                if (ex != null) {
                    result.tryFailure(new CacheException(ex));
                    return;
                }
                
                if (r != null) {
                    if (config.isWriteThrough()) {
                        commandExecutor.getConnectionManager().getExecutor().submit(() -> {
                            cacheManager.getStatBean(this).addHits(1);
                            cacheManager.getStatBean(this).addPuts(1);
                            try {
                                cacheWriter.write(new JCacheEntry<K, V>(key, value));
                            } catch (Exception e) {
                                removeValues(key);
                                
                                handleException(result, e);
                                return;
                            }
                            
                            cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                            cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                            result.trySuccess(r);
                        });
                        return;
                    }
                    cacheManager.getStatBean(this).addHits(1);
                    cacheManager.getStatBean(this).addPuts(1);
                } else {
                    cacheManager.getStatBean(this).addMisses(1);
                }
                cacheManager.getStatBean(this).addPutTime(currentNanoTime() - startTime);
                cacheManager.getStatBean(this).addGetTime(currentNanoTime() - startTime);
                result.trySuccess(r);
            });
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public void removeAll(Set<? extends K> keys) {
        RFuture<Void> future = removeAllAsync(keys);
        future.syncUninterruptibly();
    }
    
    @Override
    public RFuture<Void> removeAllAsync(Set<? extends K> keys) {
        checkNotClosed();
        
        for (K key : keys) {
            checkKey(key);
        }
        
        long startTime = currentNanoTime();
        RPromise<Void> result = new RedissonPromise<>();
        if (config.isWriteThrough()) {
            RFuture<Map<K, V>> future = getAndRemoveValues((Set<K>) keys);
            future.onComplete((r, ex) -> {
                if (ex != null) {
                    result.tryFailure(ex);
                    return;
                }
                
                try {
                    cacheWriter.deleteAll(r.keySet());
                } catch (Exception e) {
                    putAllValues(r);
                    handleException(result, e);
                    return;
                }
                cacheManager.getStatBean(this).addRemovals(r.size());
                cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                result.trySuccess(null);
            });
        } else {
            RFuture<Long> future = removeValues(keys.toArray());
            future.onComplete((res, ex) -> {
                if (ex != null) {
                    result.tryFailure(ex);
                    return;
                }

                cacheManager.getStatBean(this).addRemovals(res);
                cacheManager.getStatBean(this).addRemoveTime(currentNanoTime() - startTime);
                result.trySuccess(null);
            });
        }
        return result;
    }
    
    MapScanResult<Object, Object> scanIterator(String name, RedisClient client, long startPos) {
        RFuture<MapScanResult<Object, Object>> f 
            = commandExecutor.readAsync(client, name, codec, RedisCommands.HSCAN, name, startPos, "COUNT", 50);
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
        Set<K> keys = new HashSet<>();
        for (Iterator<K> iterator = keyIterator(); iterator.hasNext();) {
            K key = iterator.next();
            keys.add(key);
            if (keys.size() == 50) {
                removeAll(keys);
                keys.clear();
            }
        }

        if (!keys.isEmpty()) {
            removeAll(keys);
        }
    }

    @Override
    public void clear() {
        RFuture<Void> future = clearAsync();
        future.syncUninterruptibly();
    }
    
    @Override
    public RFuture<Void> clearAsync() {
        checkNotClosed();
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_OBJECTS, getName(), getTimeoutSetName());
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
        checkKey(key);
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
        if (clazz == CacheAsync.class) {
            return (T) this;
        }
        if (clazz == CacheReactive.class) {
            return (T) ReactiveProxyBuilder.create(new CommandReactiveService(commandExecutor.getConnectionManager()), this, CacheReactive.class);
        }
        if (clazz == CacheRx.class) {
            return (T) RxProxyBuilder.create(new CommandRxService(commandExecutor.getConnectionManager()), this, CacheRx.class);
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
