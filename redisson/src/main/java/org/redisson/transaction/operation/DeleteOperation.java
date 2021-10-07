/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package org.redisson.transaction.operation;

import org.redisson.RedissonKeys;
import org.redisson.RedissonLock;
import org.redisson.api.RKeys;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.RedissonTransactionalLock;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class DeleteOperation extends TransactionalOperation {

    private String lockName;
    private String transactionId;

    public DeleteOperation(String name) {
        this(name, null, null, 0);
    }
    
    public DeleteOperation(String name, String lockName, String transactionId, long threadId) {
        super(name, null, threadId);
        this.lockName = lockName;
        this.transactionId = transactionId;
    }

    @Override
    public void commit(CommandAsyncExecutor commandExecutor) {
        RKeys keys = new RedissonKeys(commandExecutor);
        keys.deleteAsync(getName());
        if (lockName != null) {
            RedissonLock lock = new RedissonTransactionalLock(commandExecutor, lockName, transactionId);
            lock.unlockAsync();
        }
    }

    @Override
    public void rollback(CommandAsyncExecutor commandExecutor) {
        if (lockName != null) {
            RedissonLock lock = new RedissonTransactionalLock(commandExecutor, lockName, transactionId);
            lock.unlockAsync();
        }
    }
    
    public String getLockName() {
        return lockName;
    }

}
