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
import java.util.Map;

/**
 * Collates result from {@link RReducer} tasks and produces a single result object.
 * Executes only once.
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 * @param <R> result type
 */
public interface RCollator<K, V, R> extends Serializable {

    /**
     * Collates result map from reduce phase of MapReduce process.
     * 
     * @param resultMap contains reduced entires
     * @return single result object
     */
    R collate(Map<K, V> resultMap);
    
}
