/**
 * Copyright 2018 Nikita Koksharov
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
import org.redisson.transaction.operation.TransactionalOperation;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RemoveOperation extends TransactionalOperation {

    private Object value;
    
    public RemoveOperation(RObject set, Object value) {
        this(set.getName(), set.getCodec(), value);
    }
    
    public RemoveOperation(String name, Codec codec, Object value) {
        super(name, codec);
        this.value = value;
    }

    @Override
    public void commit(CommandAsyncExecutor commandExecutor) {
        RSet<Object> set = new RedissonSet<Object>(codec, commandExecutor, name, null);
        set.removeAsync(value);
        set.getLock(value).unlockAsync();
    }

    @Override
    public void rollback(CommandAsyncExecutor commandExecutor) {
        RSet<Object> set = new RedissonSet<Object>(codec, commandExecutor, name, null);
        set.getLock(value).unlockAsync();
    }

    public Object getValue() {
        return value;
    }
    
}
