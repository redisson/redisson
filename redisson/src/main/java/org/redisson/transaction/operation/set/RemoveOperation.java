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
package org.redisson.transaction.operation.set;

import org.redisson.RedissonSet;
import org.redisson.api.RObject;
import org.redisson.api.RSet;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RemoveOperation extends SetOperation {

    private Object value;

    public RemoveOperation(RObject set, Object value, String transactionId, long threadId) {
        this(set.getName(), set.getCodec(), value, transactionId, threadId);
    }
    
    public RemoveOperation(String name, Codec codec, Object value, String transactionId, long threadId) {
        super(name, codec, transactionId, threadId);
        this.value = value;
    }

    @Override
    public void commit(CommandAsyncExecutor commandExecutor) {
        RSet<Object> set = new RedissonSet<>(codec, commandExecutor, name, null);
        set.removeAsync(value);
        getLock(set, commandExecutor, value).unlockAsync(threadId);
    }

    @Override
    public void rollback(CommandAsyncExecutor commandExecutor) {
        RSet<Object> set = new RedissonSet<>(codec, commandExecutor, name, null);
        getLock(set, commandExecutor, value).unlockAsync(threadId);
    }

    public Object getValue() {
        return value;
    }
    
}
