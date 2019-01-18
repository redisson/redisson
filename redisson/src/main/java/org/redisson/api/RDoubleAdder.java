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
package org.redisson.api;

import java.util.concurrent.TimeUnit;

/**
 * Distributed implementation of {@link java.util.concurrent.atomic.DoubleAdder}
 * <p>
 * Internal state maintained on client side.
 * 
 * @author Nikita Koksharov
 *
 */
public interface RDoubleAdder extends RExpirable, RDestroyable {

    /**
     * Adds value
     * 
     * @param x - value
     */
    void add(double x);
    
    /**
     * Increments value
     */
    void increment();

    /**
     * Decrements value
     */
    void decrement();
    
    /**
     * Accumulates sum across all RDoubleAdder instances
     * 
     * @return accumulated sum
     */
    double sum();
    
    /**
     * Resets value across all RDoubleAdder instances
     */
    void reset();
    
    /**
     * Accumulates sum across all RDoubleAdder instances
     * 
     * @return accumulated sum
     */
    RFuture<Double> sumAsync();

    
    /**
     * Accumulates sum across all RDoubleAdder instances 
     * within defined <code>timeout</code>.
     * 
     * @param timeout for accumulation
     * @param timeUnit for timeout
     * 
     * @return accumulated sum
     */
    RFuture<Double> sumAsync(long timeout, TimeUnit timeUnit);

    /**
     * Resets value across all RDoubleAdder instances
     * 
     * @return void
     */
    RFuture<Void> resetAsync();
    
    /**
     * Resets value across all RDoubleAdder instances 
     * within defined <code>timeout</code>.
     * 
     * @param timeout for reset
     * @param timeUnit for timeout
     * 
     * @return void
     */
    RFuture<Void> resetAsync(long timeout, TimeUnit timeUnit);

}
