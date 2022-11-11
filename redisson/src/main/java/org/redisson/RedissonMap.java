/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
import io.netty.util.ReferenceCountUtil;
import org.redisson.api.*;
import org.redisson.api.MapOptions.WriteMode;
import org.redisson.api.mapreduce.RMapReduce;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.NumberConvertor;
import org.redisson.client.protocol.decoder.MapValueDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.decoder.MapGetAllDecoder;
import org.redisson.iterator.RedissonMapIterator;
import org.redisson.mapreduce.RedissonMapReduce;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.reactive.CommandReactiveBatchService;
import org.redisson.rx.CommandRxBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.ConcurrentMap}
 * and {@link java.util.Map}
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonMap<K, V> extends RedissonExpirable implements RMap<K, V> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    final RedissonClient redisson;
    final MapOptions<K, V> options;
    final WriteBehindService writeBehindService;
    final MapWriteBehindTask writeBehindTask;
    
    public RedissonMap(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson, MapOptions<K, V> options, WriteBehindService writeBehindService) {
        super(commandExecutor, name);
        this.redisson = redisson;
        this.options = options;
        if (options != null
                && options.getWriteMode() == WriteMode.WRITE_BEHIND
                    && (options.getWriter() != null || options.getWriterAsync() != null)) {
            this.writeBehindService = writeBehindService;
            writeBehindTask = writeBehindService.start(getRawName(), options);
        } else {
            this.writeBehindService = null;
            writeBehindTask = null;
        }
    }

    public RedissonMap(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        this.name = name;
        this.redisson = null;
        this.options = null;
        this.writeBehindService = null;
        writeBehindTask = null;
    }

    public RedissonMap(Codec codec, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson, MapOptions<K, V> options, WriteBehindService writeBehindService) {
        super(codec, commandExecutor, name);
        this.redisson = redisson;
        this.options = options;
        if (options != null
                && options.getWriteMode() == WriteMode.WRITE_BEHIND
                    && (options.getWriter() != null || options.getWriterAsync() != null)) {
            this.writeBehindService = writeBehindService;
            writeBehindTask = writeBehindService.start(getRawName(), options);
        } else {
            this.writeBehindService = null;
            writeBehindTask = null;
        }
    }

    @Override
    public <KOut, VOut> RMapReduce<K, V, KOut, VOut> mapReduce() {
        return new RedissonMapReduce<>(this, redisson, commandExecutor);
    }

    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(K key) {
        String lockName = getLockByMapKey(key, "permitexpirablesemaphore");
        return new RedissonPermitExpirableSemaphore(commandExecutor, lockName);
    }

    @Override
    public RSemaphore getSemaphore(K key) {
        String lockName = getLockByMapKey(key, "semaphore");
        return new RedissonSemaphore(commandExecutor, lockName);
    }
    
    @Override
    public RCountDownLatch getCountDownLatch(K key) {
        String lockName = getLockByMapKey(key, "countdownlatch");
        return new RedissonCountDownLatch(commandExecutor, lockName);
    }
    
    @Override
    public RLock getFairLock(K key) {
        String lockName = getLockByMapKey(key, "fairlock");
        return new RedissonFairLock(commandExecutor, lockName);
    }
    
    @Override
    public RLock getLock(K key) {
        String lockName = getLockByMapKey(key, "lock");
        return new RedissonLock(commandExecutor, lockName);
    }
    
    @Override
    public RReadWriteLock getReadWriteLock(K key) {
        String lockName = getLockByMapKey(key, "rw_lock");
        return new RedissonReadWriteLock(commandExecutor, lockName);
    }
    
    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (commandExecutor instanceof CommandBatchService
                || commandExecutor instanceof CommandReactiveBatchService
                    || commandExecutor instanceof CommandRxBatchService) {
            throw new IllegalStateException("This method doesn't work in batch mode.");
        }

        checkKey(key);
        checkValue(value);
        Objects.requireNonNull(remappingFunction);

        RLock lock = getLock(key);
        lock.lock();
        try {
            V oldValue = get(key);
            V newValue = value;
            if (oldValue != null) {
                newValue = remappingFunction.apply(oldValue, value);
            }

            if (newValue == null) {
                fastRemove(key);
            } else {
                fastPut(key, newValue);
            }
            return newValue;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public RFuture<V> mergeAsync(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (commandExecutor instanceof CommandBatchService
                || commandExecutor instanceof CommandReactiveBatchService
                    || commandExecutor instanceof CommandRxBatchService) {
            throw new IllegalStateException("This method doesn't work in batch mode.");
        }

        checkKey(key);
        checkValue(value);
        Objects.requireNonNull(remappingFunction);

        RLock lock = getLock(key);
        long threadId = Thread.currentThread().getId();
        CompletionStage<V> f = lock.lockAsync(threadId).thenCompose(r -> {
            RFuture<V> oldValueFuture = getAsync(key);
            return oldValueFuture.thenCompose(oldValue -> {
                CompletableFuture<V> newValuePromise = new CompletableFuture<>();
                if (oldValue != null) {
                    commandExecutor.getConnectionManager().getExecutor().execute(() -> {
                        V newValue;
                        try {
                            newValue = remappingFunction.apply(oldValue, value);
                        } catch (Exception exception) {
                            newValuePromise.completeExceptionally(exception);
                            return;
                        }
                        newValuePromise.complete(newValue);
                    });
                } else {
                    newValuePromise.complete(value);
                }
                return newValuePromise
                        .thenCompose(newValue -> {
                            RFuture<?> future;
                            if (newValue != null) {
                                future = fastPutAsync(key, newValue);
                            } else {
                                future = fastRemoveAsync(key);
                            }
                            return future.thenApply(res -> newValue);
                        });
            }).whenComplete((c, e) -> {
                lock.unlockAsync(threadId);
            });
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<V> computeAsync(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (commandExecutor instanceof CommandBatchService
                || commandExecutor instanceof CommandReactiveBatchService
                    || commandExecutor instanceof CommandRxBatchService) {
            throw new IllegalStateException("This method doesn't work in batch mode.");
        }

        checkKey(key);
        Objects.requireNonNull(remappingFunction);

        RLock lock = getLock(key);
        long threadId = Thread.currentThread().getId();
        CompletionStage<V> f = lock.lockAsync(threadId)
                .thenCompose(r -> {
                RFuture<V> oldValueFuture = getAsync(key);
                return oldValueFuture.thenCompose(oldValue -> {
                    CompletableFuture<V> result = new CompletableFuture<>();
                    commandExecutor.getConnectionManager().getExecutor().execute(() -> {
                        V newValue;
                        try {
                            newValue = remappingFunction.apply(key, oldValue);
                        } catch (Exception exception) {
                            result.completeExceptionally(exception);
                            return;
                        }

                        if (newValue == null) {
                            if (oldValue != null) {
                                fastRemoveAsync(key).whenComplete((res, exc) -> {
                                    if (exc != null) {
                                        result.completeExceptionally(exc);
                                        return;
                                    }

                                    result.complete(newValue);
                                });
                                return;
                            }
                        } else {
                            fastPutAsync(key, newValue).whenComplete((res, exc) -> {
                                if (exc != null) {
                                    result.completeExceptionally(exc);
                                    return;
                                }

                                result.complete(newValue);
                            });
                            return;
                        }

                        result.complete(newValue);
                    });
                    return result;
                }).whenComplete((c, e) -> {
                    lock.unlockAsync(threadId);
                });
        });
        return new CompletableFutureWrapper<>(f);

    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (commandExecutor instanceof CommandBatchService
                || commandExecutor instanceof CommandReactiveBatchService
                    || commandExecutor instanceof CommandRxBatchService) {
            throw new IllegalStateException("This method doesn't work in batch mode.");
        }

        checkKey(key);
        Objects.requireNonNull(remappingFunction);

        RLock lock = getLock(key);
        lock.lock();
        try {
            V oldValue = get(key);

            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue == null) {
                if (oldValue != null) {
                    fastRemove(key);
                }
            } else {
                fastPut(key, newValue);
            }
            return newValue;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public RFuture<V> computeIfAbsentAsync(K key, Function<? super K, ? extends V> mappingFunction) {
        if (commandExecutor instanceof CommandBatchService
                || commandExecutor instanceof CommandReactiveBatchService
                    || commandExecutor instanceof CommandRxBatchService) {
            throw new IllegalStateException("This method doesn't work in batch mode.");
        }

        checkKey(key);
        Objects.requireNonNull(mappingFunction);

        RLock lock = getLock(key);
        long threadId = Thread.currentThread().getId();
        CompletionStage<V> f = lock.lockAsync(threadId)
                .thenCompose(r -> {
                    RFuture<V> oldValueFuture = getAsync(key);
                    return oldValueFuture.thenCompose(oldValue -> {
                        if (oldValue != null) {
                            return CompletableFuture.completedFuture(oldValue);
                        }

                        CompletableFuture<V> result = new CompletableFuture<>();
                        commandExecutor.getConnectionManager().getExecutor().execute(() -> {
                            V newValue;
                            try {
                                newValue = mappingFunction.apply(key);
                            } catch (Exception exception) {
                                result.completeExceptionally(exception);
                                return;
                            }
                            if (newValue != null) {
                                fastPutAsync(key, newValue).thenAccept(res -> {
                                    result.complete(newValue);
                                });
                                return;
                            }

                            result.complete(null);
                        });
                        return result;
                    }).whenComplete((c, e) -> {
                        lock.unlockAsync(threadId);
                    });
                });

        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (commandExecutor instanceof CommandBatchService
                || commandExecutor instanceof CommandReactiveBatchService
                    || commandExecutor instanceof CommandRxBatchService) {
            throw new IllegalStateException("This method doesn't work in batch mode.");
        }

        checkKey(key);
        Objects.requireNonNull(mappingFunction);

        RLock lock = getLock(key);
        lock.lock();
        try {
            V value = get(key);
            if (value == null) {
                V newValue = mappingFunction.apply(key);
                if (newValue != null) {
                    fastPut(key, newValue);
                    return newValue;
                }
                return null;
            }
            return value;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public RFuture<V> computeIfPresentAsync(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (commandExecutor instanceof CommandBatchService
                || commandExecutor instanceof CommandReactiveBatchService
                    || commandExecutor instanceof CommandRxBatchService) {
            throw new IllegalStateException("This method doesn't work in batch mode.");
        }

        checkKey(key);
        Objects.requireNonNull(remappingFunction);

        RLock lock = getLock(key);
        long threadId = Thread.currentThread().getId();
        CompletionStage<V> f = lock.lockAsync(threadId)
                .thenCompose(r -> {
                    RFuture<V> oldValueFuture = getAsync(key);
                    return oldValueFuture.thenCompose(oldValue -> {
                        if (oldValue == null) {
                            return CompletableFuture.completedFuture(null);
                        }

                        CompletableFuture<V> result = new CompletableFuture<>();
                        commandExecutor.getConnectionManager().getExecutor().execute(() -> {
                            V newValue;
                            try {
                                newValue = remappingFunction.apply(key, oldValue);
                            } catch (Exception exception) {
                                result.completeExceptionally(exception);
                                return;
                            }
                            if (newValue != null) {
                                RFuture<Boolean> fastPutFuture = fastPutAsync(key, newValue);
                                fastPutFuture.whenComplete((re, ex1) -> {
                                    if (ex1 != null) {
                                        result.completeExceptionally(ex1);
                                        return;
                                    }

                                    result.complete(newValue);
                                });
                            } else {
                                RFuture<Long> removeFuture = fastRemoveAsync(key);
                                removeFuture.whenComplete((re, ex1) -> {
                                    if (ex1 != null) {
                                        result.completeExceptionally(ex1);
                                        return;
                                    }

                                    result.complete(null);
                                });
                            }
                        });
                        return result;
                    }).whenComplete((c, e) -> {
                        lock.unlockAsync(threadId);
                    });
                });

        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (commandExecutor instanceof CommandBatchService
                || commandExecutor instanceof CommandReactiveBatchService
                    || commandExecutor instanceof CommandRxBatchService) {
            throw new IllegalStateException("This method doesn't work in batch mode.");
        }

        checkKey(key);
        Objects.requireNonNull(remappingFunction);

        RLock lock = getLock(key);
        lock.lock();
        try {
            V oldValue = get(key);
            if (oldValue == null) {
                return null;
            }

            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue != null) {
                fastPut(key, newValue);
                return newValue;
            }
            fastRemove(key);
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public RFuture<Integer> sizeAsync() {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.HLEN, getRawName());
    }

    @Override
    public int valueSize(K key) {
        return get(valueSizeAsync(key));
    }
    
    @Override
    public RFuture<Integer> valueSizeAsync(K key) {
        checkKey(key);

        String name = getRawName(key);
        return commandExecutor.readAsync(name, codec, RedisCommands.HSTRLEN, name, encodeMapKey(key));
    }

    protected void checkKey(Object key) {
        if (key == null) {
            throw new NullPointerException("map key can't be null");
        }
    }
    
    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(containsKeyAsync(key));
    }

    @Override
    public RFuture<Boolean> containsKeyAsync(Object key) {
        checkKey(key);

        CompletableFuture<V> promise = new CompletableFuture<>();
        return containsKeyAsync(key, promise);
    }

    protected RFuture<Boolean> containsKeyOperationAsync(String name, Object key) {
        return commandExecutor.readAsync(name, codec, RedisCommands.HEXISTS, name, encodeMapKey(key));
    }

    protected RFuture<Boolean> containsKeyAsync(Object key, CompletableFuture<V> promise) {
        String name = getRawName(key);
        RFuture<Boolean> future = containsKeyOperationAsync(name, key);
        if (hasNoLoader()) {
            return future;
        }

        CompletionStage<Boolean> result = future.thenCompose(res -> {
            if (!res) {
                CompletableFuture<V> f = loadValue((K) key, false);
                commandExecutor.transfer(f, promise);
                return promise.thenApply(r -> r != null);
            }
            promise.complete(null);
            return CompletableFuture.completedFuture(res);
        });
        return new CompletableFutureWrapper<>(result);
    }

    @Override
    public boolean containsValue(Object value) {
        return get(containsValueAsync(value));
    }

    @Override
    public RFuture<Boolean> containsValueAsync(Object value) {
        checkValue(value);
        
        return commandExecutor.evalReadAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local s = redis.call('hvals', KEYS[1]);" +
                        "for i = 1, #s, 1 do "
                            + "if ARGV[1] == s[i] then "
                                + "return 1 "
                            + "end "
                       + "end;" +
                     "return 0",
                Collections.singletonList(getRawName()), encodeMapValue(value));
    }

    @Override
    public Map<K, V> getAll(Set<K> keys) {
        return get(getAllAsync(keys));
    }

    @Override
    public Set<K> randomKeys(int count) {
        return get(randomKeysAsync(count));
    }

    @Override
    public Map<K, V> randomEntries(int count) {
        return get(randomEntriesAsync(count));
    }

    @Override
    public RFuture<Set<K>> randomKeysAsync(int count) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.HRANDFIELD_KEYS, getRawName(), count);
    }

    @Override
    public RFuture<Map<K, V>> randomEntriesAsync(int count) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.HRANDFIELD, getRawName(), count, "WITHVALUES");
    }

    @Override
    public RFuture<Map<K, V>> getAllAsync(Set<K> keys) {
        if (keys.isEmpty()) {
            return new CompletableFutureWrapper<>(Collections.emptyMap());
        }

        RFuture<Map<K, V>> future = getAllOperationAsync(keys);
        if (hasNoLoader()) {
            return future;
        }

        CompletionStage<Map<K, V>> f = future.thenCompose(res -> {
            if (!res.keySet().containsAll(keys)) {
                Set<K> newKeys = new HashSet<K>(keys);
                newKeys.removeAll(res.keySet());

                CompletionStage<Map<K, V>> ff = loadAllMapAsync(newKeys.spliterator(), false, 1);
                return ff.thenApply(map -> {
                    res.putAll(map);
                    return res;
                });
            }
            return CompletableFuture.completedFuture(res);
        });
        return new CompletableFutureWrapper<>(f);
    }

    protected boolean hasNoLoader() {
        return options == null || (options.getLoader() == null && options.getLoaderAsync() == null);
    }

    public RFuture<Map<K, V>> getAllOperationAsync(Set<K> keys) {
        List<Object> args = new ArrayList<>(keys.size() + 1);
        args.add(getRawName());
        encodeMapKeys(args, keys);
        RFuture<Map<K, V>> future = commandExecutor.readAsync(getRawName(), codec, new RedisCommand<>("HMGET",
                        new MapValueDecoder(new MapGetAllDecoder(new ArrayList<>(keys), 0))),
                args.toArray());
        return future;
    }
    
    @Override
    public V get(Object key) {
        return get(getAsync((K) key));
    }

    @Override
    public V put(K key, V value) {
        return get(putAsync(key, value));
    }

    @Override
    public V remove(Object key) {
        return get(removeAsync((K) key));
    }

    @Override
    public final void putAll(Map<? extends K, ? extends V> map) {
        get(putAllAsync(map));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map, int batchSize) {
        get(putAllAsync(map, batchSize));
    }
    
    @Override
    public RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map, int batchSize) {
        Map<K, V> batch = new HashMap<K, V>();
        AtomicInteger counter = new AtomicInteger();
        Iterator<Entry<K, V>> iter = ((Map<K, V>) map).entrySet().iterator();

        CompletionStage<Void> f = putAllAsync(batch, iter, counter, batchSize);
        return new CompletableFutureWrapper<>(f);
    }
    
    private CompletionStage<Void> putAllAsync(Map<K, V> batch, Iterator<Entry<K, V>> iter,
                                                    AtomicInteger counter, int batchSize) {
        batch.clear();
        
        while (iter.hasNext()) {
            Entry<K, V> entry = iter.next();
            batch.put(entry.getKey(), entry.getValue());
            counter.incrementAndGet();
            if (counter.get() % batchSize == 0) {
                RFuture<Void> future = putAllAsync(batch);
                return future.thenCompose(res -> {
                    return putAllAsync(batch, iter, counter, batchSize);
                });
            }
        }
        
        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return putAllAsync(batch);
    }
    
    @Override
    public final RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map) {
        if (map.isEmpty()) {
            return new CompletableFutureWrapper<>((Void) null);
        }

        RFuture<Void> future = putAllOperationAsync(map);
        if (hasNoWriter()) {
            return future;
        }
        
        return mapWriterFuture(future, new MapWriterTask.Add(map));
    }

    protected final <M> RFuture<M> mapWriterFuture(RFuture<M> future, MapWriterTask task) {
        return mapWriterFuture(future, task, r -> true);
    }
    
    protected final <M> RFuture<M> mapWriterFuture(RFuture<M> future, MapWriterTask task, Function<M, Boolean> condition) {
        if (options != null && options.getWriteMode() == WriteMode.WRITE_BEHIND) {
            CompletionStage<M> f = future.whenComplete((res, e) -> {
                if (e == null && condition.apply(res)) {
                    writeBehindTask.addTask(task);
                }
            });
            return new CompletableFutureWrapper<>(f);
        }

        CompletionStage<M> f = future.thenCompose(res -> {
            if (condition.apply(res)) {
                if (options.getWriter() != null) {
                    CompletableFuture<M> promise = new CompletableFuture<>();
                    commandExecutor.getConnectionManager().getExecutor().execute(() -> {
                        try {
                            if (task instanceof MapWriterTask.Add) {
                                options.getWriter().write(task.getMap());
                            } else {
                                options.getWriter().delete(task.getKeys());
                            }
                        } catch (Exception ex) {
                            promise.completeExceptionally(ex);
                            return;
                        }
                        promise.complete(res);
                    });
                    return promise;
                }

                if (task instanceof MapWriterTask.Add) {
                    return options.getWriterAsync().write(task.getMap())
                                                    .thenApply(r -> res);
                } else {
                    return options.getWriterAsync().delete(task.getKeys())
                                                    .thenApply(r -> res);
                }
            }
            return CompletableFuture.completedFuture(res);
        });

        return new CompletableFutureWrapper<>(f);
    }

    protected RFuture<Void> putAllOperationAsync(Map<? extends K, ? extends V> map) {
        List<Object> params = new ArrayList<>(map.size()*2 + 1);
        params.add(getRawName());
        encodeMapKeys(params, map);

        RFuture<Void> future = commandExecutor.writeAsync(getRawName(), codec, RedisCommands.HMSET, params.toArray());
        return future;
    }

    @Override
    public void clear() {
        delete();
    }

    @Override
    public Set<K> keySet() {
        return keySet(null);
    }
    
    @Override
    public Set<K> keySet(String pattern) {
        return keySet(pattern, 10);
    }
    
    @Override
    public Set<K> keySet(String pattern, int count) {
        return new KeySet(pattern, count);
    }

    @Override
    public Set<K> keySet(int count) {
        return keySet(null, count);
    }

    @Override
    public Collection<V> values() {
        return values(null);
    }

    @Override
    public Collection<V> values(String keyPattern, int count) {
        return new Values(keyPattern, count);
    }
    
    @Override
    public Collection<V> values(String keyPattern) {
        return values(keyPattern, 10);
    }

    @Override
    public Collection<V> values(int count) {
        return values(null, count);
    }
    
    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        return entrySet(null);
    }
    
    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet(String keyPattern) {
        return entrySet(keyPattern, 10);
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet(String keyPattern, int count) {
        return new EntrySet(keyPattern, count);
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet(int count) {
        return entrySet(null, count);
    }
    
    @Override
    public Set<K> readAllKeySet() {
        return get(readAllKeySetAsync());
    }

    @Override
    public RFuture<Set<K>> readAllKeySetAsync() {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.HKEYS, getRawName());
    }

    @Override
    public Collection<V> readAllValues() {
        return get(readAllValuesAsync());
    }

    @Override
    public RFuture<Collection<V>> readAllValuesAsync() {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.HVALS, getRawName());
    }

    @Override
    public Set<Entry<K, V>> readAllEntrySet() {
        return get(readAllEntrySetAsync());
    }

    @Override
    public RFuture<Set<Entry<K, V>>> readAllEntrySetAsync() {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.HGETALL_ENTRY, getRawName());
    }

    @Override
    public Map<K, V> readAllMap() {
        return get(readAllMapAsync());
    }

    @Override
    public RFuture<Map<K, V>> readAllMapAsync() {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.HGETALL, getRawName());
    }

    @Override
    public V putIfExists(K key, V value) {
        return get(putIfExistsAsync(key, value));
    }

    @Override
    public RFuture<V> putIfExistsAsync(K key, V value) {
        checkKey(key);
        checkValue(value);

        RFuture<V> future = putIfExistsOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }

        MapWriterTask.Add task = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, task, Objects::nonNull);
    }

    protected RFuture<V> putIfExistsOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
            "local value = redis.call('hget', KEYS[1], ARGV[1]); "
                + "if value ~= false then "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                    + "return value; "
                + "end; "
                + "return nil; ",
                Collections.singletonList(name), encodeMapKey(key), encodeMapValue(value));
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return get(putIfAbsentAsync(key, value));
    }

    @Override
    public RFuture<V> putIfAbsentAsync(K key, V value) {
        checkKey(key);
        checkValue(value);
        
        RFuture<V> future = putIfAbsentOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask.Add task = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, task, Objects::isNull);
    }

    protected boolean hasNoWriter() {
        return options == null || (options.getWriter() == null && options.getWriterAsync() == null);
    }

    protected RFuture<V> putIfAbsentOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteNoRetryAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                 "if redis.call('hsetnx', KEYS[1], ARGV[1], ARGV[2]) == 1 then "
                    + "return nil "
                + "else "
                    + "return redis.call('hget', KEYS[1], ARGV[1]) "
                + "end",
                Collections.singletonList(name), encodeMapKey(key), encodeMapValue(value));
    }

    @Override
    public boolean fastPutIfAbsent(K key, V value) {
        return get(fastPutIfAbsentAsync(key, value));
    }

    @Override
    public RFuture<Boolean> fastPutIfAbsentAsync(K key, V value) {
        checkKey(key);
        checkValue(value);
        
        RFuture<Boolean> future = fastPutIfAbsentOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask.Add task = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, task, Function.identity());
    }

    protected RFuture<Boolean> fastPutIfAbsentOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.writeAsync(name, codec, RedisCommands.HSETNX, name, encodeMapKey(key), encodeMapValue(value));
    }

    @Override
    public boolean fastPutIfExists(K key, V value) {
        return get(fastPutIfExistsAsync(key, value));
    }

    @Override
    public RFuture<Boolean> fastPutIfExistsAsync(K key, V value) {
        checkKey(key);
        checkValue(value);

        RFuture<Boolean> future = fastPutIfExistsOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }

        MapWriterTask.Add task = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, task, Function.identity());
    }

    protected RFuture<Boolean> fastPutIfExistsOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
            "local value = redis.call('hget', KEYS[1], ARGV[1]); "
                + "if value ~= false then "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                    + "return 1; "
                + "end; "
                + "return 0; ",
                Collections.singletonList(name),
                encodeMapKey(key), encodeMapValue(value));
    }

    @Override
    public boolean remove(Object key, Object value) {
        return get(removeAsync(key, value));
    }

    @Override
    public RFuture<Boolean> removeAsync(Object key, Object value) {
        checkKey(key);
        checkValue(value);
        
        RFuture<Boolean> future = removeOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask.Remove listener = new MapWriterTask.Remove(key);
        return mapWriterFuture(future, listener, Function.identity());
    }

    protected RFuture<Boolean> removeOperationAsync(Object key, Object value) {
        String name = getRawName(key);
        RFuture<Boolean> future = commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then "
                        + "return redis.call('hdel', KEYS[1], ARGV[1]) "
                + "else "
                    + "return 0 "
                + "end",
            Collections.singletonList(name), encodeMapKey(key), encodeMapValue(value));
        return future;
    }

    protected void checkValue(Object value) {
        if (value == null) {
            throw new NullPointerException("map value can't be null");
        }
    }

    protected void encodeMapKeys(Collection<Object> params, Map<?, ?> map) {
        try {
            for (java.util.Map.Entry<?, ?> t : map.entrySet()) {
                checkKey(t.getKey());
                checkValue(t.getValue());

                params.add(encodeMapKey(t.getKey()));
                params.add(encodeMapValue(t.getValue()));
            }
        } catch (Exception e) {
            params.forEach(v -> {
                ReferenceCountUtil.safeRelease(v);
            });
            throw e;
        }
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        return get(replaceAsync(key, oldValue, newValue));
    }

    @Override
    public RFuture<Boolean> replaceAsync(K key, V oldValue, V newValue) {
        checkKey(key);
        if (oldValue == null) {
            throw new NullPointerException("map oldValue can't be null");
        }
        if (newValue == null) {
            throw new NullPointerException("map newValue can't be null");
        }

        RFuture<Boolean> future = replaceOperationAsync(key, oldValue, newValue);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask.Add task = new MapWriterTask.Add(key, newValue);
        return mapWriterFuture(future, task, Function.identity());
    }

    protected RFuture<Boolean> replaceOperationAsync(K key, V oldValue, V newValue) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[3]); "
                    + "return 1; "
                + "else "
                    + "return 0; "
                + "end",
                Collections.singletonList(name), encodeMapKey(key), encodeMapValue(oldValue), encodeMapValue(newValue));
    }

    @Override
    public V replace(K key, V value) {
        return get(replaceAsync(key, value));
    }

    @Override
    public RFuture<V> replaceAsync(K key, V value) {
        checkKey(key);
        checkValue(value);
        
        RFuture<V> future = replaceOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask.Add task = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, task, r -> r != null);
    }

    protected RFuture<V> replaceOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then "
                    + "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                    + "return v; "
                + "else "
                    + "return nil; "
                + "end",
            Collections.singletonList(name), encodeMapKey(key), encodeMapValue(value));
    }
    
    @Override
    public boolean fastReplace(K key, V value) {
        return get(fastReplaceAsync(key, value));
    }

    @Override
    public RFuture<Boolean> fastReplaceAsync(K key, V value) {
        checkKey(key);
        checkValue(value);
        
        RFuture<Boolean> future = fastReplaceOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask.Add task = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, task, Function.identity());
    }

    protected RFuture<Boolean> fastReplaceOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                    + "return 1; "
                + "else "
                    + "return 0; "
                + "end",
            Collections.singletonList(name), encodeMapKey(key), encodeMapValue(value));
    }
    

    public RFuture<V> getOperationAsync(K key) {
        String name = getRawName(key);
        return commandExecutor.readAsync(name, codec, RedisCommands.HGET, name, encodeMapKey(key));
    }
    
    @Override
    public RFuture<V> getAsync(K key) {
        checkKey(key);

        RFuture<V> future = getOperationAsync(key);
        if (hasNoLoader()) {
            return future;
        }

        long threadId = Thread.currentThread().getId();
        CompletionStage<V> f = future.thenCompose(res -> {
            if (res == null) {
                return loadValue(key, false, threadId);
            }
            return CompletableFuture.completedFuture(res);
        });
        return new CompletableFutureWrapper<>(f);
    }
    
    @Override
    public void loadAll(boolean replaceExistingValues, int parallelism) {
        get(loadAllAsync(replaceExistingValues, parallelism));
    }
    
    @Override
    public RFuture<Void> loadAllAsync(boolean replaceExistingValues, int parallelism) {
        if (hasNoLoader()) {
            throw new NullPointerException("MapLoader isn't defined");
        }

        if (options.getLoaderAsync() != null) {
            return loadAllAsync(options.getLoaderAsync().loadAllKeys(), replaceExistingValues, parallelism);
        }

        return loadAllAsync(() -> options.getLoader().loadAllKeys().spliterator(), replaceExistingValues, parallelism);
    }

    RFuture<Void> loadAllAsync(AsyncIterator<K> iterator, boolean replaceExistingValues, int parallelism) {
        CompletionStage<List<K>> f = loadAllAsync(iterator, new ArrayList<>(), new AtomicInteger(parallelism));
        CompletionStage<Void> ff = f.thenCompose(elements -> {
            List<CompletableFuture<V>> futures = new ArrayList<>(elements.size());
            for (K k : elements) {
                if (replaceExistingValues) {
                    CompletableFuture<V> vFuture = loadValue(k, true);
                    futures.add(vFuture);
                } else {
                    CompletableFuture<V> vFuture = new CompletableFuture<>();
                    containsKeyAsync(k, vFuture);
                    futures.add(vFuture);
                }
            }

            CompletableFuture<Void> finalFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            if (elements.size() < parallelism) {
                return finalFuture;
            }

            return finalFuture
                        .thenCompose(v -> loadAllAsync(iterator, replaceExistingValues, parallelism));
        });
        return new CompletableFutureWrapper<>(ff);
    }

    CompletionStage<List<K>> loadAllAsync(AsyncIterator<K> iterator, List<K> elements, AtomicInteger workers) {
        return iterator.hasNext()
                .thenCompose(v -> {
                    int s = workers.decrementAndGet();
                    if (v) {
                        return iterator.next().thenCompose(k -> {
                            if (k != null) {
                                elements.add(k);
                            }
                            if (s > 0) {
                                return loadAllAsync(iterator, elements, workers);
                            }
                            return CompletableFuture.completedFuture(elements);
                        });
                    }
                    return CompletableFuture.completedFuture(elements);
                });

    }

    private RFuture<Void> loadAllAsync(Supplier<Spliterator<K>> supplier, boolean replaceExistingValues, int parallelism) {
        ForkJoinPool customThreadPool = new ForkJoinPool(parallelism);
        CompletableFuture<Void> result = new CompletableFuture<>();
        customThreadPool.submit(() -> {
            try {
                Stream<K> s = StreamSupport.stream(supplier.get(), true);
                List<CompletableFuture<?>> r = s.filter(k -> k != null)
                        .map(k -> {
                            if (replaceExistingValues) {
                                return loadValue(k, true).thenApply(v -> null);
                            }

                            CompletableFuture<V> valuePromise = new CompletableFuture<>();
                            containsKeyAsync(k, valuePromise);
                            return valuePromise.thenApply(v -> null);
                        }).collect(Collectors.toList());

                CompletableFuture<Void> ff = CompletableFuture.allOf(r.toArray(new CompletableFuture[0]));
                ff.thenApply(v -> {
                    customThreadPool.shutdown();
                    return result.complete(v);
                });
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });

        return new CompletableFutureWrapper<>(result);
    }

    protected CompletionStage<Map<K, V>> loadAllMapAsync(Spliterator<K> spliterator, boolean replaceExistingValues, int parallelism) {
        ForkJoinPool customThreadPool = new ForkJoinPool(parallelism);
        ConcurrentMap<K, V> map = new ConcurrentHashMap<>();
        CompletableFuture<Map<K, V>> result = new CompletableFuture<>();
        customThreadPool.submit(() -> {
            try {
                Stream<K> s = StreamSupport.stream(spliterator, true);
                List<CompletableFuture<?>> r = s.filter(k -> k != null)
                        .map(k -> {
                            if (replaceExistingValues) {
                                return loadValue(k, true).thenApply(v -> map.put(k, v));
                            }

                            CompletableFuture<V> valuePromise = new CompletableFuture<>();
                            containsKeyAsync(k, valuePromise);
                            return valuePromise.thenApply(v -> {
                                if (v == null) {
                                    return false;
                                }
                                return map.put(k, v);
                            });
                        }).collect(Collectors.toList());

                CompletableFuture<Void> ff = CompletableFuture.allOf(r.toArray(new CompletableFuture[0]));
                ff.thenApply(v -> {
                    customThreadPool.shutdown();
                    return result.complete(map);
                });
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });

        return result;
    }

    @Override
    public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, int parallelism) {
        get(loadAllAsync(keys, replaceExistingValues, parallelism));
    }
    
    @Override
    public RFuture<Void> loadAllAsync(Set<? extends K> keys, boolean replaceExistingValues, int parallelism) {
        return loadAllAsync(() -> (Spliterator<K>) keys.spliterator(), replaceExistingValues, parallelism);
    }

    @Override
    public RFuture<V> putAsync(K key, V value) {
        checkKey(key);
        checkValue(value);
        
        RFuture<V> future = putOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }
        
        return mapWriterFuture(future, new MapWriterTask.Add(key, value));
    }

    protected RFuture<V> putOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                + "return v",
                Collections.singletonList(name), encodeMapKey(key), encodeMapValue(value));
    }

    @Override
    public RFuture<V> removeAsync(K key) {
        checkKey(key);

        RFuture<V> future = removeOperationAsync(key);
        if (hasNoWriter()) {
            return future;
        }
        
        return mapWriterFuture(future, new MapWriterTask.Remove(key));
    }

    protected RFuture<V> removeOperationAsync(K key) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                + "redis.call('hdel', KEYS[1], ARGV[1]); "
                + "return v",
                Collections.singletonList(name), encodeMapKey(key));
    }

    @Override
    public RFuture<Boolean> fastPutAsync(K key, V value) {
        checkKey(key);
        checkValue(value);
        
        RFuture<Boolean> future = fastPutOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }
        
        return mapWriterFuture(future, new MapWriterTask.Add(key, value));
    }

    protected RFuture<Boolean> fastPutOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.writeAsync(name, codec, RedisCommands.HSET, name, encodeMapKey(key), encodeMapValue(value));
    }

    @Override
    public boolean fastPut(K key, V value) {
        return get(fastPutAsync(key, value));
    }

    @Override
    public RFuture<Long> fastRemoveAsync(K... keys) {
        if (keys == null) {
            throw new NullPointerException();
        }

        if (keys.length == 0) {
            return new CompletableFutureWrapper<>(0L);
        }

        if (hasNoWriter()) {
            return fastRemoveOperationAsync(keys);
        }

        RFuture<List<Long>> removeFuture = fastRemoveOperationBatchAsync(keys);
        CompletionStage<Long> f = removeFuture.thenCompose(res -> {
            if (res.isEmpty()) {
                return CompletableFuture.completedFuture(0L);
            }

            List<K> deletedKeys = new ArrayList<K>();
            for (int i = 0; i < res.size(); i++) {
                if (res.get(i) == 1) {
                    deletedKeys.add(keys[i]);
                }
            }

            if (options.getWriteMode() == WriteMode.WRITE_BEHIND) {
                MapWriterTask.Remove task = new MapWriterTask.Remove(deletedKeys);
                writeBehindTask.addTask(task);

                return CompletableFuture.completedFuture((long) deletedKeys.size());
            } else {
                if (options.getWriter() != null) {
                    CompletableFuture<Long> future = new CompletableFuture<>();
                    commandExecutor.getConnectionManager().getExecutor().execute(() -> {
                        try {
                            options.getWriter().delete(deletedKeys);
                        } catch (Exception ex) {
                            future.completeExceptionally(ex);
                            return;
                        }
                        future.complete((long) deletedKeys.size());
                    });
                    return future;
                }

                return options.getWriterAsync().delete(deletedKeys)
                                                .thenApply(r -> (long) deletedKeys.size());
            }
        });
        return new CompletableFutureWrapper<>(f);
    }

    protected RFuture<List<Long>> fastRemoveOperationBatchAsync(K... keys) {
        List<Object> args = new ArrayList<>(keys.length);
        encodeMapKeys(args, Arrays.asList(keys));

        RFuture<List<Long>> future = commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_LIST,
                        "local result = {}; " + 
                        "for i = 1, #ARGV, 1 do " 
                        + "local val = redis.call('hdel', KEYS[1], ARGV[i]); "
                        + "table.insert(result, val); "
                      + "end;"
                      + "return result;",
                        Arrays.asList(getRawName()),
                        args.toArray());
        return future;
    }

    protected RFuture<Long> fastRemoveOperationAsync(K... keys) {
        List<Object> args = new ArrayList<>(keys.length + 1);
        args.add(getRawName());
        encodeMapKeys(args, Arrays.asList(keys));
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.HDEL, args.toArray());
    }

    @Override
    public long fastRemove(K... keys) {
        return get(fastRemoveAsync(keys));
    }

    public ScanResult<Map.Entry<Object, Object>> scanIterator(String name, RedisClient client, long startPos, String pattern, int count) {
        RFuture<ScanResult<Map.Entry<Object, Object>>> f = scanIteratorAsync(name, client, startPos, pattern, count);
        return get(f);
    }
    
    public RFuture<ScanResult<Map.Entry<Object, Object>>> scanIteratorAsync(String name, RedisClient client, long startPos, String pattern, int count) {
        if (pattern == null) {
            RFuture<ScanResult<Map.Entry<Object, Object>>> f
                                    = commandExecutor.readAsync(client, name, codec, RedisCommands.HSCAN, name, startPos, "COUNT", count);
            return f;
        }
        RFuture<ScanResult<Map.Entry<Object, Object>>> f
                                    = commandExecutor.readAsync(client, name, codec, RedisCommands.HSCAN, name, startPos, "MATCH", pattern, "COUNT", count);
        return f;
    }

    @Override
    public V addAndGet(K key, Number value) {
        return get(addAndGetAsync(key, value));
    }

    @Override
    public RFuture<V> addAndGetAsync(K key, Number value) {
        checkKey(key);
        checkValue(value);
        
        RFuture<V> future = addAndGetOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }

        return mapWriterFuture(future, new MapWriterTask.Add() {
            @Override
            public Map<K, V> getMap() {
                return Collections.singletonMap(key, commandExecutor.getNow(future.toCompletableFuture()));
            }
        });
    }

    protected RFuture<V> addAndGetOperationAsync(K key, Number value) {
        ByteBuf keyState = encodeMapKey(key);
        String name = getRawName(key);
        RFuture<V> future = commandExecutor.writeAsync(name, StringCodec.INSTANCE,
                new RedisCommand<>("HINCRBYFLOAT", new NumberConvertor(value.getClass())),
                name, keyState, new BigDecimal(value.toString()).toPlainString());
        return future;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof Map))
            return false;
        Map<?, ?> m = (Map<?, ?>) o;
        if (m.size() != size())
            return false;

        try {
            Iterator<Entry<K, V>> i = entrySet().iterator();
            while (i.hasNext()) {
                Entry<K, V> e = i.next();
                K key = e.getKey();
                V value = e.getValue();
                if (value == null) {
                    if (!(m.get(key)==null && m.containsKey(key)))
                        return false;
                } else {
                    if (!value.equals(m.get(key)))
                        return false;
                }
            }
        } catch (ClassCastException unused) {
            return false;
        } catch (NullPointerException unused) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        Iterator<Entry<K, V>> i = entrySet().iterator();
        while (i.hasNext()) {
            h += i.next().hashCode();
        }
        return h;
    }

    protected Iterator<K> keyIterator(String pattern, int count) {
        return new RedissonMapIterator<K>(RedissonMap.this, pattern, count) {
            @Override
            protected K getValue(java.util.Map.Entry<Object, Object> entry) {
                return (K) entry.getKey();
            }
        };
    }
    
    final class KeySet extends AbstractSet<K> {

        private final String pattern;
        private final int count;
        
        KeySet(String pattern, int count) {
            this.pattern = pattern;
            this.count = count;
        }

        @Override
        public boolean isEmpty() {
            return !iterator().hasNext();
        }

        @Override
        public Iterator<K> iterator() {
            return keyIterator(pattern, count);
        }

        @Override
        public boolean contains(Object o) {
            return RedissonMap.this.containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            return RedissonMap.this.fastRemove((K) o) == 1;
        }

        @Override
        public int size() {
            if (pattern != null) {
                int size = 0;
                for (K val : this) {
                    size++;
                }
                return size;
            }
            return RedissonMap.this.size();
        }

        @Override
        public void clear() {
            RedissonMap.this.clear();
        }

    }

    protected Iterator<V> valueIterator(String pattern, int count) {
        return new RedissonMapIterator<V>(RedissonMap.this, pattern, count) {
            @Override
            protected V getValue(java.util.Map.Entry<Object, Object> entry) {
                return (V) entry.getValue();
            }
        };
    }

    final class Values extends AbstractCollection<V> {

        private final String keyPattern;
        private final int count;
        
        Values(String keyPattern, int count) {
            this.keyPattern = keyPattern;
            this.count = count;
        }

        @Override
        public boolean isEmpty() {
            return !iterator().hasNext();
        }

        @Override
        public Iterator<V> iterator() {
            return valueIterator(keyPattern, count);
        }

        @Override
        public boolean contains(Object o) {
            return RedissonMap.this.containsValue(o);
        }

        @Override
        public int size() {
            if (keyPattern != null) {
                int size = 0;
                for (V val : this) {
                    size++;
                }
                return size;
            }

            return RedissonMap.this.size();
        }

        @Override
        public void clear() {
            RedissonMap.this.clear();
        }

    }

    protected Iterator<Map.Entry<K, V>> entryIterator(String pattern, int count) {
        return new RedissonMapIterator<>(RedissonMap.this, pattern, count);
    }

    protected CompletableFuture<V> loadValue(K key, boolean replaceValue) {
        return loadValue(key, replaceValue, Thread.currentThread().getId());
    }

    protected CompletableFuture<V> loadValue(K key, boolean replaceValue, long threadId) {
        RLock lock = getLock(key);
        return lock.lockAsync(threadId).thenCompose(res -> {
            if (replaceValue) {
                return loadValue(key, lock, threadId);
            }
            
            return getOperationAsync(key).thenCompose(r -> {
                if (r != null) {
                    return lock.unlockAsync(threadId).thenApply(v -> r);
                }
                
                return loadValue(key, lock, threadId);
            });
        }).whenComplete((r, e) -> {
            if (e != null) {
                lock.unlockAsync(threadId);
            }
        }).toCompletableFuture();
    }
    
    private CompletableFuture<V> loadValue(K key, RLock lock, long threadId) {
        if (options.getLoader() != null) {
            CompletableFuture<V> result = new CompletableFuture<>();
            commandExecutor.getConnectionManager().getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    V value;
                    try {
                        value = options.getLoader().load(key);
                        if (value == null) {
                            lock.unlockAsync(threadId)
                                    .whenComplete((r, e) -> {
                                        if (e != null) {
                                            result.completeExceptionally(e);
                                            return;
                                        }

                                        result.complete(value);
                                    });
                            return;
                        }
                    } catch (Exception e) {
                        log.error("Unable to load value by key " + key + " for map " + getRawName(), e);
                        lock.unlockAsync(threadId)
                                .whenComplete((r, ex) -> {
                                    if (ex != null) {
                                        result.completeExceptionally(ex);
                                        return;
                                    }

                                    result.complete(null);
                                });
                        return;
                    }

                    putOperationAsync(key, value)
                            .whenComplete((res, e) -> {
                                if (e != null) {
                                    lock.unlockAsync(threadId);
                                    result.completeExceptionally(e);
                                    return;
                                }

                                lock.unlockAsync(threadId)
                                        .whenComplete((r, ex) -> {
                                            if (ex != null) {
                                                result.completeExceptionally(ex);
                                                return;
                                            }

                                            result.complete(value);
                                        });
                            });
                }
            });
            return result;
        }

        CompletionStage<V> valueFuture = options.getLoaderAsync().load(key);
        return valueFuture.handle((r, ex) -> {
            if (r == null) {
                return lock.unlockAsync(threadId);
            }
            if (ex != null) {
                log.error("Unable to load value by key " + key + " for map " + getRawName(), ex);
                return lock.unlockAsync(threadId);
            }

            return valueFuture;
        }).thenCompose(f -> f)
          .thenCompose(value -> {
            if (value != null) {
                return (CompletionStage<V>) putOperationAsync(key, (V) value).handle((r, ex) -> {
                    RFuture<Void> f = lock.unlockAsync(threadId);
                    if (ex != null) {
                        log.error("Unable to store value by key " + key + " for map " + getRawName(), ex);
                        return f;
                    }
                    return f.thenApply(res -> value);
                }).thenCompose(f -> f);
            }
            return CompletableFuture.completedFuture((V) value);
        }).toCompletableFuture();
    }

    final class EntrySet extends AbstractSet<Map.Entry<K, V>> {

        private final String keyPattern;
        private final int count;
        
        EntrySet(String keyPattern, int count) {
            this.keyPattern = keyPattern;
            this.count = count;
        }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return entryIterator(keyPattern, count);
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            Object key = e.getKey();
            V value = get(key);
            return value != null && value.equals(e);
        }

        @Override
        public boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                Object key = e.getKey();
                Object value = e.getValue();
                return RedissonMap.this.remove(key, value);
            }
            return false;
        }

        @Override
        public int size() {
            if (keyPattern != null) {
                int size = 0;
                for (Entry val : this) {
                    size++;
                }
                return size;
            }
            
            return RedissonMap.this.size();
        }

        @Override
        public void clear() {
            RedissonMap.this.clear();
        }

    }

    @Override
    public void destroy() {
        if (writeBehindService != null) {
            writeBehindService.stop(getRawName());
        }
    }
}
