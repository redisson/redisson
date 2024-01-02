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
package org.redisson;

import org.redisson.api.RFuture;
import org.redisson.api.RLongAdder;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonLongAdder extends RedissonBaseAdder<Long> implements RLongAdder {

    private final RedissonClient redisson;
    private final LongAdder counter = new LongAdder();
    
    public RedissonLongAdder(CommandAsyncExecutor connectionManager, String name, RedissonClient redisson) {
        super(connectionManager, name, redisson);

        this.redisson = redisson;
    }

    @Override
    protected void doReset() {
        counter.reset();
    }
    
    @Override
    protected RFuture<Long> addAndGetAsync(String id) {
        return redisson.getAtomicLong(getCounterName(id)).getAndAddAsync(counter.sum());
    }
    
    @Override
    protected RFuture<Long> getAndDeleteAsync(String id) {
        return redisson.getAtomicLong(getCounterName(id)).getAndDeleteAsync();
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
        return get(sumAsync(60, TimeUnit.SECONDS));
    }
    
}
