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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.RedissonBatch;
import org.redisson.RedissonLocalCachedMap;
import org.redisson.RedissonObject;
import org.redisson.RedissonTopic;
import org.redisson.api.BatchOptions;
import org.redisson.api.BatchResult;
import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RMultimapCacheAsync;
import org.redisson.api.RSet;
import org.redisson.api.RSetCache;
import org.redisson.api.RTopic;
import org.redisson.api.RTopicAsync;
import org.redisson.api.RTransaction;
import org.redisson.api.TransactionOptions;
import org.redisson.api.listener.MessageListener;
import org.redisson.cache.LocalCachedMapDisable;
import org.redisson.cache.LocalCachedMapDisabledKey;
import org.redisson.cache.LocalCachedMapEnable;
import org.redisson.cache.LocalCachedMessageCodec;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.CountableListener;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.transaction.operation.TransactionalOperation;
import org.redisson.transaction.operation.map.MapOperation;

import io.netty.buffer.ByteBufUtil;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonTransaction implements RTransaction {

    private final CommandAsyncExecutor commandExecutor;
    private final AtomicBoolean executed = new AtomicBoolean();
    
    private final TransactionOptions options;
    private List<TransactionalOperation> operations = new CopyOnWriteArrayList<TransactionalOperation>();
    private Set<String> localCaches = new HashSet<String>();
    private final long startTime = System.currentTimeMillis();
    
    public RedissonTransaction(CommandAsyncExecutor commandExecutor, TransactionOptions options) {
        super();
        this.options = options;
        this.commandExecutor = commandExecutor;
    }
    
    public RedissonTransaction(CommandAsyncExecutor commandExecutor, TransactionOptions options,
            List<TransactionalOperation> operations, Set<String> localCaches) {
        super();
        this.commandExecutor = commandExecutor;
        this.options = options;
        this.operations = operations;
        this.localCaches = localCaches;
    }

    @Override
    public <K, V> RLocalCachedMap<K, V> getLocalCachedMap(RLocalCachedMap<K, V> fromInstance) {
        checkState();

        localCaches.add(fromInstance.getName());
        return new RedissonTransactionalLocalCachedMap<K, V>(commandExecutor,
                operations, options.getTimeout(), executed, fromInstance);
    }
    
    @Override
    public <V> RBucket<V> getBucket(String name) {
        checkState();
        
        return new RedissonTransactionalBucket<V>(commandExecutor, name, operations, executed);
    }
    
    @Override
    public <V> RBucket<V> getBucket(String name, Codec codec) {
        checkState();

        return new RedissonTransactionalBucket<V>(codec, commandExecutor, name, operations, executed);
    }

    @Override
    public <V> RSet<V> getSet(String name) {
        checkState();
        
        return new RedissonTransactionalSet<V>(commandExecutor, name, operations, options.getTimeout(), executed);        
    }
    
    @Override
    public <V> RSet<V> getSet(String name, Codec codec) {
        checkState();
        
        return new RedissonTransactionalSet<V>(codec, commandExecutor, name, operations, options.getTimeout(), executed);
    }
    
    @Override
    public <V> RSetCache<V> getSetCache(String name) {
        checkState();
        
        return new RedissonTransactionalSetCache<V>(commandExecutor, name, operations, options.getTimeout(), executed);        
    }
    
    @Override
    public <V> RSetCache<V> getSetCache(String name, Codec codec) {
        checkState();
        
        return new RedissonTransactionalSetCache<V>(codec, commandExecutor, name, operations, options.getTimeout(), executed);
    }

    @Override
    public <K, V> RMap<K, V> getMap(String name) {
        checkState();
        
        return new RedissonTransactionalMap<K, V>(commandExecutor, name, operations, options.getTimeout(), executed);
    }

    @Override
    public <K, V> RMap<K, V> getMap(String name, Codec codec) {
        checkState();
        
        return new RedissonTransactionalMap<K, V>(codec, commandExecutor, name, operations, options.getTimeout(), executed);
    }

    @Override
    public <K, V> RMapCache<K, V> getMapCache(String name) {
        checkState();
        
        return new RedissonTransactionalMapCache<K, V>(commandExecutor, name, operations, options.getTimeout(), executed);
    }

    @Override
    public <K, V> RMapCache<K, V> getMapCache(String name, Codec codec) {
        checkState();
        
        return new RedissonTransactionalMapCache<K, V>(codec, commandExecutor, name, operations, options.getTimeout(), executed);
    }
    
    @Override
    public RFuture<Void> commitAsync() {
        checkState();
        
        checkTimeout();
        
        BatchOptions batchOptions = createOptions();
        
        final CommandBatchService transactionExecutor = new CommandBatchService(commandExecutor.getConnectionManager(), batchOptions);
        for (TransactionalOperation transactionalOperation : operations) {
            transactionalOperation.commit(transactionExecutor);
        }

        final String id = generateId();
        final RPromise<Void> result = new RedissonPromise<Void>();
        RFuture<Map<HashKey, HashValue>> future = disableLocalCacheAsync(id, localCaches, operations);
        future.addListener(new FutureListener<Map<HashKey, HashValue>>() {
            @Override
            public void operationComplete(Future<Map<HashKey, HashValue>> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(new TransactionException("Unable to execute transaction", future.cause()));
                    return;
                }
                
                final Map<HashKey, HashValue> hashes = future.getNow();
                try {
                    checkTimeout();
                } catch (TransactionTimeoutException e) {
                    enableLocalCacheAsync(id, hashes);
                    result.tryFailure(e);
                    return;
                }
                                
                RFuture<List<?>> transactionFuture = transactionExecutor.executeAsync();
                transactionFuture.addListener(new FutureListener<Object>() {
                    @Override
                    public void operationComplete(Future<Object> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(new TransactionException("Unable to execute transaction", future.cause()));
                            return;
                        }
                        
                        enableLocalCacheAsync(id, hashes);
                        executed.set(true);
                        
                        result.trySuccess(null);
                    }
                });
            }
        });
        return result;
    }

    private BatchOptions createOptions() {
        int syncSlaves = 0;
        if (!commandExecutor.getConnectionManager().isClusterMode()) {
            MasterSlaveEntry entry = commandExecutor.getConnectionManager().getEntrySet().iterator().next();
            syncSlaves = entry.getAvailableClients() - 1;
        }
        
        BatchOptions batchOptions = BatchOptions.defaults()
                .syncSlaves(syncSlaves, options.getSyncTimeout(), TimeUnit.MILLISECONDS)
                .responseTimeout(options.getResponseTimeout(), TimeUnit.MILLISECONDS)
                .retryAttempts(options.getRetryAttempts())
                .retryInterval(options.getRetryInterval(), TimeUnit.MILLISECONDS)
                .atomic();
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
        
        CommandBatchService transactionExecutor = new CommandBatchService(commandExecutor.getConnectionManager(), batchOptions);
        for (TransactionalOperation transactionalOperation : operations) {
            transactionalOperation.commit(transactionExecutor);
        }

        String id = generateId();
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
            return RedissonPromise.newSucceededFuture(null);
        }
        
        RedissonBatch publishBatch = new RedissonBatch(null, commandExecutor.getConnectionManager(), BatchOptions.defaults());
        for (Entry<HashKey, HashValue> entry : hashes.entrySet()) {
            String name = RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.TOPIC_SUFFIX);
            RTopicAsync<Object> topic = publishBatch.getTopic(name, LocalCachedMessageCodec.INSTANCE);
            LocalCachedMapEnable msg = new LocalCachedMapEnable(requestId, entry.getValue().getKeyIds().toArray(new byte[entry.getValue().getKeyIds().size()][]));
            topic.publishAsync(msg);
        }
        
        return publishBatch.executeAsync();
    }
    
    private void enableLocalCache(String requestId, Map<HashKey, HashValue> hashes) {
        if (hashes.isEmpty()) {
            return;
        }
        
        RedissonBatch publishBatch = new RedissonBatch(null, commandExecutor.getConnectionManager(), BatchOptions.defaults());
        for (Entry<HashKey, HashValue> entry : hashes.entrySet()) {
            String name = RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.TOPIC_SUFFIX);
            RTopicAsync<Object> topic = publishBatch.getTopic(name, LocalCachedMessageCodec.INSTANCE);
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
        
        Map<HashKey, HashValue> hashes = new HashMap<HashKey, HashValue>(localCaches.size());
        RedissonBatch batch = new RedissonBatch(null, commandExecutor.getConnectionManager(), BatchOptions.defaults());
        for (TransactionalOperation transactionalOperation : operations) {
            if (localCaches.contains(transactionalOperation.getName())) {
                MapOperation mapOperation = (MapOperation) transactionalOperation;
                RedissonLocalCachedMap<?, ?> map = (RedissonLocalCachedMap<?, ?>)mapOperation.getMap();
                
                HashKey hashKey = new HashKey(transactionalOperation.getName(), transactionalOperation.getCodec());
                byte[] key = map.toCacheKey(mapOperation.getKey()).getKeyHash();
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
        
        final CountDownLatch latch = new CountDownLatch(hashes.size());
        List<RTopic<Object>> topics = new ArrayList<RTopic<Object>>();
        for (final Entry<HashKey, HashValue> entry : hashes.entrySet()) {
            RTopic<Object> topic = new RedissonTopic<Object>(LocalCachedMessageCodec.INSTANCE, 
                    commandExecutor, RedissonObject.suffixName(entry.getKey().getName(), requestId + RedissonLocalCachedMap.DISABLED_ACK_SUFFIX));
            topics.add(topic);
            topic.addListener(new MessageListener<Object>() {
                @Override
                public void onMessage(String channel, Object msg) {
                    AtomicInteger counter = entry.getValue().getCounter();
                    if (counter.decrementAndGet() == 0) {
                        latch.countDown();
                    }
                }
            });
        }
        
        RedissonBatch publishBatch = new RedissonBatch(null, commandExecutor.getConnectionManager(), BatchOptions.defaults());
        for (final Entry<HashKey, HashValue> entry : hashes.entrySet()) {
            String disabledKeysName = RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.DISABLED_KEYS_SUFFIX);
            RMultimapCacheAsync<LocalCachedMapDisabledKey, String> multimap = publishBatch.getListMultimapCache(disabledKeysName, entry.getKey().getCodec());
            LocalCachedMapDisabledKey localCacheKey = new LocalCachedMapDisabledKey(requestId, options.getResponseTimeout());
            multimap.removeAllAsync(localCacheKey);
            
            RTopicAsync<Object> topic = publishBatch.getTopic(RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.TOPIC_SUFFIX), LocalCachedMessageCodec.INSTANCE);
            RFuture<Long> future = topic.publishAsync(new LocalCachedMapDisable(requestId, 
                    entry.getValue().getKeyIds().toArray(new byte[entry.getValue().getKeyIds().size()][]), options.getResponseTimeout()));
            future.addListener(new FutureListener<Long>() {
                @Override
                public void operationComplete(Future<Long> future) throws Exception {
                    if (!future.isSuccess()) {
                        return;
                    }
                    
                    int receivers = future.getNow().intValue();
                    AtomicInteger counter = entry.getValue().getCounter();
                    if (counter.addAndGet(receivers) == 0) {
                        latch.countDown();
                    }
                }
            });
        }

        try {
            publishBatch.execute();
        } catch (Exception e) {
            throw new TransactionException("Unable to execute transaction over local cached map objects: " + localCaches, e);
        }
        
        for (RTopic<Object> topic : topics) {
            topic.removeAllListeners();
        }
        
        try {
            latch.await(options.getResponseTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return hashes;
    }
    
    private RFuture<Map<HashKey, HashValue>> disableLocalCacheAsync(final String requestId, Set<String> localCaches, List<TransactionalOperation> operations) {
        if (localCaches.isEmpty()) {
            return RedissonPromise.newSucceededFuture(Collections.<HashKey, HashValue>emptyMap());
        }
        
        final RPromise<Map<HashKey, HashValue>> result = new RedissonPromise<Map<HashKey, HashValue>>();
        final Map<HashKey, HashValue> hashes = new HashMap<HashKey, HashValue>(localCaches.size());
        RedissonBatch batch = new RedissonBatch(null, commandExecutor.getConnectionManager(), BatchOptions.defaults());
        for (TransactionalOperation transactionalOperation : operations) {
            if (localCaches.contains(transactionalOperation.getName())) {
                MapOperation mapOperation = (MapOperation) transactionalOperation;
                RedissonLocalCachedMap<?, ?> map = (RedissonLocalCachedMap<?, ?>)mapOperation.getMap();
                
                HashKey hashKey = new HashKey(transactionalOperation.getName(), transactionalOperation.getCodec());
                byte[] key = map.toCacheKey(mapOperation.getKey()).getKeyHash();
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

        RFuture<BatchResult<?>> batchListener = batch.executeAsync();
        batchListener.addListener(new FutureListener<BatchResult<?>>() {
            @Override
            public void operationComplete(Future<BatchResult<?>> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                final CountableListener<Map<HashKey, HashValue>> listener = 
                                new CountableListener<Map<HashKey, HashValue>>(result, hashes, hashes.size());
                RPromise<Void> subscriptionFuture = new RedissonPromise<Void>();
                final CountableListener<Void> subscribedFutures = new CountableListener<Void>(subscriptionFuture, null, hashes.size());
                
                final List<RTopic<Object>> topics = new ArrayList<RTopic<Object>>();
                for (final Entry<HashKey, HashValue> entry : hashes.entrySet()) {
                    final String disabledAckName = RedissonObject.suffixName(entry.getKey().getName(), requestId + RedissonLocalCachedMap.DISABLED_ACK_SUFFIX);
                    RTopic<Object> topic = new RedissonTopic<Object>(LocalCachedMessageCodec.INSTANCE, 
                            commandExecutor, disabledAckName);
                    topics.add(topic);
                    RFuture<Integer> topicFuture = topic.addListenerAsync(new MessageListener<Object>() {
                        @Override
                        public void onMessage(String channel, Object msg) {
                            AtomicInteger counter = entry.getValue().getCounter();
                            if (counter.decrementAndGet() == 0) {
                                listener.decCounter();
                            }
                        }
                    });
                    topicFuture.addListener(new FutureListener<Integer>() {
                        @Override
                        public void operationComplete(Future<Integer> future) throws Exception {
                            subscribedFutures.decCounter();
                        }
                    });
                }
                
                subscriptionFuture.addListener(new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        RedissonBatch publishBatch = new RedissonBatch(null, commandExecutor.getConnectionManager(), BatchOptions.defaults());
                        for (final Entry<HashKey, HashValue> entry : hashes.entrySet()) {
                            String disabledKeysName = RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.DISABLED_KEYS_SUFFIX);
                            RMultimapCacheAsync<LocalCachedMapDisabledKey, String> multimap = publishBatch.getListMultimapCache(disabledKeysName, entry.getKey().getCodec());
                            LocalCachedMapDisabledKey localCacheKey = new LocalCachedMapDisabledKey(requestId, options.getResponseTimeout());
                            multimap.removeAllAsync(localCacheKey);
                            
                            RTopicAsync<Object> topic = publishBatch.getTopic(RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.TOPIC_SUFFIX), LocalCachedMessageCodec.INSTANCE);
                            RFuture<Long> publishFuture = topic.publishAsync(new LocalCachedMapDisable(requestId, 
                                    entry.getValue().getKeyIds().toArray(new byte[entry.getValue().getKeyIds().size()][]), options.getResponseTimeout()));
                            publishFuture.addListener(new FutureListener<Long>() {
                                @Override
                                public void operationComplete(Future<Long> future) throws Exception {
                                    if (!future.isSuccess()) {
                                        return;
                                    }
                                    
                                    int receivers = future.getNow().intValue();
                                    AtomicInteger counter = entry.getValue().getCounter();
                                    if (counter.addAndGet(receivers) == 0) {
                                        listener.decCounter();
                                    }
                                }
                            });
                        }
                        
                        RFuture<BatchResult<?>> publishFuture = publishBatch.executeAsync();
                        publishFuture.addListener(new FutureListener<BatchResult<?>>() {
                            @Override
                            public void operationComplete(Future<BatchResult<?>> future) throws Exception {
                                result.addListener(new FutureListener<Map<HashKey, HashValue>>() {
                                    @Override
                                    public void operationComplete(Future<Map<HashKey, HashValue>> future)
                                            throws Exception {
                                        for (RTopic<Object> topic : topics) {
                                            topic.removeAllListeners();
                                        }
                                    }
                                });
                                
                                if (!future.isSuccess()) {
                                    result.tryFailure(future.cause());
                                    return;
                                }
                                
                                commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
                                    @Override
                                    public void run(Timeout timeout) throws Exception {
                                        result.tryFailure(new TransactionTimeoutException("Unable to execute transaction within " + options.getResponseTimeout() + "ms"));
                                    }
                                }, options.getResponseTimeout(), TimeUnit.MILLISECONDS);
                            }
                        });
                    }
                });
            }
        });
        
        return result;
    }
    
    protected static String generateId() {
        byte[] id = new byte[16];
        // TODO JDK UPGRADE replace to native ThreadLocalRandom
        PlatformDependent.threadLocalRandom().nextBytes(id);
        return ByteBufUtil.hexDump(id);
    }

    @Override
    public void rollback() {
        rollback(operations);
    }
    
    public void rollback(List<TransactionalOperation> operations) {
        checkState();

        CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
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

        CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
        for (TransactionalOperation transactionalOperation : operations) {
            transactionalOperation.rollback(executorService);
        }

        final RPromise<Void> result = new RedissonPromise<Void>();
        RFuture<List<?>> future = executorService.executeAsync();
        future.addListener(new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(new TransactionException("Unable to rollback transaction", future.cause()));
                    return;
                }
                
                operations.clear();
                executed.set(true);
                result.trySuccess(null);
            }
        });
        return result;
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
