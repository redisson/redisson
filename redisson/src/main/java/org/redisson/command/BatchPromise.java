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
package org.redisson.command;

import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.api.RFuture;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class BatchPromise<T> extends RedissonPromise<T> {

    private final AtomicBoolean executed;
    private final RFuture<Void> sentPromise = new RedissonPromise<Void>();
    
    public BatchPromise(AtomicBoolean executed) {
        super();
        this.executed = executed;
    }
    
    public RFuture<Void> getSentPromise() {
        return sentPromise;
    }
    
    @Override
    public RPromise<T> sync() throws InterruptedException {
        if (executed.get()) {
            return super.sync();
        }
        return (RPromise<T>) sentPromise.sync();
    }
    
    @Override
    public RPromise<T> syncUninterruptibly() {
        if (executed.get()) {
            return super.syncUninterruptibly();
        }
        return (RPromise<T>) sentPromise.syncUninterruptibly();
    }
    
}
