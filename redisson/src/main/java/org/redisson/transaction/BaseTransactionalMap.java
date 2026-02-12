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
package org.redisson.transaction;

import io.netty.buffer.ByteBuf;
import org.redisson.RedissonMap;
import org.redisson.RedissonObject;
import org.redisson.ScanResult;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.convertor.NumberConvertor;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.Hash;
import org.redisson.misc.HashValue;
import org.redisson.transaction.operation.*;
import org.redisson.transaction.operation.map.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class BaseTransactionalMap<K, V> extends BaseTransactionalObject {

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
    final Map<HashValue, MapEntry> state = new HashMap<>();
    final RMap<K, V> map;
    Boolean deleted;
    boolean hasExpiration;

    public BaseTransactionalMap(CommandAsyncExecutor commandExecutor, long timeout, List<TransactionalOperation> operations, RMap<K, V> map, String transactionId) {
        super(transactionId, getLockName(map.getName()), commandExecutor);
        this.timeout = timeout;
        this.operations = operations;
        this.map = map;
    }

    HashValue toKeyHash(Object key) {
        ByteBuf keyState = ((RedissonObject) map).encodeMapKey(key);
        try {
            return new HashValue(Hash.hash128(keyState));
        } finally {
            keyState.release();
        }
    }
    
    public RFuture<Boolean> isExistsAsync() {
        if (deleted != null) {
            return new CompletableFutureWrapper<>(!deleted);
        }
        
        return map.isExistsAsync();
    }
    
    public RFuture<Boolean> unlinkAsync(CommandAsyncExecutor commandExecutor) {
        return deleteAsync(commandExecutor, new UnlinkOperation(map.getName()));
    }
    
    public RFuture<Boolean> touchAsync(CommandAsyncExecutor commandExecutor) {
        if (deleted != null && deleted) {
            operations.add(new TouchOperation(map.getName()));
            return new CompletableFutureWrapper<>(false);
        }

        CompletionStage<Boolean> f = map.isExistsAsync().thenApply(exists -> {
            operations.add(new TouchOperation(map.getName()));
            if (!exists) {
                return isExists();
            }
            return true;
        });
        return new CompletableFutureWrapper<>(f);
    }

    public RFuture<Boolean> deleteAsync(CommandAsyncExecutor commandExecutor) {
        return deleteAsync(commandExecutor, new DeleteOperation(map.getName()));
    }

    protected RFuture<Boolean> deleteAsync(CommandAsyncExecutor commandExecutor, TransactionalOperation operation) {
        if (deleted != null) {
            operations.add(operation);
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            result.complete(!deleted);
            deleted = true;
            return new CompletableFutureWrapper<>(result);
        }

        CompletionStage<Boolean> f = map.isExistsAsync().thenApply(res -> {
            operations.add(operation);
            state.replaceAll((k, v) -> MapEntry.NULL);
            deleted = true;
            return res;
        });
        return new CompletableFutureWrapper<>(f);
    }
    
    protected ScanResult<Map.Entry<Object, Object>> scanIterator(String name, RedisClient client,
                                                                 String startPos, String pattern, int count) {
        ScanResult<Map.Entry<Object, Object>> res = ((RedissonMap<?, ?>) map).scanIterator(name, client, startPos, pattern, count);
        Map<HashValue, MapEntry> newstate = new HashMap<>(state);
        Map<Object, Object> newres = null;
        for (Iterator<Map.Entry<Object, Object>> iterator = res.getValues().iterator(); iterator.hasNext();) {
            Map.Entry<Object, Object> entry = iterator.next();
            MapEntry mapEntry = newstate.remove(toKeyHash(entry.getKey()));
            if (mapEntry != null) {
                if (mapEntry == MapEntry.NULL) {
                    iterator.remove();
                    continue;
                }

                if (newres == null) {
                    newres = new HashMap<>();
                    for (Entry<Object, Object> e : res.getValues()) {
                        newres.put(e.getKey(), e.getValue());
                    }
                }

                newres.put(entry.getKey(), mapEntry.getValue());
            }
        }
        
        if ("0".equals(startPos)) {
            for (Entry<HashValue, MapEntry> entry : newstate.entrySet()) {
                if (entry.getValue() == MapEntry.NULL) {
                    continue;
                }

                if (newres == null) {
                    newres = new HashMap<>();
                    for (Entry<Object, Object> e : res.getValues()) {
                        newres.put(e.getKey(), e.getValue());
                    }
                }

                newres.put(entry.getValue().getKey(), entry.getValue().getValue());
            }
        }

        if (newres != null) {
            return new MapScanResult<>(res.getPos(), newres);
        }

        return res;
    }
    
    public RFuture<Boolean> containsKeyAsync(Object key) {
        HashValue keyHash = toKeyHash(key);
        MapEntry currentValue = state.get(keyHash);
        if (currentValue != null) {
            if (currentValue == MapEntry.NULL) {
                return new CompletableFutureWrapper<>(false);
            } else {
                return new CompletableFutureWrapper<>(true);
            }
        }
        
        return map.containsKeyAsync(key);
    }
    
    public RFuture<Boolean> containsValueAsync(Object value) {
        for (MapEntry entry : state.values()) {
            if (entry != MapEntry.NULL && isEqual(entry.getValue(), value)) {
                return new CompletableFutureWrapper<>(true);
            }
        }

        return map.containsValueAsync(value);
    }
    
    protected RFuture<V> addAndGetOperationAsync(K key, Number value) {
        long threadId = Thread.currentThread().getId();
        return executeLocked(key, () -> {
            HashValue keyHash = toKeyHash(key);
            MapEntry entry = state.get(keyHash);
            if (entry != null) {
                BigDecimal currentValue = BigDecimal.ZERO;
                if (entry != MapEntry.NULL) {
                    currentValue = (BigDecimal) entry.getValue();
                }
                BigDecimal res = currentValue.add(new BigDecimal(value.toString()));

                operations.add(new MapAddAndGetOperation(map, key, value, transactionId, threadId));
                state.put(keyHash, new MapEntry(key, res));
                if (deleted != null) {
                    deleted = false;
                }

                NumberConvertor convertor = new NumberConvertor(value.getClass());
                return CompletableFuture.completedFuture((V) convertor.convert(res.toPlainString()));
            }

            return map.getAsync(key).thenApply(r -> {
                BigDecimal currentValue = new BigDecimal(r.toString());
                BigDecimal res = currentValue.add(new BigDecimal(value.toString()));

                operations.add(new MapAddAndGetOperation(map, key, value, transactionId, threadId));
                state.put(keyHash, new MapEntry(key, res));
                if (deleted != null) {
                    deleted = false;
                }

                NumberConvertor convertor = new NumberConvertor(value.getClass());
                return (V) convertor.convert(res.toPlainString());
            });
        });
    }

    protected RFuture<V> putIfExistsOperationAsync(K key, V value) {
        long threadId = Thread.currentThread().getId();
        return putIfExistsOperationAsync(key, value, new MapPutIfExistsOperation(map, key, value, transactionId, threadId));
    }

    protected RFuture<V> putIfExistsOperationAsync(K key, V value, MapOperation mapOperation) {
        return executeLocked(key, () -> {
            HashValue keyHash = toKeyHash(key);
            MapEntry entry = state.get(keyHash);
            if (entry != null) {
                operations.add(mapOperation);
                if (entry != MapEntry.NULL) {
                    state.put(keyHash, new MapEntry(key, value));
                    if (deleted != null) {
                        deleted = false;
                    }

                    return CompletableFuture.completedFuture((V) entry.getValue());
                }
                return CompletableFuture.completedFuture(null);
            }

            return map.getAsync(key).thenApply(res -> {
                operations.add(mapOperation);
                if (res != null) {
                    state.put(keyHash, new MapEntry(key, value));
                    if (deleted != null) {
                        deleted = false;
                    }
                }
                return res;
            });
        });
    }

    protected RFuture<V> putIfAbsentOperationAsync(K key, V value) {
        long threadId = Thread.currentThread().getId();
        return putIfAbsentOperationAsync(key, value, new MapPutIfAbsentOperation(map, key, value, transactionId, threadId));
    }

    protected RFuture<V> putIfAbsentOperationAsync(K key, V value, MapOperation mapOperation) {
        return executeLocked(key, () -> {
            HashValue keyHash = toKeyHash(key);
            MapEntry entry = state.get(keyHash);
            if (entry != null) {
                operations.add(mapOperation);
                if (entry == MapEntry.NULL) {
                    state.put(keyHash, new MapEntry(key, value));
                    if (deleted != null) {
                        deleted = false;
                    }

                    return CompletableFuture.completedFuture(null);
                }
                return CompletableFuture.completedFuture((V) entry.getValue());
            }

            return map.getAsync(key).thenApply(res -> {
                operations.add(mapOperation);
                if (res == null) {
                    state.put(keyHash, new MapEntry(key, value));
                    if (deleted != null) {
                        deleted = false;
                    }
                }
                return res;
            });
        });
    }
    
    protected final RFuture<V> putOperationAsync(K key, V value) {
        long threadId = Thread.currentThread().getId();
        return putOperationAsync(key, value, new MapPutOperation(map, key, value, transactionId, threadId));
    }

    protected RFuture<V> putOperationAsync(K key, V value, MapOperation operation) {
        return executeLocked(key, () -> {
            HashValue keyHash = toKeyHash(key);
            MapEntry entry = state.get(keyHash);
            if (entry != null) {
                operations.add(operation);
                state.put(keyHash, new MapEntry(key, value));
                if (deleted != null) {
                    deleted = false;
                }

                if (entry == MapEntry.NULL) {
                    return CompletableFuture.completedFuture(null);
                }
                return CompletableFuture.completedFuture((V) entry.getValue());
            }

            return map.getAsync(key).thenApply(res -> {
                operations.add(operation);
                state.put(keyHash, new MapEntry(key, value));
                if (deleted != null) {
                    deleted = false;
                }
                return res;
            });
        });
    }

    protected RFuture<Boolean> fastPutIfExistsOperationAsync(K key, V value) {
        long threadId = Thread.currentThread().getId();
        return fastPutIfExistsOperationAsync(key, value, new MapFastPutIfExistsOperation(map, key, value, transactionId, threadId));
    }

    protected RFuture<Boolean> fastPutIfExistsOperationAsync(K key, V value, MapOperation mapOperation) {
        return executeLocked(key, () -> {
            HashValue keyHash = toKeyHash(key);
            MapEntry entry = state.get(keyHash);
            if (entry != null) {
                operations.add(mapOperation);
                if (entry != MapEntry.NULL) {
                    state.put(keyHash, new MapEntry(key, value));
                    if (deleted != null) {
                        deleted = false;
                    }

                    return CompletableFuture.completedFuture(true);
                }
                return CompletableFuture.completedFuture(false);
            }

            return map.getAsync(key).thenApply(res -> {
                operations.add(mapOperation);
                if (res != null) {
                    state.put(keyHash, new MapEntry(key, value));
                    if (deleted != null) {
                        deleted = false;
                    }
                }
                return true;
            });
        });
    }

    protected RFuture<Boolean> fastPutIfAbsentOperationAsync(K key, V value) {
        long threadId = Thread.currentThread().getId();
        return fastPutIfAbsentOperationAsync(key, value, new MapFastPutIfAbsentOperation(map, key, value, transactionId, threadId));
    }

    protected RFuture<Boolean> fastPutIfAbsentOperationAsync(K key, V value, MapOperation mapOperation) {
        return executeLocked(key, () -> {
            HashValue keyHash = toKeyHash(key);
            MapEntry entry = state.get(keyHash);
            if (entry != null) {
                operations.add(mapOperation);
                if (entry == MapEntry.NULL) {
                    state.put(keyHash, new MapEntry(key, value));
                    if (deleted != null) {
                        deleted = false;
                    }
                    return CompletableFuture.completedFuture(true);
                }
                return CompletableFuture.completedFuture(false);
            }

            return map.getAsync(key).thenApply(res -> {
                operations.add(mapOperation);
                boolean isUpdated = res == null;
                if (isUpdated) {
                    state.put(keyHash, new MapEntry(key, value));
                    if (deleted != null) {
                        deleted = false;
                    }
                }
                return isUpdated;
            });
        });
    }
    
    protected RFuture<Boolean> fastPutOperationAsync(K key, V value) {
        long threadId = Thread.currentThread().getId();
        return fastPutOperationAsync(key, value, new MapFastPutOperation(map, key, value, transactionId, threadId));
    }

    protected RFuture<Boolean> fastPutOperationAsync(K key, V value, MapOperation operation) {
        return executeLocked(key, () -> {
            HashValue keyHash = toKeyHash(key);
            MapEntry entry = state.get(keyHash);
            if (entry != null) {
                operations.add(operation);
                state.put(keyHash, new MapEntry(key, value));
                if (deleted != null) {
                    deleted = false;
                }

                return CompletableFuture.completedFuture(entry == MapEntry.NULL);
            }

            return map.getAsync(key).thenApply(res -> {
                operations.add(operation);
                state.put(keyHash, new MapEntry(key, value));
                if (deleted != null) {
                    deleted = false;
                }

                boolean isNew = res == null;
                return isNew;
            });
        });
    }
    
    @SuppressWarnings("unchecked")
    protected RFuture<Long> fastRemoveOperationAsync(K... keys) {
        long threadId = Thread.currentThread().getId();
        List<RLock> locks = Arrays.stream(keys).map(k -> getLock(k)).collect(Collectors.toList());
        return executeLocked(timeout, () -> {
            AtomicLong counter = new AtomicLong();
            List<K> keyList = new ArrayList<>(Arrays.asList(keys));
            for (Iterator<K> iterator = keyList.iterator(); iterator.hasNext();) {
                K key = iterator.next();
                HashValue keyHash = toKeyHash(key);
                MapEntry currentValue = state.get(keyHash);
                if (currentValue != null && currentValue != MapEntry.NULL) {
                    operations.add(new MapFastRemoveOperation(map, key, transactionId, threadId));
                    state.put(keyHash, MapEntry.NULL);

                    counter.incrementAndGet();
                    iterator.remove();
                }
            }

            // TODO optimize
            return ((RedissonMap<K, V>) map).getAllAsync(new HashSet<>(keyList), Long.MIN_VALUE).thenApply(res -> {
                for (K key : res.keySet()) {
                    HashValue keyHash = toKeyHash(key);
                    operations.add(new MapFastRemoveOperation(map, key, transactionId, threadId));
                    counter.incrementAndGet();
                    state.put(keyHash, MapEntry.NULL);
                }
                return counter.get();
            });
        }, locks);
    }
    
    public RFuture<Integer> valueSizeAsync(K key) {
        HashValue keyHash = toKeyHash(key);
        MapEntry entry = state.get(keyHash);
        if (entry != null) {
            if (entry == MapEntry.NULL) {
                return new CompletableFutureWrapper<>((Integer) null);
            } else {
                ByteBuf valueState = ((RedissonObject) map).encodeMapValue(entry.getValue());
                try {
                    return new CompletableFutureWrapper<>(valueState.readableBytes());
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
                return new CompletableFutureWrapper<>((V) null);
            } else {
                return new CompletableFutureWrapper<>((V) entry.getValue());
            }
        }
        return ((RedissonMap<K, V>) map).getOperationAsync(key);
    }

    public RFuture<Set<K>> readAllKeySetAsync() {
        RFuture<Set<K>> future = map.readAllKeySetAsync();
        CompletionStage<Set<K>> f = future.thenApply(res -> {
            Map<HashValue, MapEntry> newstate = new HashMap<>(state);
            for (Iterator<K> iterator = res.iterator(); iterator.hasNext();) {
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
                res.add((K) entry.getKey());
            }
            return res;
        });
        return new CompletableFutureWrapper<>(f);
    }
    
    public RFuture<Set<Entry<K, V>>> readAllEntrySetAsync() {
        RFuture<Map<K, V>> future = readAllMapAsync();
        CompletionStage<Set<Entry<K, V>>> f = future.thenApply(res -> res.entrySet());
        return new CompletableFutureWrapper<>(f);
    }
    
    public RFuture<Collection<V>> readAllValuesAsync() {
        RFuture<Map<K, V>> future = readAllMapAsync();
        CompletionStage<Collection<V>> f = future.thenApply(res -> res.values());
        return new CompletableFutureWrapper<>(f);
    }
    
    public RFuture<Map<K, V>> readAllMapAsync() {
        RFuture<Map<K, V>> future = map.readAllMapAsync();
        CompletionStage<Map<K, V>> f = future.thenApply(map -> {
            Map<HashValue, MapEntry> newstate = new HashMap<>(state);
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
                map.put((K) entry.getKey(), (V) entry.getValue());
            }
            return map;
        });
        return new CompletableFutureWrapper<>(f);
    }
    
    protected RFuture<Map<K, V>> getAllOperationAsync(Set<K> keys) {
        Set<K> keysToLoad = new HashSet<>();
        Map<K, V> map = new HashMap<>();
        for (K key : keys) {
            HashValue keyHash = toKeyHash(key);
            
            MapEntry entry = state.get(keyHash);
            if (entry != null) {
                if (entry != MapEntry.NULL) {
                    map.put(key, (V) entry.getValue());
                }
            } else {
                keysToLoad.add(key);
            }
        }

        if (keysToLoad.isEmpty()) {
            return new CompletableFutureWrapper<>(map);
        }
        
        RFuture<Map<K, V>> future = ((RedissonMap<K, V>) this.map).getAllOperationAsync(keysToLoad);
        CompletionStage<Map<K, V>> f = future.thenApply(res -> {
            map.putAll(res);
            return map;
        });
        return new CompletableFutureWrapper<>(f);
    }

    protected Set<K> keySet(String pattern, int count) {
        Set<K> keys = map.keySet(pattern, count);
        return keys.stream()
                .filter(k -> {
                    HashValue hash = toKeyHash(k);
                    if (state.get(hash) == null
                            || state.get(hash) != BaseTransactionalMap.MapEntry.NULL) {
                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toSet());
    }


    protected RFuture<V> removeOperationAsync(K key) {
        long threadId = Thread.currentThread().getId();
        return executeLocked(key, () -> {
            HashValue keyHash = toKeyHash(key);
            MapEntry entry = state.get(keyHash);
            if (entry != null) {
                operations.add(new MapRemoveOperation(map, key, transactionId, threadId));
                if (entry == MapEntry.NULL) {
                    return CompletableFuture.completedFuture(null);
                }
                state.put(keyHash, MapEntry.NULL);
                return CompletableFuture.completedFuture((V) entry.getValue());
            }

            return map.getAsync(key).thenApply(res -> {
                operations.add(new MapRemoveOperation(map, key, transactionId, threadId));
                if (res != null) {
                    state.put(keyHash, MapEntry.NULL);
                }
                return res;
            });
        });
    }
    
    protected RFuture<Boolean> removeOperationAsync(Object key, Object value) {
        long threadId = Thread.currentThread().getId();
        return executeLocked((K) key, () -> {
            HashValue keyHash = toKeyHash(key);
            MapEntry entry = state.get(keyHash);
            if (entry != null) {
                if (entry == MapEntry.NULL) {
                    return CompletableFuture.completedFuture(false);
                }

                operations.add(new MapRemoveOperation(map, key, value, transactionId, threadId));
                if (isEqual(entry.getValue(), value)) {
                    state.put(keyHash, MapEntry.NULL);
                    return CompletableFuture.completedFuture(true);
                }

                return CompletableFuture.completedFuture(false);
            }

            return map.getAsync((K) key).thenApply(r -> {
                operations.add(new MapRemoveOperation(map, key, value, transactionId, threadId));
                boolean res = isEqual(r, value);
                if (res) {
                    state.put(keyHash, MapEntry.NULL);
                }
                return res;
            });
        });
    }
    
    private boolean isEqual(Object value, Object oldValue) {
        ByteBuf valueBuf = ((RedissonObject) map).encodeMapValue(value);
        ByteBuf oldValueBuf = ((RedissonObject) map).encodeMapValue(oldValue);
        
        try {
            return valueBuf.equals(oldValueBuf);
        } finally {
            valueBuf.readableBytes();
            oldValueBuf.readableBytes();
        }
    }

    protected RFuture<Void> putAllOperationAsync(Map<? extends K, ? extends V> entries) {
        long threadId = Thread.currentThread().getId();
        List<RLock> list = entries.keySet().stream().map(k -> getLock(k)).collect(Collectors.toList());
        return executeLocked(timeout, () -> {
            for (Entry<? extends K, ? extends V> entry : entries.entrySet()) {
                operations.add(new MapPutOperation(map, entry.getKey(), entry.getValue(), transactionId, threadId));
                HashValue keyHash = toKeyHash(entry.getKey());
                state.put(keyHash, new MapEntry(entry.getKey(), entry.getValue()));
            }

            if (deleted != null) {
                deleted = false;
            }

            return CompletableFuture.completedFuture(null);
        }, list);
    }
    
    protected RFuture<Boolean> replaceOperationAsync(K key, V oldValue, V newValue) {
        long threadId = Thread.currentThread().getId();
        return executeLocked(key, () -> {
            HashValue keyHash = toKeyHash(key);
            MapEntry entry = state.get(keyHash);
            if (entry != null) {
                if (entry == MapEntry.NULL) {
                    return CompletableFuture.completedFuture(false);
                }

                operations.add(new MapReplaceOperation(map, key, newValue, oldValue, transactionId, threadId));
                if (isEqual(entry.getValue(), oldValue)) {
                    state.put(keyHash, new MapEntry(key, newValue));
                    return CompletableFuture.completedFuture(true);
                }

                return CompletableFuture.completedFuture(false);
            }

            return map.getAsync(key).thenApply(r -> {
                operations.add(new MapReplaceOperation(map, key, newValue, oldValue, transactionId, threadId));
                boolean res = isEqual(r, oldValue);
                if (res) {
                    state.put(keyHash, new MapEntry(key, newValue));
                }
                return res;
            });
        });
    }

    protected RFuture<V> replaceOperationAsync(K key, V value) {
        long threadId = Thread.currentThread().getId();
        return executeLocked(key, () -> {
            HashValue keyHash = toKeyHash(key);
            MapEntry entry = state.get(keyHash);
            operations.add(new MapReplaceOperation(map, key, value, transactionId, threadId));
            if (entry != null) {
                if (entry == MapEntry.NULL) {
                    return CompletableFuture.completedFuture(null);
                }

                state.put(keyHash, new MapEntry(key, value));
                return CompletableFuture.completedFuture((V) entry.getValue());
            }

            return map.getAsync(key).thenApply(res -> {
                operations.add(new MapReplaceOperation(map, key, value, transactionId, threadId));
                if (res != null) {
                    state.put(keyHash, new MapEntry(key, value));
                }
                return res;
            });
        });
    }

    protected <R> RFuture<R> executeLocked(K key, Supplier<CompletionStage<R>> runnable) {
        RLock lock = getLock(key);
        return executeLocked(timeout, runnable, lock);
    }

    protected RLock getLock(K key) {
        String lockName = ((RedissonMap<K, V>) map).getLockByMapKey(key, "lock");
        return new RedissonTransactionalLock(commandExecutor, lockName, transactionId);
    }

    private boolean isExists() {
        boolean notExists = state.values().stream().noneMatch(v -> v != MapEntry.NULL);
        return !notExists;
    }

    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        long currentThreadId = Thread.currentThread().getId();
        return executeLocked(timeout, () -> {
            if (isExists()) {
                operations.add(new ExpireOperation(map.getName(), null, lockName, currentThreadId, transactionId, timeToLive, timeUnit, param, keys));
                hasExpiration = true;
                return CompletableFuture.completedFuture(true);
            }

            return isExistsAsync().thenApply(res -> {
                operations.add(new ExpireOperation(map.getName(), null, lockName, currentThreadId, transactionId, timeToLive, timeUnit, param, keys));
                hasExpiration = res;
                return res;
            });
        }, getWriteLock());
    }

    public RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        long currentThreadId = Thread.currentThread().getId();
        return executeLocked(timeout, () -> {
            if (isExists()) {
                operations.add(new ExpireAtOperation(map.getName(), null, lockName, currentThreadId, transactionId, timestamp, param, keys));
                hasExpiration = true;
                return CompletableFuture.completedFuture(true);
            }

            return isExistsAsync().thenApply(res -> {
                operations.add(new ExpireAtOperation(map.getName(), null, lockName, currentThreadId, transactionId, timestamp, param, keys));
                hasExpiration = res;
                return res;
            });
        }, getWriteLock());
    }

    public RFuture<Boolean> clearExpireAsync() {
        long currentThreadId = Thread.currentThread().getId();
        return executeLocked(timeout, () -> {
            if (hasExpiration) {
                operations.add(new ClearExpireOperation(map.getName(), null, lockName, currentThreadId, transactionId));
                hasExpiration = false;
                return CompletableFuture.completedFuture(true);
            }

            return map.remainTimeToLiveAsync().thenApply(res -> {
                operations.add(new ClearExpireOperation(map.getName(), null, lockName, currentThreadId, transactionId));
                hasExpiration = false;
                return res > 0;
            });
        }, getWriteLock());
    }

}
