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
package org.redisson.api;

import java.util.Collection;

import reactor.core.publisher.Mono;

/**
 * Probabilistic data structure that lets you maintain counts of millions of items with extreme space efficiency.
 * Reactive interface.
 * 
 * @author Nikita Koksharov
 *
 * @param <V> type of stored values
 */
public interface RHyperLogLogReactive<V> extends RExpirableReactive {

    /**
     * Adds element into this structure.
     * 
     * @param obj - element to add
     * @return <code>true</code> if object has been added 
     *          or <code>false</code> if it was already added
     */
    Mono<Boolean> add(V obj);

    /**
     * Adds all elements contained in <code>objects</code> collection into this structure
     * 
     * @param objects - elements to add
     * @return <code>true</code> if at least one object has been added 
     *          or <code>false</code> if all were already added
     */
    Mono<Boolean> addAll(Collection<V> objects);

    /**
     * Returns approximated number of unique elements added into this structure.
     * 
     * @return approximated number of unique elements added into this structure
     */
    Mono<Long> count();

    /**
     * Returns approximated number of unique elements 
     * added into this instances and other instances defined through <code>otherLogNames</code>.
     * 
     * @param otherLogNames - name of instances
     * @return number
     */
    Mono<Long> countWith(String... otherLogNames);

    /**
     * Merges multiple instances into this instance.
     * 
     * @param otherLogNames - name of instances
     * @return void
     */
    Mono<Void> mergeWith(String... otherLogNames);

}
