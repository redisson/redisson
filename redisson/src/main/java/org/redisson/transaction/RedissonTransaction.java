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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.RedissonBatch;
import org.redisson.RedissonLocalCachedMap;
import org.redisson.RedissonObject;
import org.redisson.RedissonTopic;
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
import org.redisson.transaction.operation.TransactionalOperation;
import org.redisson.transaction.operation.map.MapOperation;

import io.netty.buffer.ByteBufUtil;
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
    private final List<TransactionalOperation> operations = new ArrayList<TransactionalOperation>();
    private final Set<String> localCaches = new HashSet<String>();
    private final long startTime = System.currentTimeMillis();
    
    public RedissonTransaction(CommandAsyncExecutor commandExecutor, TransactionOptions options) {
        super();
        this.options = options;
        this.commandExecutor = commandExecutor;
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
    public void commit() {
        checkState();
        
        checkTimeout();
        
        
        CommandBatchService transactionExecutor = new CommandBatchService(commandExecutor.getConnectionManager());
        for (TransactionalOperation transactionalOperation : operations) {
            transactionalOperation.commit(transactionExecutor);
        }

        String id = generateId();
        Map<TransactionalOperation, List<byte[]>> hashes = disableLocalCache(id);
        
        try {
            checkTimeout();
        } catch (TransactionTimeoutException e) {
            enableLocalCache(id, hashes);
            throw e;
        }

        int syncSlaves = 0;
        if (!commandExecutor.getConnectionManager().isClusterMode()) {
            MasterSlaveEntry entry = commandExecutor.getConnectionManager().getEntrySet().iterator().next();
            syncSlaves = entry.getAvailableClients() - 1;
        }
        
        try {
            transactionExecutor.execute(syncSlaves, options.getSyncTimeout(), false, 
                    options.getResponseTimeout(), options.getRetryAttempts(), options.getRetryInterval(), true);
        } catch (Exception e) {
            throw new TransactionException("Unable to execute transaction", e);
        }
        
        enableLocalCache(id, hashes);
        
        executed.set(true);
    }

    private void checkTimeout() {
        if (System.currentTimeMillis() - startTime > options.getTimeout()) {
            throw new TransactionTimeoutException("Transaction was discarded due to timeout " + options.getTimeout() + " milliseconds");
        }
    }

    private void enableLocalCache(String requestId, Map<TransactionalOperation, List<byte[]>> hashes) {
        if (hashes.isEmpty()) {
            return;
        }
        
        RedissonBatch publishBatch = new RedissonBatch(null, commandExecutor.getConnectionManager());
        for (Entry<TransactionalOperation, List<byte[]>> entry : hashes.entrySet()) {
            String name = RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.TOPIC_SUFFIX);
            RTopicAsync<Object> topic = publishBatch.getTopic(name, LocalCachedMessageCodec.INSTANCE);
            LocalCachedMapEnable msg = new LocalCachedMapEnable(requestId, entry.getValue().toArray(new byte[entry.getValue().size()][]));
            topic.publishAsync(msg);
        }
        
        try {
            publishBatch.execute();
        } catch (Exception e) {
            // skip it. Disabled local cache entries are enabled once reach timeout.
        }

    }

    private Map<TransactionalOperation, List<byte[]>> disableLocalCache(String requestId) {
        if (localCaches.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Map<TransactionalOperation, List<byte[]>> hashes = new HashMap<TransactionalOperation, List<byte[]>>(localCaches.size());
        RedissonBatch batch = new RedissonBatch(null, commandExecutor.getConnectionManager());
        for (TransactionalOperation transactionalOperation : operations) {
            if (localCaches.contains(transactionalOperation.getName())) {
                MapOperation mapOperation = (MapOperation) transactionalOperation;
                RedissonLocalCachedMap<?, ?> map = (RedissonLocalCachedMap<?, ?>)mapOperation.getMap();
                
                byte[] key = map.toCacheKey(mapOperation.getKey()).getKeyHash();
                List<byte[]> list = hashes.get(transactionalOperation);
                if (list == null) {
                    list = new ArrayList<byte[]>();
                    hashes.put(transactionalOperation, list);
                }
                list.add(key);

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
        
        final Map<String, AtomicInteger> map = new HashMap<String, AtomicInteger>();
        final CountDownLatch latch = new CountDownLatch(hashes.size());
        List<RTopic<Object>> topics = new ArrayList<RTopic<Object>>();
        for (final Entry<TransactionalOperation, List<byte[]>> entry : hashes.entrySet()) {
            RTopic<Object> topic = new RedissonTopic<Object>(LocalCachedMessageCodec.INSTANCE, 
                    commandExecutor, RedissonObject.suffixName(entry.getKey().getName(), requestId + RedissonLocalCachedMap.DISABLED_ACK_SUFFIX));
            topics.add(topic);
            map.put(entry.getKey().getName(), new AtomicInteger());
            topic.addListener(new MessageListener<Object>() {
                @Override
                public void onMessage(String channel, Object msg) {
                    AtomicInteger counter = map.get(entry.getKey().getName());
                    if (counter.decrementAndGet() == 0) {
                        latch.countDown();
                    }
                }
            });
        }
        
        RedissonBatch publishBatch = new RedissonBatch(null, commandExecutor.getConnectionManager());
        for (final Entry<TransactionalOperation, List<byte[]>> entry : hashes.entrySet()) {
            String disabledKeysName = RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.DISABLED_KEYS_SUFFIX);
            RMultimapCacheAsync<LocalCachedMapDisabledKey, String> multimap = publishBatch.getListMultimapCache(disabledKeysName, entry.getKey().getCodec());
            LocalCachedMapDisabledKey localCacheKey = new LocalCachedMapDisabledKey(requestId, options.getResponseTimeout());
            multimap.removeAllAsync(localCacheKey);
            
            RTopicAsync<Object> topic = publishBatch.getTopic(RedissonObject.suffixName(entry.getKey().getName(), RedissonLocalCachedMap.TOPIC_SUFFIX), LocalCachedMessageCodec.INSTANCE);
            RFuture<Long> future = topic.publishAsync(new LocalCachedMapDisable(requestId, 
                    entry.getValue().toArray(new byte[entry.getValue().size()][]), options.getResponseTimeout()));
            future.addListener(new FutureListener<Long>() {
                @Override
                public void operationComplete(Future<Long> future) throws Exception {
                    if (!future.isSuccess()) {
                        return;
                    }
                    
                    int receivers = future.getNow().intValue();
                    AtomicInteger counter = map.get(entry.getKey().getName());
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
    
    protected static String generateId() {
        byte[] id = new byte[16];
        // TODO JDK UPGRADE replace to native ThreadLocalRandom
        PlatformDependent.threadLocalRandom().nextBytes(id);
        return ByteBufUtil.hexDump(id);
    }
    
    @Override
    public void rollback() {
        checkState();

        CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
        for (TransactionalOperation transactionalOperation : operations) {
            transactionalOperation.rollback(executorService);
        }

        try {
            executorService.execute(0, 0, false, 0, 0, 0, true);
        } catch (Exception e) {
            throw new TransactionException("Unable to execute transaction", e);
        }

        operations.clear();
        executed.set(true);
    }

    protected void checkState() {
        if (executed.get()) {
            throw new IllegalStateException("Unable to execute operation. Transaction was finished!");
        }
    }
    
}
