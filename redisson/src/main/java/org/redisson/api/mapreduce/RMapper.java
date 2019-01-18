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

import java.io.Serializable;

/**
 * Mapper task invoked during map phase of MapReduce process and launched across Redisson Nodes.
 * Every task stores transformed result of input key/value into {@link RCollector} instance. 
 * Collected results are handled by {@link RReducer} instance once 
 * all Mapper tasks have finished.
 * 
 * @author Nikita Koksharov
 *
 * @param <KIn> input key
 * @param <VIn> input value
 * @param <KOut> output key
 * @param <VOut> output value
 */
public interface RMapper<KIn, VIn, KOut, VOut> extends Serializable {

    /**
     * Invoked for each Map source entry
     * 
     * @param key - input key
     * @param value - input value
     * @param collector - instance shared across all Mapper tasks
     */
    void map(KIn key, VIn value, RCollector<KOut, VOut> collector);
    
}
