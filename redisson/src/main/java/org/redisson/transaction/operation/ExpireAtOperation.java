/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.RedissonBucket;
import org.redisson.RedissonLock;
import org.redisson.api.RFuture;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.RedissonTransactionalLock;
import org.redisson.transaction.RedissonTransactionalWriteLock;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ExpireAtOperation extends TransactionalOperation {

    public static final class RedissonBucketExtended extends RedissonBucket {

        public RedissonBucketExtended(CommandAsyncExecutor connectionManager, String name) {
            super(connectionManager, name);
        }

        @Override
        protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
            return super.expireAtAsync(timestamp, param, keys);
        }

    }


    private String writeLockName;
    private String lockName;
    private String transactionId;
    private long timestamp;
    private String param;
    private String[] keys;

    public ExpireAtOperation(String name) {
        this(name, null, 0, null, 0, null, (String[]) null);
    }

    public ExpireAtOperation(String name, String lockName, long threadId, String transactionId, long timestamp, String param, String... keys) {
        super(name, null, threadId);
        this.lockName = lockName;
        this.transactionId = transactionId;
        this.timestamp = timestamp;
        this.param = param;
        this.keys = keys;
    }

    public ExpireAtOperation(String name, String lockName, String writeLockName, long threadId, String transactionId, long timestamp, String param, String... keys) {
        super(name, null, threadId);
        this.lockName = lockName;
        this.transactionId = transactionId;
        this.timestamp = timestamp;
        this.param = param;
        this.keys = keys;
        this.writeLockName = writeLockName;
    }

    @Override
    public void commit(CommandAsyncExecutor commandExecutor) {
        RedissonBucketExtended bucket = new RedissonBucketExtended(commandExecutor, name);
        bucket.expireAtAsync(timestamp, param, keys);
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
