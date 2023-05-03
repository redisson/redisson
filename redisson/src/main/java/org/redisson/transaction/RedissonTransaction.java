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
package org.redisson.transaction;

import io.netty.buffer.ByteBufUtil;
import org.redisson.RedissonBatch;
import org.redisson.RedissonLocalCachedMap;
import org.redisson.RedissonObject;
import org.redisson.RedissonTopic;
import org.redisson.api.*;
import org.redisson.api.listener.MessageListener;
import org.redisson.cache.LocalCachedMapDisable;
import org.redisson.cache.LocalCachedMapDisabledKey;
import org.redisson.cache.LocalCachedMapEnable;
import org.redisson.cache.LocalCachedMessageCodec;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.AsyncCountDownLatch;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.transaction.operation.TransactionalOperation;
import org.redisson.transaction.operation.map.MapOperation;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonTransaction implements RTransaction {

    private final CommandAsyncExecutor commandExecutor;
    private final AtomicBoolean executed = new AtomicBoolean();
    
    private final TransactionOptions options;
    private List<TransactionalOperation> operations = new CopyOnWriteArrayList<>();
    private Set<String> localCaches = new HashSet<>();
    private final Map<RLocalCachedMap<?, ?>, RLocalCachedMap<?, ?>> localCacheInstances = new HashMap<>();
    private final Map<String, Object> instances = new HashMap<>();

    private RedissonTransactionalBuckets bucketsInstance;
    private RedissonTransactionalBuckets bucketsCodecInstance;
    private final long startTime = System.currentTimeMillis();
    
    private final String id;
    
    public RedissonTransaction(CommandAsyncExecutor commandExecutor, TransactionOptions options) {
        super();
        this.options = options;
        this.commandExecutor = commandExecutor;
        this.id = commandExecutor.getServiceManager().generateId();
    }
    
    public RedissonTransaction(CommandAsyncExecutor commandExecutor, TransactionOptions options,
            List<TransactionalOperation> operations, Set<String> localCaches) {
        super();
        this.commandExecutor = commandExecutor;
        this.options = options;
        this.operations = operations;
        this.localCaches = localCaches;
        this.id = commandExecutor.getServiceManager().generateId();
    }

    @Override
    public <K, V> RLocalCachedMap<K, V> getLocalCachedMap(RLocalCachedMap<K, V> fromInstance) {
        checkState();

        localCaches.add(fromInstance.getName());
        return (RLocalCachedMap<K, V>) localCacheInstances.computeIfAbsent(fromInstance, k -> {
            return new RedissonTransactionalLocalCachedMap<>(commandExecutor,
                    operations, options.getTimeout(), executed, fromInstance, id);
        });
    }
    
    @Override
    public <V> RBucket<V> getBucket(String name) {
        checkState();

        return (RBucket<V>) instances.computeIfAbsent(name, k -> {
            return new RedissonTransactionalBucket<V>(commandExecutor, options.getTimeout(), name, operations, executed, id);
        });
    }
    
    @Override
    public <V> RBucket<V> getBucket(String name, Codec codec) {
        checkState();

        return (RBucket<V>) instances.computeIfAbsent(name, k -> {
            return new RedissonTransactionalBucket<V>(codec, commandExecutor, options.getTimeout(), name, operations, executed, id);
        });
    }

    @Override
    public RBuckets getBuckets() {
        checkState();

        if (bucketsInstance == null) {
            bucketsInstance = new RedissonTransactionalBuckets(commandExecutor, options.getTimeout(), operations, executed, id);
        }
        return bucketsInstance;
    }

    @Override
    public RBuckets getBuckets(Codec codec) {
        checkState();

        if (bucketsCodecInstance == null) {
            bucketsCodecInstance = new RedissonTransactionalBuckets(codec, commandExecutor, options.getTimeout(), operations, executed, id);
        }
        return bucketsCodecInstance;
    }
    
    @Override
    public <V> RSet<V> getSet(String name) {
        checkState();

        return (RSet<V>) instances.computeIfAbsent(name, k -> {
            return new RedissonTransactionalSet<V>(commandExecutor, name, operations, options.getTimeout(), executed, id);
        });
    }
    
    @Override
    public <V> RSet<V> getSet(String name, Codec codec) {
        checkState();

        return (RSet<V>) instances.computeIfAbsent(name, k -> {
            return new RedissonTransactionalSet<V>(codec, commandExecutor, name, operations, options.getTimeout(), executed, id);
        });
    }
    
    @Override
    public <V> RSetCache<V> getSetCache(String name) {
        checkState();

        return (RSetCache<V>) instances.computeIfAbsent(name, k -> {
            return new RedissonTransactionalSetCache<V>(commandExecutor, name, operations, options.getTimeout(), executed, id);
        });
    }
    
    @Override
    public <V> RSetCache<V> getSetCache(String name, Codec codec) {
        checkState();

        return (RSetCache<V>) instances.computeIfAbsent(name, k -> {
            return new RedissonTransactionalSetCache<V>(codec, commandExecutor, name, operations, options.getTimeout(), executed, id);
        });
    }

    @Override
    public <K, V> RMap<K, V> getMap(String name) {
        checkState();

        return (RMap<K, V>) instances.computeIfAbsent(name, k -> {
            return new RedissonTransactionalMap<K, V>(commandExecutor, name, operations, options.getTimeout(), executed, id);
        });
    }

    @Override
    public <K, V> RMap<K, V> getMap(String name, Codec codec) {
        checkState();

        return (RMap<K, V>) instances.computeIfAbsent(name, k -> {
            return new RedissonTransactionalMap<K, V>(codec, commandExecutor, name, operations, options.getTimeout(), executed, id);
        });
    }

    @Override
    public <K, V> RMapCache<K, V> getMapCache(String name) {
        checkState();

        return (RMapCache<K, V>) instances.computeIfAbsent(name, k -> {
            return new RedissonTransactionalMapCache<K, V>(commandExecutor, name, operations, options.getTimeout(), executed, id);
        });
    }

    @Override
    public <K, V> RMapCache<K, V> getMapCache(String name, Codec codec) {
        checkState();

        return (RMapCache<K, V>) instances.computeIfAbsent(name, k -> {
            return new RedissonTransactionalMapCache<K, V>(codec, commandExecutor, name, operations, options.getTimeout(), executed, id);
        });
    }
    
    @Override
    public RFuture<Void> commitAsync() {
        checkState();
        
        checkTimeout();
        
        BatchOptions batchOptions = createOptions();
        
        CommandBatchService transactionExecutor = new CommandBatchService(commandExecutor, batchOptions);
        for (TransactionalOperation transactionalOperation : operations) {
            transactionalOperation.commit(transactionExecutor);
        }

        String id = commandExecutor.getServiceManager().generateId();
        CompletionStage<Map<HashKey, HashValue>> future = disableLocalCacheAsync(id, localCaches, operations);
        CompletionStage<Void> ff = future
            .handle((hashes, ex) -> {
                    if (ex != null) {
                        throw new CompletionException(new TransactionException("Unable to execute transaction", ex));
                    }

                    try {
                        checkTimeout();
                    } catch (TransactionTimeoutException e) {
                        enableLocalCacheAsync(id, hashes);
                        CompletableFuture<Map<HashKey, HashValue>> f = new CompletableFuture<>();
                        f.completeExceptionally(e);
                        return f;
                    }

                    return hashes;
                })
            .thenCompose(hashes -> {
                RFuture<BatchResult<?>> transactionFuture = transactionExecutor.executeAsync();
                return transactionFuture.handle((r, exc) -> {
                    if (exc != null) {
                        throw new CompletionException(new TransactionException("Unable to execute transaction", exc));
                    }

                    enableLocalCacheAsync(id, (Map<HashKey, HashValue>) hashes);
                    executed.set(true);
                    return null;
                });
            });
        return new CompletableFutureWrapper<>(ff);
    }

    private BatchOptions createOptions() {
        MasterSlaveEntry entry = commandExecutor.getConnectionManager().getEntrySet().iterator().next();
        int syncSlaves = entry.getAvailableSlaves();
        if (options.getSyncSlaves() == -1) {
            syncSlaves = 0;
        } else if (options.getSyncSlaves() > 0) {
            syncSlaves = options.getSyncSlaves();
        }

        BatchOptions batchOptions = BatchOptions.defaults()
                .syncSlaves(syncSlaves, options.getSyncTimeout(), TimeUnit.MILLISECONDS)
                .responseTimeout(options.getResponseTimeout(), TimeUnit.MILLISECONDS)
                .retryAttempts(options.getRetryAttempts())
                .retryInterval(options.getRetryInterval(), TimeUnit.MILLISECONDS)
                .executionMode(BatchOptions.ExecutionMode.IN_MEMORY_ATOMIC);
        return batchOptions;
    }

    @Override
    public void commit() {
        commit(localCaches, operations);
    }
    
    public void commit(Set<String> localCaches, List<TransactionalOperation> operations) {
        checkState();
        
        checkTimeout();
        
        BatchOptions batchOptions = createOptions();
        
        CommandBatchService transactionExecutor = new CommandBatchService(commandExecutor, batchOptions);
        for (TransactionalOperation transactionalOperation : operations) {
            transactionalOperation.commit(transactionExecutor);
        }

        String id = commandExecutor.getServiceManager().generateId();
        Map<HashKey, HashValue> hashes = disableLocalCache(id, localCaches, operations);
        
        try {
            checkTimeout();
        } catch (TransactionTimeoutException e) {
            enableLocalCache(id, hashes);
            throw e;
        }

        try {
            
            transactionExecutor.execute();
        } catch (Exception e) {
            throw new TransactionException("Unable to execute transaction", e);
        }
        
        enableLocalCache(id, hashes);
        
        executed.set(true);
    }

    private void checkTimeout() {
        if (options.getTimeout() != -1 && System.currentTimeMillis() - startTime > options.getTimeout()) {
            rollbackAsync();
            throw new TransactionTimeoutException("Transaction was discarded due to timeout " + options.getTimeout() + " milliseconds");
        }
    }

    private RFuture<BatchResult<?>> enableLocalCacheAsync(String requestId, Map<HashKey, HashValue> hashes) {
        if (hashes.isEmpty()) {
            return new CompletableFutureWrapper<>((BatchResult<?>) null);
        }
        
        RedissonBatch publishBatch = createBatch();
        for (Entry<HashKey, HashValue> entry : hashes.entrySet()) {
            String name = RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.TOPIC_SUFFIX);
            RTopicAsync topic = publishBatch.getTopic(name, LocalCachedMessageCodec.INSTANCE);
            LocalCachedMapEnable msg = new LocalCachedMapEnable(requestId, entry.getValue().getKeyIds().toArray(new byte[entry.getValue().getKeyIds().size()][]));
            topic.publishAsync(msg);
        }
        
        return publishBatch.executeAsync();
    }
    
    private void enableLocalCache(String requestId, Map<HashKey, HashValue> hashes) {
        if (hashes.isEmpty()) {
            return;
        }
        
        RedissonBatch publishBatch = createBatch();
        for (Entry<HashKey, HashValue> entry : hashes.entrySet()) {
            String name = RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.TOPIC_SUFFIX);
            RTopicAsync topic = publishBatch.getTopic(name, LocalCachedMessageCodec.INSTANCE);
            LocalCachedMapEnable msg = new LocalCachedMapEnable(requestId, entry.getValue().getKeyIds().toArray(new byte[entry.getValue().getKeyIds().size()][]));
            topic.publishAsync(msg);
        }
        
        try {
            publishBatch.execute();
        } catch (Exception e) {
            // skip it. Disabled local cache entries are enabled once reach timeout.
        }
    }
    
    private Map<HashKey, HashValue> disableLocalCache(String requestId, Set<String> localCaches, List<TransactionalOperation> operations) {
        if (localCaches.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Map<HashKey, HashValue> hashes = new HashMap<>(localCaches.size());
        RedissonBatch batch = createBatch();
        for (TransactionalOperation transactionalOperation : operations) {
            if (localCaches.contains(transactionalOperation.getName())) {
                MapOperation mapOperation = (MapOperation) transactionalOperation;
                RedissonLocalCachedMap<?, ?> map = (RedissonLocalCachedMap<?, ?>) mapOperation.getMap();
                
                HashKey hashKey = new HashKey(transactionalOperation.getName(), transactionalOperation.getCodec());
                byte[] key = map.getLocalCacheView().toCacheKey(mapOperation.getKey()).getKeyHash();
                HashValue value = hashes.get(hashKey);
                if (value == null) {
                    value = new HashValue();
                    hashes.put(hashKey, value);
                }
                value.getKeyIds().add(key);

                String disabledKeysName = RedissonObject.suffixName(transactionalOperation.getName(), RedissonLocalCachedMap.DISABLED_KEYS_SUFFIX);
                RMultimapCacheAsync<LocalCachedMapDisabledKey, String> multimap = batch.getListMultimapCache(disabledKeysName, transactionalOperation.getCodec());
                LocalCachedMapDisabledKey localCacheKey = new LocalCachedMapDisabledKey(requestId, options.getResponseTimeout());
                multimap.putAsync(localCacheKey, ByteBufUtil.hexDump(key));
                multimap.expireKeyAsync(localCacheKey, options.getResponseTimeout(), TimeUnit.MILLISECONDS);
            }
        }

        try {
            batch.execute();
        } catch (Exception e) {
            throw new TransactionException("Unable to execute transaction over local cached map objects: " + localCaches, e);
        }
        
        CountDownLatch latch = new CountDownLatch(hashes.size());
        List<RTopic> topics = new ArrayList<>();
        for (Entry<HashKey, HashValue> entry : hashes.entrySet()) {
            RTopic topic = RedissonTopic.createRaw(LocalCachedMessageCodec.INSTANCE,
                    commandExecutor, RedissonObject.suffixName(entry.getKey().getName(), requestId + RedissonLocalCachedMap.DISABLED_ACK_SUFFIX));
            topics.add(topic);
            topic.addListener(Object.class, new MessageListener<Object>() {
                @Override
                public void onMessage(CharSequence channel, Object msg) {
                    AtomicInteger counter = entry.getValue().getCounter();
                    if (counter.decrementAndGet() == 0) {
                        latch.countDown();
                    }
                }
            });
        }
        
        RedissonBatch publishBatch = createBatch();
        for (Entry<HashKey, HashValue> entry : hashes.entrySet()) {
            String disabledKeysName = RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.DISABLED_KEYS_SUFFIX);
            RMultimapCacheAsync<LocalCachedMapDisabledKey, String> multimap = publishBatch.getListMultimapCache(disabledKeysName, entry.getKey().getCodec());
            LocalCachedMapDisabledKey localCacheKey = new LocalCachedMapDisabledKey(requestId, options.getResponseTimeout());
            multimap.removeAllAsync(localCacheKey);
            
            RTopicAsync topic = publishBatch.getTopic(RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.TOPIC_SUFFIX), LocalCachedMessageCodec.INSTANCE);
            RFuture<Long> future = topic.publishAsync(new LocalCachedMapDisable(requestId, 
                    entry.getValue().getKeyIds().toArray(new byte[entry.getValue().getKeyIds().size()][]), options.getResponseTimeout()));
            future.thenAccept(res -> {
                int receivers = res.intValue();
                AtomicInteger counter = entry.getValue().getCounter();
                if (counter.addAndGet(receivers) == 0) {
                    latch.countDown();
                }
            });
        }

        try {
            publishBatch.execute();
        } catch (Exception e) {
            throw new TransactionException("Unable to execute transaction over local cached map objects: " + localCaches, e);
        }
        
        for (RTopic topic : topics) {
            topic.removeAllListeners();
        }
        
        try {
            latch.await(options.getResponseTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return hashes;
    }
    
    private CompletionStage<Map<HashKey, HashValue>> disableLocalCacheAsync(String requestId, Set<String> localCaches, List<TransactionalOperation> operations) {
        if (localCaches.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
        
        Map<HashKey, HashValue> hashes = new HashMap<>(localCaches.size());
        RedissonBatch batch = createBatch();
        for (TransactionalOperation transactionalOperation : operations) {
            if (localCaches.contains(transactionalOperation.getName())) {
                MapOperation mapOperation = (MapOperation) transactionalOperation;
                RedissonLocalCachedMap<?, ?> map = (RedissonLocalCachedMap<?, ?>) mapOperation.getMap();
                
                HashKey hashKey = new HashKey(transactionalOperation.getName(), transactionalOperation.getCodec());
                byte[] key = map.getLocalCacheView().toCacheKey(mapOperation.getKey()).getKeyHash();
                HashValue value = hashes.get(hashKey);
                if (value == null) {
                    value = new HashValue();
                    hashes.put(hashKey, value);
                }
                value.getKeyIds().add(key);

                String disabledKeysName = RedissonObject.suffixName(transactionalOperation.getName(), RedissonLocalCachedMap.DISABLED_KEYS_SUFFIX);
                RMultimapCacheAsync<LocalCachedMapDisabledKey, String> multimap = batch.getListMultimapCache(disabledKeysName, transactionalOperation.getCodec());
                LocalCachedMapDisabledKey localCacheKey = new LocalCachedMapDisabledKey(requestId, options.getResponseTimeout());
                multimap.putAsync(localCacheKey, ByteBufUtil.hexDump(key));
                multimap.expireKeyAsync(localCacheKey, options.getResponseTimeout(), TimeUnit.MILLISECONDS);
            }
        }

        CompletableFuture<Map<HashKey, HashValue>> result = new CompletableFuture<>();
        RFuture<BatchResult<?>> batchListener = batch.executeAsync();
        batchListener.thenAccept(res -> {
                List<CompletableFuture<?>> subscriptionFutures = new ArrayList<>();
                List<RTopic> topics = new ArrayList<>();

                AsyncCountDownLatch latch = new AsyncCountDownLatch();
                latch.latch(() -> {
                    for (RTopic t : topics) {
                        t.removeAllListenersAsync();
                    }
                    result.complete(hashes);
                }, hashes.size());

                for (Entry<HashKey, HashValue> entry : hashes.entrySet()) {
                    String disabledAckName = RedissonObject.suffixName(entry.getKey().getName(),
                                            requestId + RedissonLocalCachedMap.DISABLED_ACK_SUFFIX);
                    RTopic topic = RedissonTopic.createRaw(LocalCachedMessageCodec.INSTANCE,
                                                                commandExecutor, disabledAckName);
                    topics.add(topic);
                    RFuture<Integer> topicFuture = topic.addListenerAsync(Object.class, (channel, msg) -> {
                        AtomicInteger counter = entry.getValue().getCounter();
                        if (counter.decrementAndGet() == 0) {
                            latch.countDown();
                        }
                    });
                    subscriptionFutures.add(topicFuture.toCompletableFuture());
                }

                CompletableFuture<Void> subscriptionFuture = CompletableFuture.allOf(subscriptionFutures.toArray(new CompletableFuture[0]));
                subscriptionFuture.thenAccept(r -> {
                        RedissonBatch publishBatch = createBatch();
                        for (Entry<HashKey, HashValue> entry : hashes.entrySet()) {
                            String disabledKeysName = RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.DISABLED_KEYS_SUFFIX);
                            RMultimapCacheAsync<LocalCachedMapDisabledKey, String> multimap = publishBatch.getListMultimapCache(disabledKeysName, entry.getKey().getCodec());
                            LocalCachedMapDisabledKey localCacheKey = new LocalCachedMapDisabledKey(requestId, options.getResponseTimeout());
                            multimap.removeAllAsync(localCacheKey);

                            RTopicAsync topic = publishBatch.getTopic(RedissonObject.suffixName(entry.getKey().getName(),
                                                        RedissonLocalCachedMap.TOPIC_SUFFIX), LocalCachedMessageCodec.INSTANCE);
                            RFuture<Long> publishFuture = topic.publishAsync(new LocalCachedMapDisable(requestId,
                                                                    entry.getValue().getKeyIds().toArray(new byte[entry.getValue().getKeyIds().size()][]),
                                                                        options.getResponseTimeout()));
                            publishFuture.thenAccept(receivers -> {
                                AtomicInteger counter = entry.getValue().getCounter();
                                if (counter.addAndGet(receivers.intValue()) == 0) {
                                    latch.countDown();
                                }
                            });
                        }

                        RFuture<BatchResult<?>> publishFuture = publishBatch.executeAsync();
                        publishFuture.thenAccept(res2 -> {
                            commandExecutor.getServiceManager().newTimeout(timeout ->
                                    result.completeExceptionally(
                                                        new TransactionTimeoutException("Unable to execute transaction within "
                                                                + options.getResponseTimeout() + "ms")),
                                                            options.getResponseTimeout(), TimeUnit.MILLISECONDS);
                        });
                });
        });
        return result;
    }

    private RedissonBatch createBatch() {
        return new RedissonBatch(null, commandExecutor,
                                    BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.IN_MEMORY_ATOMIC));
    }

    @Override
    public void rollback() {
        rollback(operations);
    }
    
    public void rollback(List<TransactionalOperation> operations) {
        checkState();

        CommandBatchService executorService = new CommandBatchService(commandExecutor);
        for (TransactionalOperation transactionalOperation : operations) {
            transactionalOperation.rollback(executorService);
        }

        try {
            executorService.execute();
        } catch (Exception e) {
            throw new TransactionException("Unable to rollback transaction", e);
        }

        operations.clear();
        executed.set(true);
    }
    
    @Override
    public RFuture<Void> rollbackAsync() {
        checkState();

        CommandBatchService executorService = new CommandBatchService(commandExecutor);
        for (TransactionalOperation transactionalOperation : operations) {
            transactionalOperation.rollback(executorService);
        }

        RFuture<BatchResult<?>> future = executorService.executeAsync();
        CompletionStage<Void> f = future.handle((res, e) -> {
            if (e != null) {
                throw new CompletionException(new TransactionException("Unable to rollback transaction", e));
            }

            operations.clear();
            executed.set(true);
            return null;
        });
        return new CompletableFutureWrapper<>(f);
    }
    
    public Set<String> getLocalCaches() {
        return localCaches;
    }
    
    public List<TransactionalOperation> getOperations() {
        return operations;
    }

    protected void checkState() {
        if (executed.get()) {
            throw new IllegalStateException("Unable to execute operation. Transaction was finished!");
        }
    }
    
}
