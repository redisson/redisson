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
package org.redisson.transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.redisson.RedissonBuckets;
import org.redisson.RedissonKeys;
import org.redisson.RedissonMultiLock;
import org.redisson.api.RFuture;
import org.redisson.api.RKeys;
import org.redisson.api.RLock;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.transaction.operation.DeleteOperation;
import org.redisson.transaction.operation.TransactionalOperation;
import org.redisson.transaction.operation.bucket.BucketSetOperation;
import org.redisson.transaction.operation.bucket.BucketsTrySetOperation;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonTransactionalBuckets extends RedissonBuckets {

    static final Object NULL = new Object();
    
    private long timeout;
    private final AtomicBoolean executed;
    private final List<TransactionalOperation> operations;
    private Map<String, Object> state = new HashMap<>();
    private final String transactionId;
    
    public RedissonTransactionalBuckets(CommandAsyncExecutor commandExecutor, 
            long timeout, List<TransactionalOperation> operations, AtomicBoolean executed, String transactionId) {
        super(commandExecutor);
        
        this.timeout = timeout;
        this.operations = operations;
        this.executed = executed;
        this.transactionId = transactionId;
    }

    public RedissonTransactionalBuckets(Codec codec, CommandAsyncExecutor commandExecutor, 
            long timeout, List<TransactionalOperation> operations, AtomicBoolean executed, String transactionId) {
        super(codec, commandExecutor);
        
        this.timeout = timeout;
        this.operations = operations;
        this.executed = executed;
        this.transactionId = transactionId;
    }

    @Override
    public <V> RFuture<Map<String, V>> getAsync(String... keys) {
        checkState();
        
        if (keys.length == 0) {
            return RedissonPromise.newSucceededFuture(Collections.emptyMap());
        }
        
        Set<String> keysToLoad = new HashSet<>();
        Map<String, V> map = new LinkedHashMap<>();
        for (String key : keys) {
            Object value = state.get(key);
            if (value != null) {
                if (value != NULL) {
                    map.put(key, (V) value);
                }
            } else {
                keysToLoad.add(key);
            }
        }
        
        if (keysToLoad.isEmpty()) {
            return RedissonPromise.newSucceededFuture(map);
        }
        
        RPromise<Map<String, V>> result = new RedissonPromise<>();
        super.getAsync(keysToLoad.toArray(new String[keysToLoad.size()])).onComplete((res, e) -> {
            if (e != null) {
                result.tryFailure(e);
                return;
            }
            
            map.putAll((Map<String, V>) res);
            result.trySuccess(map);
        });
        return result;
    }
    
    @Override
    public RFuture<Void> setAsync(Map<String, ?> buckets) {
        checkState();
        
        RPromise<Void> result = new RedissonPromise<>();
        executeLocked(result, () -> {
            for (Entry<String, ?> entry : buckets.entrySet()) {
                operations.add(new BucketSetOperation<>(entry.getKey(), getLockName(entry.getKey()), codec, entry.getValue(), transactionId));
                if (entry.getValue() == null) {
                    state.put(entry.getKey(), NULL);
                } else {
                    state.put(entry.getKey(), entry.getValue());
                }
            }
            result.trySuccess(null);
        }, buckets.keySet());
        return result;
    }
    
    @Override
    public RFuture<Long> deleteAsync(String... keys) {
        checkState();
        RPromise<Long> result = new RedissonPromise<>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                AtomicLong counter = new AtomicLong();
                AtomicLong executions = new AtomicLong(keys.length);
                for (String key : keys) {
                    Object st = state.get(key);
                    if (st != null) {
                        operations.add(new DeleteOperation(key, getLockName(key), transactionId));
                        if (st != NULL) {
                            state.put(key, NULL);
                            counter.incrementAndGet();
                        }
                        if (executions.decrementAndGet() == 0) {
                            result.trySuccess(counter.get());
                        }
                        continue;
                    }
                    
                    RedissonKeys ks = new RedissonKeys(commandExecutor);
                    ks.countExistsAsync(key).onComplete((res, e) -> {
                        if (e != null) {
                            result.tryFailure(e);
                            return;
                        }
                        
                        if (res > 0) {
                            operations.add(new DeleteOperation(key, getLockName(key), transactionId));
                            state.put(key, NULL);
                            counter.incrementAndGet();
                        }
                        
                        if (executions.decrementAndGet() == 0) {
                            result.trySuccess(counter.get());
                        }
                    });
                }
            }
        }, Arrays.asList(keys));
        return result;
    }
    
    @Override
    public RFuture<Boolean> trySetAsync(Map<String, ?> buckets) {
        checkState();
        
        RPromise<Boolean> result = new RedissonPromise<>();
        executeLocked(result, () -> {
            Set<String> keysToSet = new HashSet<>();
            for (String key : buckets.keySet()) {
                Object value = state.get(key);
                if (value != null) {
                    if (value != NULL) {
                        operations.add(new BucketsTrySetOperation(codec, (Map<String, Object>) buckets, transactionId));
                        result.trySuccess(false);
                        return;
                    }
                } else {
                    keysToSet.add(key);
                }
            }
            
            if (keysToSet.isEmpty()) {
                operations.add(new BucketsTrySetOperation(codec, (Map<String, Object>) buckets, transactionId));
                state.putAll(buckets);
                result.trySuccess(true);
                return;
            }
            
            RKeys keys = new RedissonKeys(commandExecutor);
            String[] ks = keysToSet.toArray(new String[keysToSet.size()]);
            keys.countExistsAsync(ks).onComplete((res, e) -> {
                if (e != null) {
                    result.tryFailure(e);
                    return;
                }
                
                operations.add(new BucketsTrySetOperation(codec, (Map<String, Object>) buckets, transactionId));
                if (res == 0) {
                    state.putAll(buckets);
                    result.trySuccess(true);
                } else {
                    result.trySuccess(false);
                }
            });
        }, buckets.keySet());
        return result;
    }
    
    protected <R> void executeLocked(RPromise<R> promise, Runnable runnable, Collection<String> keys) {
        List<RLock> locks = new ArrayList<>(keys.size());
        for (String key : keys) {
            RLock lock = getLock(key);
            locks.add(lock);
        }
        RedissonMultiLock multiLock = new RedissonMultiLock(locks.toArray(new RLock[locks.size()]));
        long threadId = Thread.currentThread().getId();
        multiLock.lockAsync(timeout, TimeUnit.MILLISECONDS).onComplete((res, e) -> {
            if (e == null) {
                runnable.run();
            } else {
                multiLock.unlockAsync(threadId);
                promise.tryFailure(e);
            }
        });
    }
    
    private RLock getLock(String name) {
        return new RedissonTransactionalLock(commandExecutor, getLockName(name), transactionId);
    }

    private String getLockName(String name) {
        return name + ":transaction_lock";
    }

    protected void checkState() {
        if (executed.get()) {
            throw new IllegalStateException("Unable to execute operation. Transaction is in finished state!");
        }
    }
    
}
