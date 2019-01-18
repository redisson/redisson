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
import org.redisson.api.RObject;
import org.redisson.api.RSet;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class MoveOperation extends SetOperation {

    private String destinationName;
    private Object value;
    private long threadId;
    
    public MoveOperation(RObject set, String destinationName, long threadId, Object value, String transactionId) {
        this(set.getName(), set.getCodec(), destinationName, threadId, value, transactionId);
    }
    
    public MoveOperation(String name, Codec codec, String destinationName, long threadId, Object value, String transactionId) {
        super(name, codec, transactionId);
        this.destinationName = destinationName;
        this.value = value;
        this.threadId = threadId;
    }

    @Override
    public void commit(CommandAsyncExecutor commandExecutor) {
        RSet<Object> set = new RedissonSet<Object>(codec, commandExecutor, name, null);
        RSet<Object> destinationSet = new RedissonSet<Object>(codec, commandExecutor, destinationName, null);
        set.moveAsync(destinationSet.getName(), value);

        getLock(destinationSet, commandExecutor, value).unlockAsync(threadId);
        getLock(set, commandExecutor, value).unlockAsync(threadId);
    }

    @Override
    public void rollback(CommandAsyncExecutor commandExecutor) {
        RSet<Object> set = new RedissonSet<Object>(codec, commandExecutor, name, null);
        RSet<Object> destinationSet = new RedissonSet<Object>(codec, commandExecutor, destinationName, null);
        
        getLock(destinationSet, commandExecutor, value).unlockAsync(threadId);
        getLock(set, commandExecutor, value).unlockAsync(threadId);
    }
    
    public String getDestinationName() {
        return destinationName;
    }
    
    public Object getValue() {
        return value;
    }
    
    public long getThreadId() {
        return threadId;
    }

}
