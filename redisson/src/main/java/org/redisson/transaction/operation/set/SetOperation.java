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
package org.redisson.transaction.operation.set;

import org.redisson.RedissonSet;
import org.redisson.RedissonSetCache;
import org.redisson.api.RLock;
import org.redisson.api.RSet;
import org.redisson.api.RSetCache;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.transaction.RedissonTransactionalLock;
import org.redisson.transaction.operation.TransactionalOperation;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class SetOperation extends TransactionalOperation {
    
    private final String transactionId;

    public SetOperation(String name, Codec codec, String transactionId) {
        super(name, codec);
        this.transactionId = transactionId;
    }

    protected RLock getLock(RSetCache<?> setCache, CommandAsyncExecutor commandExecutor, Object value) {
        String lockName = ((RedissonSetCache<?>) setCache).getLockByValue(value, "lock");
        return new RedissonTransactionalLock(commandExecutor, lockName, transactionId);
    }

    protected RLock getLock(RSet<?> setCache, CommandAsyncExecutor commandExecutor, Object value) {
        String lockName = ((RedissonSet<?>) setCache).getLockByValue(value, "lock");
        return new RedissonTransactionalLock(commandExecutor, lockName, transactionId);
    }

}
