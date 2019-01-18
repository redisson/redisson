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
package org.redisson;

import java.util.concurrent.atomic.LongAdder;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RFuture;
import org.redisson.api.RLongAdder;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonLongAdder extends RedissonBaseAdder<Long> implements RLongAdder {

    private final RAtomicLong atomicLong;
    private final LongAdder counter = new LongAdder();
    
    public RedissonLongAdder(CommandAsyncExecutor connectionManager, String name, RedissonClient redisson) {
        super(connectionManager, name, redisson);
        
        atomicLong = redisson.getAtomicLong(getName());
    }

    @Override
    protected void doReset() {
        counter.reset();
    }
    
    @Override
    protected RFuture<Long> addAndGetAsync() {
        return atomicLong.getAndAddAsync(counter.sum());
    }
    
    @Override
    protected RFuture<Long> getAndDeleteAsync() {
        return atomicLong.getAndDeleteAsync();
    }

    @Override
    public void add(long x) {
        counter.add(x);
    }

    @Override
    public void increment() {
        add(1L);
    }

    @Override
    public void decrement() {
        add(-1L);
    }

    @Override
    public long sum() {
        return get(sumAsync());
    }
    
}
