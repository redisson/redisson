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

import java.util.Map;

import org.redisson.RedissonBuckets;
import org.redisson.api.RBuckets;
import org.redisson.api.RLock;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.RedissonTransactionalLock;
import org.redisson.transaction.operation.TransactionalOperation;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class BucketsTrySetOperation extends TransactionalOperation {

    private String transactionId;
    private Map<String, Object> values;
    
    public BucketsTrySetOperation(Codec codec, Map<String, Object> values, String transactionId) {
        super(null, codec);
        this.values = values;
        this.transactionId = transactionId;
    }

    @Override
    public void commit(CommandAsyncExecutor commandExecutor) {
        RBuckets bucket = new RedissonBuckets(codec, commandExecutor);
        bucket.trySetAsync(values);
        
        unlock(commandExecutor);
    }

    protected void unlock(CommandAsyncExecutor commandExecutor) {
        for (String key : values.keySet()) {
            RLock lock = new RedissonTransactionalLock(commandExecutor, getLockName(key), transactionId);
            lock.unlockAsync();
        }
    }

    @Override
    public void rollback(CommandAsyncExecutor commandExecutor) {
        unlock(commandExecutor);
    }
    
    public Map<String, Object> getValues() {
        return values;
    }

    private String getLockName(String name) {
        return name + ":transaction_lock";
    }

}
