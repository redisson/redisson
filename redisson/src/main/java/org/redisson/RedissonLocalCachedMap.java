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
package org.redisson;

import io.netty.buffer.ByteBuf;
import org.redisson.api.*;
import org.redisson.api.LocalCachedMapOptions.ReconnectionStrategy;
import org.redisson.api.LocalCachedMapOptions.SyncStrategy;
import org.redisson.api.listener.LocalCacheInvalidateListener;
import org.redisson.api.listener.LocalCacheUpdateListener;
import org.redisson.cache.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.NumberConvertor;
import org.redisson.client.protocol.decoder.MapValueDecoder;
import org.redisson.client.protocol.decoder.ObjectMapEntryReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectMapReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectSetReplayDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.eviction.EvictionScheduler;
import org.redisson.misc.CompletableFutureWrapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("serial")
public class RedissonLocalCachedMap<K, V> extends RedissonMap<K, V> implements RLocalCachedMap<K, V> {

    public static final String TOPIC_SUFFIX = "topic";
    public static final String DISABLED_KEYS_SUFFIX = "disabled-keys";
    public static final String DISABLED_ACK_SUFFIX = ":topic";

    private static final RedisCommand<Set<Object>> ALL_VALUES = new RedisCommand<Set<Object>>("EVAL", new MapValueDecoder(new ObjectSetReplayDecoder<Object>()));
    private static final RedisCommand<Set<Entry<Object, Object>>> ALL_ENTRIES = new RedisCommand<>("EVAL", new ObjectMapEntryReplayDecoder());
    private static final RedisCommand<Map<Object, Object>> ALL_MAP = new RedisCommand<Map<Object, Object>>("EVAL", new ObjectMapReplayDecoder());
    
    private long cacheUpdateLogTime = TimeUnit.MINUTES.toMillis(10);
    private byte[] instanceId;
    private ConcurrentMap<CacheKey, CacheValue> cache;
    private int invalidateEntryOnChange;
    private SyncStrategy syncStrategy;
    private LocalCachedMapOptions.StoreMode storeMode;
    private boolean storeCacheMiss;

    private LocalCacheListener listener;
    private LocalCacheView<K, V> localCacheView;
    private String publishCommand;

    public RedissonLocalCachedMap(CommandAsyncExecutor commandExecutor, String name, LocalCachedMapOptions<K, V> options, 
            EvictionScheduler evictionScheduler, RedissonClient redisson, WriteBehindService writeBehindService) {
        super(commandExecutor, name, redisson, options, writeBehindService);
        init(options, evictionScheduler);
    }

    public RedissonLocalCachedMap(Codec codec, CommandAsyncExecutor connectionManager, String name, LocalCachedMapOptions<K, V> options, 
            EvictionScheduler evictionScheduler, RedissonClient redisson, WriteBehindService writeBehindService) {
        super(codec, connectionManager, name, redisson, options, writeBehindService);
        init(options, evictionScheduler);
    }

    private void init(LocalCachedMapOptions<K, V> options, EvictionScheduler evictionScheduler) {
        syncStrategy = options.getSyncStrategy();
        storeMode = options.getStoreMode();
        storeCacheMiss = options.isStoreCacheMiss();

        publishCommand = commandExecutor.getConnectionManager().getSubscribeService().getPublishCommand();
        localCacheView = new LocalCacheView<>(options, this);
        cache = localCacheView.getCache();
        listener = new LocalCacheListener(getRawName(), commandExecutor, this, codec, options, cacheUpdateLogTime, getSubscribeService().isShardingSupported()) {

            @Override
            protected CacheValue updateCache(ByteBuf keyBuf, ByteBuf valueBuf) throws IOException {
                CacheKey cacheKey = localCacheView.toCacheKey(keyBuf);
                Object key = codec.getMapKeyDecoder().decode(keyBuf, null);
                Object value = codec.getMapValueDecoder().decode(valueBuf, null);
                cachePut(cacheKey, key, value);
                return new CacheValue(key, value);
            }

        };
        listener.add(cache);
        instanceId = listener.getInstanceId();

        if (options.getSyncStrategy() != SyncStrategy.NONE) {
            invalidateEntryOnChange = 1;
        }
        if (options.getReconnectionStrategy() == ReconnectionStrategy.LOAD) {
            invalidateEntryOnChange = 2;
            evictionScheduler.schedule(listener.getUpdatesLogName(), cacheUpdateLogTime + TimeUnit.MINUTES.toMillis(1));
        }
    }

    public LocalCacheView<K, V> getLocalCacheView() {
        return localCacheView;
    }

    private void broadcastLocalCacheStore(V value, ByteBuf mapKey, CacheKey cacheKey) {
        if (storeMode != LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            return;
        }

        if (invalidateEntryOnChange != 0) {
            Object msg;
            if (syncStrategy == SyncStrategy.UPDATE) {
                ByteBuf mapValue = encodeMapValue(value);
                msg = new LocalCachedMapUpdate(instanceId, mapKey, mapValue);
                mapValue.release();
            } else {
                msg = new LocalCachedMapInvalidate(instanceId, cacheKey.getKeyHash());
            }
            listener.getInvalidationTopic().publishAsync(msg);
        }
        mapKey.release();
    }

    private CacheValue cachePut(CacheKey cacheKey, Object key, Object value) {
        if (listener.isDisabled(cacheKey)) {
            return null;
        }

        CacheValue newValue = new CacheValue(key, value);
        CacheValue oldValue = cache.put(cacheKey, newValue);
        Object oldV = null;
        if (oldValue != null) {
            oldV = oldValue.getValue();
        }
        listener.notifyInvalidate(new CacheValue(key, oldV));
        listener.notifyUpdate(newValue);
        return oldValue;
    }

    private CacheValue cachePutIfAbsent(CacheKey cacheKey, Object key, Object value) {
        if (listener.isDisabled(cacheKey)) {
            return null;
        }

        return cache.putIfAbsent(cacheKey, new CacheValue(key, value));
    }

