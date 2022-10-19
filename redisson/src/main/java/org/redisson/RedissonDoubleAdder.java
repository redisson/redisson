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
package org.redisson;

import java.util.concurrent.atomic.DoubleAdder;

import org.redisson.api.RDoubleAdder;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonDoubleAdder extends RedissonBaseAdder<Double> implements RDoubleAdder {

    private final DoubleAdder counter = new DoubleAdder();
    private final RedissonClient redisson;
    
    public RedissonDoubleAdder(CommandAsyncExecutor connectionManager, String name, RedissonClient redisson) {
        super(connectionManager, name, redisson);
        
        this.redisson = redisson;
    }

    @Override
    protected void doReset() {
        counter.reset();
    }
    
    @Override
    protected RFuture<Double> addAndGetAsync(String id) {
        return redisson.getAtomicDouble(getCounterName(id)).getAndAddAsync(counter.sum());
    }
    
    @Override
    protected RFuture<Double> getAndDeleteAsync(String id) {
        return redisson.getAtomicDouble(getCounterName(id)).getAndDeleteAsync();
    }

    @Override
    public void add(double x) {
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
    public double sum() {
        return get(sumAsync());
    }

}
