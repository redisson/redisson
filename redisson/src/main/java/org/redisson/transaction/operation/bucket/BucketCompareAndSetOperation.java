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
package org.redisson.transaction.operation.bucket;

import org.redisson.RedissonBucket;
import org.redisson.RedissonLock;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.RedissonTransactionalLock;
import org.redisson.transaction.operation.TransactionalOperation;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class BucketCompareAndSetOperation<V> extends TransactionalOperation {

    private V expected;
    private V value;
    private String lockName;
    private String transactionId;
    
    public BucketCompareAndSetOperation(String name, String lockName, Codec codec, V expected, V value, String transactionId) {
        super(name, codec);
        this.expected = expected;
        this.value = value;
        this.lockName = lockName;
        this.transactionId = transactionId;
    }

    @Override
    public void commit(CommandAsyncExecutor commandExecutor) {
        RedissonBucket<V> bucket = new RedissonBucket<V>(codec, commandExecutor, name);
        bucket.compareAndSetAsync(expected, value);
        RedissonLock lock = new RedissonTransactionalLock(commandExecutor, lockName, transactionId);
        lock.unlockAsync();
    }

    @Override
    public void rollback(CommandAsyncExecutor commandExecutor) {
        RedissonLock lock = new RedissonTransactionalLock(commandExecutor, lockName, transactionId);
        lock.unlockAsync();
    }
    
    public V getExpected() {
        return expected;
    }
    
    public V getValue() {
        return value;
    }
    
    public String getLockName() {
        return lockName;
    }

}