    private CacheValue cachePutIfExists(CacheKey cacheKey, Object key, Object value) {
        if (listener.isDisabled(cacheKey)) {
            return null;
        }

        while (true) {
            CacheValue v = cache.get(cacheKey);
            if (v != null) {
                if (cache.replace(cacheKey, v, new CacheValue(key, value))) {
                    return v;
                }
            } else {
                return null;
            }
        }
    }

    private CacheValue cacheReplace(CacheKey cacheKey, Object key, Object value) {
        if (listener.isDisabled(cacheKey)) {
            return null;
        }

        return cache.replace(cacheKey, new CacheValue(key, value));
    }

    private boolean cacheReplace(CacheKey cacheKey, Object key, Object oldValue, Object newValue) {
        if (listener.isDisabled(cacheKey)) {
            return false;
        }

        return cache.replace(cacheKey, new CacheValue(key, oldValue), new CacheValue(key, newValue));
    }

    private boolean cacheRemove(CacheKey cacheKey, Object key, Object value) {
        if (listener.isDisabled(cacheKey)) {
            return false;
        }

        return cache.remove(cacheKey, new CacheValue(key, value));
    }

    private CacheValue cacheRemove(CacheKey cacheKey) {
        CacheValue v = cache.remove(cacheKey);
        listener.notifyInvalidate(v);
        listener.notifyUpdate(v);
        return v;
    }

