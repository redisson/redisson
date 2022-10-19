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

import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class TransactionalOperation {

    protected Codec codec;
    protected String name;
    protected long threadId;
    
    public TransactionalOperation() {
    }
    
    public TransactionalOperation(String name, Codec codec) {
        this.name = name;
        this.codec = codec;
    }

    public TransactionalOperation(String name, Codec codec, long threadId) {
        this.name = name;
        this.codec = codec;
        this.threadId = threadId;
    }

    public long getThreadId() {
        return threadId;
    }

    public Codec getCodec() {
        return codec;
    }
    
    public String getName() {
        return name;
    }
    
    public abstract void commit(CommandAsyncExecutor commandExecutor);
    
    public abstract void rollback(CommandAsyncExecutor commandExecutor);
    
}
