/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.redisson.RedissonMap;
import org.redisson.RedissonMultiLock;
import org.redisson.RedissonObject;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.convertor.NumberConvertor;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.Hash;
import org.redisson.misc.HashValue;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.transaction.operation.DeleteOperation;
import org.redisson.transaction.operation.TouchOperation;
import org.redisson.transaction.operation.TransactionalOperation;
import org.redisson.transaction.operation.UnlinkOperation;
import org.redisson.transaction.operation.map.MapAddAndGetOperation;
import org.redisson.transaction.operation.map.MapFastPutIfAbsentOperation;
import org.redisson.transaction.operation.map.MapFastPutOperation;
import org.redisson.transaction.operation.map.MapFastRemoveOperation;
import org.redisson.transaction.operation.map.MapOperation;
import org.redisson.transaction.operation.map.MapPutIfAbsentOperation;
import org.redisson.transaction.operation.map.MapPutOperation;
import org.redisson.transaction.operation.map.MapRemoveOperation;
import org.redisson.transaction.operation.map.MapReplaceOperation;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class BaseTransactionalMap<K, V> {

    public static class MapEntry {
        
        public static final MapEntry NULL = new MapEntry(null, null);
        
        private final Object key;
        private final Object value;
        
        public MapEntry(Object key, Object value) {
            super();
            this.key = key;
            this.value = value;
        }
        
        public Object getKey() {
            return key;
        }
        
        public Object getValue() {
            return value;
        }
        
    }
    
    private final long timeout;
    final List<TransactionalOperation> operations;
    final Map<HashValue, MapEntry> state = new HashMap<HashValue, MapEntry>();
    final RMap<K, V> map;
    Boolean deleted;
    
    public BaseTransactionalMap(long timeout, List<TransactionalOperation> operations, RMap<K, V> map) {
        super();
        this.timeout = timeout;
        this.operations = operations;
        this.map = map;
    }

    HashValue toKeyHash(Object key) {
        ByteBuf keyState = ((RedissonObject)map).encodeMapKey(key);
        try {
            return new HashValue(Hash.hash128(keyState));
        } finally {
            keyState.release();
        }
    }
    
    private HashValue toValueHash(Object value) {
        ByteBuf keyState = ((RedissonObject)map).encodeMapValue(value);
        try {
            return new HashValue(Hash.hash128(keyState));
        } finally {
            keyState.release();
        }
    }
    
    public RFuture<Boolean> isExistsAsync() {
        if (deleted != null) {
            return RedissonPromise.newSucceededFuture(!deleted);
        }
        
        return map.isExistsAsync();
    }
    
    public RFuture<Boolean> unlinkAsync(CommandAsyncExecutor commandExecutor) {
        return deleteAsync(commandExecutor, new UnlinkOperation(map.getName(), null));
    }
    
    public RFuture<Boolean> touchAsync(CommandAsyncExecutor commandExecutor) {
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        if (deleted != null && deleted) {
            operations.add(new TouchOperation(map.getName()));
            result.trySuccess(false);
            return result;
        }
        
        map.isExistsAsync().addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                operations.add(new TouchOperation(map.getName()));
                boolean exists = future.getNow();
                if (!exists) {
                    for (MapEntry entry : state.values()) {
                        if (entry != MapEntry.NULL) {
                            exists = true;
                            break;
                        }
                    }
                }
                result.trySuccess(exists);
            }
        });
        return result;
    }

    public RFuture<Boolean> deleteAsync(CommandAsyncExecutor commandExecutor) {
        return deleteAsync(commandExecutor, new DeleteOperation(map.getName()));
    }

    protected RFuture<Boolean> deleteAsync(CommandAsyncExecutor commandExecutor, final TransactionalOperation operation) {
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        if (deleted != null) {
            operations.add(operation);
            result.trySuccess(!deleted);
            deleted = true;
            return result;
        }
        
        map.isExistsAsync().addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                operations.add(operation);
                for (HashValue key : state.keySet()) {
                    state.put(key, MapEntry.NULL);
                }
                deleted = true;
                result.trySuccess(future.getNow());
            }
        });
        
        return result;
    }
    
    protected MapScanResult<ScanObjectEntry, ScanObjectEntry> scanIterator(String name, RedisClient client,
            long startPos, String pattern) {
        MapScanResult<ScanObjectEntry, ScanObjectEntry> res = ((RedissonMap<?, ?>)map).scanIterator(name, client, startPos, pattern);
        Map<HashValue, MapEntry> newstate = new HashMap<HashValue, MapEntry>(state);
        for (Iterator<ScanObjectEntry> iterator = res.getMap().keySet().iterator(); iterator.hasNext();) {
            ScanObjectEntry entry = iterator.next();
            MapEntry mapEntry = newstate.remove(entry.getHash());
            if (mapEntry != null) {
                if (mapEntry == MapEntry.NULL) {
                    iterator.remove();
                    continue;
                }
                
                HashValue valueHash = toValueHash(mapEntry.getValue());
                res.getMap().put(entry, new ScanObjectEntry(valueHash, mapEntry.getValue()));
            }
        }
        
        if (startPos == 0) {
            for (Entry<HashValue, MapEntry> entry : newstate.entrySet()) {
                if (entry.getValue() == MapEntry.NULL) {
                    continue;
                }
                
                ScanObjectEntry key = new ScanObjectEntry(entry.getKey(), entry.getValue().getKey());
                ScanObjectEntry value = new ScanObjectEntry(toValueHash(entry.getValue().getValue()), entry.getValue().getValue());
                res.getMap().put(key, value);
            }
        }

        return res;
    }
    
    public RFuture<Boolean> containsKeyAsync(Object key) {
        HashValue keyHash = toKeyHash(key);
        MapEntry currentValue = state.get(keyHash);
        if (currentValue != null) {
            if (currentValue == MapEntry.NULL) {
                return RedissonPromise.newSucceededFuture(false);
            } else {
                return RedissonPromise.newSucceededFuture(true);
            }
        }
        
        return map.containsKeyAsync(key);
    }
    
    public RFuture<Boolean> containsValueAsync(Object value) {
        for (MapEntry entry : state.values()) {
            if (entry != MapEntry.NULL && isEqual(entry.getValue(), value)) {
                return RedissonPromise.newSucceededFuture(true);
            }
        }

        return map.containsValueAsync(value);
    }
    
    protected RFuture<V> addAndGetOperationAsync(final K key, final Number value) {
        final RPromise<V> result = new RedissonPromise<V>();
        executeLocked(result, key, new Runnable() {
            @Override
            public void run() {
                final HashValue keyHash = toKeyHash(key);
                MapEntry entry = state.get(keyHash);
                if (entry != null) {
                    BigDecimal currentValue = BigDecimal.ZERO;
                    if (entry != MapEntry.NULL) {
                        currentValue = (BigDecimal) entry.getValue();
                    }
                    BigDecimal res = currentValue.add(new BigDecimal(value.toString()));

                    operations.add(new MapAddAndGetOperation(map, key, value));
                    state.put(keyHash, new MapEntry(key, res));
                    if (deleted != null) {
                        deleted = false;
                    }

                    NumberConvertor convertor = new NumberConvertor(value.getClass());
                    result.trySuccess((V) convertor.convert(res.toPlainString()));
                    return;
                }
                
                map.getAsync(key).addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        BigDecimal currentValue = new BigDecimal(future.getNow().toString());
                        BigDecimal res = currentValue.add(new BigDecimal(value.toString()));
                        
                        operations.add(new MapAddAndGetOperation(map, key, value));
                        state.put(keyHash, new MapEntry(key, res));
                        if (deleted != null) {
                            deleted = false;
                        }
                        
                        NumberConvertor convertor = new NumberConvertor(value.getClass());
                        result.trySuccess((V) convertor.convert(res.toPlainString()));
                    }

                });
            }
        });
        return result;
    }
    
    protected RFuture<V> putIfAbsentOperationAsync(K key, V value) {
        return putIfAbsentOperationAsync(key, value, new MapPutIfAbsentOperation(map, key, value));
    }

    protected RFuture<V> putIfAbsentOperationAsync(final K key, final V value, final MapOperation mapOperation) {
        final RPromise<V> result = new RedissonPromise<V>();
        executeLocked(result, key, new Runnable() {
            @Override
            public void run() {
                final HashValue keyHash = toKeyHash(key);
                MapEntry entry = state.get(keyHash);
                if (entry != null) {
                    operations.add(mapOperation);
                    if (entry == MapEntry.NULL) {
                        state.put(keyHash, new MapEntry(key, value));
                        if (deleted != null) {
                            deleted = false;
                        }

                        result.trySuccess(null);
                    } else {
                        result.trySuccess((V) entry.getValue());
                    }
                    return;
                }

                map.getAsync(key).addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(mapOperation);
                        if (future.getNow() == null) {
                            state.put(keyHash, new MapEntry(key, value));
                            if (deleted != null) {
                                deleted = false;
                            }
                        }
                        result.trySuccess(future.getNow());
                    }
                });
            }
        });
        return result;
    }
    
    protected final RFuture<V> putOperationAsync(K key, V value) {
        return putOperationAsync(key, value, new MapPutOperation(map, key, value));
    }

    protected RFuture<V> putOperationAsync(final K key, final V value, final MapOperation operation) {
        final RPromise<V> result = new RedissonPromise<V>();
        executeLocked(result, key, new Runnable() {
            @Override
            public void run() {
                final HashValue keyHash = toKeyHash(key);
                MapEntry entry = state.get(keyHash);
                if (entry != null) {
                    operations.add(operation);
                    state.put(keyHash, new MapEntry(key, value));
                    if (deleted != null) {
                        deleted = false;
                    }
                    
                    if (entry == MapEntry.NULL) {
                        result.trySuccess(null);
                    } else {
                        result.trySuccess((V) entry.getValue());
                    }
                    return;
                }
                
                map.getAsync(key).addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(operation);
                        state.put(keyHash, new MapEntry(key, value));
                        if (deleted != null) {
                            deleted = false;
                        }
                        result.trySuccess(future.getNow());
                    }
                });
            }
        });
        return result;
    }
    
    protected RFuture<Boolean> fastPutIfAbsentOperationAsync(K key, V value) {
        return fastPutIfAbsentOperationAsync(key, value, new MapFastPutIfAbsentOperation(map, key, value));
    }

    protected RFuture<Boolean> fastPutIfAbsentOperationAsync(final K key, final V value, final MapOperation mapOperation) {
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, key, new Runnable() {
            @Override
            public void run() {
                final HashValue keyHash = toKeyHash(key);
                MapEntry entry = state.get(keyHash);
                if (entry != null) {
                    operations.add(mapOperation);
                    if (entry == MapEntry.NULL) {
                        state.put(keyHash, new MapEntry(key, value));
                        if (deleted != null) {
                            deleted = false;
                        }
                        result.trySuccess(true);
                    } else {
                        result.trySuccess(false);
                    }
                    return;
                }

                map.getAsync(key).addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(mapOperation);
                        boolean isUpdated = future.getNow() == null;
                        if (isUpdated) {
                            state.put(keyHash, new MapEntry(key, value));
                            if (deleted != null) {
                                deleted = false;
                            }
                        }
                        result.trySuccess(isUpdated);
                    }
                });
            }
        });
        return result;
    }
    
    protected RFuture<Boolean> fastPutOperationAsync(K key, V value) {
        return fastPutOperationAsync(key, value, new MapFastPutOperation(map, key, value));
    }

    protected RFuture<Boolean> fastPutOperationAsync(final K key, final V value, final MapOperation operation) {
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, key, new Runnable() {
            @Override
            public void run() {
                final HashValue keyHash = toKeyHash(key);
                MapEntry entry = state.get(keyHash);
                if (entry != null) {
                    operations.add(operation);
                    state.put(keyHash, new MapEntry(key, value));
                    if (deleted != null) {
                        deleted = false;
                    }
                    
                    if (entry == MapEntry.NULL) {
                        result.trySuccess(true);
                    } else {
                        result.trySuccess(false);
                    }
                    return;
                }
                
                map.getAsync(key).addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(operation);
                        state.put(keyHash, new MapEntry(key, value));
                        if (deleted != null) {
                            deleted = false;
                        }

                        boolean isNew = future.getNow() == null;
                        result.trySuccess(isNew);
                    }
                });
            }
        });
        return result;
    }
    
    @SuppressWarnings("unchecked")
    protected RFuture<Long> fastRemoveOperationAsync(final K... keys) {
        final RPromise<Long> result = new RedissonPromise<Long>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                final AtomicLong counter = new AtomicLong();
                List<K> keyList = Arrays.asList(keys);
                for (Iterator<K> iterator = keyList.iterator(); iterator.hasNext();) {
                    K key = iterator.next();
                    HashValue keyHash = toKeyHash(key);
                    MapEntry currentValue = state.get(keyHash);
                    if (currentValue != null && currentValue != MapEntry.NULL) {
                        operations.add(new MapFastRemoveOperation(map, key));
                        state.put(keyHash, MapEntry.NULL);

                        counter.incrementAndGet();
                        iterator.remove();
                    }
                }
                
                // TODO optimize
                map.getAllAsync(new HashSet<K>(keyList)).addListener(new FutureListener<Map<K, V>>() {
                    @Override
                    public void operationComplete(Future<Map<K, V>> future) throws Exception {
                        if (future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        for (K key : future.getNow().keySet()) {
                            HashValue keyHash = toKeyHash(key);
                            operations.add(new MapFastRemoveOperation(map, key));
                            counter.incrementAndGet();
                            state.put(keyHash, MapEntry.NULL);
                        }

                        result.trySuccess(counter.get());
                    }
                });
            }
        }, Arrays.asList(keys));
        return result;
    }
    
    public RFuture<Integer> valueSizeAsync(K key) {
        HashValue keyHash = toKeyHash(key);
        MapEntry entry = state.get(keyHash);
        if (entry != null) {
            if (entry == MapEntry.NULL) {
                return RedissonPromise.newSucceededFuture(null);
            } else {
                ByteBuf valueState = ((RedissonObject)map).encodeMapValue(entry.getValue());
                try {
                    return RedissonPromise.newSucceededFuture(valueState.readableBytes());
                } finally {
                    valueState.release();
                }
            }
        }
        
        return map.valueSizeAsync(key);
    }
    
    protected RFuture<V> getOperationAsync(K key) {
        HashValue keyHash = toKeyHash(key);
        MapEntry entry = state.get(keyHash);
        if (entry != null) {
            if (entry == MapEntry.NULL) {
                return RedissonPromise.newSucceededFuture(null);
            } else {
                return RedissonPromise.newSucceededFuture((V)entry.getValue());
            }
        }
        return ((RedissonMap<K, V>)map).getOperationAsync(key);
    }

    public RFuture<Set<K>> readAllKeySetAsync() {
        final RPromise<Set<K>> result = new RedissonPromise<Set<K>>();
        RFuture<Set<K>> future = map.readAllKeySetAsync();
        future.addListener(new FutureListener<Set<K>>() {

            @Override
            public void operationComplete(Future<Set<K>> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                Set<K> set = future.getNow();
                Map<HashValue, MapEntry> newstate = new HashMap<HashValue, MapEntry>(state);
                for (Iterator<K> iterator = set.iterator(); iterator.hasNext();) {
                    K key = iterator.next();
                    MapEntry value = newstate.remove(toKeyHash(key));
                    if (value == MapEntry.NULL) {
                        iterator.remove();
                    }
                }
                
                for (MapEntry entry : newstate.values()) {
                    if (entry == MapEntry.NULL) {
                        continue;
                    }
                    set.add((K) entry.getKey());
                }
                
                result.trySuccess(set);
            }
        });
        
        return result;
    }
    
    public RFuture<Set<Entry<K, V>>> readAllEntrySetAsync() {
        final RPromise<Set<Entry<K, V>>> result = new RedissonPromise<Set<Entry<K, V>>>();
        RFuture<Map<K, V>> future = readAllMapAsync();
        future.addListener(new FutureListener<Map<K, V>>() {
            @Override
            public void operationComplete(Future<Map<K, V>> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                result.trySuccess(future.getNow().entrySet());
            }
        });
        
        return result;
    }
    
    public RFuture<Collection<V>> readAllValuesAsync() {
        final RPromise<Collection<V>> result = new RedissonPromise<Collection<V>>();
        RFuture<Map<K, V>> future = readAllMapAsync();
        future.addListener(new FutureListener<Map<K, V>>() {
            @Override
            public void operationComplete(Future<Map<K, V>> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                result.trySuccess(future.getNow().values());
            }
        });
        
        return result;
    }
    
    public RFuture<Map<K, V>> readAllMapAsync() {
        final RPromise<Map<K, V>> result = new RedissonPromise<Map<K, V>>();
        RFuture<Map<K, V>> future = map.readAllMapAsync();
        future.addListener(new FutureListener<Map<K, V>>() {

            @Override
            public void operationComplete(Future<Map<K, V>> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                Map<HashValue, MapEntry> newstate = new HashMap<HashValue, MapEntry>(state);
                Map<K, V> map = future.getNow();
                for (Iterator<K> iterator = map.keySet().iterator(); iterator.hasNext();) {
                    K key = iterator.next();
                    MapEntry entry = newstate.remove(toKeyHash(key));
                    if (entry == MapEntry.NULL) {
                        iterator.remove();
                    } else if (entry != null) {
                        map.put(key, (V) entry.getValue());
                    }
                }
                
                for (MapEntry entry : newstate.values()) {
                    if (entry == MapEntry.NULL) {
                        continue;
                    }
                    map.put((K)entry.getKey(), (V)entry.getValue());
                }
                
                result.trySuccess(map);
            }
        });

        return result;
    }
    
    protected RFuture<Map<K, V>> getAllOperationAsync(Set<K> keys) {
        final RPromise<Map<K, V>> result = new RedissonPromise<Map<K, V>>();
        Set<K> keysToLoad = new HashSet<K>(keys);
        final Map<K, V> map = new HashMap<K, V>();
        for (K key : keys) {
            HashValue keyHash = toKeyHash(key);
            
            MapEntry entry = state.get(keyHash);
            if (entry != null) {
                if (entry != MapEntry.NULL) {
                    map.put(key, (V)entry.getValue());
                }
            } else {
                keysToLoad.add(key);
            }
        }

        RFuture<Map<K, V>> future = ((RedissonMap<K, V>)map).getAllOperationAsync(keysToLoad);
        future.addListener(new FutureListener<Map<K, V>>() {
            @Override
            public void operationComplete(Future<Map<K, V>> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                map.putAll(future.getNow());
                result.trySuccess(map);
            }
        });
        
        return result;
    }
    
    protected RFuture<V> removeOperationAsync(final K key) {
        final RPromise<V> result = new RedissonPromise<V>();
        executeLocked(result, key, new Runnable() {
            @Override
            public void run() {
                final HashValue keyHash = toKeyHash(key);
                MapEntry entry = state.get(keyHash);
                if (entry != null) {
                    operations.add(new MapRemoveOperation(map, key));
                    if (entry == MapEntry.NULL) {
                        result.trySuccess(null);
                    } else {
                        state.put(keyHash, MapEntry.NULL);
                        result.trySuccess((V) entry.getValue());
                    }
                    return;
                }

                map.getAsync(key).addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        operations.add(new MapRemoveOperation(map, key));
                        if (future.getNow() != null) {
                            state.put(keyHash, MapEntry.NULL);
                        }

                        result.trySuccess(future.getNow());
                    }
                });
            }
        });
        return result;
    }
    
    protected RFuture<Boolean> removeOperationAsync(final Object key, final Object value) {
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, (K)key, new Runnable() {
            @Override
            public void run() {
                final HashValue keyHash = toKeyHash(key);
                MapEntry entry = state.get(keyHash);
                if (entry != null) {
                    if (entry == MapEntry.NULL) {
                        result.trySuccess(false);
                        return;
                    }
                    
                    operations.add(new MapRemoveOperation(map, key, value));
                    if (isEqual(entry.getValue(), value)) {
                        state.put(keyHash, MapEntry.NULL);
                        result.trySuccess(true);
                        return;
                    }
                    
                    result.trySuccess(false);
                    return;
                }
                
                map.getAsync((K)key).addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        operations.add(new MapRemoveOperation(map, key, value));
                        boolean res = isEqual(future.getNow(), value);
                        if (res) {
                            state.put(keyHash, MapEntry.NULL);
                        }
                        result.trySuccess(res);
                    }
                });
            }
        });
        return result;
    }
    
    private boolean isEqual(Object value, Object oldValue) {
        ByteBuf valueBuf = ((RedissonObject)map).encodeMapValue(value);
        ByteBuf oldValueBuf = ((RedissonObject)map).encodeMapValue(oldValue);
        
        try {
            return valueBuf.equals(oldValueBuf);
        } finally {
            valueBuf.readableBytes();
            oldValueBuf.readableBytes();
        }
    }

    protected RFuture<Void> putAllOperationAsync(final Map<? extends K, ? extends V> entries) {
        final RPromise<Void> result = new RedissonPromise<Void>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                for (Entry<? extends K, ? extends V> entry : entries.entrySet()) {
                    operations.add(new MapPutOperation(map, entry.getKey(), entry.getValue()));
                    HashValue keyHash = toKeyHash(entry.getKey());
                    state.put(keyHash, new MapEntry(entry.getKey(), entry.getValue()));
                }
                
                if (deleted != null) {
                    deleted = false;
                }

                result.trySuccess(null);
            }
        }, (Collection<K>)entries.keySet());
        return result;
    }
    
    protected RFuture<Boolean> replaceOperationAsync(final K key, final V oldValue, final V newValue) {
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, key, new Runnable() {
            @Override
            public void run() {
                final HashValue keyHash = toKeyHash(key);
                MapEntry entry = state.get(keyHash);
                if (entry != null) {
                    if (entry == MapEntry.NULL) {
                        result.trySuccess(false);
                        return;
                    }
                    
                    operations.add(new MapReplaceOperation(map, key, newValue, oldValue));
                    if (isEqual(entry.getValue(), oldValue)) {
                        state.put(keyHash, new MapEntry(key, newValue));
                        result.trySuccess(true);
                        return;
                    }
                    
                    result.trySuccess(false);
                    return;
                }

                map.getAsync(key).addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(new MapReplaceOperation(map, key, newValue, oldValue));
                        boolean res = isEqual(future.getNow(), oldValue);
                        if (res) {
                            state.put(keyHash, new MapEntry(key, newValue));
                        }
                        result.trySuccess(res);
                    }
                });
            }
        });
        return result;
    }

    protected RFuture<V> replaceOperationAsync(final K key, final V value) {
        final RPromise<V> result = new RedissonPromise<V>();
        executeLocked(result, key, new Runnable() {
            @Override
            public void run() {
                final HashValue keyHash = toKeyHash(key);
                MapEntry entry = state.get(keyHash);
                operations.add(new MapReplaceOperation(map, key, value));
                if (entry != null) {
                    if (entry == MapEntry.NULL) {
                        result.trySuccess(null);
                        return;
                    }
                    
                    state.put(keyHash, new MapEntry(key, value));
                    result.trySuccess((V) entry.getValue());
                    return;
                }

                map.getAsync(key).addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(new MapReplaceOperation(map, key, value));
                        if (future.getNow() != null) {
                            state.put(keyHash, new MapEntry(key, value));
                        }
                        result.trySuccess(future.getNow());
                    }
                });
            }
        });
        return result;
    }
    
    protected <R> void executeLocked(final RPromise<R> promise, K key, final Runnable runnable) {
        RLock lock = map.getLock(key);
        executeLocked(promise, runnable, lock);
    }

    protected <R> void executeLocked(final RPromise<R> promise, final Runnable runnable, RLock lock) {
        lock.lockAsync(timeout, TimeUnit.MILLISECONDS).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (future.isSuccess()) {
                    runnable.run();
                } else {
                    promise.tryFailure(future.cause());
                }
            }
        });
    }
    
    protected <R> void executeLocked(final RPromise<R> promise, final Runnable runnable, Collection<K> keys) {
        List<RLock> locks = new ArrayList<RLock>(keys.size());
        for (K key : keys) {
            RLock lock = map.getLock(key);
            locks.add(lock);
        }
        final RedissonMultiLock multiLock = new RedissonMultiLock(locks.toArray(new RLock[locks.size()]));
        final long threadId = Thread.currentThread().getId();
        multiLock.lockAsync(timeout, TimeUnit.MILLISECONDS).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (future.isSuccess()) {
                    runnable.run();
                } else {
                    multiLock.unlockAsync(threadId);
                    promise.tryFailure(future.cause());
                }
            }
        });
    }

}
