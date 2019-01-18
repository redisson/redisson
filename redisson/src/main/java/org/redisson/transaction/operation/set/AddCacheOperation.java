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

import java.util.concurrent.TimeUnit;

import org.redisson.RedissonSetCache;
import org.redisson.api.RObject;
import org.redisson.api.RSetCache;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class AddCacheOperation extends SetOperation {

    private Object value;
    private long ttl;
    private TimeUnit timeUnit;
    
    public AddCacheOperation(RObject set, Object value, String transactionId) {
        this(set, value, 0, null, transactionId);
    }
    
    public AddCacheOperation(RObject set, Object value, long ttl, TimeUnit timeUnit, String transactionId) {
        this(set.getName(), set.getCodec(), value, ttl, timeUnit, transactionId);
    }

    public AddCacheOperation(String name, Codec codec, Object value, long ttl, TimeUnit timeUnit, String transactionId) {
        super(name, codec, transactionId);
        this.value = value;
        this.timeUnit = timeUnit;
        this.ttl = ttl;
    }

    @Override
    public void commit(CommandAsyncExecutor commandExecutor) {
        RSetCache<Object> set = new RedissonSetCache<Object>(codec, null, commandExecutor, name, null);
        if (timeUnit != null) {
            set.addAsync(value, ttl, timeUnit);
        } else {
            set.addAsync(value);
        }
        getLock(set, commandExecutor, value).unlockAsync();
    }

    @Override
    public void rollback(CommandAsyncExecutor commandExecutor) {
        RSetCache<Object> set = new RedissonSetCache<Object>(codec, null, commandExecutor, name, null);
        getLock(set, commandExecutor, value).unlockAsync();
    }

    public Object getValue() {
        return value;
    }
    
    public TimeUnit getTimeUnit() {
        return timeUnit;
    }
    
    public long getTTL() {
        return ttl;
    }
    
}
