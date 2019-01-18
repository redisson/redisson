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
package org.redisson.api.mapreduce;

import java.util.Map;

import org.redisson.api.RFuture;

/**
 * Contains methods for MapReduce process execution.
 * 
 * @author Nikita Koksharov
 *
 * @param <VIn> input value
 * @param <KOut> output key
 * @param <VOut> output value
 */
public interface RMapReduceExecutor<VIn, KOut, VOut> {

    /**
     * Executes MapReduce process across Redisson Nodes
     * 
     * @return map containing reduced keys and values
     */
    Map<KOut, VOut> execute();
    
    /**
     * Executes MapReduce process across Redisson Nodes
     * in asynchronous mode
     * 
     * @return map containing reduced keys and values
     */
    RFuture<Map<KOut, VOut>> executeAsync();
    
    /**
     * Executes MapReduce process across Redisson Nodes
     * and stores result in map with <code>resultMapName</code>
     * 
     * @param resultMapName - destination map name
     */
    void execute(String resultMapName);
    
    /**
     * Executes MapReduce process across Redisson Nodes
     * in asynchronous mode and stores result in map with <code>resultMapName</code>
     * 
     * @param resultMapName - destination map name
     * @return void
     */
    RFuture<Void> executeAsync(String resultMapName);
    
    /**
     * Executes MapReduce process across Redisson Nodes
     * and collides result using defined <code>collator</code>
     * 
     * @param <R> result type
     * @param collator applied to result
     * @return collated result
     */
    <R> R execute(RCollator<KOut, VOut, R> collator);
    
    /**
     * Executes MapReduce process across Redisson Nodes
     * in asynchronous mode and collides result using defined <code>collator</code>
     * 
     * @param <R> result type
     * @param collator applied to result
     * @return collated result
     */
    <R> RFuture<R> executeAsync(RCollator<KOut, VOut, R> collator);
    
}
