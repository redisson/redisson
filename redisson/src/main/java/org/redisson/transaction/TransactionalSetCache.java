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

import org.redisson.ScanIterator;
import org.redisson.ScanResult;
import org.redisson.api.RFuture;
import org.redisson.api.RSetCache;
import org.redisson.client.RedisClient;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.operation.TransactionalOperation;
import org.redisson.transaction.operation.set.AddCacheOperation;
import org.redisson.transaction.operation.set.MoveOperation;
import org.redisson.transaction.operation.set.RemoveCacheOperation;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class TransactionalSetCache<V> extends BaseTransactionalSet<V> {

    private final RSetCache<V> set;
    private final String transactionId;
    
    public TransactionalSetCache(CommandAsyncExecutor commandExecutor, long timeout, List<TransactionalOperation> operations,
            RSetCache<V> set, String transactionId) {
        super(commandExecutor, timeout, operations, set, transactionId);
        this.set = set;
        this.transactionId = transactionId;
    }

    @Override
    protected ScanResult<Object> scanIteratorSource(String name, RedisClient client, long startPos,
                                                    String pattern, int count) {
        return ((ScanIterator) set).scanIterator(name, client, startPos, pattern, count);
    }

    @Override
    protected RFuture<Set<V>> readAllAsyncSource() {
        return set.readAllAsync();
    }
    
    public RFuture<Boolean> addAsync(V value, long ttl, TimeUnit ttlUnit) {
        long threadId = Thread.currentThread().getId();
        return addAsync(value, new AddCacheOperation(set, value, ttl, ttlUnit, transactionId, threadId));
    }
    
    @Override
    protected TransactionalOperation createAddOperation(V value, long threadId) {
        return new AddCacheOperation(set, value, transactionId, threadId);
    }
    
    @Override
    protected MoveOperation createMoveOperation(String destination, V value, long threadId) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    protected TransactionalOperation createRemoveOperation(Object value, long threadId) {
        return new RemoveCacheOperation(set, value, transactionId, threadId);
    }

}
