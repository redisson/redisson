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
import org.redisson.api.RSet;
import org.redisson.client.RedisClient;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.operation.TransactionalOperation;
import org.redisson.transaction.operation.set.AddOperation;
import org.redisson.transaction.operation.set.MoveOperation;
import org.redisson.transaction.operation.set.RemoveOperation;

import java.util.List;
import java.util.Set;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class TransactionalSet<V> extends BaseTransactionalSet<V> {

    private final RSet<V> set;
    private final String transactionId;
    
    public TransactionalSet(CommandAsyncExecutor commandExecutor, long timeout, List<TransactionalOperation> operations,
            RSet<V> set, String transactionId) {
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
    
    @Override
    protected TransactionalOperation createAddOperation(V value, long threadId) {
        return new AddOperation(set, value, lockName, transactionId, threadId);
    }
    
    @Override
    protected MoveOperation createMoveOperation(String destination, V value, long threadId) {
        return new MoveOperation(set, destination, threadId, value, transactionId);
    }

    @Override
    protected TransactionalOperation createRemoveOperation(Object value, long threadId) {
        return new RemoveOperation(set, value, lockName, transactionId, threadId);
    }
    
}