    @Override
    public RFuture<Integer> sizeAsync() {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            return new CompletableFutureWrapper<>(cache.size());
        }
        return super.sizeAsync();
    }

    @Override
    public RFuture<Boolean> containsKeyAsync(Object key) {
        checkKey(key);
        
        CacheKey cacheKey = localCacheView.toCacheKey(key);
        CacheValue cacheValue = cache.get(cacheKey);
        if (cacheValue == null) {
            if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
                if (hasNoLoader()) {
                    return new CompletableFutureWrapper<>(false);
                }

                CompletableFuture<V> future = loadValue((K) key, false);
                CompletableFuture<Boolean> f = future.thenApply(value -> {
                    if (storeCacheMiss || value != null) {
                        cachePut(cacheKey, key, value);
                    }
                    return value != null;
                });
                return new CompletableFutureWrapper<>(f);
            }

            String name = getRawName(key);
            RFuture<Boolean> future = containsKeyOperationAsync(name, key);
            CompletionStage<Boolean> result = future.thenCompose(res -> {
                if (hasNoLoader()) {
                    if (!res && storeCacheMiss) {
                        cachePut(cacheKey, key, null);
                    }
                    return CompletableFuture.completedFuture(res);
                }
                if (!res) {
                    CompletableFuture<V> f = loadValue((K) key, false);
                    return f.thenApply(value -> {
                        if (storeCacheMiss || value != null) {
                            cachePut(cacheKey, key, value);
                        }
                        return value != null;
                    });
                }
                return CompletableFuture.completedFuture(res);
            });
            return new CompletableFutureWrapper<>(result);
        }

        return new CompletableFutureWrapper<>(cacheValue.getValue() != null);
    }

    @Override
    public RFuture<Boolean> containsValueAsync(Object value) {
        checkValue(value);
        
        CacheValue cacheValue = new CacheValue(null, value);
        if (!cache.containsValue(cacheValue)) {
            if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
                return new CompletableFutureWrapper<>(false);
            }

            return super.containsValueAsync(value);
        }
        return new CompletableFutureWrapper<>(true);
    }
    
    @Override
    protected RFuture<V> getAsync(K key, long threadId) {
        checkKey(key);

        CacheKey cacheKey = localCacheView.toCacheKey(key);
        CacheValue cacheValue = cache.get(cacheKey);
        if (cacheValue != null && (storeCacheMiss || cacheValue.getValue() != null)) {
            return new CompletableFutureWrapper<>((V) cacheValue.getValue());
        }

        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            if (hasNoLoader()) {
                return new CompletableFutureWrapper((Void) null);
            }

            CompletableFuture<V> future = loadValue((K) key, false, threadId);
            CompletableFuture<V> f = future.thenApply(value -> {
                if (storeCacheMiss || value != null) {
                    cachePut(cacheKey, key, value);
                }
                return value;
            });
            return new CompletableFutureWrapper<>(f);
        }

        RFuture<V> future = super.getAsync((K) key, threadId);
        CompletionStage<V> result = future.thenApply(value -> {
            if (storeCacheMiss || value != null) {
                cachePut(cacheKey, key, value);
            }
            return value;
        });
        return new CompletableFutureWrapper<>(result);
    }
    
    protected byte[] generateLogEntryId(byte[] keyHash) {
        byte[] result = new byte[keyHash.length + 1 + 8];
        result[16] = ':';
        byte[] id = getServiceManager().generateIdArray(8);

        System.arraycopy(keyHash, 0, result, 0, keyHash.length);
        System.arraycopy(id, 0, result, 17, id.length);
        return result;
    }


    @Override
    protected RFuture<V> putOperationAsync(K key, V value) {
        ByteBuf mapKey = encodeMapKey(key);
        CacheKey cacheKey = localCacheView.toCacheKey(mapKey);
        CacheValue prevValue = cachePut(cacheKey, key, value);
        broadcastLocalCacheStore(value, mapKey, cacheKey);

        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            V val = null;
            if (prevValue != null) {
                val = (V) prevValue.getValue();
            }
            return new CompletableFutureWrapper<>(val);
        }

        ByteBuf mapValue = encodeMapValue(value);
        byte[] entryId = generateLogEntryId(cacheKey.getKeyHash());
        ByteBuf msg = createSyncMessage(mapKey, mapValue, cacheKey);
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_MAP_VALUE,
                  "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                + "if ARGV[4] == '1' then "
                    + "redis.call(ARGV[7], KEYS[2], ARGV[3]); "
                + "end;"
                + "if ARGV[4] == '2' then "
                    + "redis.call('zadd', KEYS[3], ARGV[5], ARGV[6]);"
                    + "redis.call(ARGV[7], KEYS[2], ARGV[3]); "
                + "end;"
                + "return v; ",
                Arrays.asList(getRawName(), listener.getInvalidationTopicName(), listener.getUpdatesLogName()),
                mapKey, mapValue, msg, invalidateEntryOnChange, System.currentTimeMillis(), entryId, publishCommand);
    }

    protected ByteBuf createSyncMessage(ByteBuf mapKey, ByteBuf mapValue, CacheKey cacheKey) {
        if (syncStrategy == SyncStrategy.UPDATE) {
            return encode(new LocalCachedMapUpdate(instanceId, mapKey, mapValue));
        }
        return encode(new LocalCachedMapInvalidate(instanceId, cacheKey.getKeyHash()));
    }

    @Override
    protected RFuture<Boolean> fastPutOperationAsync(K key, V value) {
        ByteBuf encodedKey = encodeMapKey(key);
        CacheKey cacheKey = localCacheView.toCacheKey(encodedKey);

        CacheValue prevValue = cachePut(cacheKey, key, value);
        broadcastLocalCacheStore(value, encodedKey, cacheKey);
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            return new CompletableFutureWrapper<>(prevValue == null);
        }

        ByteBuf encodedValue = encodeMapValue(value);
        byte[] entryId = generateLogEntryId(cacheKey.getKeyHash());
        ByteBuf msg = createSyncMessage(encodedKey, encodedValue, cacheKey);

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                  "if ARGV[4] == '1' then "
                    + "redis.call(ARGV[7], KEYS[2], ARGV[3]); "
                + "end;"
                + "if ARGV[4] == '2' then "
                    + "redis.call('zadd', KEYS[3], ARGV[5], ARGV[6]);"
                    + "redis.call(ARGV[7], KEYS[2], ARGV[3]); "
                + "end;"
                + "if redis.call('hset', KEYS[1], ARGV[1], ARGV[2]) == 0 then "
                  + "return 0; "
                + "end; "
                + "return 1; ",
                Arrays.asList(getRawName(), listener.getInvalidationTopicName(), listener.getUpdatesLogName()),
                encodedKey, encodedValue, msg, invalidateEntryOnChange, System.currentTimeMillis(), entryId, publishCommand);
    }
    
    @Override
    public void destroy() {
        super.destroy();
        cache.clear();
        listener.remove();
    }

    @Override
    protected RFuture<V> removeOperationAsync(K key) {
        ByteBuf keyEncoded = encodeMapKey(key);
        CacheKey cacheKey = localCacheView.toCacheKey(keyEncoded);
        CacheValue value = cacheRemove(cacheKey);

        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            keyEncoded.release();
            LocalCachedMapInvalidate msg = new LocalCachedMapInvalidate(instanceId, cacheKey.getKeyHash());
            listener.getInvalidationTopic().publishAsync(msg);

            V val = null;
            if (value != null) {
                val = (V) value.getValue();
            }
            return new CompletableFutureWrapper<>(val);
        }

        byte[] entryId = generateLogEntryId(cacheKey.getKeyHash());
        ByteBuf msgEncoded = encode(new LocalCachedMapInvalidate(instanceId, cacheKey.getKeyHash()));

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_MAP_VALUE,
                "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                + "if redis.call('hdel', KEYS[1], ARGV[1]) == 1 then "
                    + "if ARGV[3] == '1' then "
                        + "redis.call(ARGV[6], KEYS[2], ARGV[2]); "
                    + "end; "
                    + "if ARGV[3] == '2' then "
                        + "redis.call('zadd', KEYS[3], ARGV[4], ARGV[5]);"
                        + "redis.call(ARGV[6], KEYS[2], ARGV[2]); "
                    + "end;"
                + "end; "
                + "return v",
                Arrays.asList(getRawName(), listener.getInvalidationTopicName(), listener.getUpdatesLogName()),
                keyEncoded, msgEncoded, invalidateEntryOnChange, System.currentTimeMillis(), entryId, publishCommand);
    }

    @Override
    protected RFuture<List<Long>> fastRemoveOperationBatchAsync(@SuppressWarnings("unchecked") K... keys) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            return new CompletableFutureWrapper<>(Collections.emptyList());
        }

            if (invalidateEntryOnChange == 1) {
                List<Object> params = new ArrayList<Object>(keys.length*2+1);
                params.add(publishCommand);
                for (K k : keys) {
                    ByteBuf keyEncoded = encodeMapKey(k);
                    params.add(keyEncoded);
                    
                    CacheKey cacheKey = localCacheView.toCacheKey(keyEncoded);
                    cacheRemove(cacheKey);
                    ByteBuf msgEncoded = encode(new LocalCachedMapInvalidate(instanceId, cacheKey.getKeyHash()));
                    params.add(msgEncoded);
                }
    
                return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_LIST,
                                "local result = {}; " +
                                "for j = 2, #ARGV, 2 do "
                                + "local val = redis.call('hdel', KEYS[1], ARGV[j]);" 
                                + "if val == 1 then "
                                   + "redis.call(ARGV[1], KEYS[2], ARGV[j+1]); "
                                + "end;"
                                + "table.insert(result, val);"
                              + "end;"
                              + "return result;",
                                Arrays.asList(getRawName(), listener.getInvalidationTopicName()),
                                params.toArray());            
            }
            
            if (invalidateEntryOnChange == 2) {
                List<Object> params = new ArrayList<Object>(keys.length*3);
                params.add(System.currentTimeMillis());
                params.add(publishCommand);
                for (K k : keys) {
                    ByteBuf keyEncoded = encodeMapKey(k);
                    params.add(keyEncoded);
                    
                    CacheKey cacheKey = localCacheView.toCacheKey(keyEncoded);
                    cacheRemove(cacheKey);
                    ByteBuf msgEncoded = encode(new LocalCachedMapInvalidate(instanceId, cacheKey.getKeyHash()));
                    params.add(msgEncoded);
                    
                    byte[] entryId = generateLogEntryId(cacheKey.getKeyHash());
                    params.add(entryId);
                }
                
                return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_LIST,
                                "local result = {}; " + 
                                "for j = 3, #ARGV, 3 do "
                                + "local val = redis.call('hdel', KEYS[1], ARGV[j]);" 
                                + "if val == 1 then "
                                    + "redis.call('zadd', KEYS[3], ARGV[1], ARGV[j+2]);"                                
                                    + "redis.call(ARGV[2], KEYS[2], ARGV[j+1]); "
                                + "end;"
                                + "table.insert(result, val);"
                              + "end;"
                              + "return result;",
                                Arrays.asList(getRawName(), listener.getInvalidationTopicName(), listener.getUpdatesLogName()),
                                params.toArray());            
            }
    
        List<Object> params = new ArrayList<Object>(keys.length);
        for (K k : keys) {
            ByteBuf keyEncoded = encodeMapKey(k);
            params.add(keyEncoded);
            
            CacheKey cacheKey = localCacheView.toCacheKey(keyEncoded);
            cacheRemove(cacheKey);
        }
    
        RFuture<List<Long>> future = commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_LIST,
                      "local result = {}; " + 
                      "for i = 1, #ARGV, 1 do " 
                      + "local val = redis.call('hdel', KEYS[1], ARGV[i]); "
                      + "table.insert(result, val); "
                    + "end;"
                    + "return result;",
                      Arrays.<Object>asList(getRawName()),
                      params.toArray());
        return future;
    }
    
    @Override
    protected RFuture<Long> fastRemoveOperationAsync(K... keys) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            long count = 0;
            for (K k : keys) {
                CacheKey cacheKey = localCacheView.toCacheKey(k);
                CacheValue val = cacheRemove(cacheKey);
                if (val != null) {
                    count++;
                    LocalCachedMapInvalidate msg = new LocalCachedMapInvalidate(instanceId, cacheKey.getKeyHash());
                    listener.getInvalidationTopic().publishAsync(msg);
                }
            }
            return new CompletableFutureWrapper<>(count);
        }

            if (invalidateEntryOnChange == 1) {
                List<Object> params = new ArrayList<Object>(keys.length*2);
                params.add(publishCommand);
                for (K k : keys) {
                    ByteBuf keyEncoded = encodeMapKey(k);
                    params.add(keyEncoded);
                    
                    CacheKey cacheKey = localCacheView.toCacheKey(keyEncoded);
                    cacheRemove(cacheKey);
                    ByteBuf msgEncoded = encode(new LocalCachedMapInvalidate(instanceId, cacheKey.getKeyHash()));
                    params.add(msgEncoded);
                }

                return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_LONG,
                        "local counter = 0; " + 
                                "for j = 2, #ARGV, 2 do "
                                + "if redis.call('hdel', KEYS[1], ARGV[j]) == 1 then "
                                   + "redis.call(ARGV[1], KEYS[2], ARGV[j+1]); "
                                   + "counter = counter + 1;"
                                + "end;"
                              + "end;"
                              + "return counter;",
                                Arrays.asList(getRawName(), listener.getInvalidationTopicName()),
                                params.toArray());            
            }
            
            if (invalidateEntryOnChange == 2) {
                List<Object> params = new ArrayList<Object>(keys.length*3);
                params.add(System.currentTimeMillis());
                params.add(publishCommand);
                for (K k : keys) {
                    ByteBuf keyEncoded = encodeMapKey(k);
                    params.add(keyEncoded);
                    
                    CacheKey cacheKey = localCacheView.toCacheKey(keyEncoded);
                    cacheRemove(cacheKey);
                    ByteBuf msgEncoded = encode(new LocalCachedMapInvalidate(instanceId, cacheKey.getKeyHash()));
                    params.add(msgEncoded);
                    
                    byte[] entryId = generateLogEntryId(cacheKey.getKeyHash());
                    params.add(entryId);
                }
                
                return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_LONG,
                                "local counter = 0; " + 
                                "for j = 3, #ARGV, 3 do "
                                + "if redis.call('hdel', KEYS[1], ARGV[j]) == 1 then "
                                    + "redis.call('zadd', KEYS[3], ARGV[1], ARGV[j+2]);"                                
                                    + "redis.call(ARGV[2], KEYS[2], ARGV[j+1]); "
                                    + "counter = counter + 1;"
                                + "end;"
                              + "end;"
                              + "return counter;",
                                Arrays.asList(getRawName(), listener.getInvalidationTopicName(), listener.getUpdatesLogName()),
                                params.toArray());            
            }

        List<Object> params = new ArrayList<Object>(keys.length + 1);
        params.add(getRawName());
        for (K k : keys) {
            ByteBuf keyEncoded = encodeMapKey(k);
            params.add(keyEncoded);
            
            CacheKey cacheKey = localCacheView.toCacheKey(keyEncoded);
            cacheRemove(cacheKey);
        }

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.HDEL, params.toArray());
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.<Object>asList(getRawName(), listener.getUpdatesLogName());
        return super.sizeInMemoryAsync(keys);
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        cache.clear();
        ByteBuf msgEncoded = encode(new LocalCachedMapClear(instanceId, getServiceManager().generateIdArray(), false));
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('del', KEYS[1], KEYS[3]) > 0 and ARGV[2] ~= '0' then "
                + "redis.call(ARGV[3], KEYS[2], ARGV[1]); "
                + "return 1;" 
              + "end; "
              + "return 0;",
              Arrays.asList(getRawName(), listener.getInvalidationTopicName(), listener.getUpdatesLogName()),
              msgEncoded, invalidateEntryOnChange, publishCommand);
    }

    @Override
    public RFuture<Map<K, V>> getAllAsync(Set<K> keys) {
        if (keys.isEmpty()) {
            return new CompletableFutureWrapper<>(Collections.<K, V>emptyMap());
        }

        Map<K, V> result = new HashMap<K, V>();
        Set<K> mapKeys = new HashSet<K>(keys);
        Set<K> missedKeys = new HashSet<>();
        for (Iterator<K> iterator = mapKeys.iterator(); iterator.hasNext();) {
            K key = iterator.next();
            CacheKey cacheKey = localCacheView.toCacheKey(key);
            CacheValue value = cache.get(cacheKey);
            if (value != null) {
                if (value.getValue() != null) {
                    result.put(key, (V) value.getValue());
                }
                iterator.remove();
            } else {
                missedKeys.add(key);
            }
        }

        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            if (hasNoLoader()) {
                return new CompletableFutureWrapper<>(result);
            }

            if (!missedKeys.isEmpty()) {
                CompletionStage<Map<K, V>> f = loadAllMapAsync(missedKeys.spliterator(), false, 1);
                CompletionStage<Map<K, V>> ff = f.thenApply(map -> {
                    result.putAll(map);
                    return result;
                });
                return new CompletableFutureWrapper<>(ff);
            }
            return new CompletableFutureWrapper<>(result);
        }

        RFuture<Map<K, V>> future = super.getAllAsync(missedKeys);
        CompletionStage<Map<K, V>> f = future.thenApply(map -> {
            result.putAll(map);
            cacheMap(map);

            if (storeCacheMiss) {
                missedKeys.stream()
                        .filter(key -> !map.containsKey(key))
                        .forEach(key -> {
                            CacheKey cacheKey = localCacheView.toCacheKey(key);
                            cachePut(cacheKey, key, null);
                        });
            }
            return result;
        });
        return new CompletableFutureWrapper<>(f);
    }
    
    private void cacheMap(Map<?, ?> map) {
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            CacheKey cacheKey = localCacheView.toCacheKey(entry.getKey());
            cachePut(cacheKey, entry.getKey(), entry.getValue());
        }
    }

    @Override
    protected RFuture<Void> putAllOperationAsync(Map<? extends K, ? extends V> map) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
                ByteBuf keyEncoded = encodeMapKey(entry.getKey());
                CacheKey cacheKey = localCacheView.toCacheKey(keyEncoded);
                cachePut(cacheKey, entry.getKey(), entry.getValue());

                broadcastLocalCacheStore(entry.getValue(), keyEncoded, cacheKey);
            }
            return new CompletableFutureWrapper((Void) null);
        }

        List<Object> params = new ArrayList<Object>(map.size()*3);
        params.add(publishCommand);
        params.add(invalidateEntryOnChange);
        params.add(map.size()*2);
        byte[][] hashes = new byte[map.size()][];
        int i = 0;
        
        for (java.util.Map.Entry<? extends K, ? extends V> t : map.entrySet()) {
            ByteBuf mapKey = encodeMapKey(t.getKey());
            ByteBuf mapValue = encodeMapValue(t.getValue());
            params.add(mapKey);
            params.add(mapValue);
            CacheKey cacheKey = localCacheView.toCacheKey(mapKey);
            hashes[i] = cacheKey.getKeyHash();
            i++;
        }

        ByteBuf msgEncoded = null;
        if (syncStrategy == SyncStrategy.UPDATE) {
            List<LocalCachedMapUpdate.Entry> entries = new ArrayList<LocalCachedMapUpdate.Entry>();
            for (int j = 3; j < params.size(); j += 2) {
                ByteBuf key = (ByteBuf) params.get(j);
                ByteBuf value = (ByteBuf) params.get(j+1);
                entries.add(new LocalCachedMapUpdate.Entry(key, value));
                
            }
            msgEncoded = encode(new LocalCachedMapUpdate(instanceId, entries));
        } else if (syncStrategy == SyncStrategy.INVALIDATE) {
            msgEncoded = encode(new LocalCachedMapInvalidate(instanceId, hashes));
        }
        
        if (invalidateEntryOnChange == 2) {
            long time = System.currentTimeMillis();
            for (byte[] hash : hashes) {
                byte[] entryId = generateLogEntryId(hash);
                params.add(time);
                params.add(entryId);
            }
        }
        
        if (msgEncoded != null) {
            params.add(msgEncoded);
        }

        RFuture<Void> future = commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_VOID,
            "local publishCommand = table.remove(ARGV, 1); " +
                  "for i=3, tonumber(ARGV[2]) + 2, 5000 do "
                    + "redis.call('hmset', KEYS[1], unpack(ARGV, i, math.min(i+4999, tonumber(ARGV[2]) + 2))); "
                + "end; "
                + "if ARGV[1] == '1' then "
                    + "redis.call(publishCommand, KEYS[2], ARGV[#ARGV]); "
                + "end;"
                + "if ARGV[1] == '2' then "
                    + "for i=tonumber(ARGV[2]) + 2 + 1, #ARGV - 1, 5000 do "
                        + "redis.call('zadd', KEYS[3], unpack(ARGV, i, math.min(i+4999, #ARGV - 1))); "
                    + "end; "
                    + "redis.call(publishCommand, KEYS[2], ARGV[#ARGV]); "
                + "end;",
                Arrays.asList(getRawName(), listener.getInvalidationTopicName(), listener.getUpdatesLogName()),
                params.toArray());

        CompletionStage<Void> f = future.thenApply(res -> {
            cacheMap(map);
            return null;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    protected RFuture<V> addAndGetOperationAsync(K key, Number value) {
        ByteBuf keyState = encodeMapKey(key);
        CacheKey cacheKey = localCacheView.toCacheKey(keyState);
        ByteBuf msg = encode(new LocalCachedMapInvalidate(instanceId, cacheKey.getKeyHash()));
        byte[] entryId = generateLogEntryId(cacheKey.getKeyHash());

        RFuture<V> future = commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, new RedisCommand<Object>("EVAL", new NumberConvertor(value.getClass())),
                "local result = redis.call('HINCRBYFLOAT', KEYS[1], ARGV[1], ARGV[2]); "
              + "if ARGV[3] == '1' then "
                   + "redis.call(ARGV[7], KEYS[2], ARGV[4]); "
              + "end;"
              + "if ARGV[3] == '2' then "
                   + "redis.call('zadd', KEYS[3], ARGV[5], ARGV[6]);"
                   + "redis.call(ARGV[7], KEYS[2], ARGV[4]); "
              + "end;"
              + "return result; ",
              Arrays.asList(getRawName(), listener.getInvalidationTopicName(), listener.getUpdatesLogName()),
              keyState, new BigDecimal(value.toString()).toPlainString(), invalidateEntryOnChange, msg, System.currentTimeMillis(), entryId, publishCommand);

        CompletionStage<V> f = future.thenApply(res -> {
            if (res != null) {
                CacheKey cKey = localCacheView.toCacheKey(key);
                cachePut(cKey, key, res);
            }
            return res;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Boolean> fastPutIfAbsentAsync(K key, V value) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            ByteBuf mapKey = encodeMapKey(key);
            CacheKey cacheKey = localCacheView.toCacheKey(mapKey);
            CacheValue prevValue = cachePutIfAbsent(cacheKey, key, value);
            if (prevValue == null) {
                broadcastLocalCacheStore(value, mapKey, cacheKey);
                return new CompletableFutureWrapper<>(true);
            } else {
                mapKey.release();
                return new CompletableFutureWrapper<>(false);
            }
        }

        RFuture<Boolean> future = super.fastPutIfAbsentAsync(key, value);
        CompletionStage<Boolean> f = future.thenApply(res -> {
            if (res) {
                CacheKey cacheKey = localCacheView.toCacheKey(key);
                cachePut(cacheKey, key, value);
            }
            return res;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Boolean> fastPutIfExistsAsync(K key, V value) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            ByteBuf mapKey = encodeMapKey(key);
            CacheKey cacheKey = localCacheView.toCacheKey(mapKey);
            CacheValue prevValue = cachePutIfExists(cacheKey, key, value);
            if (prevValue != null) {
                broadcastLocalCacheStore(value, mapKey, cacheKey);
                return new CompletableFutureWrapper<>(true);
            } else {
                mapKey.release();
                return new CompletableFutureWrapper<>(false);
            }
        }

        RFuture<Boolean> future = super.fastPutIfExistsAsync(key, value);
        CompletionStage<Boolean> f = future.thenApply(res -> {
            if (res) {
                CacheKey cacheKey = localCacheView.toCacheKey(key);
                cachePut(cacheKey, key, value);
            }
            return res;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Collection<V>> readAllValuesAsync() {
        List<V> result = new ArrayList<V>();
        List<Object> mapKeys = new ArrayList<Object>();
        for (CacheValue value : cache.values()) {
            if (value == null) {
                continue;
            }

            mapKeys.add(encodeMapKey(value.getKey(), mapKeys));
            result.add((V) value.getValue());
        }

        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            return new CompletableFutureWrapper<>(result);
        }

        RFuture<Collection<V>> future = commandExecutor.evalReadAsync(getRawName(), codec, ALL_VALUES,
                "local entries = redis.call('hgetall', KEYS[1]); "
              + "local result = {};"
              + "for j, v in ipairs(entries) do "
                  + "if j % 2 ~= 0 then "
                      + "local founded = false;"
                      + "for i = 1, #ARGV, 1 do "
                          + "if ARGV[i] == entries[j] then "
                              + "founded = true;"
                          + "end;"
                      + "end; "
                      + "if founded == false then "
                          + "table.insert(result, entries[j+1]);"
                      + "end;"
                  + "end; "
              + "end; "
              + "return result; ",
              Arrays.asList(getRawName()),
              mapKeys.toArray());

        CompletionStage<Collection<V>> f = future.thenApply(res -> {
            result.addAll(res);
            return result;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Map<K, V>> readAllMapAsync() {
        Map<K, V> result = new HashMap<K, V>();
        List<Object> mapKeys = new ArrayList<Object>();
        for (CacheValue value : cache.values()) {
            if (value == null) {
                continue;
            }
            mapKeys.add(encodeMapKey(value.getKey(), mapKeys));
            result.put((K) value.getKey(), (V) value.getValue());
        }

        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            return new CompletableFutureWrapper<>(result);
        }

        RFuture<Map<K, V>> future = readAll(ALL_MAP, mapKeys, result);

        CompletionStage<Map<K, V>> f = future.thenApply(res -> {
            for (Entry<K, V> entry : res.entrySet()) {
                CacheKey cacheKey = localCacheView.toCacheKey(entry.getKey());
                cachePut(cacheKey, entry.getKey(), entry.getValue());
            }
            result.putAll(res);
            return result;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void preloadCache() {
        for (Entry<K, V> entry : super.entrySet()) {
            CacheKey cacheKey = localCacheView.toCacheKey(entry.getKey());
            cachePut(cacheKey, entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void preloadCache(int count) {
        for (Entry<K, V> entry : super.entrySet(count)) {
            CacheKey cacheKey = localCacheView.toCacheKey(entry.getKey());
            cachePut(cacheKey, entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clearLocalCache() {
        get(clearLocalCacheAsync());
    }
    
    @Override
    public RFuture<Void> clearLocalCacheAsync() {
        return listener.clearLocalCacheAsync();
    }

    @Override
    public RFuture<Set<Entry<K, V>>> readAllEntrySetAsync() {
        Set<Entry<K, V>> result = new HashSet<Entry<K, V>>();
        List<Object> mapKeys = new ArrayList<Object>();
        for (CacheValue value : cache.values()) {
            if (value == null) {
                continue;
            }

            mapKeys.add(encodeMapKey(value.getKey(), mapKeys));
            result.add(new AbstractMap.SimpleEntry<K, V>((K) value.getKey(), (V) value.getValue()));
        }

        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            return new CompletableFutureWrapper<>(result);
        }

        RFuture<Set<Entry<K, V>>> future = readAll(ALL_ENTRIES, mapKeys, result);
        CompletionStage<Set<Entry<K, V>>> f = future.thenApply(res -> {
            for (Entry<K, V> entry : res) {
                CacheKey cacheKey = localCacheView.toCacheKey(entry.getKey());
                cachePut(cacheKey, entry.getKey(), entry.getValue());
            }
            result.addAll(res);
            return result;
        });
        return new CompletableFutureWrapper<>(f);
    }

    private <R> RFuture<R> readAll(RedisCommand<?> evalCommandType, List<Object> mapKeys, R result) {
        return commandExecutor.evalReadAsync(getRawName(), codec, evalCommandType,
                "local entries = redis.call('hgetall', KEYS[1]); "
              + "local result = {};"
              + "for j, v in ipairs(entries) do "
              + "if j % 2 ~= 0 then "
                      + "local founded = false;"
                      + "for i = 1, #ARGV, 1 do "
                          + "if ARGV[i] == entries[j] then "
                              + "founded = true;"
                          + "end;"
                      + "end; "
                      + "if founded == false then "
                          + "table.insert(result, entries[j]);"
                          + "table.insert(result, entries[j+1]);"
                      + "end;"
                  + "end; "
               + "end; "
              + "return result; ",
              Arrays.<Object>asList(getRawName()),
              mapKeys.toArray());
    }

    @Override
    public RFuture<Boolean> fastReplaceAsync(K key, V value) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            ByteBuf mapKey = encodeMapKey(key);
            CacheKey cacheKey = localCacheView.toCacheKey(mapKey);
            CacheValue prevValue = cacheReplace(cacheKey, key, value);
            if (prevValue != null) {
                broadcastLocalCacheStore(value, mapKey, cacheKey);
                return new CompletableFutureWrapper<>(true);
            } else {
                mapKey.release();
                return new CompletableFutureWrapper<>(false);
            }
        }

        RFuture<Boolean> future = super.fastReplaceAsync(key, value);
        CompletionStage<Boolean> f = future.thenApply(res -> {
            if (res) {
                CacheKey cacheKey = localCacheView.toCacheKey(key);
                cachePut(cacheKey, key, value);
            }
            return res;
        });
        return new CompletableFutureWrapper<>(f);
    }
    
    @Override
    protected RFuture<Boolean> fastReplaceOperationAsync(K key, V value) {
        ByteBuf keyState = encodeMapKey(key);
        ByteBuf valueState = encodeMapValue(value);
        CacheKey cacheKey = localCacheView.toCacheKey(keyState);
        byte[] entryId = generateLogEntryId(cacheKey.getKeyHash());
        ByteBuf msg = createSyncMessage(keyState, valueState, cacheKey);
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                    
                    + "if ARGV[3] == '1' then "
                        + "redis.call(ARGV[7], KEYS[2], ARGV[4]); "
                    + "end;"
                    + "if ARGV[3] == '2' then "
                        + "redis.call('zadd', KEYS[3], ARGV[5], ARGV[6]);"
                        + "redis.call(ARGV[7], KEYS[2], ARGV[4]); "
                    + "end;"

                    + "return 1; "
                + "else "
                    + "return 0; "
                + "end",
                Arrays.asList(name, listener.getInvalidationTopicName(), listener.getUpdatesLogName()),
                keyState, valueState, invalidateEntryOnChange, msg, System.currentTimeMillis(), entryId, publishCommand);
    }
    
    @Override
    protected RFuture<V> replaceOperationAsync(K key, V value) {
        ByteBuf keyState = encodeMapKey(key);
        ByteBuf valueState = encodeMapValue(value);
        CacheKey cacheKey = localCacheView.toCacheKey(keyState);
        byte[] entryId = generateLogEntryId(cacheKey.getKeyHash());
        ByteBuf msg = createSyncMessage(keyState, valueState, cacheKey);
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then "
                    + "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                    
                    + "if ARGV[3] == '1' then "
                        + "redis.call(ARGV[7], KEYS[2], ARGV[4]); "
                    + "end;"
                    + "if ARGV[3] == '2' then "
                        + "redis.call('zadd', KEYS[3], ARGV[5], ARGV[6]);"
                        + "redis.call(ARGV[7], KEYS[2], ARGV[4]); "
                    + "end;"

                    + "return v; "
                + "else "
                    + "return nil; "
                + "end",
                Arrays.asList(name, listener.getInvalidationTopicName(), listener.getUpdatesLogName()),
                keyState, valueState, invalidateEntryOnChange, msg, System.currentTimeMillis(), entryId, publishCommand);
    }
    
    @Override
    public RFuture<V> replaceAsync(K key, V value) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            ByteBuf mapKey = encodeMapKey(key);
            CacheKey cacheKey = localCacheView.toCacheKey(mapKey);
            CacheValue prevValue = cacheReplace(cacheKey, key, value);
            if (prevValue != null) {
                broadcastLocalCacheStore(value, mapKey, cacheKey);
                return new CompletableFutureWrapper<>((V) prevValue.getValue());
            } else {
                mapKey.release();
                return new CompletableFutureWrapper((Void) null);
            }
        }

        RFuture<V> future = super.replaceAsync(key, value);
        CompletionStage<V> f = future.thenApply(res -> {
            if (res != null) {
                CacheKey cacheKey = localCacheView.toCacheKey(key);
                cachePut(cacheKey, key, value);
            }
            return res;
        });
        return new CompletableFutureWrapper<>(f);
    }
    
    @Override
    protected RFuture<Boolean> replaceOperationAsync(K key, V oldValue, V newValue) {
        ByteBuf keyState = encodeMapKey(key);
        ByteBuf oldValueState = encodeMapValue(oldValue);
        ByteBuf newValueState = encodeMapValue(newValue);
        CacheKey cacheKey = localCacheView.toCacheKey(keyState);
        byte[] entryId = generateLogEntryId(cacheKey.getKeyHash());
        ByteBuf msg = createSyncMessage(keyState, newValueState, cacheKey);
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[3]); "
                    + "if ARGV[4] == '1' then "
                        + "redis.call(ARGV[8], KEYS[2], ARGV[5]); "
                    + "end;"
                    + "if ARGV[4] == '2' then "
                        + "redis.call('zadd', KEYS[3], ARGV[6], ARGV[7]);"
                        + "redis.call(ARGV[8], KEYS[2], ARGV[5]); "
                    + "end;"
                    + "return 1; "
                + "else "
                    + "return 0; "
                + "end",
                Arrays.asList(name, listener.getInvalidationTopicName(), listener.getUpdatesLogName()),
                keyState, oldValueState, newValueState, invalidateEntryOnChange, msg, System.currentTimeMillis(), entryId, publishCommand);
    }

    @Override
    public RFuture<Boolean> replaceAsync(K key, V oldValue, V newValue) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            ByteBuf mapKey = encodeMapKey(key);
            CacheKey cacheKey = localCacheView.toCacheKey(mapKey);
            if (cacheReplace(cacheKey, key, oldValue, newValue)) {
                broadcastLocalCacheStore(newValue, mapKey, cacheKey);
                return new CompletableFutureWrapper<>(true);
            } else {
                mapKey.release();
                return new CompletableFutureWrapper<>(false);
            }
        }

        RFuture<Boolean> future = super.replaceAsync(key, oldValue, newValue);
        CompletionStage<Boolean> f = future.thenApply(res -> {
            if (res) {
                CacheKey cacheKey = localCacheView.toCacheKey(key);
                cachePut(cacheKey, key, newValue);
            }
            return res;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    protected RFuture<Boolean> removeOperationAsync(Object key, Object value) {
        ByteBuf keyState = encodeMapKey(key);
        ByteBuf valueState = encodeMapValue(value);
        CacheKey cacheKey = localCacheView.toCacheKey(keyState);
        byte[] entryId = generateLogEntryId(cacheKey.getKeyHash());
        ByteBuf msg = encode(new LocalCachedMapInvalidate(instanceId, cacheKey.getKeyHash()));

        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then "
                    + "if ARGV[3] == '1' then "
                        + "redis.call(ARGV[7], KEYS[2], ARGV[4]); "
                    + "end;"
                    + "if ARGV[3] == '2' then "
                        + "redis.call('zadd', KEYS[3], ARGV[5], ARGV[6]);"
                        + "redis.call(ARGV[7], KEYS[2], ARGV[4]); "
                    + "end;"
                    + "return redis.call('hdel', KEYS[1], ARGV[1]) "
                + "else "
                    + "return 0 "
                + "end",
            Arrays.asList(name, listener.getInvalidationTopicName(), listener.getUpdatesLogName()),
            keyState, valueState, invalidateEntryOnChange, msg, System.currentTimeMillis(), entryId, publishCommand);
    }
    
    @Override
    public RFuture<Boolean> removeAsync(Object key, Object value) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            ByteBuf mapKey = encodeMapKey(key);
            CacheKey cacheKey = localCacheView.toCacheKey(mapKey);
            if (cacheRemove(cacheKey, key, value)) {
                broadcastLocalCacheStore((V) value, mapKey, cacheKey);
                return new CompletableFutureWrapper<>(true);
            } else {
                mapKey.release();
                return new CompletableFutureWrapper<>(false);
            }
        }

        RFuture<Boolean> future = super.removeAsync(key, value);
        CompletionStage<Boolean> f = future.thenApply(res -> {
            if (res) {
                CacheKey cacheKey = localCacheView.toCacheKey(key);
                cacheRemove(cacheKey);
            }
            return res;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<V> putIfExistsAsync(K key, V value) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            ByteBuf mapKey = encodeMapKey(key);
            CacheKey cacheKey = localCacheView.toCacheKey(mapKey);
            CacheValue prevValue = cachePutIfExists(cacheKey, key, value);
            if (prevValue != null) {
                broadcastLocalCacheStore((V) value, mapKey, cacheKey);
                return new CompletableFutureWrapper<>((V) prevValue.getValue());
            } else {
                mapKey.release();
                return new CompletableFutureWrapper((Void) null);
            }
        }

        RFuture<V> future = super.putIfExistsAsync(key, value);
        CompletionStage<V> f = future.thenApply(res -> {
            if (res != null) {
                CacheKey cacheKey = localCacheView.toCacheKey(key);
                cachePut(cacheKey, key, value);
            }
            return res;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<V> putIfAbsentAsync(K key, V value) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            ByteBuf mapKey = encodeMapKey(key);
            CacheKey cacheKey = localCacheView.toCacheKey(mapKey);
            CacheValue prevValue = cachePutIfAbsent(cacheKey, key, value);
            if (prevValue == null) {
                broadcastLocalCacheStore((V) value, mapKey, cacheKey);
                return new CompletableFutureWrapper((Void) null);
            } else {
                mapKey.release();
                return new CompletableFutureWrapper<>((V) prevValue.getValue());
            }
        }

        RFuture<V> future = super.putIfAbsentAsync(key, value);
        CompletionStage<V> f = future.thenApply(res -> {
            if (res == null) {
                CacheKey cacheKey = localCacheView.toCacheKey(key);
                cachePut(cacheKey, key, value);
            }
            return res;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public ByteBuf encode(Object value) {
        try {
            return LocalCachedMessageCodec.INSTANCE.getValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Set<K> cachedKeySet() {
        return localCacheView.cachedKeySet();
    }

    @Override
    public Collection<V> cachedValues() {
        return localCacheView.cachedValues();
    }
    
    @Override
    public Set<Entry<K, V>> cachedEntrySet() {
        return localCacheView.cachedEntrySet();
    }
    
    @Override
    public Map<K, V> getCachedMap() {
        return localCacheView.getCachedMap();
    }

    @Override
    public Set<K> keySet(String pattern, int count) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            return cachedKeySet();
        }
        return super.keySet(pattern, count);
    }

    @Override
    public Collection<V> values(String keyPattern, int count) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            return cachedValues();
        }
        return super.values(keyPattern, count);
    }

    @Override
    public Set<Entry<K, V>> entrySet(String keyPattern, int count) {
        if (storeMode == LocalCachedMapOptions.StoreMode.LOCALCACHE) {
            return cachedEntrySet();
        }
        return super.entrySet(keyPattern, count);
    }

    @Override
    public int addListener(ObjectListener listener) {
        if (listener instanceof LocalCacheInvalidateListener) {
            return this.listener.addListener((LocalCacheInvalidateListener) listener);
        }
        if (listener instanceof LocalCacheUpdateListener) {
            return this.listener.addListener((LocalCacheUpdateListener) listener);
        }
        return super.addListener(listener);
    }

    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        if (listener instanceof LocalCacheInvalidateListener) {
            int r = this.listener.addListener((LocalCacheInvalidateListener) listener);
            return new CompletableFutureWrapper<>(r);
        }
        if (listener instanceof LocalCacheUpdateListener) {
            int r = this.listener.addListener((LocalCacheUpdateListener) listener);
            return new CompletableFutureWrapper<>(r);
        }
        return super.addListenerAsync(listener);
    }

    @Override
    public void removeListener(int listenerId) {
        listener.removeListener(listenerId);
        super.removeListener(listenerId);
    }

    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        listener.removeListener(listenerId);
        return super.removeListenerAsync(listenerId);
    }
}
