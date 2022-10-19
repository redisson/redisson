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
package org.redisson.transaction.operation;

import org.redisson.RedissonKeys;
import org.redisson.RedissonLock;
import org.redisson.api.RKeys;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.RedissonTransactionalLock;
import org.redisson.transaction.RedissonTransactionalWriteLock;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ClearExpireOperation extends TransactionalOperation {

    private String writeLockName;
    private String lockName;
    private String transactionId;

    public ClearExpireOperation(String name, String lockName, long threadId, String transactionId) {
        super(name, null, threadId);
        this.lockName = lockName;
        this.transactionId = transactionId;
    }

    public ClearExpireOperation(String name, String lockName, String writeLockName, long threadId, String transactionId) {
        super(name, null, threadId);
        this.lockName = lockName;
        this.transactionId = transactionId;
        this.writeLockName = writeLockName;
    }

    @Override
    public void commit(CommandAsyncExecutor commandExecutor) {
        RKeys keys = new RedissonKeys(commandExecutor);
        keys.clearExpireAsync(getName());
        if (lockName != null) {
            RedissonLock lock = new RedissonTransactionalLock(commandExecutor, lockName, transactionId);
            lock.unlockAsync(getThreadId());
        }
        if (writeLockName != null) {
            RedissonLock lock = new RedissonTransactionalWriteLock(commandExecutor, writeLockName, transactionId);
            lock.unlockAsync(getThreadId());
        }
    }

    @Override
    public void rollback(CommandAsyncExecutor commandExecutor) {
        if (lockName != null) {
            RedissonLock lock = new RedissonTransactionalLock(commandExecutor, lockName, transactionId);
            lock.unlockAsync(getThreadId());
        }
        if (writeLockName != null) {
            RedissonLock lock = new RedissonTransactionalWriteLock(commandExecutor, writeLockName, transactionId);
            lock.unlockAsync(getThreadId());
        }
    }
    
    public String getLockName() {
        return lockName;
    }

}
