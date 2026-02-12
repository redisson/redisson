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

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;
import org.redisson.api.RMap;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.operation.TransactionalOperation;
import org.redisson.transaction.operation.map.MapCacheFastPutIfAbsentOperation;
import org.redisson.transaction.operation.map.MapCacheFastPutOperation;
import org.redisson.transaction.operation.map.MapCachePutIfAbsentOperation;
import org.redisson.transaction.operation.map.MapCachePutOperation;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class BaseTransactionalMapCache<K, V> extends BaseTransactionalMap<K, V> {

    public BaseTransactionalMapCache(CommandAsyncExecutor commandExecutor, long timeout, List<TransactionalOperation> operations, RMap<K, V> map, String transactionId) {
        super(commandExecutor, timeout, operations, map, transactionId);
    }
    
    public RFuture<V> putIfAbsentAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        long threadId = Thread.currentThread().getId();
        return putIfAbsentOperationAsync(key, value, new MapCachePutIfAbsentOperation(map, key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit, transactionId, threadId));
    }
    
    public RFuture<Boolean> fastPutOperationAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        long threadId = Thread.currentThread().getId();
        return fastPutOperationAsync(key, value, new MapCacheFastPutOperation(map, key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit, transactionId, threadId));
    }
    
    public RFuture<V> putOperationAsync(K key, V value, long ttlTimeout, long maxIdleTimeout, long maxIdleDelta, long ttlTimeoutDelta) {
        long threadId = Thread.currentThread().getId();
        return putOperationAsync(key, value, new MapCachePutOperation(map, key, value,
                ttlTimeoutDelta, TimeUnit.MILLISECONDS, maxIdleDelta, TimeUnit.MILLISECONDS, transactionId, threadId));
    }
    
    public RFuture<Boolean> fastPutIfAbsentAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        long threadId = Thread.currentThread().getId();
        return fastPutIfAbsentOperationAsync(key, value, new MapCacheFastPutIfAbsentOperation(map, key, value, 
                ttl, ttlUnit, maxIdleTime, maxIdleUnit, transactionId, threadId));
    }
    
}
